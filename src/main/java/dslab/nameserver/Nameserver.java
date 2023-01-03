package dslab.nameserver;

import java.io.*;
import java.util.ArrayList;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.handler.DispatchListener;

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
    private ArrayList<NameStore> children;  //TODO: put children here

    private final Shell shell;
    private final Config config;

    private final DispatchListener register;
    private final DispatchListener listener;

    private String domain;


    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) throws IOException {
        // TODO
        this.componentID=componentId;
        this.config=config;
        this.in=in;
        this.out=out;

        this.domain = config.getString("domain");

        register= new DispatchListener(config.getInt("registry.port"), 4, new NameRegisterFactory(domain, children));
        listener = new DispatchListener(config.getInt("registry.port"), 4, new NameListenerFactory());

        shell = new Shell(in, out);
        shell.setPrompt("[Nameserver " + config.getString("domain") + "] >>> ");
        shell.register(this);

    }

    @Override
    public void run() {
        // TODO
        new Thread(register).start();
        new Thread(listener).start();

        shell.run();
        shell.out().println("# "+config.getString("domain")+ " nameserver");


    }

    @Override
    @Command
    //Prints out each known nameserver (zones) in alphabetical order,
    // from the perspective of this nameserver
    public void nameservers() {
        // TODO: alphabetical
        for (int i = 0; i < children.size(); i++) {
            shell.out().println(i+". "+ children.get(i).getDomain());
        }

    }

    @Override
    @Command
    //Prints out some information about each stored mailbox server address, containing mail DOMAIN and
    //ADRESSES (IP:port), arranged by the domain in alphabetical order.
    public void addresses() {
        // TODO
        for (int i = 0; i < children.size(); i++) {
            shell.out().println(i+". "+ children.get(i).getDomain()+" "+ children.get(i).getIp()+":"+ children.get(i).getPort());
        }
    }

    @Override
    @Command
    //Shutdown the nameserver and all related resources
    public void shutdown() {
        // TODO
        register.stop();
        listener.stop();
        throw new StopShellException();
    }

    public ArrayList<NameStore>  getChildren(){
        return children;
    }

    public void addChildren(NameStore store){
        children.add(store);
    }

    public void deleteChildren(NameStore store){
        children.remove(store);
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }


}
