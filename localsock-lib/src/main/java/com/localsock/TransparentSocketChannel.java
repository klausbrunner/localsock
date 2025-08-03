package com.localsock;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

/**
 * Transparent socket channel that automatically chooses between in-memory and regular network
 * sockets based on the target address.
 */
public class TransparentSocketChannel extends SocketChannel {

    private final SocketChannelDelegate inMemoryDelegate;
    private final SocketChannelDelegate systemDelegate;
    private SocketChannel delegate;
    private boolean connected = false;

    protected TransparentSocketChannel(SelectorProvider provider, SelectorProvider systemProvider) {
        super(provider);
        this.inMemoryDelegate = new InMemorySocketDelegate();
        this.systemDelegate = new SystemSocketDelegate(systemProvider);
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        ensureDelegate(null);
        delegate.bind(local);
        return this;
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        ensureDelegate(null);
        delegate.setOption(name, value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        ensureDelegate(null);
        return delegate.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        try {
            ensureDelegate(null);
            return delegate.supportedOptions();
        } catch (IOException e) {
            // Fallback to empty set if we can't determine
            return Set.of();
        }
    }

    @Override
    public SocketChannel shutdownInput() throws IOException {
        if (delegate != null) {
            delegate.shutdownInput();
        }
        return this;
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        if (delegate != null) {
            delegate.shutdownOutput();
        }
        return this;
    }

    @Override
    public Socket socket() {
        if (delegate != null) {
            return delegate.socket();
        }
        throw new UnsupportedOperationException("Socket not available before connection");
    }

    @Override
    public boolean isConnected() {
        return connected && delegate != null && delegate.isConnected();
    }

    @Override
    public boolean isConnectionPending() {
        return delegate != null && delegate.isConnectionPending();
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        ensureDelegate(remote);
        boolean result = delegate.connect(remote);
        if (result) {
            connected = true;
        }
        return result;
    }

    @Override
    public boolean finishConnect() throws IOException {
        if (delegate != null) {
            boolean result = delegate.finishConnect();
            if (result) {
                connected = true;
            }
            return result;
        }
        return false;
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return delegate != null ? delegate.getRemoteAddress() : null;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (delegate == null) {
            throw new ConnectionFailedException("Channel not connected");
        }
        return delegate.read(dst);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (delegate == null) {
            throw new ConnectionFailedException("Channel not connected");
        }
        return delegate.read(dsts, offset, length);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (delegate == null) {
            throw new ConnectionFailedException("Channel not connected");
        }
        return delegate.write(src);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (delegate == null) {
            throw new ConnectionFailedException("Channel not connected");
        }
        return delegate.write(srcs, offset, length);
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return delegate != null ? delegate.getLocalAddress() : null;
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        if (delegate != null) {
            delegate.close();
        }
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        if (delegate != null) {
            delegate.configureBlocking(block);
        }
    }

    /**
     * Ensure we have the appropriate delegate based on the target address. If connecting to
     * localhost, use in-memory socket. Otherwise, use system socket.
     */
    private void ensureDelegate(SocketAddress remote) throws IOException {
        if (delegate != null) {
            return;
        }

        // Choose delegate based on address support
        SocketChannelDelegate chosenDelegate;
        if (remote != null && inMemoryDelegate.supports(remote)) {
            try {
                chosenDelegate = inMemoryDelegate;
            } catch (Exception e) {
                // Fall back to system delegate if in-memory fails
                chosenDelegate = systemDelegate;
            }
        } else {
            chosenDelegate = systemDelegate;
        }

        delegate = chosenDelegate.createChannel(remote);

        // Configure the delegate to match our current state
        if (delegate != null) {
            delegate.configureBlocking(isBlocking());
        }
    }
}
