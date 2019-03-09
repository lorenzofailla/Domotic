package apps.java.loref;

import java.io.IOException;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.youtube.model.LiveBroadcast;
import com.google.api.services.youtube.model.LiveStream;
import com.google.common.collect.Lists;

public class GetFirstAuth {

	public static void main(String[] args) {

		// @formatter:off
		//final String CREDENTIALS_DIRECTORY = "/etc/domotic/.oauth-token";
		//final String CLIENT_SECRETS_FILE_PATH = "/etc/domotic/client_secret.json";

		final String CREDENTIALS_DIRECTORY = "C:\\Users\\lore_f\\.oauth-token";
		final String CLIENT_SECRETS_FILE_PATH = "C:\\Users\\lore_f\\Google-API_keys\\client_secret_410796746391-j68vlpi0pjn5cerf7q7rmfpjvofufu0g.apps.googleusercontent.com.json";

		// @formatter:on

		YouTubeComm youTubeComm;

		try {

			youTubeComm = new YouTubeComm("Domotic", CLIENT_SECRETS_FILE_PATH, CREDENTIALS_DIRECTORY);
			youTubeComm.setDebugMode(true);
			youTubeComm.setListener(new YouTubeCommListener() {

				@Override
				public void onLiveStreamCreated(String requestorID, String requestID, String liveStreamID,
						String liveBroadcastID) {
					System.out.println("Requestor ID: " + requestorID + "\nrequest ID: " + requestID
							+ "\nliveStreamID: " + liveStreamID + "\n liveBroadcastID: " + liveBroadcastID);

				}

				@Override
				public void onLiveBroadCastDeleted(String broadcastID) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onLiveStreamDeleted(String broadcastID) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onLiveStreamNotCreated(String requestorID, String requestID) {
					// TODO Auto-generated method stub.

				}

			});
			
			System.out.print("Checking authorization... ");
			boolean result = youTubeComm.isAuthorized();
			System.out.print("Result=\""+result+"\".\n");
			
			System.out.print("Trying to create a live stream broadcast... ");
			youTubeComm.createLiveStream("title", "requestorID", "requestID");
						
			
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
