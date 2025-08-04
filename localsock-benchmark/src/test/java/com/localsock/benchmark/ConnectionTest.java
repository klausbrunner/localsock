package com.localsock.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import com.localsock.InMemoryChannelProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for the connection synchronization fixes.
 */
class ConnectionTest {

    @Test
    @Timeout(10)
    void testSingleConnection() throws Exception {
        InetSocketAddress address = new InetSocketAddress("localhost", 15001);
        CountDownLatch serverReady = new CountDownLatch(1);

        // Start server
        CompletableFuture<String> serverResult = CompletableFuture.supplyAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                server.bind(address);
                serverReady.countDown();

                SocketChannel client = server.accept(); // This will block until client connects
                assertNotNull(client);

                ByteBuffer buffer = ByteBuffer.allocate(64);
                int read = client.read(buffer);
                assertTrue(read > 0);

                buffer.flip();
                String message = new String(buffer.array(), 0, read);

                // Echo back
                buffer.rewind();
                client.write(buffer);
                client.close();

                return message;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for server to be ready
        assertTrue(serverReady.await(2, TimeUnit.SECONDS));

        // Connect client - should block until server accepts and peer is established
        try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
            // With the fix, isConnected() should be true immediately
            assertTrue(client.isConnected(), "Channel should be connected after openInMemorySocketChannel returns");

            String testMessage = "hello";
            ByteBuffer send = ByteBuffer.wrap(testMessage.getBytes());
            int written = client.write(send); // This should not fail with "Channel not connected"
            assertEquals(testMessage.length(), written);

            ByteBuffer receive = ByteBuffer.allocate(64);
            int read = client.read(receive);
            assertEquals(testMessage.length(), read);

            receive.flip();
            String echo = new String(receive.array(), 0, read);
            assertEquals(testMessage, echo);
        }

        String serverMessage = serverResult.get(5, TimeUnit.SECONDS);
        assertEquals("hello", serverMessage);
    }

    @Test
    @Timeout(15)
    void testMultipleSequentialConnections() throws Exception {
        InetSocketAddress address = new InetSocketAddress("localhost", 15002);
        final int connectionCount = 10;
        AtomicInteger serverConnections = new AtomicInteger(0);
        CountDownLatch serverReady = new CountDownLatch(1);

        // Start server that handles multiple connections
        CompletableFuture<Integer> serverResult = CompletableFuture.supplyAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                server.bind(address);
                serverReady.countDown();

                while (serverConnections.get() < connectionCount) {
                    SocketChannel client = server.accept();
                    if (client != null) {
                        try {
                            // Simple echo
                            ByteBuffer buffer = ByteBuffer.allocate(64);
                            int read = client.read(buffer);
                            if (read > 0) {
                                buffer.flip();
                                client.write(buffer);
                            }
                            serverConnections.incrementAndGet();
                        } finally {
                            client.close();
                        }
                    }
                }
                return serverConnections.get();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(serverReady.await(2, TimeUnit.SECONDS));

        // Connect multiple clients sequentially
        for (int i = 0; i < connectionCount; i++) {
            try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
                assertTrue(client.isConnected(), "Connection " + i + " should be connected");

                String message = "test" + i;
                ByteBuffer send = ByteBuffer.wrap(message.getBytes());
                client.write(send); // Should not fail

                ByteBuffer receive = ByteBuffer.allocate(64);
                int read = client.read(receive);
                assertEquals(message.length(), read);
            }
        }

        int finalConnections = serverResult.get(5, TimeUnit.SECONDS);
        assertEquals(connectionCount, finalConnections);
    }

    @Test
    @Timeout(20)
    void testConcurrentConnections() throws Exception {
        InetSocketAddress address = new InetSocketAddress("localhost", 15003);
        final int concurrentCount = 5; // Reduced for reliability
        final int messagesPerConnection = 3;
        CountDownLatch serverReady = new CountDownLatch(1);
        AtomicInteger acceptedConnections = new AtomicInteger(0);
        AtomicInteger completedConnections = new AtomicInteger(0);

        // Start server
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel();
                    ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {

                server.bind(address);
                serverReady.countDown();

                while (acceptedConnections.get() < concurrentCount) {
                    SocketChannel client = server.accept();
                    if (client != null) {
                        acceptedConnections.incrementAndGet();
                        pool.submit(() -> {
                            try (client) {
                                ByteBuffer buffer = ByteBuffer.allocate(1024);

                                for (int i = 0; i < messagesPerConnection; i++) {
                                    buffer.clear();
                                    int read = client.read(buffer);
                                    if (read > 0) {
                                        buffer.flip();
                                        client.write(buffer);
                                    }
                                }
                                completedConnections.incrementAndGet();
                            } catch (IOException e) {
                                // Connection error
                            }
                        });
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(serverReady.await(2, TimeUnit.SECONDS));

        // Run concurrent clients
        try (ExecutorService clientPool = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<?>[] clientTasks = new CompletableFuture[concurrentCount];

            for (int i = 0; i < concurrentCount; i++) {
                final int clientId = i;
                clientTasks[i] = CompletableFuture.runAsync(
                        () -> {
                            try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
                                // With the fix, this should not fail
                                assertTrue(client.isConnected(), "Client " + clientId + " should be connected");

                                byte[] testData = ("message-" + clientId).getBytes();

                                for (int j = 0; j < messagesPerConnection; j++) {
                                    ByteBuffer send = ByteBuffer.wrap(testData);
                                    client.write(send); // Should not fail with race condition

                                    ByteBuffer receive = ByteBuffer.allocate(1024);
                                    int bytesRead = client.read(receive);
                                    assertEquals(testData.length, bytesRead);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException("Client " + clientId + " failed", e);
                            }
                        },
                        clientPool);
            }

            CompletableFuture.allOf(clientTasks).get(15, TimeUnit.SECONDS);
        }

        serverTask.get(5, TimeUnit.SECONDS);
        assertEquals(concurrentCount, completedConnections.get());
    }

    @Test
    @Timeout(10)
    void testConnectionTimeout() throws Exception {
        InetSocketAddress address = new InetSocketAddress("localhost", 15004);

        // Try to connect without a server - should timeout with clear error message
        IOException exception = assertThrows(IOException.class, () -> {
            InMemoryChannelProvider.openInMemorySocketChannel(address);
        });

        assertTrue(
                exception.getMessage().contains("No server listening"),
                "Should get 'No server listening' error, got: " + exception.getMessage());
    }
}
