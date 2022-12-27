package dslab.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class MessageClient implements IMessageClient, Runnable {

    private final Shell shell;
    private final Config config;

    private Socket transferSocket;
    private Socket mailboxSocket;



    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) throws IOException {
        this.config = config;
        shell = new Shell(in, out);
        shell.setPrompt(config.getString("transfer.email") + " >>> ");
        shell.register(this);
    }

    @Override
    public void run() {
        shell.run();
    }

    @Command
    @Override
    public void inbox() {
        if (mailboxSocket == null || mailboxSocket.isClosed()) {
            if (!connectDMAP()) return;
        }

        try {
            var writer = new PrintWriter(mailboxSocket.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));

            List<String> messages = new ArrayList<>();

            writer.println("list");
            writer.flush();
            String response = reader.readLine();
            while(!response.equals("ok")) {
                if (response.startsWith("error")) throw new IOException();

                messages.add(response);
                response = reader.readLine();
            }

            var messageIds = messages.stream()
                    .map(message -> message.split(" ")[0])
                    .map(String::trim)
                    .collect(Collectors.toList());

            for (int i = 0; i < messages.size(); i++) {
                writer.println("show " + messageIds.get(i));
                writer.flush();

                response = reader.readLine();
                while(!response.equals("ok")) {
                    if (response.startsWith("error")) throw new IOException();

                    shell.out().println(response);
                    response = reader.readLine();
                }

                shell.out().println();
            }

        } catch (IOException e) {
            shell.err().println("Error reading inbox");
        }

    }

    @Command
    @Override
    public void delete(String id)  {
        if (mailboxSocket == null || mailboxSocket.isClosed()) {
            if (!connectDMAP()) return;
        }

        try {
            var writer = new PrintWriter(mailboxSocket.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));

            writer.println("delete " + id);
            writer.flush();

            String response = reader.readLine();
            if (!response.equals("ok")) {
                shell.err().println(response);
            }

        } catch (IOException e) {
            shell.err().println("Error deleting message " + id);
        }
    }

    @Command
    @Override
    public void verify(String id) {
        // TODO
    }

    @Command
    @Override
    public void msg(String to, String subject, String data) {
        try {
            if (transferSocket == null || transferSocket.isClosed()) {
                transferSocket = new Socket(config.getString("transfer.host"), config.getInt("transfer.port"));
            }
            var writer = new PrintWriter(transferSocket.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(transferSocket.getInputStream()));

            String serverOutput = reader.readLine();
            if (!serverOutput.equals("ok DMTP2.0")) {
                transferSocket.close();
                shell.err().println("error ok DMTP2.0");
                return;
            }

            writer.println("begin");
            writer.flush();
            serverOutput = reader.readLine();
            if (!serverOutput.equals("ok")) {
                transferSocket.close();
                shell.err().println("error begin");
                return;
            }

            writer.println("to " + to);
            writer.flush();
            serverOutput = reader.readLine();
            if (!serverOutput.startsWith("ok")) {
                transferSocket.close();
                shell.err().println("error to");
                return;
            }

            writer.println("from " + config.getString("transfer.email"));
            writer.flush();
            serverOutput = reader.readLine();
            if (!serverOutput.equals("ok")) {
                transferSocket.close();
                shell.err().println("error from");
                return;
            }

            writer.println("subject " + subject);
            writer.flush();
            serverOutput = reader.readLine();
            if (!serverOutput.equals("ok")) {
                transferSocket.close();
                shell.err().println("error subject");
                return;
            }

            writer.println("data " + data);
            writer.flush();
            serverOutput = reader.readLine();
            if (!serverOutput.equals("ok")) {
                transferSocket.close();
                shell.err().println("error data");
                return;
            }

            SecretKeySpec temp = Keys.readSecretKey(new File("keys/hmac.key"));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(temp);

            String msg = String.join("\n", config.getString("transfer.email"), to, subject, data);
            byte[] bytes = msg.getBytes();
            byte[] macResult = mac.doFinal(bytes);
            byte[] decodedBytes = Base64.getEncoder().encode(macResult);

            String hash = new String(decodedBytes, StandardCharsets.UTF_8);

            writer.println("hash " + hash);
            writer.flush();
            serverOutput = reader.readLine();
            if (!serverOutput.equals("ok")) {
                transferSocket.close();
                shell.err().println("error hash");
                return;
            }

            writer.println("send");
            writer.flush();
            serverOutput = reader.readLine();
            if (!serverOutput.equals("ok")) {
                transferSocket.close();
                shell.err().println("error send");
                return;
            }
            transferSocket.close();
        } catch (IOException e) {
            shell.err().println("Error transferring message");
        } catch (NoSuchAlgorithmException e) {
            shell.err().println("Error transferring message");
        } catch (InvalidKeyException e) {
            shell.err().println("Error transferring message");
        }
    }

    @Command
    @Override
    public void shutdown() {
        try {
            if (transferSocket != null) transferSocket.close();
            if (mailboxSocket != null) mailboxSocket.close();
        } catch (IOException ignored) {}

        throw new StopShellException();
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


            writer.println("login " + config.getString("mailbox.user") + " " + config.getString("mailbox.password"));
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
