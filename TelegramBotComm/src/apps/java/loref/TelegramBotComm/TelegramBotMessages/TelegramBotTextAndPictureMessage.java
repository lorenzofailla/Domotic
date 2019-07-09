package apps.java.loref.TelegramBotComm.TelegramBotMessages;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f.
 *         Created 20 apr 2019.
 */
@SuppressWarnings("javadoc")
public class TelegramBotTextAndPictureMessage extends TelegramBotTextMessage {
	
	/**
	 * TODO Put here a description of what this constructor does.
	 *
	 * @param recipient
	 * @param textContent
	 */
	public TelegramBotTextAndPictureMessage(String recipient, String textContent) {
		super(recipient, textContent);
		// TODO Auto-generated constructor stub.
	}

	private byte[] imageContent;

	public byte[] getImageContent() {
		return this.imageContent;
	}

	public void setImageContent(byte[] imageContent) {
		this.imageContent = imageContent;
	}
	
}
