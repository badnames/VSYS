package dslab.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;
import java.util.Optional;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MessageClient implements IMessageClient, Runnable {

    private Shell shell;

    private String mailboxUser;
    private String mailboxPassword;

    private Socket transferSocket;
    private Socket mailboxSocket;

    private Config config;


    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) throws IOException {
        this.config = config;
        shell = new Shell(in, out);
        shell.setPrompt(config.getString("transfer.email") + " >>> ");
        shell.register(this);

        mailboxUser = config.getString("mailbox.user");
        mailboxPassword = config.getString("mailbox.password");

        mailboxSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));

    }

    @Override
    public void run() {
        shell.run();
    }

    @Override
    public void inbox() {
        if (mailboxSocket == null) {
            if (!connectDMAP()) return;
        }

        try {
            var writer = new PrintWriter(mailboxSocket.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));

            writer.write("list");
            writer.flush();

            String response = reader.readLine();
            if (!response.equals("ok")) {
                shell.err().println(response);
            }

            writer.write("list");
            writer.flush();
            response = reader.readLine();
            while(!response.equals("ok")) {
                response = reader.readLine();
                shell.out().println("response");
                if (response.startsWith("error")) break;
            }

        } catch (IOException e) {
            shell.err().println("Error reading inbox");
        }

    }

    @Override
    public void delete(String id)  {
        if (mailboxSocket == null) {
            if (!connectDMAP()) return;
        }

        try {
            var writer = new PrintWriter(mailboxSocket.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));

            writer.write("delete " + id);
            writer.flush();

            String response = reader.readLine();
            if (!response.equals("ok")) {
                shell.err().println(response);
            }

        } catch (IOException e) {
            shell.err().println("Error deleting message " + id);
        }
    }

    @Override
    public void verify(String id) {
        // TODO
    }

    @Override
    public void msg(String to, String subject, String data) {

    }

    @Override
    public void shutdown() {

    }

    private boolean connectDMAP() {
        try {
            mailboxSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
        } catch (IOException e) {
            shell.err().println("Could not connect to mailbox!");
            return false;
        }

        try {
            var writer = new PrintWriter(mailboxSocket.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));

            String line = reader.readLine();
            if (!line.equals("ok DMAP")) {
                shell.err().println("Protocol error");
                mailboxSocket.close();
                mailboxSocket = null;
                return false;
            }


            writer.write("login " + config.getString("mailbox.user") + config.getString("mailbox.password"));
            writer.flush();
            line = reader.readLine();
            if (!line.equals("ok")) {
                shell.err().println("Invalid credentials");
                mailboxSocket.close();
                mailboxSocket = null;
                return false;
            }

        } catch (IOException e) {
            shell.err().println("Could not login to mailbox!");
            try {
                mailboxSocket.close();
            } catch (IOException ignored) {}
            mailboxSocket = null;
            return false;
        }

        return true;
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
