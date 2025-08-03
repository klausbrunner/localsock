package com.localsock;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Modern in-memory socket channel implementation using NIO APIs.
 * This approach bypasses the legacy SocketImpl complexity by working
 * directly with NIO channels and providing in-memory data transfer.
 */
public class InMemorySocketChannel extends SocketChannel {

    private final ReentrantLock readLock = new ReentrantLock();
    private final ReentrantLock writeLock = new ReentrantLock();

    private final ConcurrentLinkedQueue<ByteBuffer> incomingData = new ConcurrentLinkedQueue<>();
    private final String connectionKey;
    boolean connected = false; // Package private for registry access
    private InMemorySocketChannel peerChannel;

    protected InMemorySocketChannel(SelectorProvider provider, String connectionKey) {
        super(provider);
        this.connectionKey = connectionKey;
    }

    public void setPeerChannel(InMemorySocketChannel peer) {
        this.peerChannel = peer;
        this.connected = true;
    }

    @Override
    public SocketChannel bind(SocketAddress local) {
        // For in-memory sockets, binding is handled by the connection registry
        return this;
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) {
        // Most socket options don't apply to in-memory channels
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) {
        throw new UnsupportedOperationException("Socket options not supported for in-memory channels");
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return Set.of(); // No socket options supported
    }

    @Override
    public SocketChannel shutdownInput() {
        // Mark input as shut down
        return this;
    }

    @Override
    public SocketChannel shutdownOutput() {
        // Mark output as shut down
        return this;
    }

    @Override
    public Socket socket() {
        throw new UnsupportedOperationException("Legacy Socket not supported");
    }

    @Override
    public boolean isConnected() {
        return connected && peerChannel != null;
    }

    @Override
    public boolean isConnectionPending() {
        return false; // In-memory connections are immediate
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        if (connected) {
            return true;
        }

        // For in-memory connections, we should already be connected via the registry
        // If not connected, this means the server isn't available
        if (peerChannel != null) {
            connected = true;
            return true;
        }

        throw new IOException("No server listening on " + remote);
    }

    @Override
    public boolean finishConnect() {
        return isConnected();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return null; // In-memory sockets don't have real addresses
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (!isOpen()) {
            throw new IOException("Channel is closed");
        }

        readLock.lock();
        try {
            ByteBuffer data = incomingData.poll();
            if (data == null) {
                return 0; // No data available
            }

            int bytesToRead = Math.min(dst.remaining(), data.remaining());
            if (bytesToRead > 0) {
                byte[] temp = new byte[bytesToRead];
                data.get(temp);
                dst.put(temp);
            }

            // If there's still data in the buffer, put it back
            if (data.hasRemaining()) {
                incomingData.offer(data);
            }

            return bytesToRead;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long totalRead = 0;
        for (int i = offset; i < offset + length && i < dsts.length; i++) {
            int read = read(dsts[i]);
            if (read <= 0) break;
            totalRead += read;
        }
        return totalRead;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!isConnected()) {
            throw new IOException("Channel not connected");
        }

        writeLock.lock();
        try {
            int bytesToWrite = src.remaining();
            if (bytesToWrite == 0) {
                return 0;
            }

            // Copy data to peer's incoming queue
            ByteBuffer copy = ByteBuffer.allocate(bytesToWrite);
            copy.put(src);
            copy.flip();

            peerChannel.incomingData.offer(copy);
            return bytesToWrite;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long totalWritten = 0;
        for (int i = offset; i < offset + length && i < srcs.length; i++) {
            int written = write(srcs[i]);
            totalWritten += written;
            if (written == 0) break;
        }
        return totalWritten;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return null; // In-memory sockets don't have real addresses
    }

    @Override
    protected void implCloseSelectableChannel() {
        boolean open = false;
        connected = false;
        incomingData.clear();
        if (peerChannel != null) {
            peerChannel.connected = false;
        }
    }

    @Override
    protected void implConfigureBlocking(boolean block) {
        // In-memory channels can support both blocking and non-blocking modes
    }

    public String getConnectionKey() {
        return connectionKey;
    }
}
