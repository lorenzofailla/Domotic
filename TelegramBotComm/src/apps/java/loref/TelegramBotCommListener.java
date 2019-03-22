package apps.java.loref;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f.
 *         Created 17 mar 2019.
 */
public interface TelegramBotCommListener {
	
	public void onBotRegisterationSuccess();
	public void onRegistrationFailure();
	
	public void onMessageReceived(String chatID, String content);
	
}
