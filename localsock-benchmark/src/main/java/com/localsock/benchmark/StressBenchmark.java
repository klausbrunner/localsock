package com.localsock.benchmark;

import com.localsock.InMemoryChannelProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stress test with many concurrent connections to test reliability.
 */
public class StressBenchmark {

    private static final int CONCURRENT_CONNECTIONS = 100;
    private static final int MESSAGES_PER_CONNECTION = 50;
    private static final int SERVER_THREADS = 10;
    private static final byte[] TEST_MESSAGE = "Stress test message with some data".getBytes();
    private static final int PORT = 23456;

    public static void main(String[] args) throws Exception {
        System.out.println("Socket Stress Test");
        System.out.println("==================");

        System.out.println("Testing in-memory sockets...");
        runStressTest(true);

        System.out.println("\nTesting network sockets...");
        runStressTest(false);
    }

    private static void runStressTest(boolean useInMemory) throws Exception {
        AtomicInteger successfulConnections = new AtomicInteger(0);
        AtomicInteger failedConnections = new AtomicInteger(0);
        AtomicLong totalMessages = new AtomicLong(0);
        AtomicLong totalBytes = new AtomicLong(0);

        ExecutorService serverPool = Executors.newFixedThreadPool(SERVER_THREADS);
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch testComplete = new CountDownLatch(1);

        // Start server
        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                runStressServer(useInMemory, serverPool, serverReady, testComplete, totalMessages, totalBytes);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        // Wait for server
        serverReady.await();

        // Run concurrent clients
        long startTime = System.currentTimeMillis();

        try (ExecutorService clientPool = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<?>[] clientFutures = new CompletableFuture[CONCURRENT_CONNECTIONS];

            for (int i = 0; i < CONCURRENT_CONNECTIONS; i++) {
                final int clientId = i;
                clientFutures[i] = CompletableFuture.runAsync(
                        () -> {
                            try {
                                runStressClient(useInMemory, clientId, MESSAGES_PER_CONNECTION);
                                successfulConnections.incrementAndGet();
                            } catch (Exception e) {
                                System.err.println("Client " + clientId + " failed: " + e.getMessage());
                                failedConnections.incrementAndGet();
                            }
                        },
                        clientPool);
            }

            // Wait for all clients to complete
            CompletableFuture.allOf(clientFutures).get(30, TimeUnit.SECONDS);
        }

        long endTime = System.currentTimeMillis();
        testComplete.countDown();

        // Wait for server cleanup
        try {
            serverFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // Server might still be cleaning up
        }

        serverPool.shutdown();
        serverPool.awaitTermination(5, TimeUnit.SECONDS);

        // Report results
        long duration = endTime - startTime;
        String socketType = useInMemory ? "In-Memory" : "Network";

        System.out.printf("%s Results:%n", socketType);
        System.out.printf("  Duration: %,d ms%n", duration);
        System.out.printf("  Successful connections: %,d%n", successfulConnections.get());
        System.out.printf("  Failed connections: %,d%n", failedConnections.get());
        System.out.printf("  Total messages: %,d%n", totalMessages.get());
        System.out.printf("  Total bytes: %,d%n", totalBytes.get());
        System.out.printf("  Throughput: %.2f messages/sec%n", (double) totalMessages.get() * 1000 / duration);
        System.out.printf("  Bandwidth: %.2f MB/sec%n", (double) totalBytes.get() / duration / 1000);

        if (failedConnections.get() > 0) {
            System.out.printf("  SUCCESS RATE: %.1f%%%n", 100.0 * successfulConnections.get() / CONCURRENT_CONNECTIONS);
        } else {
            System.out.println("  SUCCESS RATE: 100.0%");
        }
    }

    private static void runStressServer(
            boolean useInMemory,
            ExecutorService serverPool,
            CountDownLatch serverReady,
            CountDownLatch testComplete,
            AtomicLong totalMessages,
            AtomicLong totalBytes)
            throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", PORT);

        try (ServerSocketChannel server =
                useInMemory ? InMemoryChannelProvider.openInMemoryServerSocketChannel() : ServerSocketChannel.open()) {

            server.bind(address);
            server.configureBlocking(false);
            serverReady.countDown();

            while (testComplete.getCount() > 0) {
                SocketChannel client = server.accept();
                if (client != null) {
                    serverPool.submit(() -> handleClient(client, totalMessages, totalBytes));
                } else {
                    Thread.sleep(1); // Brief pause to avoid busy waiting
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void handleClient(SocketChannel client, AtomicLong totalMessages, AtomicLong totalBytes) {
        try (client) {
            ByteBuffer buffer = ByteBuffer.allocate(1024);

            while (true) {
                buffer.clear();
                int bytesRead = client.read(buffer);
                if (bytesRead <= 0) break;

                totalMessages.incrementAndGet();
                totalBytes.addAndGet(bytesRead);

                // Echo back
                buffer.flip();
                client.write(buffer);
            }
        } catch (IOException e) {
            // Client disconnected or error - normal in stress test
        }
    }

    private static void runStressClient(boolean useInMemory, int clientId, int messageCount) throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", PORT);

        try (SocketChannel client =
                useInMemory ? InMemoryChannelProvider.openInMemorySocketChannel(address) : SocketChannel.open()) {

            if (!useInMemory) {
                client.connect(address);
            }

            ByteBuffer sendBuffer = ByteBuffer.allocate(TEST_MESSAGE.length);
            ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

            for (int i = 0; i < messageCount; i++) {
                // Send message
                sendBuffer.clear();
                sendBuffer.put(TEST_MESSAGE);
                sendBuffer.flip();
                client.write(sendBuffer);

                // Read echo
                receiveBuffer.clear();
                int bytesRead = client.read(receiveBuffer);
                if (bytesRead != TEST_MESSAGE.length) {
                    throw new IOException("Incomplete read: expected " + TEST_MESSAGE.length + ", got " + bytesRead);
                }

                // Small delay to simulate real work
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
