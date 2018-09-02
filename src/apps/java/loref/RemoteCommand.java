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

	public String getHeader() {
		return header;
	}

	public String getBody() {
		return body;
	}

	public String getReplyto() {
		return replyto;
	}

}
