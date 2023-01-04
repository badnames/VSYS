package dslab.mailbox;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.ResourceBundle;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.mailbox.handler.DMAPListenerFactory;
import dslab.mailbox.handler.DMTPListenerFactory;
import dslab.nameserver.AlreadyRegisteredException;
import dslab.nameserver.INameserverRemote;
import dslab.nameserver.InvalidDomainException;
import dslab.nameserver.NameserverRemote;
import dslab.util.handler.DispatchListener;
import dslab.util.Config;

public class MailboxServer implements IMailboxServer, Runnable {

    private final DispatchListener dmapDispatcher;
    private final DispatchListener dmtpDispatcher;

    private final Shell shell;
    private final Config config;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {
        this.config = config;

        var userPasswordMap = loadUsers();
        MessageStore.getInstance().init(userPasswordMap);

        String serverDomain = config.getString("domain");

        int port = config.getInt("registry.port");
        String host = config.getString("registry.host");
        String root_id = config.getString("root_id");

        try {
            nameRegistry(port, host, root_id, serverDomain);
        } catch (RemoteException | AlreadyRegisteredException | InvalidDomainException | NotBoundException e) {
            throw new RuntimeException(e);
        }

        PrivateKey serverPrivateKey = loadRSAKey(componentId);

        dmapDispatcher = new DispatchListener(config.getInt("dmap.tcp.port"), 4, new DMAPListenerFactory(componentId, serverPrivateKey));
        dmtpDispatcher = new DispatchListener(config.getInt("dmtp.tcp.port"), 4, new DMTPListenerFactory(serverDomain));

        shell = new Shell(in, out);
        shell.setPrompt("[Mailbox " + config.getString("domain") + "] >>> ");
        shell.register(this);
    }

    @Override
    public void run()  {
        new Thread(dmapDispatcher).start();
        new Thread(dmtpDispatcher).start();

        shell.run();
    }

    public void nameRegistry(int port, String host, String root_id, String domain) throws RemoteException, AlreadyRegisteredException, InvalidDomainException, NotBoundException {
        var rootRegistry = LocateRegistry.getRegistry(host, port);
        INameserverRemote rootNameServerRemote = (INameserverRemote) rootRegistry.lookup(root_id);

        rootNameServerRemote.registerMailboxServer(domain, host+":"+port);
    }

    @Override
    @Command
    public void shutdown() {
        dmapDispatcher.stop();
        dmtpDispatcher.stop();
        throw new StopShellException();
    }

    private HashMap<String, String> loadUsers() {
        var propertyFileName = config.getString("users.config");

        ResourceBundle bundle = ResourceBundle.getBundle(propertyFileName.substring(0, propertyFileName.length() - 11));
        HashMap<String, String> result = new HashMap<>();

        for (var key : bundle.keySet()) {
            result.put(key, bundle.getString(key));
        }

        return result;
    }

    private PrivateKey loadRSAKey(String componentId) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {
        FileInputStream inputStream = new FileInputStream("keys/server/" + componentId + ".der");
        long fileSize = inputStream.getChannel().size();
        byte[] rsaPrivateKey = new byte[(int) fileSize];
        inputStream.read(rsaPrivateKey);
        inputStream.close();

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(rsaPrivateKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        
        return keyFactory.generatePrivate(spec);
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}
