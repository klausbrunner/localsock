package com.localsock.benchmark;

import com.localsock.InMemoryChannelProvider;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Debug test to understand connection timing.
 */
class DebugConnectionTest {

    @Test
    @Timeout(15)
    void testBasicConnection() throws Exception {
        InetSocketAddress address = new InetSocketAddress("localhost", 16001);
        CountDownLatch serverBound = new CountDownLatch(1);
        CountDownLatch serverAccepting = new CountDownLatch(1);

        System.out.println("Starting debug connection test...");

        // Start server
        Thread serverThread = new Thread(() -> {
            try (ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
                System.out.println("Server: created, binding to " + address);
                server.bind(address);
                System.out.println("Server: bound to " + address);
                serverBound.countDown();

                System.out.println("Server: waiting for accept...");
                serverAccepting.countDown();
                SocketChannel client = server.accept(); // This should block until client connects
                System.out.println("Server: accepted connection");

                if (client != null) {
                    System.out.println("Server: client is connected: " + client.isConnected());

                    ByteBuffer buffer = ByteBuffer.allocate(64);
                    int read = client.read(buffer);
                    System.out.println("Server: read " + read + " bytes");

                    if (read > 0) {
                        buffer.flip();
                        client.write(buffer);
                        System.out.println("Server: echoed data back");
                    }
                    client.close();
                    System.out.println("Server: closed client connection");
                }
            } catch (Exception e) {
                System.err.println("Server error: " + e.getMessage());
                e.printStackTrace();
            }
        });

        serverThread.start();

        // Wait for server setup
        System.out.println("Waiting for server to bind...");
        serverBound.await(5, TimeUnit.SECONDS);
        System.out.println("Waiting for server to start accepting...");
        serverAccepting.await(5, TimeUnit.SECONDS);

        // Brief additional delay
        Thread.sleep(100);

        // Connect client
        System.out.println("Client: connecting to " + address);
        try (SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(address)) {
            System.out.println("Client: connection returned, isConnected=" + client.isConnected());

            String message = "test";
            ByteBuffer send = ByteBuffer.wrap(message.getBytes());
            System.out.println("Client: writing " + message.length() + " bytes");
            int written = client.write(send);
            System.out.println("Client: wrote " + written + " bytes");

            ByteBuffer receive = ByteBuffer.allocate(64);
            int read = client.read(receive);
            System.out.println("Client: read " + read + " bytes");

        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        serverThread.join(5000);
        System.out.println("Debug test complete");
    }
}
