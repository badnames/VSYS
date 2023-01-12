package dslab.nameserver;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.stream.Collectors;

//Nameservers only host exactly one zone.
//A nameserver can communicate with the nameservers on the next lower level
public class Nameserver implements INameserver {

    private final Shell shell;
    private final Config config;
    private NameserverRemote remote;
    private Registry registry;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.config = config;
        Logger.setLogStream(shell.out());
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

    @Override
    public void run() {
        int port = config.getInt("registry.port");
        String host = config.getString("registry.host");
        String root_id = config.getString("root_id");

        remote = new NameserverRemote();

        INameserverRemote remoteStub;

        try {
            remoteStub = (INameserverRemote) UnicastRemoteObject.exportObject(remote, 0);
        } catch (RemoteException e) {
            shell.err().println("Unable to export remote object.");
            return;
        }

        if (!config.containsKey("domain")) {
            //we are root
            try {
                //creating and binding registry
                registry = LocateRegistry.createRegistry(port);
                registry.bind(root_id, remoteStub);
            } catch (RemoteException | AlreadyBoundException e) {
                shell.err().println("Unable to bind remote object.");
                return;
            }
            shell.setPrompt("[Nameserver root] >>> ");
        } else {

            //we are a zone nameserver
            String domain = config.getString("domain");
            shell.setPrompt("[Nameserver " + domain + "] >>> ");

            Registry rootRegistry;
            INameserverRemote rootNameServerRemote;

            try {
                //connecting to root Nameserver
                rootRegistry = LocateRegistry.getRegistry(host, port);
                rootNameServerRemote = (INameserverRemote) rootRegistry.lookup(root_id);

                //registering at root Nameserver
                rootNameServerRemote.registerNameserver(domain, remoteStub);
            } catch (RemoteException | AlreadyRegisteredException | InvalidDomainException | NotBoundException e) {
                shell.err().println("Failed to register with root nameserver!");
            }
        }

        shell.run();
    }

    @Override
    @Command
    // Prints out each known nameserver (zones) in alphabetical order,
    // from the perspective of this nameserver
    public void nameservers() {
        List<String> subZones = NameserverStore.getInstance().getKnownSubZones()
                .stream()
                .sorted()
                .collect(Collectors.toList());

        subZones.forEach(zone -> shell.out().println(zone));
    }

    @Override
    @Command
    //Prints out some information about each stored mailbox server address, containing mail DOMAIN and
    //ADRESSES (IP:port), arranged by the domain in alphabetical order.
    public void addresses() {
        List<String> mailboxes = NameserverStore.getInstance().getKnownMailboxes()
                .stream()
                .sorted()
                .collect(Collectors.toList());

        mailboxes.forEach(mailbox -> {
            String address = NameserverStore.getInstance().getMailbox(mailbox);
            shell.out().println(mailbox + " " + address);
        });
    }

    @Override
    @Command
    //Shutdown the nameserver and all related resources
    public void shutdown() {
        try {
            if (!config.containsKey("domain")) {
                //we are root
                UnicastRemoteObject.unexportObject(registry, true);
            }

            UnicastRemoteObject.unexportObject(remote, true);
        } catch (NoSuchObjectException ignored) {
        }

        throw new StopShellException();
    }
}
