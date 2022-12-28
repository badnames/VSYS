package dslab.nameserver;

import dslab.mailbox.MessageStore;
import dslab.util.DMAPState;
import dslab.util.handler.IListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NameserverListener implements Runnable, IListener {
    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public NameserverListener(Socket socket) {
        this.socket = socket;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream());
        } catch (IOException e) {
            System.err.println("Error creating reader/writer for socket!");
        }
    }

    @Override
    public void run() {
        //Todo: löschen ok Nameserver
        writer.println("ok Nameserver");
        writer.flush();

        DMAPState state = DMAPState.WAITING;
        MessageStore store = MessageStore.getInstance();
        String username = null;

        while(!socket.isClosed()) {
            String input;

            try {
                input = reader.readLine();
            } catch (IOException e) {
                System.err.println("Error reading input from socket!");
                stop();
                return;
            }

            if (input == null) {
                stop();
                return;
            }

            input = input.strip();

            switch (state) {
                case WAITING:
                    if (input.startsWith("login")) {
                        username = parseLogin(input, store);
                    } else if (input.equals("list")
                            || input.startsWith("show")
                            || input.startsWith("delete")
                            || input.equals("logout")) {
                        writer.println("error not logged in");
                    } else {
                        writer.println("error protocol error");
                    }

                    if (username != null) {
                        state = DMAPState.LOGGED_IN;
                    }
                    break;

                case LOGGED_IN:
                    if (input.equals("list")) {
                        listMessages(username, store);
                    } else if (input.startsWith("show")) {
                        showMessage(input, username, store);
                    } else if (input.startsWith("delete")) {
                        deleteMessage(input, username, store);
                    } else if (input.equals("logout")) {
                        username = null;
                        state = DMAPState.WAITING;
                        writer.println("ok");
                    }
                    break;
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

}
