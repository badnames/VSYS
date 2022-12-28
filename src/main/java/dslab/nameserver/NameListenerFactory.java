package dslab.nameserver;

import dslab.mailbox.handler.DMAPListener;
import dslab.util.handler.IListener;
import dslab.util.handler.IListenerFactory;

import java.net.Socket;
import java.util.LinkedList;

public class NameListenerFactory implements IListenerFactory {
    LinkedList<NameserverListener> handlers = new LinkedList<>();

    @Override
    public IListener newHandler(Socket socket) {
        NameserverListener handler = new NameserverListener(socket);
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
