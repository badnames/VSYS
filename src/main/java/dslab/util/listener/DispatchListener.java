package dslab.util.listener;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DispatchListener implements IListener {

    private final ServerSocket socket;
    private final ExecutorService pool;
    private final IListenerFactory factory;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public DispatchListener(int port, int threads, IListenerFactory factory) throws IOException {
        socket = new ServerSocket(port);
        pool = Executors.newFixedThreadPool(threads);
        this.factory = factory;
    }

    @Override
    public void run() {
        while (running.get()) {

            Socket clientSocket;
            try {
                clientSocket = socket.accept();
            } catch (IOException e) {
                System.err.println("Error while accepting new connection: " + e.getMessage());
                continue;
            }

            pool.execute(factory.newHandler(clientSocket));
        }
    }

    @Override
    public synchronized void stop() {
        running.set(false);

        try {
            socket.close();
            factory.stopAll();
            //noinspection ResultOfMethodCallIgnored
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (IOException e) {
            System.err.println("Error closing socket of dispatch listener: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Interrupted during await termination: " + e.getMessage());
        }
    }
}
