package apps.java.loref;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

public class FCMUtilities {

    private static final String FCM_NOTIFICATION_FAILURE_OUTPUT_MESSAGE = "Notification failure.";
    private static final String FCM_URL = "https://fcm.googleapis.com/fcm/send";

    public static String sendFCM(String API_KEY, String to, String title, String body, String dataPayload) {

	JSONObject notification = new JSONObject();
	notification.put("title", title);
	notification.put("body", body);

	JSONObject json = new JSONObject();
	json.put("to", to);
	json.put("notification", notification);

	try {
	    JSONObject data = new JSONObject(dataPayload);

	    json.put("data", data);
	    
	} catch (NullPointerException | JSONException e) {
	    	    

	}

	System.out.println("outgoing JSON:\n" + json.toString());

	try {

	    URL url = new URL(FCM_URL);
	    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

	    connection.setRequestMethod("POST");

	    connection.setRequestProperty("Content-Type", "application/json");
	    connection.setRequestProperty("Authorization", "key=" + API_KEY);

	    connection.setDoOutput(true);

	    OutputStream os = connection.getOutputStream();
	    os.write(json.toString().getBytes());

	    os.flush();
	    os.close();

	    int responseCode = connection.getResponseCode();

	    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	    String inputLine;
	    StringBuffer response = new StringBuffer();

	    while ((inputLine = in.readLine()) != null) {
		response.append(inputLine);
	    }
	    in.close();

	    JSONObject reply = new JSONObject(response.toString());
	    System.out.println(reply.toString());

	    if (reply.has("message_id")) {

		// messaggio inviato con successo
		return reply.get("message_id").toString();

	    } else {

		return FCM_NOTIFICATION_FAILURE_OUTPUT_MESSAGE;
	    }

	} catch (MalformedURLException e) {

	    return e.getMessage();

	} catch (IOException e) {

	    return e.getMessage();

	}

    }

}
