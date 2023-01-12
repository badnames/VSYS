package dslab.transfer;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.transfer.listener.ClientListenerFactory;
import dslab.transfer.listener.MailboxListener;
import dslab.util.Config;
import dslab.util.Message;
import dslab.util.listener.DispatchListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class TransferServer implements ITransferServer, Runnable {

    private final DispatchListener dispatcher;
    private final MailboxListener mailboxListener;
    private final Shell shell;
    private final BlockingDeque<Message> commandQueue;


    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) throws IOException {
        commandQueue = new LinkedBlockingDeque<>();
        dispatcher = new DispatchListener(config.getInt("tcp.port"), 8, new ClientListenerFactory(commandQueue));

        var usageServerAddress = new MailboxAddress(config.getString("monitoring.host"), config.getInt("monitoring.port"));
        mailboxListener = new MailboxListener(commandQueue,
                usageServerAddress,
                new MailboxAddress(InetAddress.getLocalHost().getHostAddress(), config.getInt("tcp.port")));

        shell = new Shell(in, out);
        shell.setPrompt("[Transfer] >>> ");
        shell.register(this);
    }

    @Override
    public void run() {
        DomainRegistry.getInstance().init();

        new Thread(mailboxListener).start();
        new Thread(dispatcher).start();
        shell.run();
    }

    @Override
    @Command
    public void shutdown() {
        dispatcher.stop();
        mailboxListener.stop();
        commandQueue.push(new Message(null, null, null, null, null));
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

}
