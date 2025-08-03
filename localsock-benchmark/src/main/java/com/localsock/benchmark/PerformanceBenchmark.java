package com.localsock.benchmark;

import com.localsock.InMemoryChannelProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Performance comparison between in-memory and network sockets.
 */
public class PerformanceBenchmark {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 10_000;
    private static final byte[] TEST_DATA = "Hello, World! This is a test message.".getBytes();
    private static final int PORT = 12345;

    public static void main(String[] args) throws Exception {
        System.out.println("Socket Performance Benchmark");
        System.out.println("============================");

        // Warmup
        System.out.println("Warming up...");
        runBenchmark("Warmup In-Memory", true, WARMUP_ITERATIONS);
        runBenchmark("Warmup Network", false, WARMUP_ITERATIONS);

        // Actual benchmarks
        System.out.println("\nRunning benchmarks...");

        long inMemoryTime = runBenchmark("In-Memory Sockets", true, BENCHMARK_ITERATIONS);
        long networkTime = runBenchmark("Network Sockets", false, BENCHMARK_ITERATIONS);

        // Results
        System.out.println("\nResults:");
        System.out.println("========");
        System.out.printf(
                "In-Memory: %,d operations in %,d ms (%.2f ops/sec)%n",
                BENCHMARK_ITERATIONS, inMemoryTime, (double) BENCHMARK_ITERATIONS * 1000 / inMemoryTime);
        System.out.printf(
                "Network:   %,d operations in %,d ms (%.2f ops/sec)%n",
                BENCHMARK_ITERATIONS, networkTime, (double) BENCHMARK_ITERATIONS * 1000 / networkTime);

        double speedup = (double) networkTime / inMemoryTime;
        System.out.printf("%nSpeedup: %.2fx faster%n", speedup);
    }

    private static long runBenchmark(String name, boolean useInMemory, int iterations) throws Exception {
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch testComplete = new CountDownLatch(1);

        // Start server
        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                runServer(useInMemory, iterations, serverReady, testComplete);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for server to be ready
        serverReady.await();

        // Run benchmark
        long startTime = System.currentTimeMillis();
        runClient(useInMemory, iterations);
        long endTime = System.currentTimeMillis();

        testComplete.countDown();
        serverFuture.get(5, TimeUnit.SECONDS);

        long duration = endTime - startTime;
        if (!name.startsWith("Warmup")) {
            System.out.printf("%s: %,d ms%n", name, duration);
        }

        return duration;
    }

    private static void runServer(
            boolean useInMemory, int expectedConnections, CountDownLatch serverReady, CountDownLatch testComplete)
            throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", PORT);

        try (ServerSocketChannel server =
                useInMemory ? InMemoryChannelProvider.openInMemoryServerSocketChannel() : ServerSocketChannel.open()) {

            server.bind(address);
            serverReady.countDown();

            int connections = 0;
            while (connections < expectedConnections && testComplete.getCount() > 0) {
                SocketChannel client = server.accept();
                if (client != null) {
                    connections++;
                    // Echo back the data
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    int bytesRead = client.read(buffer);
                    if (bytesRead > 0) {
                        buffer.flip();
                        client.write(buffer);
                    }
                    client.close();
                }
            }
        }
    }

    private static void runClient(boolean useInMemory, int iterations) throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", PORT);

        for (int i = 0; i < iterations; i++) {
            try (SocketChannel client =
                    useInMemory ? InMemoryChannelProvider.openInMemorySocketChannel(address) : SocketChannel.open()) {

                if (!useInMemory) {
                    client.connect(address);
                }

                // Send data
                ByteBuffer sendBuffer = ByteBuffer.wrap(TEST_DATA);
                client.write(sendBuffer);

                // Read response
                ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);
                client.read(receiveBuffer);
            }
        }
    }
}
