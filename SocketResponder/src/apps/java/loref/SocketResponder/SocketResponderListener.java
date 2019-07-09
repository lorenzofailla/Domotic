package apps.java.loref.SocketResponder;

import java.util.HashMap;

import apps.java.loref.RemoteCommand;

@SuppressWarnings("javadoc")
public interface SocketResponderListener {

    void onCreated(int port);
    void onConnected(String hostID);
    void onLineReceived(String hostID, String data);
    void onCommand(String hostID, RemoteCommand command, HashMap<String, Object> params);
    void onDisconnect(String hostID, boolean byTimeout);
    void onAuth(String hostID, String reason);
    
}
