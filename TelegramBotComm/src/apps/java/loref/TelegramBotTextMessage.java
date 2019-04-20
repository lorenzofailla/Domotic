package apps.java.loref;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f.
 *         Created 20 apr 2019.
 */
@SuppressWarnings("javadoc")
public class TelegramBotTextMessage extends TelegramBotMessage {

	private String textContent;
	private InlineKeyboardMarkup keyboard;

	public String getTextContent() {
		return this.textContent;
	}

	public void setTextContent(String textContent) {
		this.textContent = textContent;
	}

	public InlineKeyboardMarkup getKeyboard() {
		return this.keyboard;
	}

	public void setKeyboard(InlineKeyboardMarkup keyboard) {
		this.keyboard = keyboard;
	}
			
}
