package apps.java.loref;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f.
 *         Created 20 apr 2019.
 */
@SuppressWarnings("javadoc")
public class TelegramBotMessage {
	
	private String recipientID;
	private SendMode mode = SendMode.NEW;

	public String getRecipientID() {
		return this.recipientID;
	}

	public void setRecipientID(String recipientID) {
		this.recipientID = recipientID;
	}

	public SendMode getMode() {
		return this.mode;
	}

	public void setMode(SendMode mode) {
		this.mode = mode;
	}
		
}
