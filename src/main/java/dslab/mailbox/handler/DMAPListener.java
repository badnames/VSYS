package dslab.mailbox.handler;

import dslab.mailbox.MessageStore;
import dslab.util.DMAPState;
import dslab.util.handler.IListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class DMAPListener implements Runnable, IListener {
    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    private DMAPState state = DMAPState.WAITING;

    public DMAPListener(Socket socket) {
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
        writer.println("ok DMAP");
        writer.flush();

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

            if (input.equals("quit")) {
                writer.println("ok bye");
                writer.flush();
                stop();
                return;
            }

            switch (state) {
                case WAITING:
                    username = parseWaitingState(input, store);

                    if (username != null) {
                        state = DMAPState.LOGGED_IN;
                    }
                    break;

                case LOGGED_IN:
                    parseLoggedInState(input, username, store);

                    if (state == DMAPState.WAITING) {
                        username = null;
                    }
                    break;
                case AUTHENTICATING:
                    if(input.startsWith("ok") && input.chars().filter(ch -> ch == ' ').count() == 3) {
                        //TODO SEND MESSAGE 4
                    }
                    break;

                case AUTHENTICATED_WAITING:
                    //TODO: decrypt message
                    username = parseWaitingState(input, store);

                    if (username != null) {
                        state = DMAPState.AUTHENTICATED_LOGGED_IN;
                    }
                    break;

                case AUTHENTICATED_LOGGED_IN:
                    //TODO: decrypt message
                    parseLoggedInState(input, username, store);

                    if (state == DMAPState.AUTHENTICATED_WAITING) {
                        username = null;
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

    private String parseWaitingState(String input, MessageStore store) {
        String username;

        if (input.startsWith("login")) {
            return parseLogin(input, store);
        }

        if (input.equals("list")
                || input.startsWith("show")
                || input.startsWith("delete")
                || input.equals("logout")) {
            writer.println("error not logged in");
            return null;
        }

        if (input.equals("startsecure")) {
            if (state == DMAPState.AUTHENTICATED_WAITING) {
                writer.println("error already authenticated");
                return null;
            }

            writer.println("ok " + "COMPID TODO");
            // TODO SEND MESSAGE 2
            state = DMAPState.AUTHENTICATING;
            return null;
        }

        writer.println("error protocol error");
        return null;
    }

    void parseLoggedInState(String input, String username, MessageStore store) {
        if (input.equals("list")) {
            listMessages(username, store);
        }

        if (input.startsWith("show")) {
            showMessage(input, username, store);
        }

        if (input.startsWith("delete")) {
            deleteMessage(input, username, store);
        }

        if (input.equals("logout")) {
            state = DMAPState.WAITING;
            writer.println("ok");
        }

        if(input.equals("startsecure")) {
            writer.println("ok " + "COMPID TODO");
            // TODO SEND MESSAGE 2
            state = DMAPState.AUTHENTICATING;
        }
    }

    private String parseLogin(String input, MessageStore store) {
        String[] parts = input.split(" ");

        if (parts.length != 3) {
            writer.println("error protocol error");
            return null;
        }

        if (!store.hasUser(parts[1])) {
            writer.println("error unknown user");
            return null;
        }

        if (!store.isPasswordCorrect(parts[1], parts[2])) {
            writer.println("error wrong password");
            return null;
        }

        writer.println("ok");

        return parts[1];
    }

    private void listMessages(String username, MessageStore store) {
        var messages = store.getAllMessagesReadOnly(username);

        messages.forEach((id, message) -> writer.println(id + " " + message.getFrom() + " " + message.getSubject()));
        writer.println("ok");
    }

    private void showMessage(String input, String username, MessageStore store) {
        var parts = input.split(" ");
        int messageId;

        try {
            messageId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            writer.println("error number expected");
            return;
        }

        if (messageId < 0) {
            writer.println("error invalid id");
            return;
        }

        try {
            var message = store.getMessage(username, messageId);
            // the hash will be omitted if it has not been set
            var hash = message.getHash() != null ? "\nhash " + message.getHash() : "";
            writer.println("from " + message.getFrom()
                    + "\nto " + message.getTo()
                    + "\nsubject " + message.getSubject()
                    + "\ndata " + message.getData()
                    + hash);
        } catch (IllegalArgumentException e) {
            writer.println("error " + e.getMessage());
        }

        writer.println("ok");
    }

    private void deleteMessage(String input, String username, MessageStore store) {
        var parts = input.split(" ");
        var messageId = Integer.parseInt(parts[1]);

        try {
            store.deleteMessage(username, messageId);
            writer.println("ok");
        } catch (IllegalArgumentException e) {
            writer.println("error " + e.getMessage());
        }
    }
}
