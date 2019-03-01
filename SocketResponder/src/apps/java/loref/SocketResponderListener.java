package apps.java.loref;

import java.util.HashMap;

@SuppressWarnings("javadoc")
public interface SocketResponderListener {

    void onCreated(int port);
    void onConnected(String hostID);
    void onLineReceived(String hostID, String data);
    void onCommand(String hostID, RemoteCommand command, HashMap<String, Object> params);
    void onDisconnect(String hostID, boolean byTimeout);
    void onAuth(String hostID, String reason);
    
}
