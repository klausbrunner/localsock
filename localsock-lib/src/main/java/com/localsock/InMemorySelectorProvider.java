package com.localsock;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

/**
 * Custom SelectorProvider that transparently intercepts localhost socket connections and provides
 * in-memory implementations while delegating other operations to the system provider.
 */
public class InMemorySelectorProvider extends SelectorProvider {

    private final SelectorProvider systemProvider;

    public InMemorySelectorProvider() {
        // Get the system default provider to delegate to
        this.systemProvider = SelectorProvider.provider();
    }

    @Override
    public DatagramChannel openDatagramChannel() throws IOException {
        return systemProvider.openDatagramChannel();
    }

    @Override
    public DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
        return systemProvider.openDatagramChannel(family);
    }

    @Override
    public Pipe openPipe() throws IOException {
        return systemProvider.openPipe();
    }

    @Override
    public AbstractSelector openSelector() throws IOException {
        return systemProvider.openSelector();
    }

    @Override
    public ServerSocketChannel openServerSocketChannel() {
        // Always return in-memory server socket channel for transparent operation
        return new InMemoryServerSocketChannel(this);
    }

    @Override
    public SocketChannel openSocketChannel() {
        // Return in-memory socket channel - connection logic will determine
        // if it should be in-memory or delegate to system provider
        return new TransparentSocketChannel(this, systemProvider);
    }
}
