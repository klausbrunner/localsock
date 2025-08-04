package com.localsock.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import com.localsock.InMemoryChannelProvider;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Debug test for single connection.
 */
class DebugSingleTest {

    @Test
    @Timeout(10)
    void testSingleConnectionDebug() throws Exception {
        System.out.println("=== Debug Single Connection Test ===");

        InetSocketAddress address = new InetSocketAddress("localhost", 15999);
        CountDownLatch serverReady = new CountDownLatch(1);

        // Start server
        CompletableFuture<String> serverResult = CompletableFuture.supplyAsync(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                System.out.println("Server: binding to " + address);
                server.bind(address);
                System.out.println("Server: bound, signaling ready");
                serverReady.countDown();

                System.out.println("Server: calling accept()");
                SocketChannel client = server.accept();
                System.out.println("Server: accept returned: " + (client != null ? "connection" : "null"));

                if (client == null) {
                    return "SERVER_ERROR: accept returned null";
                }

                System.out.println("Server: client.isConnected() = " + client.isConnected());

                ByteBuffer buffer = ByteBuffer.allocate(64);
                System.out.println("Server: calling read()");
                int read = client.read(buffer);
                System.out.println("Server: read " + read + " bytes");

                if (read <= 0) {
                    return "SERVER_ERROR: read returned " + read;
                }

                buffer.flip();
                String message = new String(buffer.array(), 0, read);
                System.out.println("Server: received message: " + message);

                // Echo back
                buffer.rewind();
                System.out.println("Server: echoing back " + read + " bytes");
                int written = client.write(buffer);
                System.out.println("Server: wrote " + written + " bytes");

                client.close();
                System.out.println("Server: closed client");

                return message;
            } catch (Exception e) {
                System.err.println("Server error: " + e.getMessage());
                e.printStackTrace();
                return "SERVER_ERROR: " + e.getMessage();
            }
        });

        // Wait for server to be ready
        System.out.println("Waiting for server to be ready...");
        assertTrue(serverReady.await(2, TimeUnit.SECONDS), "Server should be ready");

        // Connect client
        System.out.println("Client: connecting to " + address);
        try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
            System.out.println("Client: openInMemorySocketChannel returned");
            System.out.println("Client: client.isConnected() = " + client.isConnected());

            if (!client.isConnected()) {
                fail("Client should be connected after openInMemorySocketChannel returns");
            }

            String testMessage = "hello";
            ByteBuffer send = ByteBuffer.wrap(testMessage.getBytes());
            System.out.println("Client: writing " + testMessage.length() + " bytes: " + testMessage);
            int written = client.write(send);
            System.out.println("Client: wrote " + written + " bytes");
            assertEquals(testMessage.length(), written, "Should write all bytes");

            ByteBuffer receive = ByteBuffer.allocate(64);
            System.out.println("Client: calling read()");
            int read = client.read(receive);
            System.out.println("Client: read " + read + " bytes");

            if (read != testMessage.length()) {
                fail("Expected to read " + testMessage.length() + " bytes but got " + read);
            }

            receive.flip();
            String echo = new String(receive.array(), 0, read);
            System.out.println("Client: received echo: " + echo);
            assertEquals(testMessage, echo, "Echo should match original message");
        }

        String serverMessage = serverResult.get(5, TimeUnit.SECONDS);
        System.out.println("Server final result: " + serverMessage);
        assertEquals("hello", serverMessage);

        System.out.println("=== Test Complete Successfully ===");
    }
}
