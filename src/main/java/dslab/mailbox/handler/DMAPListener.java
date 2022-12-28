package dslab.mailbox.handler;

import dslab.mailbox.MessageStore;
import dslab.util.AESParameters;
import dslab.util.Base64AES;
import dslab.util.DMAPState;
import dslab.util.handler.IListener;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Optional;

public class DMAPListener implements Runnable, IListener {
    private final Socket socket;
    private final String componentId;
    private final PrivateKey rsaPrivateKey;
    private BufferedReader reader;
    private PrintWriter writer;

    private DMAPState state = DMAPState.WAITING;
    private String username = null;

    public DMAPListener(Socket socket, String componentId, PrivateKey rsaPrivateKey) {
        this.socket = socket;
        this.componentId = componentId;
        this.rsaPrivateKey = rsaPrivateKey;
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

        AESParameters aesParameters = null;

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

            String response;
            Optional<String> decryptedInputOptional;
            Optional<String> encryptedOutputOptional;

            switch (state) {
                case WAITING:
                    response = parseWaitingState(input, store);

                    writer.println(response);
                    if (username != null) {
                        state = DMAPState.LOGGED_IN;
                    }
                    break;

                case LOGGED_IN:
                    response = parseLoggedInState(input, username, store);

                    writer.println(response);
                    if (state == DMAPState.WAITING) {
                        username = null;
                    }
                    break;

                case AUTHENTICATING_WAITING:
                    try {
                        aesParameters = parseAuthenticatingState(input, rsaPrivateKey);
                    } catch (IOException ignored) {}
                    state = DMAPState.AUTHENTICATED_WAITING;
                    break;

                case AUTHENTICATING_LOGGED_IN:
                    try {
                        aesParameters = parseAuthenticatingState(input, rsaPrivateKey);
                    } catch (IOException ignored) {}
                    state = DMAPState.AUTHENTICATED_LOGGED_IN;
                    break;

                case AUTHENTICATED_WAITING:
                    if (aesParameters == null) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        continue;
                    }

                    decryptedInputOptional = Base64AES.decrypt(input, aesParameters);
                    if (decryptedInputOptional.isEmpty()) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        continue;
                    }

                    input = decryptedInputOptional.get();
                    response = parseWaitingState(input, store);

                    if (username != null) {
                        state = DMAPState.AUTHENTICATED_LOGGED_IN;
                    }

                    encryptedOutputOptional = Base64AES.encrypt(response, aesParameters);
                    if (encryptedOutputOptional.isEmpty()) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        continue;
                    }

                    writer.println(response);
                    break;

                case AUTHENTICATED_LOGGED_IN:
                    if (aesParameters == null) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        continue;
                    }

                    decryptedInputOptional = Base64AES.decrypt(input, aesParameters);
                    if (decryptedInputOptional.isEmpty()) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        continue;
                    }

                    response = parseLoggedInState(input, username, store);

                    encryptedOutputOptional = Base64AES.encrypt(response, aesParameters);
                    if (encryptedOutputOptional.isEmpty()) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        continue;
                    }

                    writer.println(response);

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
        if (input.startsWith("login")) {
            return parseLogin(input, store);
        }

        if (input.equals("list")
                || input.startsWith("show")
                || input.startsWith("delete")
                || input.equals("logout")) {
            return "error not logged in";
        }

        if (input.equals("startsecure")) {
            if (state == DMAPState.AUTHENTICATED_WAITING) {
                return "error already authenticated";
            }

            state = DMAPState.AUTHENTICATING_WAITING;
            return "ok " + componentId;
        }

        return "error protocol error";
    }

    private String parseLoggedInState(String input, String username, MessageStore store) {
        if (input.equals("list")) {
            return listMessages(username, store);
        }

        if (input.startsWith("show")) {
            return showMessage(input, username, store);
        }

        if (input.startsWith("delete")) {
            return deleteMessage(input, username, store);
        }

        if (input.equals("logout")) {
            state = DMAPState.WAITING;
            return "ok";
        }

        if(input.equals("startsecure")) {
            if (state == DMAPState.AUTHENTICATED_WAITING) {
                return "error already authenticated";
            }

            state = DMAPState.AUTHENTICATING_LOGGED_IN;

            return "ok " + componentId;
        }

        return "error unknown command";
    }

    private AESParameters parseAuthenticatingState(String input, PrivateKey privateKey) throws IOException {
        if (!input.startsWith("ok")) {
            socket.close();
            return null;
        }

        byte[] inputDecoded = Base64.getDecoder().decode(input);
        String decryptedInput;

        try {
            Cipher decryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] inputDecrypted = decryptCipher.doFinal(inputDecoded);
            decryptedInput = new String(inputDecrypted, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException
                 | NoSuchPaddingException
                 | InvalidKeyException
                 | IllegalBlockSizeException
                 | BadPaddingException e) {
            socket.close();
            return null;
        }

        if (!decryptedInput.startsWith("ok")) {
            socket.close();
            return null;
        }

        var parts = decryptedInput.split(" ");
        String challenge = parts[1];
        String secretKey = parts[2];
        String initializationVector = parts[3];

        writer.println("ok " + challenge);
        writer.flush();

        String response = reader.readLine();
        if (!response.equals("ok")) {
            socket.close();
            return null;
        }

        byte[] secretKeyDecoded = Base64.getDecoder().decode(secretKey);
        byte[] initializationVectorDecoded = Base64.getDecoder().decode(initializationVector);

        SecretKey aesKey = new SecretKeySpec(secretKeyDecoded, "AES");
        IvParameterSpec aesInitializationVector = new IvParameterSpec(initializationVectorDecoded);

        return new AESParameters(aesKey, aesInitializationVector);
    }

    private String parseLogin(String input, MessageStore store) {
        String[] parts = input.split(" ");

        if (parts.length != 3) {
            return "error protocol error";
        }

        if (!store.hasUser(parts[1])) {
            return "error unknown user";
        }

        if (!store.isPasswordCorrect(parts[1], parts[2])) {
            return "error wrong password";
        }

        username =  parts[1];
        return "ok";
    }

    private String listMessages(String username, MessageStore store) {
        var messages = store.getAllMessagesReadOnly(username);

        StringBuilder response = new StringBuilder();
        messages.forEach((id, message) -> response.append(id).append(" ").append(message.getFrom()).append(" ").append(message.getSubject()).append("\n"));
        response.append("ok");

        return response.toString();
    }

    private String showMessage(String input, String username, MessageStore store) {
        var parts = input.split(" ");
        int messageId;

        try {
            messageId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "error number expected";
        }

        if (messageId < 0) {
            return "error invalid id";
        }

        StringBuilder response = new StringBuilder();
        try {
            var message = store.getMessage(username, messageId);
            // the hash will be omitted if it has not been set
            var hash = message.getHash() != null ? "\nhash " + message.getHash() : "";
            response.append("from " + message.getFrom()
                    + "\nto " + message.getTo()
                    + "\nsubject " + message.getSubject()
                    + "\ndata " + message.getData()
                    + hash);
        } catch (IllegalArgumentException e) {
            return "error " + e.getMessage();
        }

        response.append("\nok");

        return response.toString();
    }

    private String deleteMessage(String input, String username, MessageStore store) {
        var parts = input.split(" ");
        var messageId = Integer.parseInt(parts[1]);

        try {
            store.deleteMessage(username, messageId);
            return "ok";
        } catch (IllegalArgumentException e) {
            return "error " + e.getMessage();
        }
    }
}
