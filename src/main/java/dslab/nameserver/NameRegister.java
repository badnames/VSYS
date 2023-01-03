package dslab.nameserver;

<<<<<<< HEAD
=======
import dslab.util.Config;
>>>>>>> origin/Nameserver
import dslab.util.handler.IListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.ArrayList;

public class NameRegister implements Runnable, IListener, INameserverRemote {
    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ArrayList<NameserverDomain> store;
    //private String domain;

    public NameRegister(Socket socket, String serverDomain, ArrayList<NameserverDomain> store) {
        this.socket = socket;
        //this.domain = serverDomain;
        this.store = store;
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
            for (int i = 0; i < store.size(); i++) {
                //TODO: get the exact Nameserver here to register
                //registerNameserver(store.get(i).getDomain(),store.get(i).);
            }
        }
    }

    @Override
    public synchronized void stop() {
        try {
            this.socket.close();
        } catch (IOException e) {
            System.err.println("Could not close socket on exit!");
        }
    }

    @Override
    //Registers a mailbox server with the given address for the given domain.
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        String zone = "";
        int zoneCount = 0;
        for (int i = 0; i < domain.length(); i++) {
            if (domain.charAt(i) == '.'){
                zoneCount++;
            }
        }

        if (store.isEmpty()){ //nothing is there --> set root
            store.add(new NameserverDomain(domain,0,0)); //TODO: port and host
        }else{  //see if the zone is already registered
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getDomain().endsWith(zone)){ //if yes, the message goes there

                }
            }

            //look for children
            //if exist, pass the zone there
            //if not exist, then make a new zone there

        }
    }

    @Override
    //Returns a reference to the remote object of the ns for the given zone. For example, if called with the argument
    //     * 'earth' on the remote object of zone 'planet', the call returns the reference to the nameserver of the zone
    //     * 'earth.planet'.
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        return null;
    }

    @Override
    //It locates the root nameserver and queries it for the nameserver of the top-level
    public String lookup(String username) throws RemoteException {
        return null;
    }
}
