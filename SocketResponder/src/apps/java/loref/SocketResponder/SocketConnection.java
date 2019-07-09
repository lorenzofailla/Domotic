package apps.java.loref.SocketResponder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;


@SuppressWarnings("javadoc")
public class SocketConnection {

    private Socket socket;
    private long connectionTime;
    private long lastActivityTime;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private String id;
    private InactivityTimeoutCheckLoop timeoutLoop;
    private boolean messagesEnabled = true;
    private boolean isAuthenticated = false;
    
    public static enum Mode{
	PERSISTENT,
	EXITATCOMMAND
    }
    
    private Mode modeSet = Mode.PERSISTENT;
    
    public SocketConnection(String ID, Socket socket) {

	this.socket = socket;
	this.id = ID;
	this.connectionTime = System.currentTimeMillis();
	this.lastActivityTime = this.connectionTime;

	try {
	    
	    this.in = new BufferedInputStream(socket.getInputStream());
	    this.out = new BufferedOutputStream(socket.getOutputStream());
	    
	} catch (IOException e) {

	    exceptionLog_REDXTERM(this.getClass(), e);
	    
	}

    }

    /*
     * Getters / setters
     */

    public String getID() {
	return this.id;
    }
    
    public void setInactivityTimeoutCheckLoop(InactivityTimeoutCheckLoop loop){
	
	this.timeoutLoop=loop;
	
    }
    
    public boolean getMessagesEnabled(){
	return this.messagesEnabled;
    }
    
    public void setMessagesEnabled(boolean value){
	this.messagesEnabled=value;
    }
    
    public void setIsAuthenticated(boolean value){
	this.isAuthenticated=value;
    }
    
    public boolean getIsAuthenticated(){
	return this.isAuthenticated;
    }
    
    public Mode getMode(){
	return this.modeSet;
    }

    public void setMode(Mode mode){
	this.modeSet=mode;
    }
    
    /*
     * Methods
     */

    public long getTotalTime() {
	return System.currentTimeMillis() - this.connectionTime;
    }

    public long getInactivityTime() {
	return System.currentTimeMillis() - this.lastActivityTime;
    }

    public void resetInactivityTime() {
	this.lastActivityTime = System.currentTimeMillis();
    }

    public void send(byte[] data) {

	try {

	    this.out.write(data);
	    this.out.flush();

	} catch (IOException e) {

	    exceptionLog_REDXTERM(this.getClass(),e);
	    
	}

    }

    public void send(String data) {

	send(data.getBytes());

    }

    public BufferedInputStream getInStream() {
	return this.in;
    }

    public boolean isClosed() {
	return this.socket.isClosed();
    }

    public void closeConnection() {
	closeConnection(0);
    }

    public void closeConnection(long timeout_ms) {

	TimerTask task = new TimerTask() {

	    @Override
	    public void run() {
		
		try {

		    // stops the inactivity timeout check loop
		    SocketConnection.this.timeoutLoop.close();
		    		    
		    SocketConnection.this.socket.shutdownOutput();
		    SocketConnection.this.socket.shutdownInput();
		    		    
		    
		} catch (IOException e) {

		    exceptionLog_REDXTERM(this.getClass(), e);

		}
		
		this.cancel();

	    }

	};
	
	Timer timer = new Timer();
	timer.schedule(task, timeout_ms);
	
    }

}
