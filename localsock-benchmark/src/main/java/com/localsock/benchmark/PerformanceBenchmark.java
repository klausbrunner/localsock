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

    private static final int WARMUP_ITERATIONS = 5_000;
    private static final int BENCHMARK_ITERATIONS = 50_000;
    private static final byte[] TEST_DATA = "Hello, World! This is a test message.".getBytes();
    private static final int PORT = 12345;

    public static void main(String[] args) throws Exception {
        System.out.println("Socket Performance Benchmark");
        System.out.println("============================");

        // Extended warmup for JVM optimization
        System.out.println("Warming up JVM (this may take a while)...");
        for (int i = 0; i < 3; i++) {
            System.out.printf("Warmup round %d/3...%n", i + 1);
            runBenchmark("Warmup In-Memory", true, WARMUP_ITERATIONS);
            runBenchmark("Warmup Network", false, WARMUP_ITERATIONS);
        }

        // Force GC before actual benchmarks
        System.gc();
        Thread.sleep(1000);

        // Actual benchmarks
        System.out.println("\nRunning benchmarks...");

        // Run multiple benchmark rounds for statistical significance
        long[] inMemoryTimes = new long[5];
        long[] networkTimes = new long[5];

        for (int round = 0; round < 5; round++) {
            System.out.printf("Benchmark round %d/5...%n", round + 1);
            inMemoryTimes[round] = runBenchmark("In-Memory Sockets", true, BENCHMARK_ITERATIONS);
            networkTimes[round] = runBenchmark("Network Sockets", false, BENCHMARK_ITERATIONS);
            System.gc(); // GC between rounds
            Thread.sleep(100);
        }

        // Calculate averages (excluding best and worst)
        long inMemoryTime = calculateMedian(inMemoryTimes);
        long networkTime = calculateMedian(networkTimes);

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

        // Show all times for transparency
        System.out.printf("%nDetailed times:%n");
        System.out.printf("In-Memory rounds: ");
        for (long time : inMemoryTimes) {
            System.out.printf("%,d ", time);
        }
        System.out.printf("ms%n");
        System.out.printf("Network rounds:   ");
        for (long time : networkTimes) {
            System.out.printf("%,d ", time);
        }
        System.out.printf("ms%n");
    }

    private static long calculateMedian(long[] times) {
        long[] sorted = times.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2]; // Return median
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
