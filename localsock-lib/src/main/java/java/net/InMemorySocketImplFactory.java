package java.net;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class InMemorySocketImplFactory implements SocketImplFactory {
    private static final Logger LOG = Logger.getLogger(InMemorySocketImplFactory.class.toString());

    @Override
    public SocketImpl createSocketImpl() {
        return new InMemorySocketImpl();
    }

    /**
     * Try to set this factory as the default socket factory. Proceed if it doesn't work (e.g. because
     * the factories have already been set before).
     */
    public static void setSelfAsDefault() {
        InMemorySocketImplFactory factory = new InMemorySocketImplFactory();
        try {
            Socket.setSocketImplFactory(factory);
            ServerSocket.setSocketFactory(factory);
            LOG.info("socket factories set to " + InMemorySocketImplFactory.class);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "failed to set socket factories to " + InMemorySocketImplFactory.class + ", proceeding anyway");
        }
    }
}
