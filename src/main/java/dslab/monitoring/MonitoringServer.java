package dslab.monitoring;

import java.io.InputStream;
import java.io.PrintStream;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.monitoring.listener.UsageListener;
import dslab.util.Config;

public class MonitoringServer implements IMonitoringServer {

    private final Shell shell;
    private final UsageListener handler;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        UsageStore.getInstance().init();

        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt("[Monitoring] >>>");

        handler = new UsageListener(config.getInt("udp.port"));
    }

    @Override
    @Command
    public void run() {
        new Thread(handler).start();
        shell.run();
    }

    @Override
    @Command
    public void addresses() {
        shell.out().println(UsageStore.getInstance().addressAccessesToString());
    }

    @Override
    @Command
    public void servers() {
        shell.out().println(UsageStore.getInstance().serverAccessesToString());
    }

    @Override
    @Command
    public void shutdown() {
        handler.stop();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }

}
