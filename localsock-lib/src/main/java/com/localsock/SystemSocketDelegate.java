package com.localsock;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * Delegate for creating system socket channels.
 */
public final class SystemSocketDelegate implements SocketChannelDelegate {

    private final SelectorProvider systemProvider;

    public SystemSocketDelegate(SelectorProvider systemProvider) {
        this.systemProvider = systemProvider;
    }

    @Override
    public SocketChannel createChannel(SocketAddress remote) throws IOException {
        return systemProvider.openSocketChannel();
    }

    @Override
    public boolean supports(SocketAddress address) {
        return true; // System sockets support all addresses
    }

    @Override
    public String getName() {
        return "System";
    }
}
