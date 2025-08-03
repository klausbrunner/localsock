package com.localsock;

import java.net.SocketAddress;

/**
 * Exception thrown when attempting to connect to a server that is not available.
 */
public final class ServerNotAvailableException extends InMemorySocketException {

    private final SocketAddress address;

    public ServerNotAvailableException(SocketAddress address) {
        super("No server listening on " + address);
        this.address = address;
    }

    /**
     * Get the address that was not available.
     */
    public SocketAddress getAddress() {
        return address;
    }
}
