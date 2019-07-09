package apps.java.loref.TelegramBotComm.TelegramBotMessages;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f.
 *         Created 11 mag 2019.
 */
public class TelegramBotTextAndHyperlinkMessage extends TelegramBotTextMessage {
	
	private String hyperlink;

	protected String getHyperlink() {
		return this.hyperlink;
	}

	protected void setHyperlink(String hyperlink) {
		this.hyperlink = hyperlink;
	}
	
}
