package dslab.mailbox.handler;

import dslab.util.handler.IListener;
import dslab.util.handler.IListenerFactory;

import java.net.Socket;
import java.util.LinkedList;

public class DMAPListenerFactory implements IListenerFactory {
    LinkedList<DMAPListener> handlers = new LinkedList<>();

    @Override
    public IListener newHandler(Socket socket) {
        DMAPListener handler = new DMAPListener(socket);
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
