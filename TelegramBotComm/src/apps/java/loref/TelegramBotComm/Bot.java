package apps.java.loref.TelegramBotComm;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import static apps.java.loref.LogUtilities.*;

import java.io.ByteArrayInputStream;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nullable;

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

				boolean isAuthenticated = this.parent.isValidUser("" + userID);

				if (this.parent.hasListener())
					this.parent.getListener().onTextMessageReceived(isAuthenticated, chatID, userID, text);

			}

		} else if (update.hasCallbackQuery()) {

			CallbackQuery callbackQuery = update.getCallbackQuery();

			boolean isAuthenticated = this.parent.isValidUser(callbackQuery.getMessage().getFrom().getId().toString());

			if (this.parent.hasListener())
				this.parent.getListener().onQueryCallBackReceived(isAuthenticated, callbackQuery);

		} else if (update.hasEditedMessage()) {

			Message message = update.getEditedMessage();

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

		return sendMessage(recipientID, messageContent, null, -1, -1);

	}

	public String sendMessage(String recipientID, String messageContent, InlineKeyboardMarkup keyboard,
			long keyboardExpirationTime, long deleteAfter) {

		String result = "";
		int messageID = -1;
		SendMessage sendMessage = new SendMessage().setChatId(recipientID).setText(messageContent)
				.setParseMode(ParseMode.HTML);

		if (keyboard != null) {
			sendMessage.setReplyMarkup(keyboard);

		}

		try {

			Message message = execute(sendMessage);
			messageID = message.getMessageId();

			if (messageID != -1) {
				result = recipientID + "_" + messageID;

				if (keyboardExpirationTime > 0) {

					new Timer().schedule(new RemoveKeyboardTask(message), keyboardExpirationTime);

				}

				if (deleteAfter > 0) {

					new Timer().schedule(new DeleteMessageTask(message), deleteAfter);

				}

			}

		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}

		return result;

	}

	public void updateMessage(int messageID, long chatID, String messageContent) {

		updateMessage(messageID, chatID, messageContent, null);

	}

	public void updateMessage(int messageID, long chatID, String messageContent, InlineKeyboardMarkup keyboard) {
		
		EditMessageText editMessageText = new EditMessageText().setChatId(chatID).setMessageId(messageID)
				.setText(messageContent).setParseMode(ParseMode.HTML);

		if (keyboard != null)
			editMessageText.setReplyMarkup(keyboard);

		try {

			execute(editMessageText);

		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}

	}

	public void deleteMessage(int messageID, long chatID) {

		DeleteMessage deleteMessage = new DeleteMessage(chatID, messageID);

		try {
			execute(deleteMessage);
		} catch (TelegramApiException e) {
			exceptionLog_REDXTERM(Bot.class, e);
		}

	}

	public String sendImageBytes(String recipientID, String description, byte[] imageContent) {

		return sendImageBytes(recipientID, description, imageContent, null);

	}

	public String sendImageBytes(String recipientID, String description, byte[] imageContent,
			InlineKeyboardMarkup keyboard) {

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

	public void updateCaption(String chatID, String messageID, String newCaption) {

		EditMessageCaption editMessageCaption = new EditMessageCaption().setChatId(chatID)
				.setMessageId(Integer.parseInt(messageID)).setCaption(newCaption).setParseMode(ParseMode.HTML);

		try {

			execute(editMessageCaption);

		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}

	}
	
	public void updateMessageReplyMarkup(String chatID, String messageID, InlineKeyboardMarkup keyboard) {

		EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup().setChatId(chatID).setMessageId(Integer.parseInt(messageID)).setReplyMarkup(keyboard);
		
		try {

			execute(editMessageReplyMarkup);

		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}

	}
	
	public void removeMessageReplyMarkup(long chatID, int messageID) {

		EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup().setChatId(chatID).setMessageId(messageID).setReplyMarkup(null);
		
		try {

			execute(editMessageReplyMarkup);

		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}

	}

	private class RemoveKeyboardTask extends TimerTask {

		private Message message;

		public RemoveKeyboardTask(Message message) {

			this.message = message;

		}

		@Override
		public void run() {

			removeMessageReplyMarkup(this.message.getChatId(), this.message.getMessageId());

		}

	}

	private class DeleteMessageTask extends TimerTask {

		private Message message;

		public DeleteMessageTask(Message message) {

			this.message = message;

		}

		@Override
		public void run() {

			deleteMessage(this.message.getMessageId(), this.message.getChatId());

		}

	}

}
