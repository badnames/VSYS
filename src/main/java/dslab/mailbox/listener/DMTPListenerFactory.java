package dslab.mailbox.listener;

import dslab.util.listener.IListener;
import dslab.util.listener.IListenerFactory;

import java.net.Socket;
import java.util.LinkedList;

public class DMTPListenerFactory implements IListenerFactory {

    private final String serverDomain;
    private final LinkedList<DMTPListener> handlers = new LinkedList<>();

    public DMTPListenerFactory(String serverDomain) {
        this.serverDomain = serverDomain;
    }

    @Override
    public IListener newHandler(Socket socket) {
        DMTPListener handler = new DMTPListener(socket, serverDomain);
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
