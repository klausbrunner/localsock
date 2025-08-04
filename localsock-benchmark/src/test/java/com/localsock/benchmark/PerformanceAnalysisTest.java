package com.localsock.benchmark;

import com.localsock.InMemoryChannelProvider;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/**
 * Detailed performance analysis to understand bottlenecks.
 */
class PerformanceAnalysisTest {

    @Test
    void analyzeConnectionOverhead() throws Exception {
        System.out.println("=== Connection Overhead Analysis ===");

        int iterations = 100; // Smaller number for detailed analysis
        InetSocketAddress address = new InetSocketAddress("localhost", 16500);

        // Test 1: Just connection establishment (no I/O)
        long startTime = System.currentTimeMillis();

        CountDownLatch serverReady = new CountDownLatch(1);
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                server.bind(address);
                serverReady.countDown();

                for (int i = 0; i < iterations; i++) {
                    SocketChannel client = server.accept();
                    if (client != null) {
                        client.close(); // Close immediately, no I/O
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        serverReady.await();

        // Connect clients (no I/O)
        long connectStart = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
                // Just connect and close, no I/O
            }
        }
        long connectEnd = System.currentTimeMillis();

        serverTask.get();

        System.out.printf(
                "Connection-only test: %d connections in %d ms (%.2f ms per connection)%n",
                iterations, connectEnd - connectStart, (double) (connectEnd - connectStart) / iterations);
    }

    @Test
    void analyzeIOOverhead() throws Exception {
        System.out.println("=== I/O Overhead Analysis ===");

        InetSocketAddress address = new InetSocketAddress("localhost", 16501);
        byte[] testData = "Hello World!".getBytes();

        CountDownLatch serverReady = new CountDownLatch(1);

        // Server that just echoes data
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                server.bind(address);
                serverReady.countDown();

                SocketChannel client = server.accept();
                if (client != null) {
                    ByteBuffer buffer = ByteBuffer.allocate(1024);

                    // Time the server read
                    long readStart = System.currentTimeMillis();
                    int bytesRead = client.read(buffer);
                    long readEnd = System.currentTimeMillis();

                    System.out.printf("Server read %d bytes in %d ms%n", bytesRead, readEnd - readStart);

                    // Echo back
                    buffer.flip();
                    long writeStart = System.currentTimeMillis();
                    client.write(buffer);
                    long writeEnd = System.currentTimeMillis();

                    System.out.printf("Server write took %d ms%n", writeEnd - writeStart);
                    client.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        serverReady.await();

        // Client I/O timing
        try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
            ByteBuffer sendBuffer = ByteBuffer.wrap(testData);

            long writeStart = System.currentTimeMillis();
            client.write(sendBuffer);
            long writeEnd = System.currentTimeMillis();

            System.out.printf("Client write took %d ms%n", writeEnd - writeStart);

            ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);
            long readStart = System.currentTimeMillis();
            int bytesRead = client.read(receiveBuffer);
            long readEnd = System.currentTimeMillis();

            System.out.printf("Client read %d bytes in %d ms%n", bytesRead, readEnd - readStart);
        }

        serverTask.get();
    }

    @Test
    void analyzeNonBlockingPerformance() throws Exception {
        System.out.println("=== Non-Blocking Performance Analysis ===");

        InetSocketAddress address = new InetSocketAddress("localhost", 16502);
        byte[] testData = "Hello World!".getBytes();
        int iterations = 1000;

        CountDownLatch serverReady = new CountDownLatch(1);

        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                server.bind(address);
                server.configureBlocking(false); // Non-blocking mode
                serverReady.countDown();

                int processed = 0;
                while (processed < iterations) {
                    SocketChannel client = server.accept();
                    if (client != null) {
                        client.configureBlocking(false);
                        ByteBuffer buffer = ByteBuffer.allocate(1024);

                        // Non-blocking read loop
                        int totalRead = 0;
                        while (totalRead == 0) {
                            totalRead = client.read(buffer);
                            if (totalRead == 0) {
                                Thread.yield(); // Let other threads run
                            }
                        }

                        // Echo back
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            client.write(buffer);
                        }

                        client.close();
                        processed++;
                    } else {
                        Thread.yield(); // No connection available
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        serverReady.await();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
                client.configureBlocking(false);
                ByteBuffer sendBuffer = ByteBuffer.wrap(testData);

                // Non-blocking write
                while (sendBuffer.hasRemaining()) {
                    client.write(sendBuffer);
                }

                // Non-blocking read
                ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);
                int totalRead = 0;
                while (totalRead == 0) {
                    totalRead = client.read(receiveBuffer);
                    if (totalRead == 0) {
                        Thread.yield();
                    }
                }
            }
        }

        long endTime = System.currentTimeMillis();

        serverTask.get();

        System.out.printf(
                "Non-blocking: %d operations in %d ms (%.2f ops/sec)%n",
                iterations, endTime - startTime, (double) iterations * 1000 / (endTime - startTime));
    }
}
