package apps.java.loref.TelegramBotComm;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import apps.java.loref.TelegramBotComm.TelegramBotMessages.TelegramBotTextAndPictureMessage;
import apps.java.loref.TelegramBotComm.TelegramBotMessages.TelegramBotTextMessage;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f.
 *         Created 17 mar 2019.
 */
@SuppressWarnings("javadoc")
public interface TelegramBotCommListener {
	
	public void onBotRegisterationSuccess();
	public void onRegistrationFailure();
	
	public void onTextMessageReceived(boolean isAuthenticated, long chatID, int userID, String content);
	public void onQueryCallBackReceived(boolean isAuthenticated, CallbackQuery callbackQuery);
	
	public void onMessageSent(String messageKey, String messageType, String messageTag, String messageID);
	
	public void onTextMessageSent(TelegramBotTextMessage message);
	public void onTextAndPictureMessageSent(TelegramBotTextAndPictureMessage message);
	
	public void onTextMessageCompleteUpdateDone(TelegramBotTextMessage message);
	
	public void onTextAndPictureMessageUpdated(TelegramBotTextAndPictureMessage message);
	
}
