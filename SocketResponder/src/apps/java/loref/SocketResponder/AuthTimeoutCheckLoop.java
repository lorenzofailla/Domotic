package apps.java.loref.SocketResponder;

import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;
import static apps.java.loref.LogUtilities.debugLog;

@SuppressWarnings("javadoc")
public class AuthTimeoutCheckLoop extends Thread {

    private SocketConnection connection;
    private long authTimeout;

    private SocketResponder parent;

    private boolean keepLooping = true;

    public AuthTimeoutCheckLoop(SocketResponder parent, SocketConnection connection) {

	this.parent = parent;
	this.connection = connection;
	this.authTimeout = DefaultValues.AUTH_TIMEOUT;

    }

    public void setAuthTimeout(long value) {

	this.authTimeout = value;

    }

    @Override
    public void run() {

	if (AuthTimeoutCheckLoop.this.parent.getDebugMode()) {

	    debugLog(AuthTimeoutCheckLoop.class, "Thread started, attached to id:" + AuthTimeoutCheckLoop.this.connection.getID());

	}

	// attende che venga raggiunto il timeout di inattività
	while (this.keepLooping && (this.connection.getTotalTime() < this.authTimeout) && !this.connection.getIsAuthenticated()) {

	    sleepSafe(DefaultValues.TICK_TIME);

	}

	// if keepLooping flag is true, this means that the auth time has
	// reached its timeout
	// therefore, a goodbye message will be send and the connection closed
	// on the contrary, if keepLooping flag is false, this means that the
	// loop has been broken by the closeConnection method
	// in this case no message has to be sent and no connection has to be
	// closed

	if (this.keepLooping) {

	    // timeout di auth raggiunto
	    if (this.connection.getMessagesEnabled()) {
		this.connection.send(DefaultValues.AUTH_TIMEOUT_MSG);
	    }

	    // termina la connessione
	    this.parent.closeConnection(this.connection.getID(), true);

	    if (AuthTimeoutCheckLoop.this.parent.getDebugMode()) {

		debugLog(AuthTimeoutCheckLoop.class, "Auth timeout threashold triggered for id:" + AuthTimeoutCheckLoop.this.connection.getID());

	    }

	}

	if (AuthTimeoutCheckLoop.this.parent.getDebugMode()) {

	    debugLog(AuthTimeoutCheckLoop.class, "Thread terminated, attached to id:" + AuthTimeoutCheckLoop.this.connection.getID());

	}

    }

    public void close() {

	this.keepLooping = false;

    }

}
