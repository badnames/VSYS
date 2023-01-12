package dslab.transfer.listener;

import dslab.transfer.DomainRegistry;
import dslab.transfer.MailboxAddress;
import dslab.util.Message;
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
import java.util.concurrent.BlockingDeque;

public class ClientListener implements IListener, IDMTPParserListener {

    private final Socket socket;
    private final DMTPParser parser;
    private final BlockingDeque<Message> commandQueue;
    private BufferedReader reader;
    private PrintWriter writer;
    private Message message;
    private List<MailboxAddress> messageDestinations = new LinkedList<>();


    public ClientListener(Socket socket, BlockingDeque<Message> commandQueue) {
        this.socket = socket;
        this.parser = new DMTPParser(this);
        this.commandQueue = commandQueue;

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

        while (!socket.isClosed()) {
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

        stop();
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
        message = new Message("", "", "", "", "");
        messageDestinations = new LinkedList<>();
        writer.println("ok");
    }

    @Override
    public void onQuitCommand() {
        writer.println("ok bye");
        writer.flush();
        stop();
    }

    @Override
    public void onToCommand(List<String> recipientAddresses) {
        for (String recipient : recipientAddresses) {
            var domainParts = recipient.split("@");
            messageDestinations.add(DomainRegistry.getInstance().getAddress(domainParts[1]));
        }

        var recipientListJoined = recipientAddresses.stream()
                .reduce((s1, s2) -> s1 + "," + s2);

        if (recipientListJoined.isEmpty()) {
            writer.println("error no recipients");
            return;
        }

        message.setTo(recipientListJoined.get());

        writer.println("ok " + recipientAddresses.size());
    }

    @Override
    public void onSubjectCommand(String subject) {
        message.setSubject(subject);
        writer.println("ok");
    }

    @Override
    public void onFromCommand(String from) {
        if (!from.contains("@")) {
            writer.println("error wrong address format");
            return;
        }
        message.setFrom(from);
        writer.println("ok");
    }

    @Override
    public void onDataCommand(String data) {
        message.setData(data);
        writer.println("ok");
    }

    @Override
    public void onHashCommand(String hash) {
        message.setHash(hash);
        writer.println("ok");
    }

    @Override
    public boolean onSendCommand() {
        if (messageDestinations.size() == 0) {
            writer.println("error no recipients");
            return false;
        }

        if (message.getFrom().length() == 0) {
            writer.println("error no sender");
            return false;
        }

        commandQueue.add(message);
        writer.println("ok");

        return true;
    }
}
