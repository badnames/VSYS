package dslab.nameserver;

import dslab.mailbox.handler.DMTPListener;
import dslab.util.handler.IListener;
import dslab.util.handler.IListenerFactory;

import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;

public class NameRegisterFactory implements IListenerFactory {
    private final String serverDomain;
    private final ArrayList<NameStore> store;
    private final LinkedList<NameRegister> handlers = new LinkedList<>();

    public NameRegisterFactory(String serverDomain,ArrayList<NameStore> store ) {
        this.serverDomain = serverDomain;
        this.store =store;
    }

    @Override
    public IListener newHandler(Socket socket) {
        NameRegister handler = new NameRegister(socket, serverDomain, store);
        handlers.add(handler);
        return handler;
    }

    @Override
    public void stopAll() {
        for (var handler : handlers) {
            handler.stop();
        }
    }
}
