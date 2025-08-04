package com.localsock.benchmark.jmh;

import com.localsock.InMemoryChannelProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, warmups = 1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class SocketThroughputBenchmark {

    @Param({"true", "false"})
    private boolean useInMemory;

    @Param({"37", "1024", "4096"})
    private int messageSize;

    private static final int PORT = 12347;
    private CompletableFuture<Void> serverFuture;
    private volatile boolean stopServer = false;
    private byte[] testData;

    @Setup(Level.Trial)
    public void setupServer() throws Exception {
        stopServer = false;
        testData = new byte[messageSize];
        for (int i = 0; i < messageSize; i++) {
            testData[i] = (byte) ('A' + (i % 26));
        }

        CountDownLatch serverReady = new CountDownLatch(1);

        serverFuture = CompletableFuture.runAsync(() -> {
            try {
                runEchoServer(serverReady);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        serverReady.await();
        Thread.sleep(100); // Give server time to stabilize
    }

    @TearDown(Level.Trial)
    public void stopServer() throws Exception {
        stopServer = true;
        if (serverFuture != null) {
            serverFuture.get(5, TimeUnit.SECONDS);
        }
    }

    @Benchmark
    public void echoMessage(Blackhole bh) throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", PORT);

        try (SocketChannel client =
                useInMemory ? InMemoryChannelProvider.openInMemorySocketChannel(address) : SocketChannel.open()) {

            if (!useInMemory) {
                client.connect(address);
            }

            // Send data
            ByteBuffer sendBuffer = ByteBuffer.wrap(testData);
            client.write(sendBuffer);

            // Read echo response
            ByteBuffer receiveBuffer = ByteBuffer.allocate(messageSize * 2);
            int bytesRead = client.read(receiveBuffer);

            // Consume to prevent dead code elimination
            bh.consume(bytesRead);
            bh.consume(receiveBuffer);
        }
    }

    private void runEchoServer(CountDownLatch serverReady) throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", PORT);

        try (ServerSocketChannel server =
                useInMemory ? InMemoryChannelProvider.openInMemoryServerSocketChannel() : ServerSocketChannel.open()) {

            server.bind(address);
            server.configureBlocking(true);
            server.socket().setSoTimeout(100); // 100ms timeout for graceful shutdown
            serverReady.countDown();

            while (!stopServer) {
                try {
                    SocketChannel client = server.accept();
                    try {
                        // Echo back data
                        ByteBuffer buffer = ByteBuffer.allocate(messageSize * 2);
                        int bytesRead = client.read(buffer);
                        if (bytesRead > 0) {
                            buffer.flip();
                            client.write(buffer);
                        }
                    } finally {
                        client.close();
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout is expected for graceful shutdown, continue loop
                }
            }
            // TODO: Consider Selector-based approach for even better performance in future benchmarks
        } catch (IOException e) {
            Thread.currentThread().interrupt();
        }
    }
}
