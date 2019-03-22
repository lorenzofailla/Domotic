package apps.java.loref;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

@SuppressWarnings("javadoc")
public class NetworkTools {

	public static boolean checkInetConnection(String serverHttpAddress) {
			
		try {

			InetAddress inetAddress = InetAddress.getByName(serverHttpAddress);
			return inetAddress.isReachable(5000);
			
		} catch (UnknownHostException e) {
			
			return false;
			
		} catch (IOException e) {
			
			return false;
		
		}

	}

}
