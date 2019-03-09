package apps.java.loref;

import apps.java.loref.YouTubeComm;
import apps.java.loref.YouTubeNotAuthorizedException;

public class Test {

    public static void main(String[] args) {
	
	String APP_NAME = "Domotic";

	String youTubeJSONLocation = "/etc/domotic/client_secret.json";
	String youTubeOAuthFolder = "/etc/domotic/.oauth-token";
	
	System.out.println("YouTubeComm test class");

	try {
	    
	    YouTubeComm youTubeComm = new YouTubeComm(APP_NAME, youTubeJSONLocation, youTubeOAuthFolder);
	    
	} catch (YouTubeNotAuthorizedException e) {
	    
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	    
	}
	
    }

}
