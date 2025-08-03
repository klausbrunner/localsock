package com.localsock;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Simple tests for the in-memory socket API that focus on core functionality. */
class SimpleInMemoryTest {

    @Test
    void testLocalhostDetection() {
        // Test localhost detection logic
        assertTrue(InMemoryChannelProvider.shouldUseInMemory(new InetSocketAddress("localhost", 8080)));
        assertTrue(InMemoryChannelProvider.shouldUseInMemory(new InetSocketAddress("127.0.0.1", 8080)));

        // Should not use in-memory for remote addresses
        assertFalse(InMemoryChannelProvider.shouldUseInMemory(new InetSocketAddress("google.com", 80)));
        assertFalse(InMemoryChannelProvider.shouldUseInMemory(new InetSocketAddress("192.168.1.1", 80)));
    }

    @Test
    void testInMemoryServerSocketChannelCreation() {
        // Test that we can create in-memory server socket channels
        InMemoryServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel();
        assertNotNull(server, "Should create in-memory server socket channel");
        assertInstanceOf(InMemoryServerSocketChannel.class, server, "Should return correct type");
        assertTrue(server.isOpen(), "Server channel should be open");
    }

    @Test
    void testSocketRegistryStatistics() {
        // Test registry statistics
        int initialServers = InMemorySocketRegistry.getActiveServerCount();
        int initialPending = InMemorySocketRegistry.getPendingConnectionCount();

        // Should be able to get statistics without errors
        assertTrue(initialServers >= 0, "Server count should be non-negative");
        assertTrue(initialPending >= 0, "Pending connection count should be non-negative");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void testChannelProviderFactoryMethods() {
        // Test that all factory methods work without throwing exceptions
        assertDoesNotThrow(
                () -> {
                    InMemoryChannelProvider.openInMemoryServerSocketChannel();
                },
                "Should be able to create in-memory server socket channel");

        assertDoesNotThrow(
                () -> {
                    InMemoryChannelProvider.openServerSocketChannel(true);
                },
                "Should be able to create server socket channel with forceInMemory=true");

        assertDoesNotThrow(
                () -> {
                    InMemoryChannelProvider.openServerSocketChannel(false);
                },
                "Should be able to create server socket channel with forceInMemory=false");
    }
}
