package com.localsock;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

/**
 * Delegate for creating in-memory socket channels.
 */
public final class InMemorySocketDelegate implements SocketChannelDelegate {

    @Override
    public SocketChannel createChannel(SocketAddress remote) throws IOException {
        return InMemorySocketRegistry.createClientChannel(remote);
    }

    @Override
    public boolean supports(SocketAddress address) {
        return InMemorySocketRegistry.isLocalConnection(address);
    }

    @Override
    public String getName() {
        return "InMemory";
    }
}
