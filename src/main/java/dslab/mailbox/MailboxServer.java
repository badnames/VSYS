package dslab.mailbox;

import java.io.InputStream;
import java.io.PrintStream;

import dslab.ComponentFactory;
import dslab.util.Config;

public class MailboxServer implements IMailboxServer, Runnable {

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        // TODO
    }

    @Override
    public void run() {
        // TODO
    }

    @Override
    public void shutdown() {
        // TODO
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}
