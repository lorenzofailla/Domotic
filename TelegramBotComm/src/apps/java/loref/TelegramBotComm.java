package apps.java.loref;

import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.BotSession;

import static apps.java.loref.LogUtilities.*;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 16 mar 2019.
 */
@SuppressWarnings("javadoc")
public class TelegramBotComm {

	private String userName;
	private String token;
	
	private BotSession botSession;
	
	private List<String> authorizedUsers = new ArrayList<String>();
	private List<String> recipientsList = new ArrayList<String>();
	
	public List<String> getAuthorizedUsers() {
		return this.authorizedUsers;
	}

	public List<String> getRecipientsList() {
		return this.recipientsList;
	}
	
	private Bot bot;
	
	public Bot getBot(){
		return this.bot;
	}
		
	private TelegramBotCommListener listener;
	
	public void setListener(TelegramBotCommListener value){
		this.listener=value;
	}
	
	public TelegramBotCommListener getListener(){
		return this.listener;
	}
	
	public boolean hasListener(){
		return (this.listener!=null);
	}

	public TelegramBotComm(String botUserName, String botToken) {

		this.userName = botUserName;
		this.token = botToken;

	}

	public void start() {
		
		// Initialize Api Context
		ApiContextInitializer.init();

		// Instantiate Telegram Bots API
		TelegramBotsApi botsApi = new TelegramBotsApi();
		this.bot = new Bot().setBotUsername(this.userName).setBotToken(this.token).setParent(this);
		
		// Register the bot
		try {

			this.botSession = botsApi.registerBot(this.bot);

			if(this.listener!=null)
				this.listener.onBotRegisterationSuccess();

		} catch (TelegramApiRequestException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
			
			if(this.listener!=null)
				this.listener.onRegistrationFailure();

		}

	}
	
	public void dispatchMessageToAll(String message){
		
		for (String recipient:this.recipientsList){
			
			this.bot.sendMessage(recipient, message);
						
		}
				
	}
		
}
