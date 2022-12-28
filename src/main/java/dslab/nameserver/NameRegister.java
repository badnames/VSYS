package dslab.nameserver;

import dslab.util.handler.IListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NameRegister implements Runnable, IListener {
    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private NameStore store = new NameStore(null,0,0);

    public NameRegister(Socket socket) {
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
        writer.println("ok Nameserver Register");
        writer.flush();

        while(!socket.isClosed()) {
            register();
        }
    }

    public void register(){

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
