package com.localsock;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * Factory for creating in-memory socket channels. This provides a clean API for creating in-memory
 * sockets without requiring bootclasspath manipulation or Java agent usage.
 */
public class InMemoryChannelProvider {

    /** Create a new in-memory server socket channel. */
    public static InMemoryServerSocketChannel openInMemoryServerSocketChannel() {
        return new InMemoryServerSocketChannel(SelectorProvider.provider());
    }

    /** Create a new in-memory socket channel and connect it to the specified address. */
    public static InMemorySocketChannel openInMemorySocketChannel(SocketAddress remote) throws IOException {
        return InMemorySocketRegistry.createClientChannel(remote);
    }

    /** Check if an address should use in-memory sockets. */
    public static boolean shouldUseInMemory(SocketAddress address) {
        return InMemorySocketRegistry.isLocalConnection(address);
    }

    /** Create either a regular or in-memory socket channel based on the target address. */
    public static SocketChannel openSocketChannel(SocketAddress remote) throws IOException {
        if (shouldUseInMemory(remote)) {
            return openInMemorySocketChannel(remote);
        } else {
            return SocketChannel.open(remote);
        }
    }

    /** Create either a regular or in-memory server socket channel. */
    public static ServerSocketChannel openServerSocketChannel(boolean forceInMemory) throws IOException {
        if (forceInMemory) {
            return openInMemoryServerSocketChannel();
        } else {
            return ServerSocketChannel.open();
        }
    }
}
