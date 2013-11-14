package localsock;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * A crude TCP client/server project (faking something like HTTP).
 * <p/>
 * To actually use the in-memory sockets, this requires the localsock-init JAR
 * to be added as a Java agent, and the localsock-lib JAR added to the boot class path.
 * Here's a typical java call:
 * <p/>
 * <code>
 * java -javaagent:/path/localsock-init.jar -Xbootclasspath/a:/path/localsock-lib.jar ...
 * </code>
 * <p/>
 * Without these, the application will just work as usual, using the default socket implementation.
 */
final class Main {
    private static final int PORT = 65000;

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("starting...");
        talk();
    }

    private static void talk() throws InterruptedException, IOException {
        final Thread server = new Thread() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSock = new ServerSocket(PORT);

                    while (!Thread.currentThread().isInterrupted()) {
                        Socket connectionSocket = serverSock.accept();

                        BufferedReader inFromClient =
                                new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                        String clientSentence = inFromClient.readLine();
                        System.out.println("Received: " + clientSentence);

                        String bla = "200 OK\r\nbla, fasel\r\n\r\n";
                        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                        outToClient.writeBytes(bla);
                        connectionSocket.close();
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        server.setDaemon(true);
        server.start();

        while (!Thread.currentThread().isInterrupted()) {
            Socket clientSocket = new Socket("localhost", PORT);
            DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
            String sentence = "HEAD / HTTP/1.0\r\n\r\n";
            outToServer.writeBytes(sentence);


            BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String line;
            do {
                line = inFromServer.readLine();
                System.out.println("FROM SERVER: " + line);
            } while (!line.isEmpty());

            clientSocket.close();


            TimeUnit.SECONDS.sleep(5);
        }

    }

}
