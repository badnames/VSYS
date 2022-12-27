package dslab.transfer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.net.SocketTimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import dslab.ComponentFactory;
import dslab.Constants;
import dslab.Sockets;
import dslab.TestBase;
import dslab.monitoring.MonitoringServerTest;
import dslab.util.Config;

/**
 * TransferServerTest.
 */
public class TransferServerTest extends TestBase {

    private static final Log LOG = LogFactory.getLog(MonitoringServerTest.class);

    @Test
    public void runAndShutdownTransferServer_createsAndStopsTcpSocketCorrectly() throws Exception {
        ITransferServer component = ComponentFactory.createTransferServer("transfer-1", in, out);
        int port = new Config("transfer-1").getInt("tcp.port");

        assertThat(component, is(notNullValue()));

        Thread componentThread = new Thread(component);
        LOG.info("Starting thread with component " + component);
        componentThread.start();

        try {
            LOG.info("Waiting for socket to open on port " + port);
            Sockets.waitForSocket("localhost", port, Constants.COMPONENT_STARTUP_WAIT);
        } catch (SocketTimeoutException e) {
            err.addError(new AssertionError("Expected a TCP server socket on port " + port, e));
        }

        LOG.info("Shutting down component " + component);
        in.addLine("shutdown"); // send "shutdown" command to command line
        Thread.sleep(Constants.COMPONENT_TEARDOWN_WAIT);

        try {
            LOG.info("Waiting for thread to stop for component " + component);
            componentThread.join();
        } catch (InterruptedException e) {
            err.addError(new AssertionError("Monitoring server was not terminated correctly"));
        }

        err.checkThat("Expected tcp socket on port " + port + " to be closed after shutdown",
                Sockets.isServerSocketOpen(port), is(false));
    }

}
