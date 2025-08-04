package com.localsock.benchmark;

import com.localsock.InMemoryChannelProvider;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/**
 * Detailed analysis of connection establishment overhead.
 */
class ConnectionOverheadAnalysis {

    @Test
    void analyzeDetailedConnectionTiming() throws Exception {
        System.out.println("=== Detailed Connection Timing Analysis ===");

        InetSocketAddress address = new InetSocketAddress("localhost", 16700);
        int iterations = 10;

        CountDownLatch serverReady = new CountDownLatch(1);

        // Server that immediately accepts and closes
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                long bindStart = System.nanoTime();
                server.bind(address);
                long bindEnd = System.nanoTime();
                System.out.printf("Server bind took: %.3f ms%n", (bindEnd - bindStart) / 1_000_000.0);

                serverReady.countDown();

                for (int i = 0; i < iterations; i++) {
                    long acceptStart = System.nanoTime();
                    SocketChannel client = server.accept();
                    long acceptEnd = System.nanoTime();

                    System.out.printf("Server accept[%d] took: %.3f ms%n", i, (acceptEnd - acceptStart) / 1_000_000.0);

                    if (client != null) {
                        client.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        serverReady.await();
        Thread.sleep(10); // Small delay to ensure server is ready

        // Time each client connection step by step
        for (int i = 0; i < iterations; i++) {
            long connectStart = System.nanoTime();

            try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
                long connectEnd = System.nanoTime();

                long isConnectedStart = System.nanoTime();
                boolean connected = client.isConnected();
                long isConnectedEnd = System.nanoTime();

                System.out.printf(
                        "Client[%d] - connect: %.3f ms, isConnected: %.3f ms (result: %s)%n",
                        i,
                        (connectEnd - connectStart) / 1_000_000.0,
                        (isConnectedEnd - isConnectedStart) / 1_000_000.0,
                        connected);
            }
        }

        serverTask.get();
    }

    @Test
    void compareWithNetworkConnections() throws Exception {
        System.out.println("=== Network vs In-Memory Connection Comparison ===");

        // Test network connections for comparison
        int port = 16701;
        InetSocketAddress address = new InetSocketAddress("localhost", port);
        int iterations = 100;

        // Start a simple network server
        CountDownLatch serverReady = new CountDownLatch(1);
        CompletableFuture<Void> networkServer = CompletableFuture.runAsync(() -> {
            try (ServerSocketChannel server = ServerSocketChannel.open()) {
                server.bind(address);
                serverReady.countDown();

                for (int i = 0; i < iterations; i++) {
                    SocketChannel client = server.accept();
                    if (client != null) {
                        client.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        serverReady.await();
        Thread.sleep(10);

        // Time network connections
        long networkStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try (SocketChannel client = SocketChannel.open()) {
                client.connect(address);
            }
        }
        long networkEnd = System.nanoTime();
        networkServer.get();

        double networkTimeMs = (networkEnd - networkStart) / 1_000_000.0;
        double networkPerConnection = networkTimeMs / iterations;

        System.out.printf(
                "Network: %d connections in %.3f ms (%.3f ms per connection)%n",
                iterations, networkTimeMs, networkPerConnection);

        // Now test in-memory connections
        InetSocketAddress inMemoryAddress = new InetSocketAddress("localhost", 16702);
        CountDownLatch inMemoryServerReady = new CountDownLatch(1);

        CompletableFuture<Void> inMemoryServer = CompletableFuture.runAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                server.bind(inMemoryAddress);
                inMemoryServerReady.countDown();

                for (int i = 0; i < iterations; i++) {
                    SocketChannel client = server.accept();
                    if (client != null) {
                        client.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        inMemoryServerReady.await();
        Thread.sleep(10);

        long inMemoryStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(inMemoryAddress)) {
                // Just connect and close
            }
        }
        long inMemoryEnd = System.nanoTime();
        inMemoryServer.get();

        double inMemoryTimeMs = (inMemoryEnd - inMemoryStart) / 1_000_000.0;
        double inMemoryPerConnection = inMemoryTimeMs / iterations;

        System.out.printf(
                "In-Memory: %d connections in %.3f ms (%.3f ms per connection)%n",
                iterations, inMemoryTimeMs, inMemoryPerConnection);

        System.out.printf(
                "In-memory is %.1fx slower than network for connection establishment%n",
                inMemoryPerConnection / networkPerConnection);
    }
}
