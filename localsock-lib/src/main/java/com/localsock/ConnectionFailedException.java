package com.localsock;

/**
 * Exception thrown when a connection attempt fails for reasons other than server unavailability.
 */
public final class ConnectionFailedException extends InMemorySocketException {

    public ConnectionFailedException(String message) {
        super(message);
    }

    public ConnectionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
