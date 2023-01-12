package dslab.mailbox.listener;

import dslab.util.Message;
import dslab.mailbox.MessageStore;
import dslab.util.listener.IListener;
import dslab.util.parser.DMTPParser;
import dslab.util.parser.IDMTPParserListener;
import dslab.util.parser.ParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

public class DMTPListener implements IListener, IDMTPParserListener {
    private final Socket socket;
    private final String serverDomain;
    private final DMTPParser parser;
    private final MessageStore store = MessageStore.getInstance();
    private BufferedReader reader;
    private PrintWriter writer;

    private Message message = new Message("", "", "", "","");
    private List<String> recipients = new LinkedList<>();


    public DMTPListener(Socket socket, String serverDomain) {
        this.socket = socket;
        this.serverDomain = serverDomain;
        this.parser = new DMTPParser(this);

        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream());
        } catch (IOException e) {
            System.err.println("Error creating reader/writer for socket!");
        }
    }

    @Override
    public void run() {
        writer.println("ok DMTP2.0");
        writer.flush();

        while(!socket.isClosed()) {
            String input = "";
            try {
                input = reader.readLine();
            } catch (IOException e) {
                System.err.println("Error reading input from socket!");
                stop();
            }

            try {
                parser.parse(input);
            } catch (ParserException e) {
                writer.println("error protocol error");
            }

            writer.flush();
        }
    }

    @Override
    public synchronized void stop() {
        try {
            this.socket.close();
        } catch (IOException e) {
            System.err.println("Could not close socket on exit!");
        }
    }

    @Override
    public void onBeginCommand() {
        writer.println("ok");
        message = new Message("", "", "", "","");
        recipients = new LinkedList<>();
    }

    @Override
    public void onQuitCommand() {
        writer.println("ok bye");
        writer.flush();
        stop();
    }

    @Override
    public void onToCommand(List<String> recipientAddresses) {
        List<String> recipients = new LinkedList<>();

        for (var recipientAddress : recipientAddresses) {
            String trimmedRecipientAddress = recipientAddress.trim();
            String[] addressParts = trimmedRecipientAddress.split("@");

            if (addressParts.length != 2) {
                writer.println("error wrong address format");
                return;
            }

            // drop all recipients that have a different domain than this server
            if (!addressParts[1].equals(serverDomain))
                continue;

            // if a user is not known, we stop parsing
            if (!store.hasUser(addressParts[0])) {
                writer.println("error unknown recipient " + addressParts[0]);
                return;
            }

            recipients.add(addressParts[0]);
        }

        var recipientListJoined = recipientAddresses.stream()
                .reduce((s1, s2) -> s1 + "," + s2);

        message.setTo(
                recipientListJoined.orElse("")
        );
        this.recipients = recipients;
        writer.println("ok " + recipients.size());
    }

    @Override
    public void onSubjectCommand(String subject) {
        this.message.setSubject(subject);
        writer.println("ok");
    }

    @Override
    public void onFromCommand(String from) {
        if (!from.contains("@")) {
            writer.println("error invalid address");
            return;
        }
        this.message.setFrom(from);
        writer.println("ok");
    }

    @Override
    public void onDataCommand(String data) {
        this.message.setData(data);
        writer.println("ok");
    }

    @Override
    public void onHashCommand(String hash) {
        this.message.setHash(hash);
        writer.println("ok");
    }

    @Override
    public boolean onSendCommand() {
        if (recipients.size() == 0) {
            writer.println("error no recipients");
            return false;
        }

        if (message.getFrom().length() == 0) {
            writer.println("error no sender");
            return false;
        }

        for (var recipient : recipients) {
            store.putMessage(recipient, message);
        }

        writer.println("ok");

        return true;
    }
}
