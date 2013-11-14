package java.net;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A custom SocketImpl that intercepts input/output stream calls and replaces them with in-JVM communications
 * where applicable. Falls back to standard SocksSocketImpl otherwise.
 * <p/>
 * <p>Implementation note: This is done via inheritance, as delegating to a SocksSocketImpl is difficult
 * thanks to it being package private and, what's worse, java.net.Socket/ServerSocket happily breaking encapsulation
 * by accessing various *Impl fields directly. See http://www.javaspecialists.eu/archive/Issue168.html for a very
 * clever but reflection-heavy (and thus, potentially slow) alternative approach to dealing with this challenge.</p>
 */
final class InMemorySocketImpl extends SocksSocketImpl {

    private static final Logger LOG = Logger.getLogger(InMemorySocketImpl.class.toString());

    // FIXME: should use a "soft" map instead
    private static final Map<String, InMemorySocketImpl> connections = new HashMap<>();
    private static final Object connectionsLock = new Object();

    // TODO: find something that's more efficient for multi-threaded use (CBB seems to lock a lot)
    private final CircularByteBuffer outputBuffer = new CircularByteBuffer(2048);

    /**
     * @return "ownport~remoteport" (or reverse)
     */
    private static String makeKey(boolean own, InMemorySocketImpl impl) {
        if (own) {
            return Integer.toString(impl.getLocalPort()) + "~" + Integer.toString(impl.getPort());
        } else {
            return Integer.toString(impl.getPort()) + "~" + Integer.toString(impl.getLocalPort());
        }
    }

    private String printInfo() {
        return " localport = " + this.getLocalPort() + " port = " + this.getPort() + ", ia = " + this.getInetAddress();
    }

    @Override
    protected void accept(SocketImpl s) throws IOException {
        super.accept(s);

        if (s instanceof InMemorySocketImpl) {
            InMemorySocketImpl inms = ((InMemorySocketImpl) s);
            LOG.finer("accept" + inms.printInfo());
            setOwnEntry(inms);
        }
    }

    @Override
    void socketConnect(InetAddress address, int port, int timeout) throws IOException {
        super.socketConnect(address, port, timeout);

        LOG.finer("connect " + printInfo());
        setOwnEntry(this);
    }

    private void setOwnEntry(InMemorySocketImpl impl) {
        if (eligibleConnection()) {
            // check own entry and set if necessary
            synchronized (connectionsLock) {
                InMemorySocketImpl ownEntry = connections.get(makeKey(true, impl));
                if (ownEntry == null) {
                    connections.put(makeKey(true, impl), impl);
                    LOG.finer("set own " + makeKey(true, impl) + " -> " + impl);
                }
            }
        }
    }

    private void deleteOwnEntry() {
        if (eligibleConnection()) {
            // check own entry and set if necessary
            synchronized (connectionsLock) {
                connections.remove(makeKey(true, this));
            }
        }
    }

    private boolean eligibleConnection() {
        return true; // FIXME: should check if loopback, connected?
    }

    private InMemorySocketImpl getOtherEntry() {
        if (eligibleConnection()) {
            synchronized (connectionsLock) {
                InMemorySocketImpl otherEntry = connections.get(makeKey(false, this));
                if (otherEntry == null) {
                    LOG.finer("other not found at key " + makeKey(false, this));
                } else {
                    assert otherEntry != this;
                    LOG.finer("found other: " + otherEntry);
                }
                return otherEntry;
            }
        }

        return null;
    }

    @Override
    protected synchronized OutputStream getOutputStream() throws IOException {
        LOG.finer("getOutputStream" + printInfo());

        if (getOtherEntry() != null) {
            LOG.fine("writing to inmemory stream " + printInfo());
            return this.getOutputBuffer().getOutputStream();
        }

        return super.getOutputStream();
    }

    @Override
    protected synchronized InputStream getInputStream() throws IOException {
        LOG.finer("getInputStream" + printInfo());

        final InMemorySocketImpl otherEntry = getOtherEntry();
        if (otherEntry != null) {
            LOG.fine("connecting to other inmemory stream " + printInfo());
            return otherEntry.getOutputBuffer().getInputStream();
        }

        return super.getInputStream();
    }

    private CircularByteBuffer getOutputBuffer() {
        return outputBuffer;
    }

    @Override
    protected void close() throws IOException {
        deleteOwnEntry();
        super.close();
    }
}