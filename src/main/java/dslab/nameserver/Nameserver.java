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

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */

    private String componentID;
    private InputStream in;
    private PrintStream out;
    private ArrayList<NameserverDomain> children;  //TODO: put children here

    private final Shell shell;
    private final Config config;

    private final DispatchListener register;
    private final DispatchListener listener;

    private String domain;


    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) throws IOException,
            AlreadyBoundException,
            NotBoundException,
            AlreadyRegisteredException,
            InvalidDomainException {
        // TODO
        this.componentID=componentId;
        this.config=config;
        this.in=in;
        this.out=out;

        port = config.getInt("registry.port");
        host = config.getString("registry.host");
        root_id =  config.getString("root_id");
        domain = config.getString("domain");

        if (domain == null){
            //root
            LocateRegistry.createRegistry(port).bind(root_id, new NameserverRemote());
        } else {
            //zone nameserver
            //LocateRegistry.getRegistry(String host,int port), and 2) locating the object using
            //Registry.lookup(String name), using the name given in the root id property.
            var rootRegistry = LocateRegistry.getRegistry(host, port);
            INameserverRemote rootNameServerRemote = (INameserverRemote) rootRegistry.lookup(root_id);

            rootNameServerRemote.registerNameserver(domain, new NameserverRemote());
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
        // TODO: do it for all nameservers (lookup)
        if (!children.isEmpty()) {
            String[] names = new String[children.size()];
            for (int i = 0; i < children.size(); i++) {
                names[i] = children.get(i).getDomain();
            }

            Arrays.sort(names);

            for (int i = 0; i < names.length; i++) {
                shell.out().println(i + ". " + names[i]);
            }
        }
    }

    @Override
    @Command
    //Prints out some information about each stored mailbox server address, containing mail DOMAIN and
    //ADRESSES (IP:port), arranged by the domain in alphabetical order.
    public void addresses() {
        // TODO: alphabetical
        if (!children.isEmpty()){
            String[] names = new String[children.size()];
            for (int i = 0; i < children.size(); i++) {
                names[i] = children.get(i).getDomain();
            }

            //alphabetical sort
            Arrays.sort(names);

            for (int i = 0; i < children.size(); i++) {
                //looking for the namestore after the alphabetical sort to extract IP and PORT
                NameserverDomain nameStore;
                for (int j = 0; j < children.size(); j++) {
                    nameStore=children.get(j);
                    if (nameStore.getDomain().equals(names[i])){
                        shell.out().println(i+". "+ names[i]+" "+ children.get(j).getIp()+":"+ children.get(j).getPort());
                    }
                }
            }
        }


    }

    @Override
    @Command
    //Shutdown the nameserver and all related resources
    public void shutdown() {
        throw new StopShellException();
    }

    public ArrayList<NameserverDomain>  getChildren(){
        return children;
    }

    public void addChildren(NameserverDomain store){
        children.add(store);
    }

    public void deleteChildren(NameserverDomain store){
        children.remove(store);
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

}
