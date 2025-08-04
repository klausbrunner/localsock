package com.localsock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

/** Modern in-memory server socket channel implementation. */
public class InMemoryServerSocketChannel extends ServerSocketChannel {

    private SocketAddress localAddress;
    private boolean bound = false;
    private String connectionKey;
    private final java.util.concurrent.locks.ReentrantLock acceptLock = new java.util.concurrent.locks.ReentrantLock();
    private final java.util.concurrent.locks.Condition connectionAvailable = acceptLock.newCondition();

    protected InMemoryServerSocketChannel(SelectorProvider provider) {
        super(provider);
    }

    @Override
    public ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
        if (bound) {
            throw new IOException("Already bound");
        }

        this.localAddress = local;
        this.bound = true;
        this.connectionKey = makeConnectionKey(local);

        // Register with the registry
        InMemorySocketRegistry.registerServer(this, local);

        return this;
    }

    @Override
    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value) {
        // Most socket options don't apply to in-memory channels
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) {
        throw new UnsupportedOperationException("Socket options not supported for in-memory channels");
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return Set.of(); // No socket options supported
    }

    @Override
    public ServerSocket socket() {
        throw new UnsupportedOperationException("Legacy ServerSocket not supported");
    }

    @Override
    public SocketChannel accept() throws IOException {
        if (!isOpen()) {
            throw new IOException("Channel is closed");
        }
        if (!bound) {
            throw new IOException("Channel not bound");
        }

        // In blocking mode, wait for a connection
        if (isBlocking()) {
            acceptLock.lock();
            try {
                SocketChannel connection;
                while ((connection = InMemorySocketRegistry.acceptConnection(connectionKey)) == null) {
                    if (!isOpen()) {
                        throw new IOException("Channel closed while waiting");
                    }
                    try {
                        // Efficient blocking using Condition variable
                        connectionAvailable.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting for connection", e);
                    }
                }
                return connection;
            } finally {
                acceptLock.unlock();
            }
        } else {
            // Non-blocking mode - return null if no connection
            return InMemorySocketRegistry.acceptConnection(connectionKey);
        }
    }

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    protected void implCloseSelectableChannel() {
        if (connectionKey != null) {
            InMemorySocketRegistry.unregisterServer(connectionKey);
        }
    }

    @Override
    protected void implConfigureBlocking(boolean block) {
        // In-memory channels can support both blocking and non-blocking modes
    }

    /**
     * Signal that a connection is available for accept().
     * Called by the registry when a client connects.
     */
    public void signalConnectionAvailable() {
        acceptLock.lock();
        try {
            connectionAvailable.signal();
        } finally {
            acceptLock.unlock();
        }
    }

    private String makeConnectionKey(SocketAddress address) {
        return switch (address) {
            case InetSocketAddress inet -> inet.getAddress().getHostAddress() + ":" + inet.getPort();
            case null -> throw new IllegalArgumentException("Address cannot be null");
            default -> address.toString();
        };
    }
}
