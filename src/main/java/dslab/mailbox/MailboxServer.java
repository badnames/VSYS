package dslab.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.ResourceBundle;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.mailbox.handler.DMAPListenerFactory;
import dslab.mailbox.handler.DMTPListenerFactory;
import dslab.util.handler.DispatchListener;
import dslab.util.Config;

public class MailboxServer implements IMailboxServer, Runnable {

    private final DispatchListener dmapDispatcher;
    private final DispatchListener dmtpDispatcher;

    private final Shell shell;
    private final Config config;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) throws IOException {
        this.config = config;

        var userPasswordMap = loadUsers();
        MessageStore.getInstance().init(userPasswordMap);

        String serverDomain = config.getString("domain");

        dmapDispatcher = new DispatchListener(config.getInt("dmap.tcp.port"), 4, new DMAPListenerFactory(componentId));
        dmtpDispatcher = new DispatchListener(config.getInt("dmtp.tcp.port"), 4, new DMTPListenerFactory(serverDomain));

        shell = new Shell(in, out);
        shell.setPrompt("[Mailbox " + config.getString("domain") + "] >>> ");
        shell.register(this);
    }

    @Override
    public void run() {
        new Thread(dmapDispatcher).start();
        new Thread(dmtpDispatcher).start();

        shell.run();
    }

    @Override
    @Command
    public void shutdown() {
        dmapDispatcher.stop();
        dmtpDispatcher.stop();
        throw new StopShellException();
    }

    private HashMap<String, String> loadUsers() {
        var propertyFileName = config.getString("users.config");

        ResourceBundle bundle = ResourceBundle.getBundle(propertyFileName.substring(0, propertyFileName.length() - 11));
        HashMap<String, String> result = new HashMap<>();

        for (var key : bundle.keySet()) {
            result.put(key, bundle.getString(key));
        }

        return result;
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}
