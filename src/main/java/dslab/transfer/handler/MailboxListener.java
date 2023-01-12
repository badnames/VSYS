package dslab.transfer.handler;

import dslab.nameserver.INameserverRemote;
import dslab.transfer.DomainRegistry;
import dslab.transfer.MailboxAddress;
import dslab.util.Config;
import dslab.util.Message;
import dslab.util.handler.IListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.stream.Collectors;

public class MailboxListener implements IListener {

    private final BlockingDeque<Message> queue;
    private final MailboxAddress usageServerAddress;
    private final MailboxAddress transferServerAddress;

    public MailboxListener(BlockingDeque<Message> queue,
                           MailboxAddress usageServerAddress,
                           MailboxAddress transferServerAddress) {
        this.queue = queue;
        this.usageServerAddress = usageServerAddress;
        this.transferServerAddress = transferServerAddress;
    }

    @Override
    public void run() {
        while (true) {
            Message message = null;
            try {
                message = queue.take();
            } catch (InterruptedException ignored) {
            }

            if (message == null) {
                return;
            }

            // A message with null strings means, that we should stop executing
            if (message.equals(new Message(null, null, null, null, null))) {
                return;
            }

            List<MailboxAddress> addresses = getMailboxAddresses(message);

            if (addresses == null) {
                sendErrorMessage("mailbox not known", message);
                continue;
            }

            for (MailboxAddress address : addresses) {
                try (var socket = new Socket(address.getDomain(), address.getPort());
                     var writer = new PrintWriter(socket.getOutputStream());
                     var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    var error = sendMessage(writer, reader, message);

                    writer.println("quit");
                    writer.flush();

                    if (error.isPresent())
                        sendErrorMessage(error.get(), message);
                    else
                        sendUsageDatagram(address, message.getFrom());

                } catch (IOException e) {
                    sendErrorMessage("failed to connect to mailbox", message);
                }
            }
        }
    }

    @Override
    public void stop() {
    }

    private Optional<String> sendMessage(PrintWriter writer, BufferedReader reader, Message message) throws IOException {
        String serverOutput = reader.readLine();
        if (!serverOutput.equals("ok DMTP2.0")) {
            return Optional.of("domain lookup error");
        }

        writer.println("begin");
        writer.flush();
        serverOutput = reader.readLine();
        if (!serverOutput.equals("ok")) {
            return Optional.of("mailbox server error");
        }

        writer.println("to " + message.getTo());
        writer.flush();
        serverOutput = reader.readLine();
        if (!serverOutput.startsWith("ok")) {
            return Optional.of("unknown recipient");
        }

        writer.println("from " + message.getFrom());
        writer.flush();
        serverOutput = reader.readLine();
        if (!serverOutput.equals("ok")) {
            return Optional.of("invalid from section");
        }

        writer.println("subject " + message.getSubject());
        writer.flush();
        serverOutput = reader.readLine();
        if (!serverOutput.equals("ok")) {
            return Optional.of("invalid subject");
        }

        writer.println("data " + message.getData());
        writer.flush();
        serverOutput = reader.readLine();
        if (!serverOutput.equals("ok")) {
            return Optional.of("invalid data");
        }

        if (message.getHash() != null) {
            writer.println("hash " + message.getHash());
            writer.flush();
            serverOutput = reader.readLine();
            if (!serverOutput.equals("ok")) {
                return Optional.of("invalid hash");
            }
        }

        writer.println("send");
        writer.flush();
        serverOutput = reader.readLine();
        if (!serverOutput.equals("ok")) {
            return Optional.of("missing data");
        }

        return Optional.empty();
    }

    private void sendErrorMessage(String error, Message originalMessage) {
        var address = getSenderAddress(originalMessage);
        // if we can't send the message just give up
        if (address == null)
            return;

        var message = new Message(originalMessage.getFrom(),
                "mailer@" + transferServerAddress.getDomain(),
                "error transmitting message " + originalMessage.getSubject(),
                "Cause: " + error, null);

        try (var socket = new Socket(address.getDomain(), address.getPort());
             var writer = new PrintWriter(socket.getOutputStream());
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            var sendError = sendMessage(writer, reader, message);

            if (sendError.isEmpty()) {
                sendUsageDatagram(address, "mailer@" + transferServerAddress.getDomain());
            }
        } catch (IOException ignored) {
        }
    }

    private void sendUsageDatagram(MailboxAddress address, String sender) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = transferServerAddress.getDomain() + ":" + transferServerAddress.getPort() + " " + sender;
            var buffer = message.getBytes(StandardCharsets.UTF_8);

            InetAddress usageServerIp = InetAddress.getByName(usageServerAddress.getDomain());
            DatagramPacket packet = new DatagramPacket(buffer,
                    buffer.length,
                    usageServerIp,
                    usageServerAddress.getPort());

            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Could not send usage datagram: " + e.getMessage());
        }
    }

    private List<MailboxAddress> getMailboxAddresses(Message message) {
        var toSection = message.getTo();
        var addresses = toSection.split(",");
        var hostnames = Arrays.stream(addresses)
                .map(address -> address.split("@")[1])
                .collect(Collectors.toList());

        var result = new ArrayList<MailboxAddress>();

        for (String hostname : hostnames) {
            MailboxAddress address = lookupMailbox(hostname, message);

            if (address == null) {
                return null;
            }
            result.add(address);
        }
        return result;
    }

    private MailboxAddress lookupMailbox(String address, Message message) {
        Config config = new Config("ns-root");
        int port = config.getInt("registry.port");
        String host = config.getString("registry.host");
        String root_id = config.getString("root_id");

        // If we get e.g. vienna.earth.planet we need to contact the nameservers
        // vienna and earth, then request the mailbox planet.
        String[] zones = address.split("\\.");

        try {
            if (zones.length == 0) {
                return null;
            }

            INameserverRemote nameServerRemote = (INameserverRemote) LocateRegistry.getRegistry(host, port).lookup(root_id);

            Deque<String> zoneQueue = new ArrayDeque<>(List.of(zones));
            //it handle the zones from the last like by "vienna.earth.planet" starts with "planet", till it reach the last zone
            while (zoneQueue.size() > 1) {
                String zone = zoneQueue.removeLast();
                nameServerRemote = nameServerRemote.getNameserver(zone);
            }

            // The last element in the queue is the mailboxes' address.
            // Therefore, we need to request it individually from the last nameserver.
            String[] parsedAddress = nameServerRemote
                    .lookup(zoneQueue.removeLast())
                    .split(":");

            return new MailboxAddress(parsedAddress[0], Integer.parseInt(parsedAddress[1]));
        } catch (NotBoundException | RemoteException e) {
            return null;
        }
    }

    private MailboxAddress getSenderAddress(Message message) {
        var fromSection = message.getFrom();
        var hostname = fromSection.split("@")[1];

        if (!DomainRegistry.getInstance().hasAddress(hostname))
            return null;

        return DomainRegistry.getInstance().getAddress(hostname);
    }
}
