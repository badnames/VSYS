package dslab.monitoring.handler;

import dslab.monitoring.UsageStore;
import dslab.util.handler.IListener;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class UsageListener implements IListener {

    private final int port;
    private boolean running = true;
    private final Object runningLock = new Object();

    private DatagramSocket socket;

    public UsageListener(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
             socket = new DatagramSocket(port);
        } catch (SocketException e) {
            System.err.println("Could not connect to socket!");
            return;
        }


        while (true) {
            synchronized (runningLock) {
                if (!running) break;
            }

            byte[] buffer = new byte[65535];

            var packet = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(packet);
            } catch (IOException e) {
                System.err.println("Socket encountered io exception: " + e.getMessage());
            }

            var message = new String(buffer, StandardCharsets.UTF_8);
            handleMessage(message.trim());
        }
    }

    @Override
    public void stop() {
        socket.close();
        synchronized (runningLock) {
            this.running = false;
        }
    }

    private void handleMessage(String message) {
        var messageParts = message.split(" ");
        if (messageParts.length != 2)
            return;

        UsageStore.getInstance().addServerAccess(messageParts[0]);
        UsageStore.getInstance().addAddressAccess(messageParts[1]);
    }
}
