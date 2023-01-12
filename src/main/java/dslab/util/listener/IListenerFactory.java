package dslab.util.listener;

import java.net.Socket;

public interface IListenerFactory {
    IListener newHandler(Socket socket);

    void stopAll();
}
