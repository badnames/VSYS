package dslab.nameserver;

import java.io.*;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.mailbox.handler.DMAPListenerFactory;
import dslab.mailbox.handler.DMTPListenerFactory;
import dslab.util.Config;
import dslab.util.handler.DispatchListener;

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
    private TreeNode root = null;

    private final DispatchListener nameDispatcher;

    private final Shell shell;
    private final Config config;


    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) throws IOException {
        // TODO
        this.componentID=componentId;
        this.config=config;
        this.in=in;
        this.out=out;

        //TODO: by the root there is no server domain..??
        //String serverDomain = config.getString("domain");

        nameDispatcher = new DispatchListener(config.getInt("root_id"), 4, new NameListenerFactory());

        shell = new Shell(in, out);
        //TODO: by the root there is no server domain..??
        shell.setPrompt("[Nameserver " + config.getString("domain") + "] >>> ");
        shell.register(this);

    }

    @Override
    public void run() {
        // TODO
        new Thread(nameDispatcher).start();
        shell.run();

        if (root==null){
            try {
                root= (TreeNode) new Nameserver(componentID, config, in, out);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
    }

    @Override
    public void nameservers() {
        // TODO

    }

    @Override
    public void addresses() {
        // TODO
    }

    @Override
    @Command
    public void shutdown() {
        // TODO
        nameDispatcher.stop();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

}
