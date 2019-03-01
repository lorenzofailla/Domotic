package apps.java.loref;

import static apps.java.loref.GeneralUtilitiesLibrary.parseHttpRequest;

public class NetworkTools {

    public static boolean checkInetConnection(String serverHttpAddress) {

	String httpReply = parseHttpRequest(serverHttpAddress);
	return !httpReply.startsWith("Error");

    }

}
