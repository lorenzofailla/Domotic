package apps.java.loref;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import static apps.java.loref.LogUtilities.*;

import java.io.ByteArrayInputStream;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 16 mar 2019.
 */
@SuppressWarnings("javadoc")
public class Bot extends TelegramLongPollingBot {

	private String botUsername;
	private String botToken;
	private TelegramBotComm parent;

	public Bot setParent(TelegramBotComm value) {
		this.parent = value;
		return this;
	}

	public Bot setBotUsername(String botUsername) {
		this.botUsername = botUsername;
		return this;
	}

	public Bot setBotToken(String botToken) {
		this.botToken = botToken;
		return this;
	}

	@Override
	public void onUpdateReceived(Update update) {
		
		if (update.hasMessage()) {

			Message message = update.getMessage();

			if (message.hasText()) {

				String text = message.getText();
				long chatID = message.getChatId();
				int userID = message.getFrom().getId();

				boolean isAuthenticated = this.parent.isEnabled("" + userID);

				if (this.parent.hasListener())
					this.parent.getListener().onMessageReceived(isAuthenticated, chatID, userID, text);

			}

		} else if (update.hasCallbackQuery()) {

			CallbackQuery callbackQuery = update.getCallbackQuery();

			String data = callbackQuery.getData();
			long chatID = callbackQuery.getMessage().getChatId();
			int messageID = callbackQuery.getMessage().getMessageId();
			int userID = callbackQuery.getMessage().getFrom().getId();
			boolean isAuthenticated = this.parent.isEnabled("" + userID);

			if (this.parent.hasListener())
				this.parent.getListener().onQueryCallBackReceived(isAuthenticated, chatID, messageID, userID, data);

		} else if (update.hasEditedMessage()) {
			
			Message message = update.getEditedMessage();
			System.out.println("Messaggio " + message.getMessageId() + " modificato.");
			
		}

	}

	@Override
	public String getBotUsername() {

		return this.botUsername;
		
	}

	@Override
	public String getBotToken() {

		return this.botToken;
	}

	public String sendMessage(String recipientID, String messageContent) {

		return sendMessage(recipientID, messageContent, null);

	}

	public String sendMessage(String recipientID, String messageContent, InlineKeyboardMarkup keyboard) {

		String result = "";
		int messageID = -1;
		SendMessage sendMessage = new SendMessage().setChatId(recipientID).setText(messageContent);

		if (keyboard != null)

			sendMessage.setReplyMarkup(keyboard);

		try {

			messageID = execute(sendMessage).getMessageId();
			if (messageID != -1)
				result = recipientID + "_" + messageID;

		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}

		return result;

	}

	public void updateMessage(int messageID, long chatID, String messageContent) {

		updateMessage(messageID, chatID, messageContent, null);

	}

	public void updateMessage(int messageID, long chatID, String messageContent, InlineKeyboardMarkup keyboard) {
		
		EditMessageText editMessageText = new EditMessageText().setChatId(chatID).setMessageId(messageID).setText(messageContent);
				
		if (keyboard != null)
			editMessageText.setReplyMarkup(keyboard);

		try {
			
			execute(editMessageText);

		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}

	}
	
	public String sendImageBytes(String recipientID, String description, byte[] imageContent) {

		return sendImageBytes(recipientID, description, imageContent, null);

	}

	public String sendImageBytes(String recipientID, String description, byte[] imageContent, InlineKeyboardMarkup keyboard) {
		
		String result = "";
		int messageID = -1;
		
		ByteArrayInputStream inputStream = new ByteArrayInputStream(imageContent);

		SendPhoto sendPhoto = new SendPhoto().setChatId(recipientID).setPhoto(description, inputStream);
		if (keyboard != null)
			sendPhoto.setReplyMarkup(keyboard);
				
		try {

			messageID = execute(sendPhoto).getMessageId();
			if (messageID != -1)
				result = recipientID + "_" + messageID;
			
		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}
		
		return result;

	}

}
