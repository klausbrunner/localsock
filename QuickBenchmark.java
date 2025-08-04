import com.localsock.InMemoryChannelProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Quick and dirty performance comparison - no Thread.sleep() issues!
 */
public class QuickBenchmark {
    private static final int ITERATIONS = 10000;
    private static final byte[] TEST_DATA = "Hello".getBytes();
    
    public static void main(String[] args) throws Exception {
        System.out.println("Quick Socket Performance Comparison");
        System.out.println("==================================");
        
        // Warmup
        runTest("Warmup In-Memory", true, 1000);
        runTest("Warmup Network", false, 1000);
        
        // Real tests
        long inMemoryTime = runTest("In-Memory", true, ITERATIONS);
        long networkTime = runTest("Network", false, ITERATIONS);
        
        double speedup = (double) networkTime / inMemoryTime;
        System.out.printf("\nResults:\n");
        System.out.printf("In-Memory: %d ms (%.0f ops/sec)\n", inMemoryTime, ITERATIONS * 1000.0 / inMemoryTime);
        System.out.printf("Network:   %d ms (%.0f ops/sec)\n", networkTime, ITERATIONS * 1000.0 / networkTime);
        System.out.printf("Speedup: %.2fx\n", speedup);
    }
    
    private static long runTest(String name, boolean useInMemory, int iterations) throws Exception {
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        
        // Start server in background
        CompletableFuture.runAsync(() -> {
            try {
                runServer(useInMemory, iterations, serverReady, done);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        serverReady.await();
        
        long start = System.nanoTime();
        runClient(useInMemory, iterations);
        long end = System.nanoTime();
        
        done.countDown();
        
        long timeMs = (end - start) / 1_000_000;
        System.out.printf("%s: %d ms\n", name, timeMs);
        
        return timeMs;
    }
    
    private static void runServer(boolean useInMemory, int expectedConnections, 
                                CountDownLatch serverReady, CountDownLatch done) throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", 12345);
        
        try (ServerSocketChannel server = useInMemory ? 
                InMemoryChannelProvider.openInMemoryServerSocketChannel() : 
                ServerSocketChannel.open()) {
            
            server.bind(address);
            server.configureBlocking(true);
            serverReady.countDown();
            
            int connections = 0;
            while (connections < expectedConnections && done.getCount() > 0) {
                SocketChannel client = server.accept();
                if (client != null) {
                    connections++;
                    // Simple echo
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
        InetSocketAddress address = new InetSocketAddress("localhost", 12345);
        
        for (int i = 0; i < iterations; i++) {
            try (SocketChannel client = useInMemory ? 
                    InMemoryChannelProvider.openInMemorySocketChannel(address) : 
                    SocketChannel.open()) {
                
                if (!useInMemory) {
                    client.connect(address);
                }
                
                // Send and receive
                ByteBuffer sendBuffer = ByteBuffer.wrap(TEST_DATA);
                client.write(sendBuffer);
                
                ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);
                client.read(receiveBuffer);
            }
        }
    }
}