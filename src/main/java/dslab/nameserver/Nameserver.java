package dslab.nameserver;

import java.io.*;
import java.util.ArrayList;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.handler.DispatchListener;

//Nameservers only host exactly one zone.
//A nameserver can communicate with the nameservers on the next lower level
public class Nameserver implements INameserver {

    private final Config config;
    private final Shell shell = new Shell();

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
        this.config=config;


        int port = config.getInt("registry.port");
        String host = config.getString("registry.host");
        String root_id = config.getString("root_id");
        String domain = config.getString("domain");

        if (domain == null){
            //root
            LocateRegistry.createRegistry(port).bind(root_id, new NameserverRemote(shell.out()));
        } else {
            //zone nameserver
            var rootRegistry = LocateRegistry.getRegistry(host, port);
            INameserverRemote rootNameServerRemote = (INameserverRemote) rootRegistry.lookup(root_id);

            rootNameServerRemote.registerNameserver(domain, new NameserverRemote(shell.out()));
        }
    }

    @Override
    public void run() {
        shell.run();
        shell.out().println("# "+config.getString("domain")+ " nameserver");
    }

    @Override
    @Command
    //Prints out each known nameserver (zones) in alphabetical order,
    // from the perspective of this nameserver
    public void nameservers() {

    }

    @Override
    @Command
    //Prints out some information about each stored mailbox server address, containing mail DOMAIN and
    //ADRESSES (IP:port), arranged by the domain in alphabetical order.
    public void addresses() {


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
