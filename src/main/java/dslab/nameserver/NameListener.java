package dslab.nameserver;
import dslab.util.handler.IListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NameListener implements Runnable, IListener {
    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private NameStore store = new NameStore(null,0,0);

    public NameListener(Socket socket) {
        this.socket = socket;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream());
        } catch (IOException e) {
            System.err.println("Error creating reader/writer for socket!");
        }
    }

    @Override
    public void run() {
        //Todo: löschen ok Nameserver
        writer.println("ok Nameserver Listener");
        writer.flush();

        while(!socket.isClosed()) {
            find();
        }
    }

    //find the mailbox server for the mail and puts the mail there
    //TODO: should be separate?
    //TODO: separate class because thread, but syncronised how?
    //TODO: do a boolean function to see, if the mailbox server is already registered before putting the e-mail there
    public void find(){

    }

    @Override
    public synchronized void stop() {
        try {
            this.socket.close();
        } catch (IOException e) {
            System.err.println("Could not close socket on exit!");
        }
    }

}
