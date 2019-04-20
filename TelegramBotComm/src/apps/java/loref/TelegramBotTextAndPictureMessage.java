package apps.java.loref;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f.
 *         Created 20 apr 2019.
 */
@SuppressWarnings("javadoc")
public class TelegramBotTextAndPictureMessage extends TelegramBotTextMessage {
	
	private byte[] imageContent;

	public byte[] getImageContent() {
		return this.imageContent;
	}

	public void setImageContent(byte[] imageContent) {
		this.imageContent = imageContent;
	}
	
}
