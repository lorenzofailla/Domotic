package apps.java.loref.TelegramBotComm;

import org.json.JSONObject;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.BotSession;

import apps.java.loref.TelegramBotComm.Defaults;
import apps.java.loref.TelegramBotComm.TelegramBotMessages.TelegramBotTextAndPictureMessage;
import apps.java.loref.TelegramBotComm.TelegramBotMessages.TelegramBotTextMessage;

import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;
import static apps.java.loref.LogUtilities.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

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
		public void run() {

			if (this.parent.isDebugMode())
				debugLog_GRAYXTERM(this.getClass(), "Thread started.");

			while (this.parent.isMessagingEngineRunning()) {

				if (!(this.parent.messagesQueue.isEmpty() || this.parent.isPaused())) {

					// retrieve the data of the next file to be uploaded
					Iterator<Entry<String, Object>> iterator = this.parent.messagesQueue.entrySet().iterator();

					while (this.parent.isMessagingEngineRunning() && !this.parent.isPaused() && iterator.hasNext()) {

						Entry<String, Object> nextItem = iterator.next();
						Object object = nextItem.getValue();
						String key = nextItem.getKey();
						String id = "";
						String tag = "";

						if (this.parent.isDebugMode())
							debugLog_GRAYXTERM(this.getClass(),
									"Processing message key: " + key + ". Type= " + object.getClass().getName());

						if (object.getClass().equals(TelegramBotTextMessage.class)) { 

							TelegramBotTextMessage message = (TelegramBotTextMessage) object;
							
							if (message.getMode().equals(SendMode.NEW)) { // new message dispatch is required
								
								id = this.parent.bot.sendMessage(message.getRecipientID(), message.getTextContent(),
										message.getKeyboard(), message.getKeyboardTimeOut(), message.getLifeTimeOut());
								tag = message.getTag();
								
								message.setMessageCompoundID(id);

								// notify the listener
								if (this.parent.getListener() != null)
									this.parent.getListener().onTextMessageSent(message);
								
							} else if (message.getMode().equals(SendMode.UPDATE)) { // complete message update is required
																								
								this.parent.bot.updateMessage(Integer.parseInt(message.getMessageID()), Long.parseLong(message.getRecipientID()), message.getTextContent(), message.getKeyboard());

								// notify the listener
								if (this.parent.getListener() != null)
									this.parent.getListener().onTextMessageCompleteUpdateDone(message);
								
							} 

						} else if (object.getClass().equals(TelegramBotTextAndPictureMessage.class)) {

							TelegramBotTextAndPictureMessage message = (TelegramBotTextAndPictureMessage) object;
							id = this.parent.bot.sendImageBytes(message.getRecipientID(), message.getTextContent(),
									message.getImageContent(), message.getKeyboard());
							tag = message.getTag();

							// notify the listener
							if (this.parent.getListener() != null)
								this.parent.getListener().onTextAndPictureMessageSent(message);

						}

						if (this.parent.getListener() != null)
							this.parent.getListener().onMessageSent(key, object.getClass().getName(), tag, id);

						iterator.remove();

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

	static long uniqueID = 0L;

	public static long getUniqueID() {
		return (uniqueID++);
	}

	public boolean isPaused() {
		return this.paused;
	}

	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	public boolean isMessagingEngineRunning() {
		return this.messagingEngineRunning;
	}

	private ConcurrentHashMap<String, Object> messagesQueue = new ConcurrentHashMap<String, Object>();

	public int getQueueLength() {
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

	private JSONObject jsonKeyboards;

	public void setKeyboardsData(String keyboardsData) {

		this.jsonKeyboards = new JSONObject(keyboardsData);

	}

	public String getKeyboardJSON(String ID) {

		if (this.jsonKeyboards.has(ID)) {

			return this.jsonKeyboards.getString(ID).toString();

		} else {

			return "";

		}

	}

	private Bot bot;
	
	protected Bot getBot(){
		return this.bot;
	}

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

	private boolean debugMode = false;

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

	public boolean isValidUser(String userID) {

		return this.authorizedUsers.contains(userID);

	}

	public void terminateSession(boolean waitForQueue) {

		if (waitForQueue) {

			while (getQueueLength() > 0) {

				if (this.debugMode)
					debugLog_GRAYXTERM(this.getClass(),
							"Waiting queue to terminate session. Items in queue=" + this.messagesQueue.size() + ".");

				sleepSafe(Defaults.TICK_TIME);

			}

		}

		pauseSession();
		this.messagingEngineRunning = false;

	}

	public void resumeSession() {

		this.botSession.start();

	}

	public void pauseSession() {

		this.botSession.stop();

	}

	public void send(TelegramBotTextMessage message) {

		if (this.debugMode)
			debugLog_GRAYXTERM(this.getClass(), "Message \"" + message.getKey() + "\" added to queue.");

		this.messagesQueue.put(message.getKey(), message);

	}

	public void send(TelegramBotTextAndPictureMessage message) {

		this.messagesQueue.put(message.getRecipientID() + "_" + System.currentTimeMillis(), message);

	}

}
