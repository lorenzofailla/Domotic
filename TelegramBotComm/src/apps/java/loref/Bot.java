package apps.java.loref;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.WebhookInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;

import static apps.java.loref.LogUtilities.*;

import java.io.ByteArrayInputStream;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 16 mar 2019.
 */
public class Bot extends TelegramLongPollingBot {

	private String botUsername;
	private String botToken;
	private TelegramBotComm parent;

	public Bot setParent(TelegramBotComm value) {
		this.parent = value;
		return this;
	}

	private WebhookInfo webhookInfo = new WebhookInfo();

	public Bot setBotUsername(String botUsername) {
		this.botUsername = botUsername;
		return this;
	}

	public Bot setBotToken(String botToken) {
		this.botToken = botToken;
		return this;
	}

	public void onUpdateReceived(Update update) {

		if (update.hasMessage()) {

			Message message = update.getMessage();

			if (message.hasText()) {

				String text = message.getText();
				String chatID = message.getChatId().toString();

				if (this.parent.hasListener())
					this.parent.getListener().onMessageReceived(chatID, text);

			}
		}

	}

	public String getBotUsername() {

		return this.botUsername;
	}

	@Override
	public String getBotToken() {

		return this.botToken;
	}

	public void sendMessage(String recipientID, String messageContent) {

		SendMessage sendMessage = new SendMessage().setChatId(recipientID).setText(messageContent);

		try {

			execute(sendMessage);

		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}

	}

	public void sendImageBytes(String recipientID, String description, byte[] imageContent) {

		ByteArrayInputStream inputStream = new ByteArrayInputStream(imageContent);

		SendPhoto sendPhoto = new SendPhoto().setChatId(recipientID).setPhoto(description, inputStream);

		try {

			execute(sendPhoto);

		} catch (TelegramApiException e) {

			exceptionLog_REDXTERM(Bot.class, e);

		}

	}

}
