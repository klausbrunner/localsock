package com.localsock.benchmark;

import com.localsock.InMemoryChannelProvider;
import com.localsock.InMemorySocketRegistry;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Simple test to debug connection issues.
 */
class SimpleConnectionTest {

    @Test
    @Timeout(10)
    void testConnectionDebug() throws Exception {
        System.out.println("=== Starting Simple Connection Test ===");

        // Clear any state
        System.out.println("Active servers before test: " + InMemorySocketRegistry.getActiveServerCount());
        System.out.println("Pending connections before test: " + InMemorySocketRegistry.getPendingConnectionCount());

        InetSocketAddress address = new InetSocketAddress("localhost", 16099);
        System.out.println("Test address: " + address);

        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch clientConnected = new CountDownLatch(1);

        // Start server thread
        Thread serverThread = new Thread(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                System.out.println("Server: binding to " + address);
                server.bind(address);
                System.out.println("Server: bound to " + address);

                System.out.println("Active servers after bind: " + InMemorySocketRegistry.getActiveServerCount());
                serverReady.countDown();

                System.out.println("Server: calling accept() - should block");
                SocketChannel client = server.accept();
                System.out.println("Server: accept() returned: " + (client != null ? "connection" : "null"));

                if (client != null) {
                    System.out.println("Server: client connected: " + client.isConnected());
                    clientConnected.countDown();
                    client.close();
                }
            } catch (Exception e) {
                System.err.println("Server error: " + e.getMessage());
                e.printStackTrace();
            }
        });

        serverThread.start();

        // Wait for server to be ready
        System.out.println("Waiting for server to be ready...");
        serverReady.await(2, TimeUnit.SECONDS);

        // Small delay to ensure server is listening
        Thread.sleep(100);

        System.out.println("Active servers after server ready: " + InMemorySocketRegistry.getActiveServerCount());
        System.out.println(
                "Pending connections before client connect: " + InMemorySocketRegistry.getPendingConnectionCount());

        // Connect client
        System.out.println("Client: attempting to connect to " + address);
        System.out.println("Registry servers count before connect: " + InMemorySocketRegistry.getActiveServerCount());
        try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
            System.out.println("Client: connection successful, isConnected=" + client.isConnected());

            // Wait for server to acknowledge
            clientConnected.await(2, TimeUnit.SECONDS);
            System.out.println("Test completed successfully");
        } catch (Exception e) {
            System.err.println("Client connection failed: " + e.getMessage());
            throw e;
        }

        serverThread.join(3000);
        System.out.println("=== Test Complete ===");
    }
}
