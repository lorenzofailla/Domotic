package apps.java.loref;

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
	
	public void onMessageReceived(boolean isAuthenticated, long chatID, int userID, String content);
	public void onQueryCallBackReceived(boolean isAuthenticated, long chatID, int messageID, int userID, String callBackData);
	
	public void onMessageSent(String messageKey, String messageType, String messageID);
	
}
