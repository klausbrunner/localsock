package com.localsock;

import java.util.Map;

/**
 * Immutable statistics about the in-memory socket registry state.
 *
 * @param activeServerCount number of active server sockets
 * @param pendingConnectionCount total number of pending client connections
 * @param connectionsByKey map of connection keys to pending connection counts
 */
public record RegistryStatistics(
        int activeServerCount, int pendingConnectionCount, Map<String, Integer> connectionsByKey) {

    public RegistryStatistics {
        if (activeServerCount < 0) {
            throw new IllegalArgumentException("Active server count cannot be negative");
        }
        if (pendingConnectionCount < 0) {
            throw new IllegalArgumentException("Pending connection count cannot be negative");
        }
        connectionsByKey = Map.copyOf(connectionsByKey); // Ensure immutability
    }

    /**
     * Check if there are any active servers.
     */
    public boolean hasActiveServers() {
        return activeServerCount > 0;
    }

    /**
     * Check if there are any pending connections.
     */
    public boolean hasPendingConnections() {
        return pendingConnectionCount > 0;
    }

    /**
     * Get the number of pending connections for a specific key.
     */
    public int getPendingConnectionsFor(String connectionKey) {
        return connectionsByKey.getOrDefault(connectionKey, 0);
    }
}
