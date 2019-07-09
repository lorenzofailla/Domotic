package apps.java.loref.SocketResponder;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static apps.java.loref.LogUtilities.debugLog_GRAYXTERM;
import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;

/**
 * Provides a basic implementation of a TCP IO interface on a TCP port.
 *
 * @author lore_f.
 *         Created 08 dic 2018.
 */

@SuppressWarnings("javadoc")
public class InStreamReaderLoop extends Thread {

    private static final int DEFAULT_MAX_INPUT_LENGTH = 65535;
    private SocketResponder parent;
    private SocketConnection connection;
    private int maxInputLength;

    public InStreamReaderLoop(SocketResponder parent, SocketConnection connection) {

	this.parent = parent;
	this.connection = connection;
	this.maxInputLength = DEFAULT_MAX_INPUT_LENGTH;
    }

    public void setMaxInputLenght(int value) {
	this.maxInputLength = value;
    }

    @Override
    public void run() {

	BufferedInputStream in = this.connection.getInStream();

	int b = 0;
	ByteArrayOutputStream out = new ByteArrayOutputStream();

	/*
	 * attende il flusso in arrivo
	 */

	try {

	    while ((b = in.read()) != -1) {

		// azzera il timeout di inattività della connessione
		// soltanto se è una connessione autenticata
		if (this.connection.getIsAuthenticated()) {
		    this.connection.resetInactivityTime();
		}

		if (b == '\n') {

		    if (this.parent.getListener() != null) {

			out.flush();
			
			if (this.parent.getDebugMode()) {
			    debugLog_GRAYXTERM(InStreamReaderLoop.class, "New line received --> "+ out.toString());
			}

			this.parent.getListener().onLineReceived(this.connection.getID(), out.toString());

			String reply = this.parent.processInStream(this.connection.getID(), out.toString());

			if (this.connection.getMessagesEnabled()) {

			    this.connection.send((reply + "\n").getBytes());

			}

			// reinizializza l'array di byte
			out = new ByteArrayOutputStream();

		    }

		} else {

		    if (out.size() > this.maxInputLength) {

			this.connection.send(DefaultValues.MAXINPUTLENGTHREACHED);

			// reinizializza l'array di byte
			out = new ByteArrayOutputStream();

		    }

		    out.write(b);
		}

	    }

	    if (this.parent.getDebugMode()) {

		debugLog_GRAYXTERM(InStreamReaderLoop.class, "End of inputstream loop thread");

	    }

	} catch (IOException e) {

	    exceptionLog_REDXTERM(this.getClass(), e);

	} finally {

	    // si assicura che la connessione venga eliminata dalla mappa
	    if (this.parent.getConnections().containsKey(this.connection.getID())) {

		this.parent.closeConnection(this.connection.getID(), false);

	    }

	}

    }

}
