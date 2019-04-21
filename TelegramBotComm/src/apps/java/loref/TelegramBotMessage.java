package apps.java.loref;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 20 apr 2019.
 */
@SuppressWarnings("javadoc")
public class TelegramBotMessage {

	private String key;
	
	public String getKey() {
		return this.key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	private String recipientID;

	public String getRecipientID() {
		return this.recipientID;
	}

	public void setRecipientID(String recipientID) {
		this.recipientID = recipientID;
	}

	private SendMode mode = SendMode.NEW;

	public SendMode getMode() {
		return this.mode;
	}

	public void setMode(SendMode mode) {
		this.mode = mode;
	}

}
