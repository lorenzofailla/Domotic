package apps.java.loref;

public interface YouTubeCommListener {
    
    void onLiveBroadCastDeleted(String broadcastID);
    void onLiveStreamDeleted(String broadcastID);
    
    void onLiveStreamCreated(String requestorID, String requestID, String liveStreamID, String liveBroadcastID);
    void onLiveStreamNotCreated(String requestorID, String requestID);
    
}
