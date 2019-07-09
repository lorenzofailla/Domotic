/**
 * Copyright 2018 Lorenzo Failla
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package apps.java.loref.Domotic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHShell {

    // constants
    private static final int DEFAULT_TERMINAL_COLS = 108;
    private static final int DEFAULT_TERMINAL_ROWS = 36;
    private static final int DEFAULT_TERMINAL_BUFS = 8000;
    private static final long DEFAULT_UPDATE_TIMEOUT = 500L;
    private static final long DEFAULT_INACIVITY_TIMEOUT = 600000L;

    private static final byte[] UP = { (byte) 0x1b, (byte) 0x4f, (byte) 0x41 };
    private static final byte[] DOWN = { (byte) 0x1b, (byte) 0x4f, (byte) 0x42 };
    private static final byte[] RIGHT = { (byte) 0x1b, (byte) /* 0x5b */0x4f, (byte) 0x43 };
    private static final byte[] LEFT = { (byte) 0x1b, (byte) /* 0x5b */0x4f, (byte) 0x44 };
    private static final byte[] DEL = { (byte) 0x7f };

    // connection data
    private String user;
    private String password;
    private String host;
    private int port;

    // private Connection connection;

    private Session session;
    private Channel channel;

    // private TerminalScreen terminal;
    // private TerminalScreenImage terminalImg;

    private InputStream in;
    private OutputStream out;
    private byte[] lastOutput;

    private SSHShellListener interfaceListener;

    // terminal parameters
    private int terminalCols;
    private int terminalRows;
    private int terminalBufS;

    private long updateTimeout;

    private long inactivityTimeout;

    private Timer inactivityTimer;

    private class InactivityDisconnection extends TimerTask {

	@Override
	public void run() {

	    // calls disconnect() method
	    disconnect();

	}

    }

    // getters + setters
    public long getInactivityTimeout() {
	return inactivityTimeout;
    }

    public void setInactivityTimeout(long inactivityTimeout) {
	this.inactivityTimeout = inactivityTimeout;
    }

    public long getUpdateTimeout() {
	return updateTimeout;
    }

    public void setUpdateTimeout(long updateTimeout) {
	this.updateTimeout = updateTimeout;
    }

    public int getTerminalCols() {
	return terminalCols;
    }

    public void setTerminalCols(int terminalCols) {
	this.terminalCols = terminalCols;
    }

    public int getTerminalRows() {
	return terminalRows;
    }

    public void setTerminalRows(int terminalRows) {
	this.terminalRows = terminalRows;
    }

    public int getTerminalBufS() {
	return terminalBufS;
    }

    public void setTerminalBufS(int terminalBufS) {
	this.terminalBufS = terminalBufS;
    }

    // constructor
    public SSHShell(String hostName, String userName, String accessPw, int hostPort) {

	host = hostName;
	user = userName;
	password = accessPw;
	port = hostPort;

	updateTimeout = DEFAULT_UPDATE_TIMEOUT;

	inactivityTimeout = DEFAULT_INACIVITY_TIMEOUT;

	terminalCols = DEFAULT_TERMINAL_COLS;
	terminalRows = DEFAULT_TERMINAL_ROWS;
	terminalBufS = DEFAULT_TERMINAL_BUFS;

    }

    public void initialize() {

	try {

	    session = new JSch().getSession(user, host, port);
	    session.setPassword(password);
	    session.setConfig("StrictHostKeyChecking", "no");

	    if (interfaceListener != null) {
		interfaceListener.onCreated();
	    }

	} catch (JSchException e) {

	    if (interfaceListener != null)
		interfaceListener.onError(e);

	}

    }

    public interface SSHShellListener {

	// void onOutputChanged(String formattedOutput);

	// void onOutputImageChanged(String imageEncodedData);

	void onOutputDataChanged(byte[] data);

	void onConnected();

	void onDisconnected();

	void onCreated();

	void onError(Exception e);

    }

    private Thread mainLoop = new Thread() {

	public void run() {

	    long lastUpdate = System.currentTimeMillis(); // ultimo
							  // aggiornamento
	    boolean sendUpdate = false;

	    while (channel.isConnected()) {

		byte[] newData = new byte[0];

		try {

		    int dataSize = in.available();
		    if (dataSize > 0) {
			newData = new byte[dataSize];
			in.read(newData, 0, dataSize);
			if (interfaceListener != null) {

			    System.out.println(newData.length);
			    interfaceListener.onOutputDataChanged(newData);

			}
		    }

		} catch (IOException e) {
		    // TODO Auto-generated catch block
		    e.printStackTrace();
		}

		/*
		// attende che il tempo DEFAULT_UPDATE_TIMEOUT sia trascorso e
		// che l'output sia cambiato
		
		
		if (((System.currentTimeMillis() - lastUpdate) > DEFAULT_UPDATE_TIMEOUT) && sendUpdate) {
		
		    if (interfaceListener != null) {
			// interfaceListener.onOutputChanged(terminal.getScreen());
			// interfaceListener.onOutputImageChanged(terminalImg.getImageDataEncoded());
			
			interfaceListener.onOutputDataChanged(data.toByteArray());
		
		    }
		
		    // aggiorna
		    lastUpdate = System.currentTimeMillis(); // ultimo
		
		} else {
		    try {
			Thread.sleep(DEFAULT_UPDATE_TIMEOUT);
		    } catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		    }
		}
		*/

	    }

	    session.disconnect();

	    while (session.isConnected()) {

		try {

		    Thread.sleep(1000);

		} catch (InterruptedException e) {
		    if (interfaceListener != null)
			interfaceListener.onError(e);
		}

	    }

	    if (interfaceListener != null)
		interfaceListener.onDisconnected();

	}

    };

    public void setListener(SSHShellListener listener) {

	interfaceListener = listener;

    }

    public void connect() {

	try {

	    // connetto la sessione
	    session.connect();

	    // apro il canale della shell
	    channel = session.openChannel("shell");

	    out = channel.getOutputStream();
	    in = channel.getInputStream();

	    /*
	    final InputStream fin = in;
	    final OutputStream fout = out;
	    final Channel fchannel = channel;
	    */

	    // imposto una nuova istanza dell'oggetto Connection
	    /*
	    connection = new Connection() {
	    
	    @Override
	    public InputStream getInputStream() {
	        return fin;
	    }
	    
	    @Override
	    public OutputStream getOutputStream() {
	        return fout;
	    }
	    
	    @Override
	    public void requestResize(Term term) {
	    
	        if (fchannel instanceof ChannelShell) {
	    	System.out.println(String.format("col=%d, row=%d, wp=%d, hp=%d", term.getColumnCount(),
	    		term.getRowCount(), term.getColumnCount() * term.getCharWidth(),
	    		term.getRowCount() * term.getCharHeight()));
	    
	    	((ChannelShell) fchannel).setPtyType("vt100", term.getColumnCount(), term.getRowCount(),
	    		term.getColumnCount() * term.getCharWidth(), term.getRowCount() * term.getCharHeight());
	        }
	    
	    }
	    
	    @Override
	    public void close() {
	    
	        channel.disconnect();
	    
	    }
	    
	    };
	    */

	    channel.connect();

	    /* terminal = new TerminalScreen(terminalCols, terminalRows,
	     * terminalBufS); terminalImg = new
	     * TerminalScreenImage(terminalCols, terminalRows);
	     * 
	     * terminal.start(connection); terminalImg.start(connection); */

	    // initialize the inactivity timer and schedule disconnection in
	    // <inactivityTimeout> milliseconds

	    inactivityTimer = new Timer();
	    inactivityTimer.schedule(new InactivityDisconnection(), inactivityTimeout);

	    mainLoop.start();

	    if (interfaceListener != null)
		interfaceListener.onConnected();

	} catch (JSchException e) {

	    if (interfaceListener != null)
		interfaceListener.onError(e);

	} catch (IOException e) {

	    if (interfaceListener != null)
		interfaceListener.onError(e);
	}

    }

    public void disconnect() {

	// connection.close();
	channel.disconnect();
    }

    public void insertInput(byte[] input) {

	try {

	    // connection.getOutputStream().write(input);
	    // connection.getOutputStream().flush();

	    out.write(input);
	    out.flush();

	} catch (IOException e) {

	    if (interfaceListener != null)
		interfaceListener.onError(e);

	}

	// reset the inactivity timer and schedule disconnection in
	// <inactivityTimeout> milliseconds
	inactivityTimer.cancel();
	inactivityTimer = new Timer();
	inactivityTimer.schedule(new InactivityDisconnection(), inactivityTimeout);

    }

    public void keyBackspace() {
	insertInput(LEFT);
	insertInput(DEL);

    }

    public void keyDelete() {
	insertInput(DEL);
    }

    public void keyUp() {
	insertInput(UP);
    };

    public void keyDown() {
	insertInput(DOWN);
    };

    public void keyRight() {
	insertInput(RIGHT);
    };

    public void keyLeft() {
	insertInput(LEFT);
    }

}
