package com.localsock;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Registry for managing in-memory socket connections. Uses concurrent collections and weak
 * references to avoid memory leaks.
 */
public class InMemorySocketRegistry {

    private static final Logger LOG = Logger.getLogger(InMemorySocketRegistry.class.getName());

    // Use weak references to avoid memory leaks
    private static final ConcurrentHashMap<String, WeakReference<InMemoryServerSocketChannel>> servers =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<InMemorySocketChannel>> pendingConnections =
            new ConcurrentHashMap<>();

    /**
     * Check if a connection should use in-memory sockets. Currently checks for localhost connections
     * on the same JVM.
     */
    public static boolean isLocalConnection(SocketAddress address) {
        return switch (address) {
            case InetSocketAddress inet -> inet.getAddress().isLoopbackAddress();
            case null -> false;
            default -> false;
        };
    }

    /** Create an in-memory socket channel for client connections. */
    public static InMemorySocketChannel createClientChannel(SocketAddress remote) throws IOException {
        if (!isLocalConnection(remote)) {
            throw new IllegalArgumentException("Only local connections supported");
        }

        String connectionKey = makeConnectionKey(remote);
        LOG.fine("Client looking for server with key: " + connectionKey);

        // Look for server immediately
        WeakReference<InMemoryServerSocketChannel> serverRef = servers.get(connectionKey);
        if (serverRef != null) {
            InMemoryServerSocketChannel server = serverRef.get();
            if (server != null && server.isOpen()) {
                InMemorySocketChannel clientChannel =
                        new InMemorySocketChannel(SelectorProvider.provider(), connectionKey);

                // Add to pending connections for the server to accept
                pendingConnections
                        .computeIfAbsent(connectionKey, k -> new ConcurrentLinkedQueue<>())
                        .offer(clientChannel);
                LOG.fine("Client connection queued for server on " + connectionKey);

                // Signal the server that a connection is available
                server.signalConnectionAvailable();

                // Wait for the server to complete the connection (setPeerChannel)
                try {
                    boolean connected = clientChannel.waitForConnection(5, TimeUnit.SECONDS);
                    if (!connected) {
                        // Remove from pending queue if connection timed out
                        ConcurrentLinkedQueue<InMemorySocketChannel> pending = pendingConnections.get(connectionKey);
                        if (pending != null) {
                            pending.remove(clientChannel);
                        }
                        throw new IOException("Connection timeout - server did not accept connection to " + remote);
                    }

                    LOG.fine("Client connection established on " + connectionKey);
                    return clientChannel;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for connection", e);
                }
            } else {
                // Clean up dead reference
                servers.remove(connectionKey);
            }
        }

        throw new IOException("No server listening on " + remote);
    }

    /** Register a server socket channel. */
    public static void registerServer(InMemoryServerSocketChannel server, SocketAddress local) {
        String connectionKey = makeConnectionKey(local);
        servers.put(connectionKey, new WeakReference<>(server));
        LOG.fine("Server registered with key: " + connectionKey);
    }

    /** Accept a pending connection for a server. */
    public static InMemorySocketChannel acceptConnection(String connectionKey) {
        ConcurrentLinkedQueue<InMemorySocketChannel> pending = pendingConnections.get(connectionKey);
        if (pending != null) {
            InMemorySocketChannel clientChannel = pending.poll();
            if (clientChannel != null) {
                // Create server-side channel
                InMemorySocketChannel serverChannel =
                        new InMemorySocketChannel(SelectorProvider.provider(), connectionKey);

                // Connect the channels bidirectionally
                clientChannel.setPeerChannel(serverChannel);
                serverChannel.setPeerChannel(clientChannel);

                LOG.fine("Connection established on " + connectionKey);
                return serverChannel;
            }
        }
        return null;
    }

    /** Clean up resources for a server. */
    public static void unregisterServer(String connectionKey) {
        servers.remove(connectionKey);
        pendingConnections.remove(connectionKey);
        LOG.fine("Server unregistered from " + connectionKey);
    }

    private static String makeConnectionKey(SocketAddress address) {
        return switch (address) {
            case InetSocketAddress inet -> inet.getAddress().getHostAddress() + ":" + inet.getPort();
            case null -> throw new IllegalArgumentException("Address cannot be null");
            default -> address.toString();
        };
    }

    /** Get statistics about active connections. */
    public static int getActiveServerCount() {
        // Clean up dead references while counting
        servers.entrySet().removeIf(entry -> entry.getValue().get() == null);
        return servers.size();
    }

    public static int getPendingConnectionCount() {
        return pendingConnections.values().stream()
                .mapToInt(ConcurrentLinkedQueue::size)
                .sum();
    }
}
