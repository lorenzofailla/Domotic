package apps.java.loref;

public class RemoteCommand {

    private String header;
    private String body;
    private String replyto;

    public RemoteCommand() {

    }

    public RemoteCommand(String header, String body, String replyto) {

	this.header = header;
	this.body = body;
	this.replyto = replyto;

    }

    public RemoteCommand(String stringToParse, String replyTo) {
	
	String[] lines = stringToParse.split("[&]");

	if (lines.length != 2) {

	    this.header = "";
	    this.body ="";

	} else {

	    this.replyto = replyTo;
	    
	    for (String l : lines) {

		String[] struct = l.split("[=]");

		if (struct.length != 2) {
		    this.header = "";
		    this.body = "";

		    break;

		} else {

		    switch (struct[0]) {
		    case "header":
			this.header = struct[1];
			break;
		    case "body":
			this.body = struct[1];
			break;

		    }

		}

	    }

	}

    }

    public RemoteCommand(String stringToParse) {

	String[] lines = stringToParse.split("[&]");

	if (lines.length != 3) {

	    this.header = "";
	    this.body = "";
	    this.replyto = "";

	} else {

	    for (String l : lines) {

		String[] struct = l.split("[=]");

		if (struct.length != 2) {
		    this.header = "";
		    this.body = "";
		    this.replyto = "";

		    break;

		} else {

		    switch (struct[0]) {
		    case "header":
			this.header = struct[1];
			break;
		    case "body":
			this.body = struct[1];
			break;
		    case "replyto":
			this.replyto = struct[1];
			break;

		    }

		}

	    }

	}

    }

    public String getHeader() {
	return header;
    }

    public String getBody() {
	return body;
    }

    public String getReplyto() {
	return replyto;
    }

    public String toString() {
	return String.format("?header=%s&body=%s&replyto=%s\n", this.header, this.body, this.replyto);
    }

}
