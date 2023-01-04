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
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.stream.Collectors;
import java.util.Arrays;

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

            // A message with null strings means, that we should stop executing
            if (message != null && message.equals(new Message(null, null, null, null, null))) {
                return;
            }

            var addresses = getMailboxAddresses(message);

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
        Config config = new Config("ns-root");
        int port = config.getInt("registry.port");
        String host = config.getString("registry.host");
        String root_id = config.getString("root_id");

        for (String hostname : hostnames) {
            try {
                List<String> zones = Arrays.asList(hostname.split("."));
                Collections.reverse(zones);

                if (zones.size() == 0) {
                    //TODO
                    throw new RuntimeException();
                }
                
                INameserverRemote nameServerRemote = (INameserverRemote) LocateRegistry.getRegistry(host, port).lookup(root_id);

                for (String zone : zones) {
                    //vienna.earth.planet
                    //from planet ---> to vienna
                    if (zone.equals(zones.get(zones.size() - 1))) {
                        String[] parsedAddress = nameServerRemote.lookup(zone).split(":");
                        result.add(new MailboxAddress(parsedAddress[1], Integer.parseInt(parsedAddress[0])));
                    } else {
                        nameServerRemote = nameServerRemote.getNameserver(zone);
                    }
                }
            } catch (Exception e) {
                //TODO
                throw new RuntimeException();
            }

            if (!DomainRegistry.getInstance().hasAddress(hostname)) {
                return null;
            }
        }
        return result;
    }

    private MailboxAddress getSenderAddress(Message message) {
        var fromSection = message.getFrom();
        var hostname = fromSection.split("@")[1];

        if (!DomainRegistry.getInstance().hasAddress(hostname))
            return null;

        return DomainRegistry.getInstance().getAddress(hostname);
    }
}
