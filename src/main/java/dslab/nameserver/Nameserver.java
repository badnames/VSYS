package dslab.nameserver;

import java.io.*;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.stream.Collectors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.handler.DispatchListener;

//Nameservers only host exactly one zone.
//A nameserver can communicate with the nameservers on the next lower level
public class Nameserver implements INameserver {

    private final Shell shell;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) throws IOException,
            AlreadyBoundException,
            NotBoundException,
            AlreadyRegisteredException,
            InvalidDomainException {

        shell = new Shell(in, out);
        shell.register(this);

        int port = config.getInt("registry.port");
        String host = config.getString("registry.host");
        String root_id = config.getString("root_id");

        Logger.setLogStream(shell.out());

        NameserverRemote remote = new NameserverRemote();
        INameserverRemote remoteStub = (INameserverRemote) UnicastRemoteObject.exportObject(remote, 0);

        if (!config.containsKey("domain")) {
            //we are root
            LocateRegistry.createRegistry(port).bind(root_id, remoteStub);
            shell.setPrompt("[Nameserver root] >>> ");
            
            return;
        }

        //we are a zone nameserver
        String domain = config.getString("domain");
        shell.setPrompt("[Nameserver " +  domain + "] >>> ");

        var rootRegistry = LocateRegistry.getRegistry(host, port);
        INameserverRemote rootNameServerRemote = (INameserverRemote) rootRegistry.lookup(root_id);

        rootNameServerRemote.registerNameserver(domain, remoteStub);
    }

    @Override
    public void run() {
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
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }
}
