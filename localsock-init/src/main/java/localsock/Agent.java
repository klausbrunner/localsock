package localsock;

import java.lang.instrument.Instrumentation;
import java.net.InMemorySocketImplFactory;

public final class Agent {
    /**
     * Initialise InMemorySockets. Doesn't do anything else (no instrumentation), it's just
     * a convenient way to run setSocketFactory calls before anything else happens.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        InMemorySocketImplFactory.setSelfAsDefault();
    }
}
