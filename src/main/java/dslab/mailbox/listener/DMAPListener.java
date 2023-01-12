package dslab.mailbox.listener;

import dslab.mailbox.MessageStore;
import dslab.util.AESParameters;
import dslab.util.Base64AES;
import dslab.util.Base64CryptoException;
import dslab.util.DMAPState;
import dslab.util.listener.IListener;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Base64;

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
        writer.println("ok DMAP2.0");
        writer.flush();

        //setting up message store
        MessageStore store = MessageStore.getInstance();

        AESParameters aesParameters = null;
        // indicates whether the connection is encrypted.
        boolean secure = false;

        while(!socket.isClosed()) {
            String input;

            try {
                //reading response
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
            String response = "";

            // If the client wants the connection to be encrypted,
            // we need to decrypt the input before further processing.
            if (secure) {
                // If decryption fails in some, just kill the connection.
                if (aesParameters == null) {
                    stop();
                    return;
                }

                try {
                    input = Base64AES.decrypt(input, aesParameters);
                } catch (Base64CryptoException e) {
                    stop();
                    return;
                }
            }

            if (input.startsWith("startsecure")) {
                // Starting a secure connection, while the connection is already
                // encrypted, makes no sense.
                if (secure) {
                    try {
                        String message = Base64AES.encrypt("error already secure", aesParameters);
                        writer.println(message);
                        writer.flush();
                    } catch (Base64CryptoException e) {
                        stop();
                        return;
                    }
                }

                try {
                    aesParameters = performAuthentication(rsaPrivateKey);
                    secure = true;
                    // we have successfully established an encrypted connection, go back to the beginning
                    continue;
                } catch (IOException e) {
                    stop();
                    return;
                }
            }

            if (input.equals("quit")) {
                String message = "ok bye";

                // We need to encrypt the "ok bye" message separately, since we will stop the connection right after.
                if (secure) {
                    try {
                        message = Base64AES.encrypt(message, aesParameters);
                    } catch (Base64CryptoException ignored) {}
                }

                writer.println(message);
                writer.flush();

                stop();
                return;
            }

            switch (state) {
                case WAITING:
                    response = parseWaitingState(input, store);
                    // Username will be set if a login was performed,
                    // which means that we must switch state if the variable is no longer null.
                    if (username != null) {
                        state = DMAPState.LOGGED_IN;
                    }
                    break;

                case LOGGED_IN:
                    response = parseLoggedInState(input, username, store);
                    if (state == DMAPState.WAITING) {
                        username = null;
                    }
                    break;
            }

            // encrypt the response if the connection is secure
            if (secure) {
                try {
                    response = Base64AES.encrypt(response, aesParameters);
                } catch (Base64CryptoException e) {
                    stop();
                    return;
                }
            }

            //sending response
            writer.println(response);
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

        return "error protocol error";
    }

    // Logic for deciding what happens next based on input
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

        return "error unknown command";
    }

    private AESParameters performAuthentication(PrivateKey privateKey) throws IOException {
        // respond to command "startsecure"
        writer.println("ok " + componentId);
        writer.flush();

        // parse RSA message
        String input = reader.readLine();

        byte[] inputDecoded = Base64.getDecoder().decode(input);
        String decryptedInputString;

        try {
            Cipher decryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] inputDecrypted = decryptCipher.doFinal(inputDecoded);
            decryptedInputString = new String(inputDecrypted, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException
                 | NoSuchPaddingException
                 | InvalidKeyException
                 | IllegalBlockSizeException
                 | BadPaddingException e) {
            socket.close();
            return null;
        }

        if (!decryptedInputString.startsWith("ok")) {
            socket.close();
            return null;
        }

        var parts = decryptedInputString.split(" ");
        String challenge = parts[1];
        String secretKey = parts[2];
        String initializationVector = parts[3];

        byte[] secretKeyDecoded = Base64.getDecoder().decode(secretKey);

        byte[] initializationVectorDecoded = Base64.getDecoder().decode(initializationVector);

        // creating secret AES Key
        SecretKey aesKey = new SecretKeySpec(secretKeyDecoded, "AES");
        IvParameterSpec aesInitializationVector = new IvParameterSpec(initializationVectorDecoded);

        AESParameters aesParameters = new AESParameters(aesKey, aesInitializationVector);

        try {
            // Encrypt the challenge
            String encryptedResponseOptional = Base64AES.encrypt("ok " + challenge, aesParameters);
            writer.println(encryptedResponseOptional);
            writer.flush();

            String response = reader.readLine();

            // Check if the challenge was successfully received
            String decryptedResponse = Base64AES.decrypt(response, aesParameters);
            if (!decryptedResponse.equals("ok")) {
                stop();
                return null;
            }
        } catch (Base64CryptoException e) {
            stop();
            return null;
        }

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
        messages.forEach((id, message) -> response.append(id)
                .append(" ")
                .append(message.getFrom())
                .append(" ")
                .append(message.getSubject())
                .append("\n"));
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
            response.append("from ").append(message.getFrom())
                    .append("\nto ").append(message.getTo())
                    .append("\nsubject ").append(message.getSubject())
                    .append("\ndata ").append(message.getData()).append(hash);
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
