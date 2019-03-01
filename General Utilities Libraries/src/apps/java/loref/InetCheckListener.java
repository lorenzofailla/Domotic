package apps.java.loref;

public interface InetCheckListener {
    
    void onConnectionLost();
    
    void onConnectionRestored();
    
    void onCheck(boolean status);
    
}
