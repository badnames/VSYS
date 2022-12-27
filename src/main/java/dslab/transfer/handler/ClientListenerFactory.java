package dslab.transfer.handler;

import dslab.util.Message;
import dslab.util.handler.IListener;
import dslab.util.handler.IListenerFactory;

import java.net.Socket;
import java.util.LinkedList;
import java.util.concurrent.BlockingDeque;

public class ClientListenerFactory implements IListenerFactory {

    private final LinkedList<ClientListener> handlers = new LinkedList<>();
    private final BlockingDeque<Message> commandQueue;

    public ClientListenerFactory(BlockingDeque<Message> commandQueue) {
        this.commandQueue = commandQueue;
    }

    @Override
    public IListener newHandler(Socket socket) {
        ClientListener handler = new ClientListener(socket, commandQueue);
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
