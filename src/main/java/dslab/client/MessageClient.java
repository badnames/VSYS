package dslab.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.AESParameters;
import dslab.util.Base64AES;
import dslab.util.Base64CryptoException;
import dslab.util.Config;
import dslab.util.Keys;
import dslab.util.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MessageClient implements IMessageClient, Runnable {

    private final Shell shell;
    private final Config config;
    private Socket transferSocket;
    private Socket mailboxSocket;
    private AESParameters aesParameters;


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

            String toSend = "list";
            String toSendEncrypted = Base64AES.encrypt(toSend, aesParameters);

            writer.println(toSendEncrypted);
            writer.flush();

            String response = reader.readLine();

            var decryptedInputOptional = Base64AES.decrypt(response, aesParameters);
            if (decryptedInputOptional.isEmpty()) {
                try {
                    mailboxSocket.close();
                } catch (IOException ignored) {
                }
                return;
            }


            String[] listLines = decryptedInputOptional.get().split("\n");
            for (var line : listLines) {
                if (line.startsWith("error")) throw new IOException();
                if (line.startsWith("ok")) continue;
                messages.add(line);
            }

            var messageIds = messages.stream()
                    .map(message -> message.split(" ")[0])
                    .map(String::trim)
                    .collect(Collectors.toList());

            for (int i = 0; i < messages.size(); i++) {
                toSend = "show " + messageIds.get(i);
                toSendEncrypted = Base64AES.encrypt(toSend, aesParameters);
                writer.println(toSendEncrypted);
                writer.flush();

                response = reader.readLine();

                decryptedInputOptional = Base64AES.decrypt(response, aesParameters);
                if (decryptedInputOptional.isEmpty()) {
                    try {
                        mailboxSocket.close();
                    } catch (IOException ignored) {
                    }
                    return;
                }

                shell.out().println("id " + messageIds.get(i));
                String[] showLines = decryptedInputOptional.get().split("\n");
                for (var line : showLines) {
                    if (line.startsWith("error")) throw new IOException();
                    if (line.equals("ok")) continue;

                    shell.out().println(line);
                }
                shell.out().println();
            }

        } catch (IOException | Base64CryptoException e) {
            shell.err().println("Error reading inbox");

            try {
                mailboxSocket.close();
            } catch (IOException ignored) {}
            mailboxSocket = null;
        }
    }

    @Command
    @Override
    public void delete(String id) {
        // TODO: verschlüsseln
        if (mailboxSocket == null || mailboxSocket.isClosed()) {
            if (!connectDMAP()) return;
        }

        try {
            var writer = new PrintWriter(mailboxSocket.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));

            String toSend = "delete " + id;
            String toSendEncrypted = Base64AES.encrypt(toSend, aesParameters);
            writer.println(toSendEncrypted);
            writer.flush();

            String response = reader.readLine();
            //TODO: decrypt
            if (!response.equals("ok")) {
                shell.err().println(response);
            }

        } catch (IOException | Base64CryptoException e) {
            shell.err().println("Error deleting message " + id);

            try {
                mailboxSocket.close();
            } catch (IOException ignored) {}
            mailboxSocket = null;
        }
    }

    @Command
    @Override
    public void verify(String id) {
        // TODO
        if (mailboxSocket == null || mailboxSocket.isClosed()) {
            if (!connectDMAP()) return;
        }

        try {
            var writer = new PrintWriter(mailboxSocket.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));
            String toSend = "show " + id;
            String toSendEncrypted = Base64AES.encrypt(toSend, aesParameters);
            writer.println(toSendEncrypted);
            writer.flush();

            String compHash = null;

            Message message = new Message("", "", "", "", "");
            String response = reader.readLine();
            var responseOptional = Base64AES.decrypt(response, aesParameters);
            if (responseOptional.isEmpty()) {
                try {
                    mailboxSocket.close();
                } catch (IOException ignored) {}
                mailboxSocket = null;
                return;
            }

            String[] responseLines = responseOptional.get().split("\n");
            for (var line : responseLines) {
                if (line.startsWith("error")) throw new IOException();
                if (line.equals("ok")) continue;

                if (line.startsWith("to")) {
                    // The first part of the message needs to be removed
                    message.setTo(line.substring(3));
                } else if (line.startsWith("from")) {
                    message.setFrom(line.substring(5));
                } else if (line.startsWith("subject")) {
                    message.setSubject(line.substring(8));
                } else if (line.startsWith("data")) {
                    message.setData(line.substring(5));
                } else if (line.startsWith("hash")) {
                    compHash = line.substring(5);
                }
            }

            if (compHash == null) {
                shell.out().println("HMAC not found, unknown validity!");
            }

            String hash = calculateBase64HMAC(message.getFrom(), message.getSubject(), message.getData());

            if (hash.equals(compHash)) {
                shell.out().println("Valid message!");
            } else {
                shell.out().println("Invalid message!");
            }


        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException | Base64CryptoException e) {
            shell.err().println("Error verifying message " + id);
        }
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

            String hash = calculateBase64HMAC(to, subject, data);

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
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            shell.err().println("Error transferring message");
        }
    }

    @Command
    @Override
    public void shutdown() {
        try {
            if (transferSocket != null) transferSocket.close();
            if (mailboxSocket != null) mailboxSocket.close();
        } catch (IOException ignored) {
        }

        throw new StopShellException();
    }

    public void startSecure(PrintWriter writer, BufferedReader reader) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidKeyException, InvalidAlgorithmParameterException {
        String serverOutput;
        writer.println("startsecure");
        writer.flush();

        serverOutput = reader.readLine();
        if (!serverOutput.startsWith("ok") || !(serverOutput.chars().filter(ch -> ch == ' ').count() == 1)) {
            mailboxSocket.close();
            shell.err().println("error begin");
            return;
        }

        SecureRandom secRandom = new SecureRandom();

        //reading server public key and creating a publicKey Object
        String compId = serverOutput.substring(3);
        FileInputStream inputStream = new FileInputStream("keys/client/" + compId + "_pub.der");
        long fileSize = inputStream.getChannel().size();
        byte[] rsaPublicKey = new byte[(int) fileSize];
        inputStream.read(rsaPublicKey);
        inputStream.close();
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(rsaPublicKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        //Generating a random clientChallenge and converting it to a String
        byte[] clientChallenge = new byte[32];
        secRandom.nextBytes(clientChallenge);
        String clientChallengeString = Base64.getEncoder().encodeToString(clientChallenge);

        //generating an AES private Key and converting it to a String
        byte[] secureRandomKeyBytes = new byte[128 / 8];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(secureRandomKeyBytes);
        var aesKeySpec = new SecretKeySpec(secureRandomKeyBytes, "AES");
        String aesKey = Base64.getEncoder().encodeToString(secureRandomKeyBytes);

        //generating an initialization Vector and converting it to a String
        byte[] iv = new byte[16];
        secRandom.nextBytes(iv);
        String ivString = Base64.getEncoder().encodeToString(iv);

        //Generating the message3 that should be sent
        String toEncrypt = "ok " + clientChallengeString + " " + aesKey + " " + ivString;

        //Encrypting the message3 via the server RSA public key
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedMessage = rsaCipher.doFinal(toEncrypt.getBytes());
        String messageToSend = Base64.getEncoder().encodeToString(encryptedMessage);

        //Sending the message
        writer.println(messageToSend);
        writer.flush();

        //read message 4
        serverOutput = reader.readLine();

        // Decrypt the message4
        aesParameters = new AESParameters(aesKeySpec, new IvParameterSpec(iv));
        var decryptedMessageOptional = Base64AES.decrypt(serverOutput, aesParameters);
        if (decryptedMessageOptional.isEmpty()) {
            shell.err().println("Error establishing secure connection!");
            return;
        }

        String decryptedMessageString = decryptedMessageOptional.get();

        //analyse decrypted message4
        if (!decryptedMessageString.startsWith("ok ")) {
            mailboxSocket.close();
            shell.err().println("Error establishing secure connection!");
            return;
        }
        String receivedClientChallenge = decryptedMessageString.substring(3);

        if (!clientChallengeString.equals(receivedClientChallenge)) {
            mailboxSocket.close();
            shell.err().println("Error establishing secure connection!");
            return;
        }

        //sending message 5
        try {
            String okResponseEncrypted = Base64AES.encrypt("ok", aesParameters);
            writer.println(okResponseEncrypted);
            writer.flush();
        } catch (Base64CryptoException e) {
            mailboxSocket.close();
            shell.err().println("Error establishing secure connection!");
        }
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


            if (!line.equals("ok DMAP2.0")) {
                shell.err().println("Protocol error");
                mailboxSocket.close();
                mailboxSocket = null;
                return false;
            }

            startSecure(writer, reader);

            String toSend = "login " + config.getString("mailbox.user") + " " + config.getString("mailbox.password");
            String toSendEncrypted = Base64AES.encrypt(toSend, aesParameters);
            writer.println(toSendEncrypted);
            writer.flush();

            line = reader.readLine();
            var decryptedInputOptional = Base64AES.decrypt(line, aesParameters);
            if (decryptedInputOptional.isEmpty()) {
                try {
                    mailboxSocket.close();
                } catch (IOException ignored) {
                }
                return false;
            }

            if (!decryptedInputOptional.get().equals("ok")) {
                shell.err().println("Invalid credentials");
                mailboxSocket.close();
                mailboxSocket = null;
                return false;
            }

        } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException |
                 BadPaddingException | InvalidKeySpecException | InvalidKeyException | InvalidAlgorithmParameterException
                 | Base64CryptoException e) {

            shell.err().println("Could not login to mailbox!");
            try {
                mailboxSocket.close();
            } catch (IOException ignored) {}
            mailboxSocket = null;
            return false;
        }

        return true;
    }


    private String calculateBase64HMAC(String to, String subject, String data) throws NoSuchAlgorithmException, IOException, InvalidKeyException {
        SecretKeySpec temp = Keys.readSecretKey(new File("keys/hmac.key"));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(temp);

        String msg = String.join("\n", config.getString("transfer.email"), to, subject, data);
        byte[] bytes = msg.getBytes();
        byte[] macResult = mac.doFinal(bytes);
        byte[] decodedBytes = Base64.getEncoder().encode(macResult);

        return new String(decodedBytes, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
