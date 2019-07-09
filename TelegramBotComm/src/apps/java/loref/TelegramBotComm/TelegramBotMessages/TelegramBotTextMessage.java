package apps.java.loref.TelegramBotComm.TelegramBotMessages;

import org.json.JSONObject;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import apps.java.loref.TelegramBotComm.Bot;

import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;
import static apps.java.loref.TelegramBotComm.Keyboards.Keyboards.createKeyboard;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 20 apr 2019.
 */
@SuppressWarnings("javadoc")
public class TelegramBotTextMessage extends TelegramBotMessage implements Cloneable{
		
	public TelegramBotTextMessage() {
		super();
		
	}
	
	public TelegramBotTextMessage(Message message) {
		super(message.getChatId().toString());
		this.textContent = message.getText();
		setMessageCompoundID(message.getChatId().toString()+"_"+message.getMessageId().toString());
		
	}
	
	public TelegramBotTextMessage(String recipient, String textContent) {
		super(recipient);
		this.textContent = textContent;
		
	}
	
	public TelegramBotTextMessage(String recipient, String textContent, InlineKeyboardMarkup keyboard) {
		super(recipient);
		this.textContent = textContent;
		this.keyboard = keyboard;
	}

	public TelegramBotTextMessage(String recipient, String tag, String textContent, InlineKeyboardMarkup keyboard) {

		super(recipient);
		setTag(tag);
		this.textContent = textContent;
		this.keyboard = keyboard;
	}
	
	public TelegramBotTextMessage getClone(){
		
		try {
			return (TelegramBotTextMessage) this.clone();
		} catch (CloneNotSupportedException e) {

			exceptionLog_REDXTERM(TelegramBotTextMessage.class, e);
			return null;
		}
		
	}

	private String textContent;
	private InlineKeyboardMarkup keyboard;
	private long keyboardTimeOut = -1;
	private long lifeTimeOut = -1;

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
	
	public void setKeyboard(String keyCode) {
		this.keyboard = createKeyboard(keyCode);
	}
	
	public void setKeyboard(JSONObject keyCodeJSON) {
		this.keyboard = createKeyboard(keyCodeJSON);
	}

	public long getKeyboardTimeOut() {
		return this.keyboardTimeOut;
	}

	public void setKeyboardTimeOut(long keyboardTimeOut) {
		this.keyboardTimeOut = keyboardTimeOut;
	}

	public long getLifeTimeOut() {
		return this.lifeTimeOut;
	}

	public void setLifeTimeOut(long lifeTimeOut) {
		this.lifeTimeOut = lifeTimeOut;
	}
	
}
