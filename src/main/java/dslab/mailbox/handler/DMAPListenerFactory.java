package dslab.mailbox.handler;

import dslab.util.handler.IListener;
import dslab.util.handler.IListenerFactory;

import java.io.PrintWriter;
import java.net.Socket;
import java.security.PrivateKey;
import java.util.LinkedList;

public class DMAPListenerFactory implements IListenerFactory {
    private LinkedList<DMAPListener> handlers = new LinkedList<>();
    private final String componentId;
    private final PrivateKey rsaPrivateKey;

    public DMAPListenerFactory(String componentId, PrivateKey rsaPrivateKey) {
        this.componentId = componentId;
        this.rsaPrivateKey = rsaPrivateKey;
    }

    @Override
    public IListener newHandler(Socket socket) {
        DMAPListener handler = new DMAPListener(socket, componentId, rsaPrivateKey);
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
