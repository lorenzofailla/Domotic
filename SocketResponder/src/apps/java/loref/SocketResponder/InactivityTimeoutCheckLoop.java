package apps.java.loref.SocketResponder;

import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;

import static apps.java.loref.LogUtilities.debugLog;

@SuppressWarnings("javadoc")
public class InactivityTimeoutCheckLoop extends Thread {

    private SocketConnection connection;
    private long inactivityTimeout;

    private SocketResponder parent;

    private boolean keepLooping = true;

    public InactivityTimeoutCheckLoop(SocketResponder parent, SocketConnection connection) {

	this.parent = parent;
	this.connection = connection;
	this.inactivityTimeout = DefaultValues.INACTIVITY_TIMEOUT;

    }

    public void setInactivityTimeout(long value) {

	this.inactivityTimeout = value;

    }

    @Override
    public void run() {
	
	if (InactivityTimeoutCheckLoop.this.parent.getDebugMode()) {

	    debugLog(InactivityTimeoutCheckLoop.class, "Thread started, attached to id:" + InactivityTimeoutCheckLoop.this.connection.getID());

	}

	// attende che venga raggiunto il timeout di inattività
	while (this.keepLooping && (this.connection.getInactivityTime() < this.inactivityTimeout)) {

	    sleepSafe(DefaultValues.TICK_TIME);

	}

	// if keepLooping flag is true, this means that the inactivity time has
	// reached its timeout
	// therefore, a goodbye message will be send and the connection closed
	// on the contrary, if keepLooping flag is false, this means that the
	// loop has been broken by the closeConnection method
	// in this case no message has to be sent and no connection has to be
	// closed

	if (this.keepLooping) {

	    // timeout di inattività raggiunto
	    if (this.connection.getMessagesEnabled()) {
		this.connection.send("Inactivity timeout. Goodbye.");
	    }

	    // termina la connessione
	    this.parent.closeConnection(this.connection.getID(), true);
	    
	    if (InactivityTimeoutCheckLoop.this.parent.getDebugMode()) {

		    debugLog(InactivityTimeoutCheckLoop.class, "Inactivity timeout threashold triggered for id:" + InactivityTimeoutCheckLoop.this.connection.getID());

		}

	}
	
	if (InactivityTimeoutCheckLoop.this.parent.getDebugMode()) {

	    debugLog(InactivityTimeoutCheckLoop.class, "Thread terminated, attached to id:" + InactivityTimeoutCheckLoop.this.connection.getID());

	}

    }

    public void close() {

	this.keepLooping = false;

    }

}
