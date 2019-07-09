package apps.java.loref.SocketResponder;

/**
 * Provides the default configuration values for SocketResponder class
 *
 * @author lore_f.
 *         Created 23 dic 2018.
 */

@SuppressWarnings("javadoc")
public class DefaultValues {
    
    /**
     * Default timeout for authentication, in milliseconds
     */
    public static final long AUTH_TIMEOUT = 10000;
    
    /**
     * Default timeout for inactivity, in milliseconds
     */
    public static final long INACTIVITY_TIMEOUT = 60000;
    
    /**
     * Default tick used for all the threads where a loop is needed.
     */
    public static final long TICK_TIME = 1000;
    
    
    public static final String AUTH_OK_LOCAL = "Local connections enabled. No need to authenticate. :)\n";
    public static final String AUTH_OK_WL = "Host IP address in whitelist. :)\n";
    public static final String AUTH_KO = "Host IP address NOT in whitelist. Please authenticate. :(\n";
    
    public static final String MAXINPUTLENGTHREACHED="Max. input length reached. Input ignored. :(\n";
    
    public static final String AUTH_TIMEOUT_MSG="Auth timeout. Goodbye.";
    
    public static final String AUTH_REASON_LOCAL = "Local";
    public static final String AUTH_REASON_WL = "Whitelist";
    public static final String AUTH_REASON_USER = "User";
    
    public static final String WRONG_DATA_FORMAT = "Unkwnown command :(";

    public static final String HEADER_EXIT = "@EXIT";
    public static final String HEADER_QUIET = "@QUIET";
    public static final String HEADER_VERBOSE = "@VERBOSE";
    public static final String HEADER_COMMAND = "@COMMAND";
    public static final String HEADER_HELLO = "@HELLO";
    public static final String HEADER_USER = "@USER";
    public static final String HEADER_MODEPERSISTENT = "@MODE-PERSISTENT";
    public static final String HEADER_MODEEXITATCOMMAND = "@MODE-EXITATCOMMAND";
    public static final String HEADER_CONNINFO = "@CONNINFO";
    public static final String HEADER_SETTIMEOUT = "@SETTIMEOUT";

    public static final String HEADER_HELPCMDLIST = "@HELP-CMDLIST";

    public static final String REPLY_EXIT = "Closing connection. Goodbye. :|";
    public static final String REPLY_COMMAND_OK = "Command received. :)";
    public static final String REPLY_COMMAND_KO = "Unable to structure command. :(";
    public static final String REPLY_QUIET = "Quiet mode set. :)";
    public static final String REPLY_VERBOSE = "Verbose mode set. :)";
    public static final String REPLY_MODEPERSISTENT = "Persistent mode set. :)";
    public static final String REPLY_MODEEXITATCOMMAND = "Exit-at-command mode set. :)";
    public static final String REPLY_ALREADYAUTHTENTICATED = "Already authenticated. :(";
    public static final String REPLY_NOTYETAUTHENTICATED = "Please authenticate first. :(";
    public static final String REPLY_NOTINWHITELIST = "Provided UID is not in whitelist. :(";
    public static final String REPLY_AUTHENTICATIONSUCCEEDED= "Autentication Succeeded! :)";
        
    public static final String REPLY_HELPLIST = HEADER_EXIT + "\n" + HEADER_QUIET + "\n" + HEADER_VERBOSE + "\n" + HEADER_COMMAND + "\n" + HEADER_HELLO + "\n" + HEADER_USER + "\n" + HEADER_HELPCMDLIST;
    
}
