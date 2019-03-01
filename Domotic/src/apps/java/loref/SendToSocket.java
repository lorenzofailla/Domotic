package apps.java.loref;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class SendToSocket {

    public static void printUsage() {
	System.out.println("Usage: SendToSocket hostname port data");
    }

    public static void main(String[] args) {

	if (args.length < 3) {
	    printUsage();
	    return;
	}

	int port;
	try {
	    port = Integer.parseInt(args[1]);
	} catch (NumberFormatException e) {
	    System.out.println(String.format("Could not read port number. Passed argument: %s", args[1]));
	    return;
	}

	String host = args[0];

	StringBuilder dataBuilder = new StringBuilder();

	for (int i = 2; i < args.length; i++) {
	    dataBuilder.append(args[i]);
	    
	    if(i<args.length-1)
		dataBuilder.append(" ");
	    
	}
	String data = dataBuilder.toString();
	
	System.out.println(String.format("host:%s\nport:%d\ndata:%s", host, port, data));

	Socket socket;
	try {
	    
	    System.out.println("Creating socket...");
	    
	    socket = new Socket(host, port);
	    System.out.println("Socket created.");
	    
	    socket.getOutputStream().write(data.getBytes());
	    socket.getOutputStream().flush();
	    System.out.println("Data sent.");
	    
	    socket.close();
	    System.out.println("Socket closed.");
	    
	} catch (IOException e) {
	    System.out.println(String.format("Error: %s", e.getMessage()));
	}

    }

}
