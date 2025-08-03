package com.localsock;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

/**
 * Strategy interface for creating socket channels based on target address.
 */
public sealed interface SocketChannelDelegate permits InMemorySocketDelegate, SystemSocketDelegate {

    /**
     * Create a socket channel for the given remote address.
     */
    SocketChannel createChannel(SocketAddress remote) throws IOException;

    /**
     * Check if this delegate supports the given address.
     */
    boolean supports(SocketAddress address);

    /**
     * Get a descriptive name for this delegate type.
     */
    String getName();
}
