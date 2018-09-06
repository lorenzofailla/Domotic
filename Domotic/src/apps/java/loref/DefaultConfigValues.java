package apps.java.loref;

public class DefaultConfigValues {
	
	public static final String CONFIG_FILE_LOCATION = "/etc/domotic/domotic.conf";
	public static final String LOCAL_COMMAND_DIRECTORY = "/var/lib/domotic";
	
	public static final String PUBLIC_IP_COMMAND = "curl ifconfig.co";
	public static final String LOCAL_IP_COMMAND = "hostname -I";
	
	public static final String ERROR = "[error]";
	
	public final static long CONNECTION_CHECK_INTERVAL_TIMEOUT_LONG=10000L;
	public final static long CONNECTION_CHECK_INTERVAL_TIMEOUT_SHORT=10000L;
	
	public final static long DEVICE_STATUS_UPDATE_RATE=60000L;
	public final static long DEVICE_NETWORK_STATUS_UPDATE_RATE=3600000L;
	
	public final static long FIREBASE_DB_UPDATE_TIMEOUT=10000L;
	
	
	
}
