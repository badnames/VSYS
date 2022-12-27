package dslab.util.handler;

import java.net.Socket;

public interface IListenerFactory {
    IListener newHandler(Socket socket);
    void stopAll();
}
