package com.localsock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** Test demonstrating in-memory socket functionality using NIO channels. */
public class InMemorySocketTest {

    private static final int PORT = 65000;
    private static final InetSocketAddress ADDRESS = new InetSocketAddress("localhost", PORT);

    public static void main(String[] args) throws Exception {
        System.out.println("Starting in-memory socket test...");

        // Start server in background
        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(InMemorySocketTest::runServer);

        // Give server time to start
        Thread.sleep(100);

        // Run client
        runClient();

        // Clean shutdown
        serverFuture.cancel(true);
        System.out.println("Test completed.");
    }

    private static void runServer() {
        try (ServerSocketChannel serverChannel = InMemoryChannelProvider.openInMemoryServerSocketChannel()) {
            serverChannel.bind(ADDRESS);
            System.out.println("Server listening on " + ADDRESS);

            while (!Thread.currentThread().isInterrupted()) {
                SocketChannel clientChannel = serverChannel.accept();
                if (clientChannel != null) {
                    handleClient(clientChannel);
                } else {
                    // No pending connections, brief pause
                    Thread.sleep(10);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (!Thread.currentThread().isInterrupted()) {
                System.err.println("Server error: " + e.getMessage());
            }
        }
    }

    private static void handleClient(SocketChannel clientChannel) {
        try {
            System.out.println("Client connected: " + clientChannel);

            // Read request
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                String request = StandardCharsets.UTF_8.decode(buffer).toString();
                System.out.println("Received: " + request.trim());

                // Send response
                String response = "HTTP/1.0 200 OK\\r\\nContent-Length: 13\\r\\n\\r\\nHello, World!";
                ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                clientChannel.write(responseBuffer);
            }

            clientChannel.close();
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private static void runClient() throws Exception {
        for (int i = 0; i < 5; i++) {
            try (SocketChannel clientChannel = InMemoryChannelProvider.openInMemorySocketChannel(ADDRESS)) {
                System.out.println("Client connecting to " + ADDRESS);

                // Send request
                String request = "GET / HTTP/1.0\\r\\n\\r\\n";
                ByteBuffer requestBuffer = ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8));
                clientChannel.write(requestBuffer);

                // Read response
                ByteBuffer responseBuffer = ByteBuffer.allocate(1024);
                int bytesRead = clientChannel.read(responseBuffer);
                if (bytesRead > 0) {
                    responseBuffer.flip();
                    String response =
                            StandardCharsets.UTF_8.decode(responseBuffer).toString();
                    System.out.println("Response: " + response.trim());
                }

            } catch (IOException e) {
                System.err.println("Client error: " + e.getMessage());
            }

            Thread.sleep(1000); // Wait between requests
        }
    }
}
