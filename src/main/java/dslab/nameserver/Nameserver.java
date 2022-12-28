package dslab.nameserver;

import java.io.*;
import java.lang.reflect.Array;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.mailbox.handler.DMAPListenerFactory;
import dslab.mailbox.handler.DMTPListenerFactory;
import dslab.util.Config;
import dslab.util.handler.DispatchListener;
import dslab.util.handler.IListener;

import javax.swing.tree.TreeNode;

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
    private ArrayList<NameStore> nameStores;

    private final Shell shell;
    private final Config config;

    private final DispatchListener register;
    private final DispatchListener listener;


    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) throws IOException {
        // TODO
        this.componentID=componentId;
        this.config=config;
        this.in=in;
        this.out=out;

        String serverDomain = config.getString("domain");

        register= new DispatchListener(config.getInt("registry.port"), 4, new NameListenerFactory());
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

        if (nameStores==null){
            // new
            nameStores.add(new NameStore(null,0,0));
        }else {

        }
        //TODO:
        /*
        if (root==null){
            //TODO initialize root
            root= new Nameserver(null,null,null,null);
        }else {
            //TODO search for the domain
            function()
            //if domain is found, place it there
            //else create new Nameserver

        }*/
        shell.out().println("# "+config.getString("domain")+ " nameserver");
    }

    @Override
    @Command
    public void nameservers() {
        // TODO
        for (int i = 0; i < nameStores.size(); i++) {
            shell.out().println(i+". "+nameStores.get(i).getDomain());
        }

    }

    @Override
    @Command
    public void addresses() {
        // TODO
        for (int i = 0; i < nameStores.size(); i++) {
            shell.out().println(i+". "+nameStores.get(i).getDomain()+" "+nameStores.get(i).getIp()+":"+nameStores.get(i).getPort());
        }
    }

    @Override
    @Command
    public void shutdown() {
        // TODO
        register.stop();
        listener.stop();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }


}
