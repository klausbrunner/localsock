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
 * Test performance with connection reuse to isolate I/O performance.
 */
class ConnectionReuseTest {

    @Test
    void testConnectionReusePerformance() throws Exception {
        System.out.println("=== Connection Reuse Performance Test ===");

        InetSocketAddress address = new InetSocketAddress("localhost", 16600);
        byte[] testData = "Hello World! This is test data.".getBytes();
        int iterations = 10000; // Same as main benchmark

        CountDownLatch serverReady = new CountDownLatch(1);

        // Server that handles multiple messages on same connection
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                server.bind(address);
                serverReady.countDown();

                SocketChannel client = server.accept();
                if (client != null) {
                    ByteBuffer buffer = ByteBuffer.allocate(1024);

                    for (int i = 0; i < iterations; i++) {
                        buffer.clear();
                        int bytesRead = client.read(buffer);
                        if (bytesRead > 0) {
                            buffer.flip();
                            client.write(buffer);
                        }
                    }
                    client.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        serverReady.await();

        // Client that reuses same connection for multiple operations
        long startTime = System.currentTimeMillis();

        try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
            ByteBuffer sendBuffer = ByteBuffer.allocate(testData.length);
            ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

            for (int i = 0; i < iterations; i++) {
                // Send data
                sendBuffer.clear();
                sendBuffer.put(testData);
                sendBuffer.flip();
                client.write(sendBuffer);

                // Read response
                receiveBuffer.clear();
                client.read(receiveBuffer);
            }
        }

        long endTime = System.currentTimeMillis();

        serverTask.get();

        long duration = endTime - startTime;
        double opsPerSec = (double) iterations * 1000 / duration;

        System.out.printf("Connection reuse: %d operations in %d ms (%.2f ops/sec)%n", iterations, duration, opsPerSec);
        System.out.printf(
                "This is %.1fx faster than creating new connections%n",
                opsPerSec / 760); // Compare to our current benchmark result
    }
}
