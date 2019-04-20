package apps.java.loref;

import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.BotSession;

import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;
import static apps.java.loref.LogUtilities.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 16 mar 2019.
 */
@SuppressWarnings("javadoc")
public class TelegramBotComm {
		
	private class MessagingEngine extends Thread {
		
		private TelegramBotComm parent;
		private long tickTime = Defaults.TICK_TIME;
				
		public void setParent(TelegramBotComm parent) {
			this.parent = parent;
		}

		@Override
		public void run(){
			
			if (this.parent.isDebugMode())
				debugLog_GRAYXTERM(this.getClass(), "Thread started.");
			
			while (this.parent.isMessagingEngineRunning()){
								
				if (!(this.parent.messagesQueue.isEmpty() || this.parent.isPaused())) {
					
					// retrieve the data of the next file to be uploaded
					Iterator<Entry<String, Object>> iterator = this.parent.messagesQueue.entrySet().iterator();

					while (this.parent.isMessagingEngineRunning() && !this.parent.isPaused() && iterator.hasNext()) {
						
						Entry<String, Object> nextItem = iterator.next();
						Object object = nextItem.getValue();
						String key = nextItem.getKey();
						String id = "";
						
						if (this.parent.isDebugMode())
							debugLog_GRAYXTERM(this.getClass(), "Processing message key: "
									+ key + ". Type= " + object.getClass().getName()) ;
						
						if (object.getClass().equals(TelegramBotTextMessage.class)){
													
							TelegramBotTextMessage message = (TelegramBotTextMessage) object;
							id = this.parent.bot.sendMessage(message.getRecipientID(), message.getTextContent(), message.getKeyboard());
							
						} else if (object.getClass().equals(TelegramBotTextAndPictureMessage.class)){
							
							TelegramBotTextAndPictureMessage message = (TelegramBotTextAndPictureMessage) object;
							id = this.parent.bot.sendImageBytes(message.getRecipientID(), message.getTextContent(), message.getImageContent(), message.getKeyboard());
														
						}
						
						if (this.parent.getListener()!=null)
							this.parent.getListener().onMessageSent(key, object.getClass().getName(), id);

						iterator.remove();
						this.parent.messagesQueue.remove(object);
						
					}
										
				} else {

					if (this.parent.isDebugMode())
						debugLog_GRAYXTERM(this.getClass(), "Thread sleeping. Items in queue="
								+ this.parent.messagesQueue.size() + "; paused=" + this.parent.paused);

					sleepSafe(this.tickTime);

				}
				
			}
			
			if (this.parent.isDebugMode())
				debugLog_GRAYXTERM(this.getClass(), "Thread terminated. Items in queue="
						+ this.parent.messagesQueue.size() + "; paused=" + this.parent.paused);
			
		}
		
	}
	
	private MessagingEngine messagingEngine;
	private boolean messagingEngineRunning;
	private boolean paused;
	
	
		
	public boolean isPaused() {
		return this.paused;
	}

	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	public boolean isMessagingEngineRunning() {
		return this.messagingEngineRunning;
	}

	private HashMap<String, Object> messagesQueue = new HashMap<String, Object>();
	
	public int getQueueLength(){
		return this.messagesQueue.size();
	}

	private String userName;
	private String token;

	private List<String> authorizedUsers = new ArrayList<String>();
	private List<String> recipientsList = new ArrayList<String>();

	public List<String> getAuthorizedUsers() {
		return this.authorizedUsers;
	}

	public List<String> getRecipientsList() {
		return this.recipientsList;
	}

	private Bot bot;
	
	private BotSession botSession;
	
	private TelegramBotCommListener listener;

	public void attachListener(TelegramBotCommListener value) {
		this.listener = value;
	}
	
	public void detachListener() {
		this.listener = null;
	}

	public TelegramBotCommListener getListener() {
		return this.listener;
	}

	public boolean hasListener() {
		return (this.listener != null);
	}

	public TelegramBotComm(String botUserName, String botToken) {

		this.userName = botUserName;
		this.token = botToken;

	}
	
	private boolean debugMode=false;
	
	public boolean isDebugMode() {
		return this.debugMode;
	}

	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;
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

			if (this.listener != null)
				this.listener.onBotRegisterationSuccess();
			
			this.messagingEngineRunning = true;
			
			this.messagingEngine = new MessagingEngine();
			this.messagingEngine.setParent(this);
			this.messagingEngine.start();
						

		} catch (TelegramApiRequestException e) {

			exceptionLog_REDXTERM(this.getClass(), e);

			if (this.listener != null)
				this.listener.onRegistrationFailure();

		}

	}

	public void dispatchMessageToAll(String message) {

		for (String recipient : this.recipientsList) {

			this.bot.sendMessage(recipient, message);

		}

	}

	public void dispatchMessageToAll(String message, InlineKeyboardMarkup keyboard) {

		for (String recipient : this.recipientsList) {

			this.bot.sendMessage(recipient, message, keyboard);

		}

	}
	
	public void dispatchImageBytesToAll(String description, byte[] imageData) {

		for (String recipient : this.recipientsList) {

			this.bot.sendImageBytes(recipient, description, imageData);

		}

	}

	public static void _createKeyboard(List<List<InlineKeyboardButton>> keys) {

		InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
		markupInline.setKeyboard(keys);

	}
	
	public boolean isEnabled(String userID){
		
		return this.authorizedUsers.contains(userID);
		
	}
	
	public void terminateSession(){
		
		pauseSession();
		this.messagingEngineRunning=false;
				
	}
	
	public void resumeSession(){
		
		this.botSession.start();
						
	}
	
	public void pauseSession(){
		
		this.botSession.stop();
				
	}
	
	public void send(TelegramBotTextMessage message){
		
		this.messagesQueue.put(message.getRecipientID() + "_" + System.currentTimeMillis(), message);
		
	}
	
	public void send(TelegramBotTextAndPictureMessage message){
		
		this.messagesQueue.put(message.getRecipientID() + "_" + System.currentTimeMillis(), message);
		
	}

}
