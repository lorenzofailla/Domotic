package apps.java.loref;

import java.io.IOException;
import java.net.Socket;

import static apps.java.loref.LogUtilities.debugLog_GRAYXTERM;
import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;

@SuppressWarnings("javadoc")
public class ConnectionListenerLoop extends Thread {

    private SocketResponder parent;

    public ConnectionListenerLoop(SocketResponder parent) {

	this.parent = parent;

    }

    @Override
    public void run() {

	if (ConnectionListenerLoop.this.parent.getDebugMode()) {

	    debugLog_GRAYXTERM(ConnectionListenerLoop.class, "Thread started.");

	}

	while (this.parent.isActive()) {

	    try {

		// WAITS, until a connection request comes.
		Socket socket = this.parent.getServerSocket().accept();

		String socketID = String.format("%s_%d", socket.getInetAddress().toString(), System.currentTimeMillis());
		SocketConnection connection = new SocketConnection(socketID, socket);

		// - Connection established -

		// adds the connection data to the map
		this.parent.getConnections().put(socketID, connection);

		// invia un messaggio di benvenuto
		if (connection.getMessagesEnabled()) {
		    connection.send(String.format("Hello, %s.\n\r", socketID));
		}

		// manda una notifica all'interfaccia
		if (this.parent.getListener() != null) {
		    this.parent.getListener().onConnected(socket.getInetAddress().toString());
		}

		if (this.parent.getDebugMode()) {

		    System.out.println("Host Address: " + socket.getInetAddress().getHostAddress());
		    System.out.println("Host Name: " + socket.getInetAddress().getHostName());
		    System.out.println("isLoopBackAddress: " + socket.getInetAddress().isLoopbackAddress());
		    System.out.println("isAnyLocalAddress: " + socket.getInetAddress().isAnyLocalAddress());
		    System.out.println("isLinkLocalAddress: " + socket.getInetAddress().isLinkLocalAddress());

		}

		// effettua una serie di controlli, in ordine:
		// 1. controlla se l'indirizzo dell'host remoto è locale e se le
		// connessioni locali sono ammesse
		// 2. controlla se l'indirizzo dell'host è nella whitelist
		// se nessuna di queste condizioni viene verificata, invia una
		// richiesta di autenticazione all'utente
		// e fa partire il timeout di autenticazione.
		// se l'autenticazione non viene effettuata entro il timeout
		// specificato, la connessione viene chiusa

		if (this.parent.getAllowLocalConnections() && socket.getInetAddress().isLoopbackAddress()) {
		    // l'indirizzo dell'host remoto è locale e le connessioni
		    // locali sono ammesse

		    connection.setIsAuthenticated(true);

		    if (connection.getMessagesEnabled()) {
			connection.send(DefaultValues.AUTH_OK_LOCAL);
		    }

		    if (this.parent.getListener() != null) {
			this.parent.getListener().onAuth(socketID, DefaultValues.AUTH_REASON_LOCAL);
		    }

		} else if (this.parent.isInWhiteList(socket.getInetAddress())) {
		    // l'indirizzo dell'host è nella whitelist

		    connection.setIsAuthenticated(true);

		    if (connection.getMessagesEnabled()) {
			connection.send(DefaultValues.AUTH_OK_WL);
		    }

		    if (this.parent.getListener() != null) {
			this.parent.getListener().onAuth(socketID, DefaultValues.AUTH_REASON_WL);
		    }

		} else {

		    // TODO
		    // starts the authentication timeout thread for this
		    // connection

		    if (connection.getMessagesEnabled()) {
			connection.send(DefaultValues.AUTH_KO);
		    }

		}

		// starts the input stream reader thread for this connection
		new InStreamReaderLoop(this.parent, connection).start();

		// starts the inactivity timeout thread for this connection
		InactivityTimeoutCheckLoop inactivityTimeoutCheckLoop = new InactivityTimeoutCheckLoop(this.parent, connection);
		inactivityTimeoutCheckLoop.start();

		connection.setInactivityTimeoutCheckLoop(inactivityTimeoutCheckLoop);

	    } catch (IOException e) {
		// questa porzione di codice viene normalmente eseguita nel momento in cui viene invocato il metodo 
		// terminate() nella classe SocketResponder
		
		// se il ServerSocket è chiuso, questa eccezione è attesa.
		// altrimenti, stampa un messaggio di errore.

		if (!this.parent.getServerSocket().isClosed()) {
		    exceptionLog_REDXTERM(ConnectionListenerLoop.class, e);
		}

	    }

	}

	if (ConnectionListenerLoop.this.parent.getDebugMode()) {

	    debugLog_GRAYXTERM(ConnectionListenerLoop.class, "Thread completed.");

	}

    }

}
