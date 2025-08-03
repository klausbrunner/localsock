package com.localsock;

import java.io.IOException;

/**
 * Base exception for in-memory socket operations.
 */
public sealed class InMemorySocketException extends IOException
        permits ServerNotAvailableException, ConnectionFailedException {

    public InMemorySocketException(String message) {
        super(message);
    }

    public InMemorySocketException(String message, Throwable cause) {
        super(message, cause);
    }
}
