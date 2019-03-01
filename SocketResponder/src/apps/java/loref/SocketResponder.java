package apps.java.loref;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;
import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;
import static apps.java.loref.SocketConnection.Mode;

@SuppressWarnings("javadoc")
public class SocketResponder {

    public static final int DEFAULT_PORT = 9099;

    /*
     * main thread
     */

    public static void main(String[] args) {

	System.out.println("SocketResponder");
	SocketResponder socketResponder = new SocketResponder();
	socketResponder.setListener(new SocketResponderListener() {

	    @Override
	    public void onCreated(int port) {

		System.out.println(String.format("Socket created. Port: %d", port));

	    }

	    @Override
	    public void onConnected(String host) {

		System.out.println(String.format("Connected to host %s.", host));
		System.out.println(String.format("Total n. of connections: %d", socketResponder.getConnectionsCount()));

	    }

	    @Override
	    public void onLineReceived(String hostID, String data) {
		System.out.println(String.format("%s, <<%s>>", hostID, data));

	    }

	    @Override
	    public void onCommand(String hostID, RemoteCommand command, HashMap<String, Object> params) {
		//

	    }

	    @Override
	    public void onDisconnect(String hostID, boolean byTimeout) {

		System.out.println(String.format("host id \"%s\" disconnected. By timeout=%s.", hostID, byTimeout));

	    }

	    @Override
	    public void onAuth(String hostID, String reason) {

		System.out.println(String.format("host id \"%s\" successfully authenticated. reason: %s", hostID, reason));

	    }

	});

	socketResponder.init();

	while (socketResponder.isActive()) {
	    sleepSafe(1000);
	}

    }

    /*
     * process
     */
    private ServerSocket serverSocket;
    private int port;
    private HashMap<String, SocketConnection> connections = new HashMap<>();
    private ConnectionListenerLoop connectionListenerLoop;
    private boolean debugMode = true;

    private boolean mainLoopFlag = true;

    private boolean allowLocalConnections = true;

    private List<InetAddress> remoteHostsWhiteList = new ArrayList<InetAddress>();
    private List<String> usersWhiteList = new ArrayList<String>();

    /*
     * getters and setters
     */

    public boolean getDebugMode() {
	return this.debugMode;
    }

    public void setDebugMode(boolean v) {
	this.debugMode = v;
    }

    public boolean getAllowLocalConnections() {
	return this.allowLocalConnections;
    }

    /*
     * constructor
     */
    public SocketResponder() {
	this.port = DEFAULT_PORT;

    }

    public SocketResponder(int port) {
	this.port = port;
    }

    /*
     * listener
     */

    private SocketResponderListener listener;

    public void setListener(SocketResponderListener l) {
	this.listener = l;
    }

    public SocketResponderListener getListener() {
	return this.listener;
    }

    /*
     * methods
     */

    public void init() {
	// inizializza il ServerSocket
	try {
	    this.serverSocket = new ServerSocket(this.port);

	    // manda una notifica all'interfaccia

	    if (this.listener != null) {
		this.listener.onCreated(this.serverSocket.getLocalPort());
	    }

	    /*
	     * avvia il loop di ascolto connessioni
	     */
	    this.connectionListenerLoop = new ConnectionListenerLoop(this);
	    this.connectionListenerLoop.start();

	} catch (IOException e) {

	    exceptionLog_REDXTERM(this.getClass(), e);

	}

    }

    public int getConnectionsCount() {
	return this.connections.size();
    }

    /**
     * chiude tutte le eventuali connessioni attive
     */
    public void terminate() {

	for (Entry<String, SocketConnection> e : this.connections.entrySet()) {
	    
	    this.closeConnection(e.getValue().getID(), false);
	    
	}

	// chiude il serversocket per l'ascolto delle connessioni in entrata
	this.mainLoopFlag = false;

	try {

	    this.serverSocket.close();

	} catch (IOException e) {

	    exceptionLog_REDXTERM(this.getClass(), e);

	}

    }

    public void closeConnection(String connectionID, long delay, boolean byTimeout) {

	SocketConnection connection = this.connections.get(connectionID);
	connection.closeConnection(delay);
	this.connections.remove(connectionID);

	// notifica al listener
	if (this.listener != null) {

	    this.listener.onDisconnect(connectionID, byTimeout);

	}
    }

    public void closeConnection(String connectionID, boolean byTimeout) {

	closeConnection(connectionID, 0, byTimeout);

    }

    public boolean isActive() {

	return this.mainLoopFlag;

    }

    public ServerSocket getServerSocket() {
	return this.serverSocket;
    }

    public HashMap<String, SocketConnection> getConnections() {
	return this.connections;
    }

    public String processInStream(String connectionID, String inputData) {

	/*
	 * processa la stringa in ingresso.
	 */

	// divide l'input con il carattere '?'
	String[] inputSplit = inputData.split("[?]");

	String header = inputSplit[0];

	SocketConnection connection = this.getConnections().get(connectionID);

	if (header.equals(DefaultValues.HEADER_USER)) {

	    if (!connection.getIsAuthenticated()) {

		// Checks whether an UID has been provided.

		if (inputSplit.length > 1) {
		    // An UID has been provided.

		    // Checks whether the provided UID is in the whitelist.

		    if (isUserInWhiteList(inputSplit[1])) {
			// Authentication succeeded.

			// Updates the property.
			connection.setIsAuthenticated(true);

			// Triggers the interface, if it exists.
			if (this.listener != null) {
			    this.listener.onAuth(connectionID, DefaultValues.AUTH_REASON_USER);
			}

			// Provides a reply message.
			return DefaultValues.REPLY_AUTHENTICATIONSUCCEEDED;

		    } else {
			// Authentication failed.

			// Provides a reply message.
			return DefaultValues.REPLY_NOTINWHITELIST;

		    }

		} else {
		    // No UID has been provided.

		    // Provides a reply message.
		    return DefaultValues.REPLY_NOTINWHITELIST;
		}

	    } else {

		// Authentication has already been done, and cannot be done
		// twice.

		// Provides a reply message.
		return DefaultValues.REPLY_ALREADYAUTHTENTICATED;

	    }

	} else {

	    if (!connection.getIsAuthenticated()) {

		// No action other than authentication can be done if
		// authentication has not been done yet.

		// Provides a reply message.
		return DefaultValues.REPLY_NOTYETAUTHENTICATED;

	    } else {

		switch (header) {

		case (DefaultValues.HEADER_CONNINFO):
		    String reply = new StringBuilder().append("").toString();

		    return reply;

		case (DefaultValues.HEADER_MODEEXITATCOMMAND):
		    connection.setMode(Mode.EXITATCOMMAND);
		    return DefaultValues.REPLY_MODEEXITATCOMMAND;

		case (DefaultValues.HEADER_MODEPERSISTENT):
		    connection.setMode(Mode.PERSISTENT);
		    return DefaultValues.REPLY_MODEPERSISTENT;

		case (DefaultValues.HEADER_QUIET):

		    connection.setMessagesEnabled(false);
		    return DefaultValues.REPLY_QUIET;

		case (DefaultValues.HEADER_VERBOSE):

		    connection.setMessagesEnabled(true);
		    return DefaultValues.REPLY_VERBOSE;

		case (DefaultValues.HEADER_EXIT):

		    closeConnection(connectionID, 1000, false);

		    // returns a goodbye message
		    return DefaultValues.REPLY_EXIT;

		case (DefaultValues.HEADER_HELLO):

		    // returns the handshake message
		    return String.format("Hello, %s.", connectionID);

		case (DefaultValues.HEADER_COMMAND):

		    if (inputSplit.length > 1) {

			RemoteCommand remoteCommand = new RemoteCommand(inputSplit[1], "tcp://" + connectionID);

			if (remoteCommand.getHeader().equals("")) {

			    return DefaultValues.REPLY_COMMAND_KO;

			} else if (this.listener != null) {

			    HashMap<String, Object> params = new HashMap<>();
			    params.put("disconnect", connection.getMode() == SocketConnection.Mode.EXITATCOMMAND);

			    this.listener.onCommand(connectionID, remoteCommand, params);
			    return DefaultValues.REPLY_COMMAND_OK;

			}

		    } else {

			return DefaultValues.REPLY_COMMAND_KO;

		    }

		default:

		    return DefaultValues.WRONG_DATA_FORMAT;

		}

	    }

	}

    }

    public void sendData(String connectionID, byte[] data) {

	if (this.connections.containsKey(connectionID)) {

	    this.connections.get(connectionID).send(data);

	}

    }

    public void addWhiteListHost(InetAddress hostToBeAdded) {

	this.remoteHostsWhiteList.add(hostToBeAdded);

    }

    public void removeWhiteListHost(InetAddress hostToBeRemoved) {

	int index = this.remoteHostsWhiteList.indexOf(hostToBeRemoved);
	if (index != -1) {
	    this.remoteHostsWhiteList.remove(index);
	}

    }

    public void clearWhiteListHost() {
	this.remoteHostsWhiteList.clear();
    }

    public boolean isInWhiteList(InetAddress addressToBeChecked) {
	return this.remoteHostsWhiteList.indexOf(addressToBeChecked) != -1;
    }

    public void addWhiteListUser(String userToBeAdded) {

	this.usersWhiteList.add(userToBeAdded);

    }

    public void removeWhiteListUser(InetAddress userToBeRemoved) {

	int index = this.usersWhiteList.indexOf(userToBeRemoved);
	if (index != -1) {
	    this.usersWhiteList.remove(index);
	}

    }

    public void clearWhiteList() {
	this.usersWhiteList.clear();
    }

    public boolean isUserInWhiteList(String userToBeChecked) {
	return this.usersWhiteList.indexOf(userToBeChecked) != -1;
    }

}
