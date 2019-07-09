/**
 * Copyright 2018-2019 Lorenzo Failla
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package apps.java.loref.Domotic;

import static apps.java.loref.Domotic.Defaults.*;
import static apps.java.loref.FCMUtilities.sendFCM;
import static apps.java.loref.GeneralUtilitiesLibrary.compress;
import static apps.java.loref.GeneralUtilitiesLibrary.execShellCommand;
import static apps.java.loref.GeneralUtilitiesLibrary.parseJSON;
import static apps.java.loref.GeneralUtilitiesLibrary.parseShellCommand;

import static apps.java.loref.GeneralUtilitiesLibrary.readPlainTextFromFile;
import static apps.java.loref.GeneralUtilitiesLibrary.readLinesFromFile;
import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;
import static apps.java.loref.GeneralUtilitiesLibrary.encode;

import static apps.java.loref.MotionComm.getEventJpegFileName;
import static apps.java.loref.MotionComm.getShortName;

import static apps.java.loref.LinuxCommands.*;
import static apps.java.loref.TransmissionDaemonCommands.*;

import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;
import static apps.java.loref.LogUtilities.firebaseErrorLog_XTERM;
import static apps.java.loref.LogUtilities.printLog;
import static apps.java.loref.LogUtilities.printLogColor;

import static apps.java.loref.TimeUtilities.getTimeStamp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import com.google.api.client.util.Base64;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import apps.java.loref.GeneralUtilitiesLibrary;
import apps.java.loref.InternetConnectionCheck;
import apps.java.loref.InternetConnectionStatusListener;
import apps.java.loref.LogUtilities;
import apps.java.loref.MotionComm;
import apps.java.loref.MotionCommListener;
import apps.java.loref.RemoteCommand;
import apps.java.loref.TransmissionDaemonComm;
import apps.java.loref.YouTubeComm;
import apps.java.loref.YouTubeCommListener;
import apps.java.loref.YouTubeNotAuthorizedException;
import apps.java.loref.Domotic.SSHShell.SSHShellListener;
import apps.java.loref.FirebaseCloudUploader.FirebaseCloudUploader;
import apps.java.loref.FirebaseCloudUploader.FirebaseUploadItemListener;
import apps.java.loref.SocketResponder.SocketResponder;
import apps.java.loref.SocketResponder.SocketResponderListener;
import apps.java.loref.TelegramBotComm.SendMode;
import apps.java.loref.TelegramBotComm.TelegramBotComm;
import apps.java.loref.TelegramBotComm.TelegramBotCommListener;
import apps.java.loref.TelegramBotComm.TelegramBotMessages.TelegramBotTextAndPictureMessage;
import apps.java.loref.TelegramBotComm.TelegramBotMessages.TelegramBotTextMessage;

@SuppressWarnings({ "javadoc" })

public class DomoticCore {

	// Constant values
	private String firebaseDatabaseURL;
	private String jsonAuthFileLocation;
	private String deviceName;
	private String groupName;
	private String storageBucketAddress;

	private boolean loopFlag = true;
	private boolean exit = false;
	private boolean debugMode = false;

	private final long runningSince = System.currentTimeMillis();

	private boolean notificationsEnabled = false;
	private String fcmServiceKey = "";

	private boolean firebaseServicesEnabled = false;

	private static enum DatabaseNodes {
		COMMANDS_INCOMING_TOME, MOTION_EVENTS, DEVICE, LOGS, VIDEOCAMERAS, INCOMING_COMMANDS, FILES_INCOMING_TOME
	}

	private DatabaseReference getDBReference(DatabaseNodes value) {

		return FirebaseDatabase.getInstance().getReference(getDatabaseNode(value));

	}

	private String getDatabaseNode(DatabaseNodes value) {

		switch (value) {

		case COMMANDS_INCOMING_TOME:
			return new StringBuilder().append(INCOMING_COMMANDS_NODE).append("/").append(this.groupName).append("/")
					.append(this.deviceName).toString();

		case FILES_INCOMING_TOME:
			return new StringBuilder().append(INCOMING_FILES_NODE).append("/").append(this.groupName).append("/")
					.append(this.deviceName).toString();

		case MOTION_EVENTS:

			return new StringBuilder().append(MOTION_EVENTS_NODE).append("/").append(this.groupName).toString();

		case DEVICE:
			return new StringBuilder().append(DEVICES_NODE).append("/").append(this.groupName).append("/")
					.append(this.deviceName).toString();

		case LOGS:
			return new StringBuilder().append(LOGS_NODE).append("/").append(this.groupName).append("/")
					.append(this.deviceName).toString();

		case VIDEOCAMERAS:
			return new StringBuilder().append(VIDEO_CAMERAS_NODE).append("/").append(this.groupName).toString();

		case INCOMING_COMMANDS:
			return new StringBuilder().append(INCOMING_COMMANDS_NODE).append("/").append(this.groupName).toString();

		default:
			return "null";
		}

	}

	private String getVideoCameraFullID(String cameraID) {
		return new StringBuilder().append(this.deviceName).append("-").append(cameraID).toString();
	}

	/**
	 * 
	 * Enable the Firebase services, if @param is set to true.
	 *
	 * @param value
	 */
	private void setFirebaseServicesEnabled(boolean value) {

		this.firebaseServicesEnabled = value;

		if (this.firebaseServicesEnabled) {

			printLog(LogTopics.LOG_TOPIC_FIREBASE_DB, "Firebase services available.");

			this.incomingCommands = getDBReference(DatabaseNodes.COMMANDS_INCOMING_TOME);

			// register the device
			registerDeviceServices();

			// attiva i timer per i task periodici

			this.deviceStatusUpdateTimer = new Timer();
			this.deviceStatusUpdateTimer.scheduleAtFixedRate(new DeviceStatusUpdateTask(), 0,
					this.deviceStatusUpdateRate);

			this.deviceNetworkStatusUpdateTimer = new Timer();
			this.deviceNetworkStatusUpdateTimer.schedule(new DeviceNetworkStatusUpdateTask(), 0,
					this.deviceNetworkStatusUpdateRate);

			this.videocamerasShotUpdateTimer = new Timer();
			this.videocamerasShotUpdateTimer.schedule(new VideocamerasShotUpdateTask(), 0,
					this.videocamerasShotUpdateRate);

			printLog(LogTopics.LOG_TOPIC_INET_OUT, "Periodical update tasks resumed.");

			// resume the Firebase Uploader
			this.firebaseCloudUploader.resume();
			printLog(LogTopics.LOG_TOPIC_INET_IN, "Firebase cloud upload engine resumed.");
			
			// attaches the listeners to the database nodes
			attachListeners();

			// writes a log entry in the Firebase Database
			LogEntry log = new LogEntry(getTimeStamp(), LogTopics.LOG_TOPIC_INET_IN, "Firebase services restored.");
			firebaseLog(log);

		} else {

			printLog(LogTopics.LOG_TOPIC_FIREBASE_DB, "Firebase services unavailable.");

		}

	}

	private boolean allowDirectoryNavigation = false;
	private boolean allowTorrentManagement = false;
	private boolean allowVideoSurveillanceManagement = false;
	private boolean allowSSH = false;

	private boolean hasDirectoryNavigation = false;
	private boolean hasTorrentManagement = false;
	private boolean hasVideoSurveillance = false;

	private String logsNode = "";

	// --------------------------------------------------------------------------------------------
	// @SECTION: Device status periodical update
	// --------------------------------------------------------------------------------------------
	private boolean servicesConnected = false;
	private Timer deviceStatusUpdateTimer;
	private long deviceStatusUpdateRate = Defaults.DEVICE_STATUS_UPDATE_RATE;

	private class DeviceStatusUpdateTask extends TimerTask {

		@Override
		public void run() {

			if (DomoticCore.this.internetConnectionCheck.getConnectionAvailable()) {
				updateDeviceStatus();
			} else {
				printLog(LogTopics.LOG_TOPIC_TIMER_TASK,
						"Device status update not started due to internet unavailability.");
			}

		}

	}

	private long firebaseDBUpdateTimeOut = Defaults.FIREBASE_DB_UPDATE_TIMEOUT;
	private Timer firebaseDBUpdateTimeoutTimer = null;

	private class FirebaseDBUpdateTimeoutTask extends TimerTask {

		@Override
		public void run() {

			// Manages a timeout issue when trying to update the device status over the Firebase DB node.

			// Prints a log message
			printLog(LogTopics.LOG_TOPIC_ERROR, "Timeout exceeded during device status update on Firebase DB node.");

			// start the internet connectivity check loop
			DomoticCore.this.internetConnectionCheck.start();

		}

	}

	private void updateDeviceStatus() {

		FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.DEVICE)).child(STATUS_DATA_CHILDNODE)
				.setValue(getStatusDeviceDataJSON(), new CompletionListener() {

					@Override
					public void onComplete(DatabaseError error, DatabaseReference ref) {

						if (error == null) {

							// stop the timer, if exists
							if (DomoticCore.this.firebaseDBUpdateTimeoutTimer != null) {
								DomoticCore.this.firebaseDBUpdateTimeoutTimer.cancel();
							}

						} else {

							firebaseErrorLog_XTERM(error);

						}

					}

				});

		// if a previous timer was already running, stops it
		if (this.firebaseDBUpdateTimeoutTimer != null) {
			this.firebaseDBUpdateTimeoutTimer.cancel();
		}

		// starts a new timer
		this.firebaseDBUpdateTimeoutTimer = new Timer();
		this.firebaseDBUpdateTimeoutTimer.schedule(new FirebaseDBUpdateTimeoutTask(), this.firebaseDBUpdateTimeOut);

	}

	// --------------------------------------------------------------------------------------------
	// @SECTION: Internet connectivity check and management
	// --------------------------------------------------------------------------------------------
	private void goOffLine() {

		// pause the Firebase Uploader
		DomoticCore.this.firebaseCloudUploader.pause();
		printLog(LogTopics.LOG_TOPIC_INET_OUT, "Firebase cloud upload engine paused.");

		detachListeners();
		printLog(LogTopics.LOG_TOPIC_INET_OUT, "Firebase listeners detached.");

		// cancella i timer per i task periodici
		this.deviceStatusUpdateTimer.cancel();
		this.deviceNetworkStatusUpdateTimer.cancel();
		this.videocamerasShotUpdateTimer.cancel();

		printLog(LogTopics.LOG_TOPIC_INET_OUT, "Periodical update tasks cancelled.");

		int nOfApps = FirebaseApp.getApps().size();

		for (int i = 0; i < nOfApps; i++) {

			String appName = FirebaseApp.getApps().get(i).getName();
			FirebaseApp.getApps().get(i).delete();
			printLog(LogTopics.LOG_TOPIC_INET_OUT, "Firebase app: " + appName + " deleted.");

		}

		// sets the availability of the Telegram Bot
		this.telegramBotActive = false;
		printLog(LogTopics.LOG_TOPIC_INET_OUT, "Telegram Bot deactivated.");

		this.servicesConnected = false;

	}

	private void goOnLine() {

		setFirebaseServicesEnabled(connectToFirebaseApp());

		// Initialize the Telegram bot, if not already done
		if (this.telegramBot == null) { // there is no Telegram bot existing

			// Initializes the telegram bot.

			printLog(LogTopics.LOG_TOPIC_INIT, "Initializing Telegram bot...");
			initTelegramBot();

		} else { // Telegram bot already exists

			this.telegramBotActive = true;

			// dispatch a notification on all the Telegram recipients

		}

		this.servicesConnected = true;

	}

	// --------------------------------------------------------------------------------------------
	// @SECTION: Network status periodical update
	// --------------------------------------------------------------------------------------------
	private long deviceNetworkStatusUpdateRate = Defaults.DEVICE_NETWORK_STATUS_UPDATE_RATE;

	private Timer deviceNetworkStatusUpdateTimer;

	private class DeviceNetworkStatusUpdateTask extends TimerTask {

		@Override
		public void run() {

			if (DomoticCore.this.internetConnectionCheck.getConnectionAvailable()) {
				printLog(LogTopics.LOG_TOPIC_TIMER_TASK, "Start of network status update (async).");
				updateNetworkStatus();
			} else {
				printLog(LogTopics.LOG_TOPIC_TIMER_TASK, "Network update not started due to internet unavailability.");
			}

		}

	}

	private void updateNetworkStatus() {

		FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.DEVICE)).child(NETWORK_DATA_CHILDNODE)
				.setValueAsync(getNetworkDeviceDataJSON());

	}

	// --------------------------------------------------------------------------------------------
	// @SECTION: Videocameras frames update management
	// --------------------------------------------------------------------------------------------
	private long videocamerasShotUpdateRate = Defaults.VIDEOCAMERA_SHOT_UPDATE_RATE;
	private Timer videocamerasShotUpdateTimer;

	private class VideocamerasShotUpdateTask extends TimerTask {

		@Override
		public void run() {

			requestVideoCamerasFrameUpdate();

		}

	}

	private void requestVideoCamerasFrameUpdate() {

		if (DomoticCore.this.internetConnectionCheck.getConnectionAvailable()) {

			printLog(LogTopics.LOG_TOPIC_TIMER_TASK, "Start of videocameras frames update.");
			String cameraIDs[] = DomoticCore.this.motionComm.getThreadsIDs();

			for (String id : cameraIDs) {
				DomoticCore.this.motionComm.captureFrames(id, 1, "DBNode");
			}

		} else {

			printLog(LogTopics.LOG_TOPIC_TIMER_TASK,
					"Videocameras frames update not started due to internet unavailability.");

		}

	}

	private void updateTMateStatus() {

		//		HashMap<String, Object> tMateStatus = new HashMap<>();
		//		tMateStatus.put("SSHAddress", "later");
		//		tMateStatus.put("WebAddress", "later");
		//
		//		String refNode = GROUP_NODE + "/" + this.groupName + "/" + DEVICES_NODE + "/" + this.thisDevice + "/"
		//				+ TMATE_DATA_NODE;
		//		FirebaseDatabase.getInstance().getReference(refNode).setValueAsync(tMateStatus);

	}

	// --------------------------------------------------------------------------------------------
	// @SECTION: Telegram bot management
	// --------------------------------------------------------------------------------------------

	private TelegramBotComm telegramBot = null;
	private boolean telegramBotActive = false;

	private ConcurrentHashMap<String, List<String>> notificationSubscriptions = new ConcurrentHashMap<String, List<String>>();

	private ConcurrentHashMap<String, TelegramBotTextMessage> telegramInitMessagesMap = new ConcurrentHashMap<String, TelegramBotTextMessage>();

	private TelegramBotCommListener telegramBotCommListener = new TelegramBotCommListener() {

		TelegramBotComm botInterface = DomoticCore.this.telegramBot;

		@Override
		public void onRegistrationFailure() {

			printLogColor(LogUtilities.YELLOW, LogTopics.LOG_TOPIC_INIT,
					"Error raised during Telegram bot registration.");

			this.botInterface.detachListener();
			this.botInterface = null;
			DomoticCore.this.telegramBotActive = false;
		}

		@Override
		public void onBotRegisterationSuccess() {

			DomoticCore.this.telegramBotActive = true;
			printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_INIT, "Telegram bot registered.");

			// sends a welcome message
			DomoticCore.this.sendInitToAll();

		}

		@Override
		public void onTextMessageReceived(boolean isAuthenticated, long chatID, int userID, String content) {

			String color;
			String topic;
			if (isAuthenticated) {
				color = LogUtilities.GREEN;
				topic = LogTopics.LOG_TOPIC_AUTHENTICATED_TELEGRAM_MESSAGE_IN;

				processIncomingTelegramMessage("" + userID, content);

			} else {
				color = LogUtilities.YELLOW;
				topic = LogTopics.LOG_TOPIC_NONAUTHENTICATED_TELEGRAM_MESSAGE_IN;
			}

			printLogColor(color, topic,
					String.format("chat id='%s'; user id='%s'; content='%s'", chatID, userID, content));

		}

		@Override
		public void onQueryCallBackReceived(boolean isAuthenticated, CallbackQuery callbackQuery) {

			String color;
			String topic;
			
			String chatID = callbackQuery.getMessage().getChatId().toString();
			String userID = callbackQuery.getFrom().getId().toString();
			String callBackData = callbackQuery.getData();
			String messageID = callbackQuery.getMessage().getMessageId().toString();
			
			if (isAuthenticated) { // message sender is authenticated

				color = LogUtilities.GREEN;
				topic = LogTopics.LOG_TOPIC_AUTHENTICATED_TELEGRAM_CALLBACK_IN;

				String callBackArgument = callBackData.split("___")[1];
							
				// obtains a clone of the INIT MESSAGE
				TelegramBotTextMessage referenceMessage = null;
				if (DomoticCore.this.telegramInitMessagesMap.containsKey(chatID)) {

					// obtains a copy of the init message in order to modify it according to the selection
					referenceMessage = DomoticCore.this.telegramInitMessagesMap.get(chatID).getClone();
					referenceMessage.setMode(SendMode.UPDATE);

				}

				if (callBackData.startsWith("service_select")) {

					if (referenceMessage != null) {

						switch (callBackArgument) {

						case "VIDEOSURVEILLANCE": // sends back the option for the VideoSurveillance management

							referenceMessage.setKeyboard(DomoticCore.this.getTelegramVSurveillanceKeyboard());
							referenceMessage.setTextContent("Select a camera...");

							DomoticCore.this.telegramBot.send(referenceMessage);

							break;

						case "INIT": // sends back the initial message

							DomoticCore.this.telegramBot.send(referenceMessage);

							break;

						case "WAKEONLAN": // shows the options for the wake on lan service

							referenceMessage.setMode(SendMode.UPDATE);
							referenceMessage.setTextContent("Select the device to wake:");
							referenceMessage.setKeyboard(DomoticCore.this.getTelegramWOLKeyboard());
							DomoticCore.this.telegramBot.send(referenceMessage);

							break;
							
						case "STATUS": // shows the options for the wake on lan service

							referenceMessage.setMode(SendMode.UPDATE);
							referenceMessage.setTextContent(getStatusMsg());
							referenceMessage.setKeyboard(DomoticCore.this.getBackToInitKeyboard());
							DomoticCore.this.telegramBot.send(referenceMessage);

							break;

						default:
							break;

						} // end of switch

					} else {

						printLog(LogTopics.LOG_TOPIC_AUTHENTICATED_TELEGRAM_CALLBACK_IN,
								"Unable to retrieve original message");

					}

				} else if (callBackData.startsWith("videocamera_select")) {

					referenceMessage.setTextContent("Select an option");
					referenceMessage
							.setKeyboard(DomoticCore.this.getTelegramVideocameraOptionsKeyboard(callBackArgument));

				} else if (callBackData.startsWith("wakeonlan_select")) {

					// retrieve the MAC address of the device to wake up
					int selectedDevice = Integer.parseInt(callBackArgument);

					// wakes up the device
					DomoticCore.this.wakeDevice(selectedDevice);

					// turns back to init message
					DomoticCore.this.telegramBot.send(referenceMessage);

				} else if (callBackData.startsWith("subscription_removal")) { // attempts to remove the message sender's ID from the given notifications subscription list

					if (DomoticCore.this.notificationSubscriptions.containsKey(callBackArgument)) { // a notification subscription has been found

						// remove the message sender's ID from the list.
						List<String> subscriptions = DomoticCore.this.notificationSubscriptions.get(callBackArgument);
						if (subscriptions.removeIf(s -> s.startsWith("" + chatID))) { // operation succeeded
														
							// logs the event
							printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_AUTHENTICATED_TELEGRAM_CALLBACK_IN, String.format("Recipient ID \"%s\" successfully removed from subscription \"%s\".", chatID, messageID));

							
						} else { // something went wrong

							// logs
							printLogColor(LogUtilities.YELLOW, LogTopics.LOG_TOPIC_AUTHENTICATED_TELEGRAM_CALLBACK_IN, String.format("Failed to remove recipient ID \"%s\" from subscription \"%s\".", chatID, messageID));

						}
						
					} 
					
				}

			} else { // message sender is not authenticated

				color = LogUtilities.YELLOW;
				topic = LogTopics.LOG_TOPIC_AUTHENTICATED_TELEGRAM_CALLBACK_IN;

			}

			printLogColor(color, topic,
					String.format("chat id='%s'; user id='%s'; content='%s'", chatID, userID, callBackData));

		}

		@Override
		public void onMessageSent(String messageKey, String messageType, String messageTag, String messageID) {
			// unused
		}

		@Override
		public void onTextMessageSent(TelegramBotTextMessage message) {

			if (message.getTag().startsWith("frameupdates_camera_")) {

				if (DomoticCore.this.notificationSubscriptions.containsKey(message.getTag())) {

					List<String> notificationSubscriptionMessageIDs = DomoticCore.this.notificationSubscriptions
							.get(message.getTag());

					if (notificationSubscriptionMessageIDs.contains(message.getRecipientID())) {

						notificationSubscriptionMessageIDs.remove(message.getRecipientID());
						notificationSubscriptionMessageIDs.add(message.getRecipientID() + "_" + message.getMessageID());

						printLog(LogTopics.LOG_TOPIC_AUTHENTICATED_TELEGRAM_MESSAGE_IN,
								"Notification subscription message ID registered. Topic: " + message.getTag()
										+ ". Chat ID: " + message.getRecipientID() + ". Message ID: "
										+ message.getMessageID() + ".");

					}

				}

			} else {

				switch (message.getTag()) {

				case ("__init_message"):

					printLog(LogTopics.LOG_TOPIC_AUTHENTICATED_TELEGRAM_MESSAGE_IN, "Init message registered for user: "
							+ message.getRecipientID() + ". Message ID: " + message.getMessageID() + ".");
					DomoticCore.this.telegramInitMessagesMap.put(message.getRecipientID(), message);

				}

			}

		}

		@Override
		public void onTextAndPictureMessageSent(TelegramBotTextAndPictureMessage message) { // no action

		}

		@Override
		public void onTextMessageCompleteUpdateDone(TelegramBotTextMessage message) { // no action

		}

		@Override
		public void onTextAndPictureMessageUpdated(TelegramBotTextAndPictureMessage message) { // no action

		}

	};

	private void initTelegramBot() {

		JSONObject telegramBotConfigJson = new JSONObject(
				readPlainTextFromFile(new File(TELEFGRAM_CONFIG_FILE_LOCATION)));
		if (telegramBotConfigJson.has("user_id") && telegramBotConfigJson.has("token")) { // inizializza la classe del bot Telegram

			printLog(LogTopics.LOG_TOPIC_INIT, "Creating bot...");
			this.telegramBot = new TelegramBotComm(telegramBotConfigJson.getString("user_id"),
					telegramBotConfigJson.getString("token"));
			this.telegramBot.setDebugMode(false);
			this.telegramBot.attachListener(this.telegramBotCommListener);

			if (telegramBotConfigJson.has("group_notification_ids")) {

				JSONArray notifications = telegramBotConfigJson.getJSONArray("group_notification_ids");
				printLog(LogTopics.LOG_TOPIC_INIT, notifications.length() + " recipients found. Adding...");

				for (int i = 0; i < notifications.length(); i++) {

					this.telegramBot.getRecipientsList().add(notifications.getString(i));
					printLog(LogTopics.LOG_TOPIC_INIT, "Recipient \"" + notifications.getString(i) + "\" added.");

				}

			}

			if (telegramBotConfigJson.has("authorized_users_ids")) {

				JSONArray authorized = telegramBotConfigJson.getJSONArray("authorized_users_ids");
				printLog(LogTopics.LOG_TOPIC_INIT, authorized.length() + " authorized users found. Adding...");

				for (int i = 0; i < authorized.length(); i++) {

					this.telegramBot.getAuthorizedUsers().add(authorized.getString(i));
					printLog(LogTopics.LOG_TOPIC_INIT, "Authorized user id \"" + authorized.getString(i) + "\" added.");

				}

			}

			// start the bot
			printLog(LogTopics.LOG_TOPIC_INIT, "Starting Telegram bot...");
			this.telegramBot.start();

		}

	}

	private void createNotificationsSubscriptions() {

		this.notificationSubscriptions.clear();
		List<String> recipients = new ArrayList<String>();
		for (String recipient : this.telegramBot.getRecipientsList()) {
			recipients.add(recipient);
		}

		if (this.hasVideoSurveillance && this.motionComm != null) {

			for (String cameraID : this.motionComm.getThreadsIDs()) {

				this.notificationSubscriptions.put("frameupdates_camera_" + cameraID, recipients);
				this.notificationSubscriptions.put("motionupdates_camera_" + cameraID, recipients);

			}

		}

		this.notificationSubscriptions.put("connectionrestored", recipients);

	}

	private void processIncomingTelegramMessage(String userID, String textContent) {

		switch (textContent.toLowerCase()) {

		case "ping":

			this.telegramBot.send(new TelegramBotTextMessage(userID, "pong"));
			break;

		case "status":

			this.telegramBot.send(new TelegramBotTextMessage(userID, "pong"));
			break;

		case "/start":

			this.telegramBot.send(getTelegramInitMessage(userID));
			createNotificationsSubscriptions();
			requestVideoCamerasFrameUpdate();
			break;

		}

	}

	private TelegramBotTextMessage getTelegramInitMessage(String userID) {

		TelegramBotTextMessage message = new TelegramBotTextMessage(userID, "Hi!");

		JSONObject keyboard = new JSONObject();

		JSONObject line1 = new JSONObject();

		JSONObject statusBtn = new JSONObject().put("action", "service_select___STATUS").put("label", "Status");
		line1.append("buttons", statusBtn);

		if (this.hasTorrentManagement) {
			JSONObject torrentBtn = new JSONObject().put("action", "service_select___TORRENT").put("label", "Torrent");
			line1.append("buttons", torrentBtn);
		}

		if (this.wolDevices.length > 0) {
			JSONObject wakeonlanBtn = new JSONObject().put("action", "service_select___WAKEONLAN").put("label",
					"Wake-on-lan");
			line1.append("buttons", wakeonlanBtn);
		}

		keyboard.append("lines", line1);

		if (this.hasVideoSurveillance) {
			JSONObject line2 = new JSONObject();

			JSONObject videosurveillanceBtn = new JSONObject().put("action", "service_select___VIDEOSURVEILLANCE")
					.put("label", "Videosurveillance");
			line2.append("buttons", videosurveillanceBtn);
			keyboard.append("lines", line2);
		}

		message.setKeyboard(keyboard);
		message.setTag("__init_message");
		message.setRecipientID(userID);
		return message;

	}

	private void sendInitToAll() {

		for (String user : this.telegramBot.getRecipientsList()) {

			//this.telegramBot.send(getTelegramInitMessage(user));
			processIncomingTelegramMessage(user, "/start");

		}

	}

	private void sendToNotificationList(String HTMLMessage, String notificationListID, boolean updateIfExists) {
		sendToNotificationList(HTMLMessage, notificationListID, updateIfExists, null, -1, -1);
	}

	private void sendToNotificationList(String HTMLMessage, String notificationListID, boolean updateIfExists, JSONObject keyboard) {
		sendToNotificationList(HTMLMessage, notificationListID, updateIfExists, keyboard, -1, -1);
	}

	private void sendToNotificationList(String HTMLMessage, String notificationListID, boolean updateIfExists,
			JSONObject keyboard, long keyboardTimeout, long messageTimeout) {

		if (this.notificationSubscriptions.containsKey(notificationListID)) {

			for (String notificationSubscriptionMessageID : this.notificationSubscriptions.get(notificationListID)) {

				String[] notificationInfo = notificationSubscriptionMessageID.split("_");

				TelegramBotTextMessage message = new TelegramBotTextMessage(notificationInfo[0], HTMLMessage);

				if (updateIfExists && notificationInfo.length == 2) {

					message.setMessageCompoundID(notificationSubscriptionMessageID);
					message.setMode(SendMode.UPDATE);

				} else {

					message.setTag(notificationListID);

				}
				
				if (keyboard != null)
					message.setKeyboard(keyboard);

				message.setKeyboardTimeOut(keyboardTimeout);
				message.setLifeTimeOut(messageTimeout);
				
				this.telegramBot.send(message);

			}

		} else {

			//

		}

	}

	private JSONObject getTelegramVSurveillanceKeyboard() {

		JSONObject keyboard = new JSONObject();

		for (String id : this.motionComm.getThreadsIDs()) {

			JSONObject line = new JSONObject();

			JSONObject videoCameraBtn = new JSONObject().put("action", "service_select___CAMERA&ID=" + id).put("label",
					this.motionComm.getCameraName(id));
			line.append("buttons", videoCameraBtn);

			keyboard.append("lines", line);

		}

		JSONObject line = getBackToInitLine();
		keyboard.append("lines", line);

		return keyboard;

	}

	private JSONObject getTelegramWOLKeyboard() {

		JSONObject keyboard = new JSONObject();

		for (int i = 0; i < this.wolDevices.length; i++) {

			JSONObject line = new JSONObject();

			JSONObject deviceBtn = new JSONObject().put("action", "wakeonlan_select___" + i).put("label",
					this.wolDeviceNames[i]);
			line.append("buttons", deviceBtn);

			keyboard.append("lines", line);

		}

		JSONObject line = getBackToInitLine();
		keyboard.append("lines", line);

		return keyboard;

	}

	private JSONObject getTelegramVideocameraOptionsKeyboard(String cameraID) {

		JSONObject keyboard = new JSONObject();

		JSONObject line;
		JSONObject optionButton;

		// Line 1
		line = new JSONObject();

		// button: 'get picture'
		optionButton = new JSONObject().put("action", "videocamera_option___" + cameraID+","+"get_picture").put("label",
				"Get picture");
		line.append("buttons", optionButton);
		keyboard.append("lines", line);

		// Line 2
		line = new JSONObject();

		// button: 'get live stream link'
		optionButton = new JSONObject().put("action", "videocamera_option___" + cameraID+","+"get_livestream").put("label",
				"Live stream");
		line.append("buttons", optionButton);

		// button: 'get a motion'
		optionButton = new JSONObject().put("action", "videocamera_option___" + cameraID+","+"emulate_motion").put("label",
				"Emulate motion");
		line.append("buttons", optionButton);

		keyboard.append("lines", line);

		// Line 2
		line = new JSONObject();

		JSONObject backBtn = new JSONObject().put("action", "service_select___VIDEOSURVEILLANCE").put("label",
				"< Back");
		line.append("buttons", backBtn);
		keyboard.append("lines", line);

		return keyboard;

	}
	
	private JSONObject getBackToInitKeyboard() {

		JSONObject keyboard = new JSONObject();
		keyboard.append("lines", getBackToInitLine());

		return keyboard;

	}
	
	private JSONObject getBackToInitLine() {

		JSONObject line = new JSONObject();
		JSONObject backBtn = new JSONObject().put("action", "service_select___INIT").put("label", "< Back");
		line.append("buttons", backBtn);
		
		return line;

	}

	// ----- ---- --- -- - VPN connection - -- --- ---- -----

	private String vpnConnectionConfigFilePath = null;

	// ----- ---- --- -- - Firebase cloud uploader
	private FirebaseCloudUploader firebaseCloudUploader;

	// ----- ---- --- -- - TCP interface

	SocketResponder tcpInterface = new SocketResponder();
	private boolean tcpInitialized = false;

	SocketResponderListener tcpInterfaceListener = new SocketResponderListener() {

		@Override
		public void onLineReceived(String hostID, String data) {
			// intentionally blank

		}

		@Override
		public void onCreated(int port) {

			DomoticCore.this.tcpInitialized = true;

			// print a log
			printLog(LogTopics.LOG_TOPIC_TCP, String.format("TCP Listener initialized on port: %d.", port));

		}

		@Override
		public void onConnected(String hostID) {
			// print a log
			printLog(LogTopics.LOG_TOPIC_TCP,
					String.format("New incoming TCP connection accepted: %s. Total connections: %d", hostID,
							DomoticCore.this.tcpInterface.getConnectionsCount()));

		}

		@Override
		public void onCommand(final String hostID, final RemoteCommand command, final HashMap<String, Object> params) {

			new Thread() {

				@Override
				public void run() {

					replyToRemoteCommand(command, String.format("tcp://%s", hostID), params);

				}

			}.start();

		}

		@Override
		public void onDisconnect(String hostID, boolean byTimeout) {
			// print a log
			String byTimeoutFlag = "";
			if (byTimeout) {
				byTimeoutFlag = " (by timeout)";
			}

			printLog(LogTopics.LOG_TOPIC_TCP, String.format("Client %s disconnected%s. Total connections: %d", hostID,
					byTimeoutFlag, DomoticCore.this.tcpInterface.getConnectionsCount()));

		}

		@Override
		public void onAuth(String hostID, String reason) {

			// print a log
			printLog(LogTopics.LOG_TOPIC_TCP, String.format("Client %s authenticated. Reason: %s", hostID, reason));

		}

	};

	// Internet connectivity loop check

	private String internetConnectionCheckServer = Defaults.CONNECTIVITY_TEST_SERVER_ADDRESS;
	private long internetConnectionCheckRate = Defaults.CONNECTIVITY_TEST_RATE;
	private InternetConnectionCheck internetConnectionCheck;
	private InternetConnectionStatusListener internetConnectionStatusListener = new InternetConnectionStatusListener() {

		@Override
		public void onConnectionRestored(long inactivityTime) {

			// prints a log message
			printLog(LogTopics.LOG_TOPIC_INET_IN,
					"Internet connectivity available after " + inactivityTime / 1000 + " seconds.");

			// detach listeners, cancel the periodical tasks and destroy the firebase database app
			if (!DomoticCore.this.servicesConnected) {
				goOnLine();

				// dispatch a message to the Telegram Bot notifications subscibers

				if (DomoticCore.this.telegramBotActive) { // the Telegram Bot is up

					// dispatch the message to all the user who subsribed the topic relevant to the internet connectivity restore

					JSONObject keyboard = new JSONObject();

					JSONObject line = new JSONObject();
					JSONObject button = new JSONObject().put("action", "subscription_removal___connectionrestored")
							.put("label", "Stop notifications.");
					line.append("buttons", button);
					keyboard.append("lines", line);

					DomoticCore.this.sendToNotificationList(String.format("Internet connectivity has been restored. Unavailabiliy time: %s seconds.", inactivityTime / 1000 ),
							"connectionrestored", false, keyboard, -1, 120000);

				}

			}

			// stops the internet connectivity check loop
			DomoticCore.this.internetConnectionCheck.stop();

		}

		@Override
		public void onConnectionLost() {

			printLog(LogTopics.LOG_TOPIC_INET_OUT, "Internet connectivity not available.");

			// detach listeners, cancel the periodical tasks and destroy the firebase database app
			if (DomoticCore.this.servicesConnected)
				goOffLine();

		}

	};

	// Firebase database incoming message management

	ChildEventListener incomingMessagesNodeListener = new ChildEventListener() {

		@Override
		public void onChildRemoved(DataSnapshot snapshot) {
			// no action foreseen

		}

		@Override
		public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
			// no action foreseen

		}

		@Override
		public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
			// no action foreseen

		}

		@Override
		public void onChildAdded(DataSnapshot snapshot, String previousChildName) {

			// retrieve the incoming message in the form of a new RemoteCommand instance

			RemoteCommand remoteCommand = snapshot.getValue(RemoteCommand.class);

			// waits for a TICK, this is needed in order to avoid possible duplicate timestamps

			sleepSafe(10);

			// performs the needed operations according to the content of the incoming message
			replyToRemoteCommand(remoteCommand, snapshot.getKey(), null);

		}

		@Override
		public void onCancelled(DatabaseError error) {
			// no action foreseen

		}

	};

	ValueEventListener onlineStatusEventListener = new ValueEventListener() {

		@Override
		public void onDataChange(DataSnapshot snapshot) {

			//TODO: for some reason, some client put the Online key value to false.
			// this should force a call to a verification of the online status, and trigger the necessary actions in case
		}

		@Override
		public void onCancelled(DatabaseError error) {
			firebaseErrorLog_XTERM(error);

		}

	};

	// Firebase log

	private void firebaseLog(LogEntry log) {
		FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.LOGS)).child(getTimeStamp())
				.setValueAsync(log);
	}

	// Device registration

	private boolean deviceRegistered;
	private int videoSurveillanceRegistered;
	private boolean incomingMessagesCleared;
	private boolean incomingFilesCleared;
	private DatabaseReference incomingCommands;

	// WakeOnLan

	private String[] wolDevices;
	private String[] wolDeviceNames;

	private void wakeDevice(int devID) {

		if (devID < this.wolDevices.length) {
			try {

				parseShellCommand("wakeonlan " + this.wolDevices[devID]);
				printLog(LogTopics.LOG_TOPIC_CMDEXEC, "\'wakeonlan\' command sent to device ID\'" + devID
						+ "\':, MAC address:\'" + this.wolDevices[devID] + "\'");

			} catch (IOException | InterruptedException e) {

				exceptionLog_REDXTERM(this.getClass(), e);

			}
		}

	}

	// SSH Shell

	private HashMap<String, SSHShell> sshShells = new HashMap<String, SSHShell>();
	private SSHShell sshShell;
	private String sshUsername;
	private String sshPassword;
	private String sshHost;
	private int sshPort;

	// Videosurveillance

	private MotionComm motionComm;
	private String videoSurveillanceServerAddress = "";
	private int videoSurveillanceServerControlPort = -1;
	private String videoSurveillanceDaemonShutdownCommand = "";

	private String youTubeJSONLocation = "";
	private String youTubeOAuthFolder = "";

	private HashMap<String, String> youTubeLiveBroadcasts; // registra i LiveBroadcast ID delle varie videocamere
	private HashMap<String, String> youTubeLiveStreamRequestors; // registra i dispositivi che	richiedono	un live	streaming

	private MotionCommListener motionCommListener = new MotionCommListener() {

		@Override
		public void onNewFrame(final String cameraID, byte[] frameImageData, String destination) {

			printLog(LogTopics.LOG_TOPIC_VSURV,
					String.format("Frame image received. Camera ID: %s; Image bytes: %d; Destination: %s", cameraID,
							frameImageData.length, destination));

			if (destination.startsWith("tcp://")) { // Sends the received camera data as a TCP REPLY

				RemoteCommand remoteCommand = new RemoteCommand(ReplyPrefix.FRAME_IMAGE_DATA, encode(
						String.format("%s|data=%s", cameraID, Base64.encodeBase64String(compress(frameImageData)))),
						"null");

				sendMessageToDevice(remoteCommand, destination, "");

			} else if (destination.startsWith("tgm://")) { // Sends the received camera data in a message on the TelegramBot

			}

			// By default, stores the the received camera data into the relevant Firebase Database node.

			if (!DomoticCore.this.firebaseCloudUploader.isPaused()) { // the FirebaseCloudUploader is active 

				// set the position in the Firebase Cloud Storage
				String remotePosition = VIDEOCAMERA_SHOT_CLOUD_URL
						.replaceAll("%%group_name&&", DomoticCore.this.groupName)
						.replaceAll("%%device_name&&", DomoticCore.this.deviceName)
						.replaceAll("%%thread_id&&", cameraID) + "shotview.jpg";

				// print log entry
				printLog(LogTopics.LOG_TOPIC_VSURV,
						String.format("Starting upload of camera frame on position: \"%s\"", remotePosition));

				// set up the FirebaseCloudUploader and start the upload operation
				DomoticCore.this.firebaseCloudUploader.addToQueue(frameImageData, remotePosition,
						new FirebaseUploadItemListener() {

							@Override
							public void onError(Exception e) {

								exceptionLog_REDXTERM(this.getClass(), e);

							}

							@Override
							public void onComplete(Blob info, String uploadID) { // upload of the video file completed

								// print log entry
								printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_FIREBASE_CLOUD,
										"Frame image successfully uploaded on Firebase Cloud (" + info.getSize()
												+ " bytes).");

								HashMap<String, Object> frameData = new HashMap<String, Object>();

								frameData.put("Date", System.currentTimeMillis());

								FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.VIDEOCAMERAS))
										.child(getVideoCameraFullID(cameraID)).child("LastShotData")
										.setValue(frameData, new CompletionListener() {

											@Override
											public void onComplete(DatabaseError error, DatabaseReference ref) {

												if (error == null) {
													printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_VSURV,
															String.format(
																	"Videocamera shot frame data info successfully updated for camera id: '%s'.",
																	cameraID));
												} else {

													firebaseErrorLog_XTERM(error);

												}

											}

										});

								// manage the Telegram Bot notifications

								if (DomoticCore.this.telegramBotActive) { // the Telegram Bot is up

									// obtain a signed URL to easily download the picture
									URL url = info.signUrl(5, TimeUnit.MINUTES);

									// dispatch the message to all the user who subscribed the topic relevant to the frame update of this camera
									DomoticCore.this.sendToNotificationList(
											"New <a href=\"" + url + "\">frame</a> received from <b>"
													+ DomoticCore.this.motionComm.getCameraName(cameraID) + "</b> at "
													+ getTimeStamp("HH.mm") + ".",
											"frameupdates_camera_" + cameraID, true, getTelegramVideocameraOptionsKeyboard(cameraID));

								}

							}

						});

			} else { // the FirebaseCloudUploader is paused, therefore the frame upload will be skipped.

				printLog(LogTopics.LOG_TOPIC_VSURV, String.format("Frame skipped."));

			}

		}

		@Override
		public void statusChanged(String cameraID) {

			FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.VIDEOCAMERAS))
					.child(getVideoCameraFullID(cameraID)).child(MODETSTATUS_CHILDNODE)
					.setValueAsync(DomoticCore.this.motionComm.getThreadMoDetStatus(cameraID));

		}

	};

	private YouTubeComm youTubeComm = null;

	private YouTubeCommListener youTubeCommListener = new YouTubeCommListener() {

		@Override
		public void onLiveStreamDeleted(String broadcastID) {

			// print a log message
			printLog(LogTopics.LOG_TOPIC_VSURV,
					String.format("Broadcast id '%s and relevant bound live stream deleted.", broadcastID));

		}

		@Override
		public void onLiveStreamCreated(String requestorID, String requestID, String liveStreamID,
				String liveBroadcastID) { // A live stream has been created

			printLog(LogTopics.LOG_TOPIC_VSURV,
					String.format("Youtube live stream created for camera ID: %s, Live stream ID: %s, Broadcast ID: %s",
							requestID, liveStreamID, liveBroadcastID));

			String shellCommand = String.format("domotic-youtube_livestream_start %s %s %s %s %s", requestID,
					DomoticCore.this.motionComm.getCameraRateFPS(requestID),
					DomoticCore.this.motionComm.getStreamFullURL(requestID), liveStreamID, "05:00");

			printLog(LogTopics.LOG_TOPIC_VSURV, String.format("Starting shell command '%s'", shellCommand));

			try {

				// lancia il comando bash per avviare lo streaming
				execShellCommand(shellCommand);

				printLog(LogTopics.LOG_TOPIC_VSURV,
						String.format(
								"Youtube live stream started for camera ID: %s, Live stream ID: %s, Broadcast ID: %s",
								requestID, liveStreamID, liveBroadcastID));

				// aggiorna i dati relativi allo streaming
				setCameraLiveBroadcastData(requestID, liveBroadcastID);

				// inserisce i dati relativi allo streaming
				DomoticCore.this.youTubeLiveBroadcasts.put(requestID, liveBroadcastID);
				DomoticCore.this.youTubeLiveStreamRequestors.put(requestorID, requestID);

			} catch (IOException e) {

				// aggiorna i dati relativi allo streaming
				setCameraLiveBroadcastStatus(requestID, "idle");
				setCameraLiveBroadcastData(requestID, "");

				DomoticCore.this.youTubeLiveStreamRequestors.remove(requestorID);

				exceptionLog_REDXTERM(this.getClass(), e);

			}

		}

		@Override
		public void onLiveBroadCastDeleted(String broadcastID) { // no action foreseen

		}

		@Override
		public void onLiveStreamNotCreated(String requestorID, String requestID) {

			// aggiorna i dati relativi allo streaming
			setCameraLiveBroadcastStatus(requestID, "idle");
			DomoticCore.this.youTubeLiveStreamRequestors.remove(requestorID);

		}

	};

	private void setCameraLiveBroadcastData(String cameraID, String broadcastData) {

		FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.VIDEOCAMERAS))
				.child(getVideoCameraFullID(cameraID)).child("LiveStreamingBroadcastData").setValueAsync(broadcastData);

	}

	private void setCameraLiveBroadcastStatus(String cameraID, String broadcastStatus) {
		FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.VIDEOCAMERAS))
				.child(getVideoCameraFullID(cameraID)).child("LiveStreamingBroadcastStatus")
				.setValueAsync(broadcastStatus);

	}

	private class MainLoop implements Runnable {

		public MainLoop() {

		}

		@Override
		public void run() {

			printLog(LogTopics.LOG_TOPIC_MAIN, "Session started");

			while (DomoticCore.this.loopFlag) {

				// sleeps
				sleepSafe(TICK_TIME_MS);

			}

			printLog(LogTopics.LOG_TOPIC_TERM, "Terminating session");

			terminate();

		}

	}

	public DomoticCore() {

		System.out.println("--------------------------------------------------------------------------------");
		System.out.println(getTimeStamp("ddd dd/MMM/YYYY - hh.mm.ss"));
		System.out.println("--------------------------------------------------------------------------------");
		System.out.println();
		System.out.println("####   ###  #   #  ###  ##### ###  ####");
		System.out.println("#   # #   # ## ## #   #   #    #  #");
		System.out.println("#   # #   # # # # #   #   #    #  #");
		System.out.println("#   # #   # #   # #   #   #    #  #");
		System.out.println("####   ###  #   #  ###    #    #   ####");
		System.out.println();
		System.out.println("--------------------------------------------------------------------------------");
		printLog(LogTopics.LOG_TOPIC_INIT, "Domotic for linux desktop - by Lorenzo Failla");

		// Retrieves all the parameters values from the configuration file

		printLog(LogTopics.LOG_TOPIC_INIT, "Configuration file reading started.");

		if (!getConfiguration())
			System.exit(ExitCodes.EXIT_CODE___WRONG_CONFIGURATION);

		printLog(LogTopics.LOG_TOPIC_INIT, "Configuration file reading successfully completed.");

		// Initialize the TCP interface
		printLog(LogTopics.LOG_TOPIC_INIT, "Initializing TCP interface...");

		String[] users = readLinesFromFile(new File(USERS_FILE_LOCATION));
		printLog(LogTopics.LOG_TOPIC_INIT, users.length + " users found. Adding to whitelist...");

		for (String user : users) {

			this.tcpInterface.addWhiteListUser(user);

			printLog(LogTopics.LOG_TOPIC_INIT, "User " + user + " added to whitelist.");

		}

		this.tcpInterface.setListener(this.tcpInterfaceListener);
		this.tcpInterface.setDebugMode(false);
		this.tcpInterface.init();

		// initialize the FirebaseCloudUploader class
		this.firebaseCloudUploader = new FirebaseCloudUploader();
		this.firebaseCloudUploader.setDebugMode(true);
		this.firebaseCloudUploader.pause();

		// Available services probe
		retrieveServices();

		printLog(LogTopics.LOG_TOPIC_INIT, "Available services probing completed.");

		// initializes and starts the Internet connectivity check loop
		this.internetConnectionCheck = new InternetConnectionCheck(this.internetConnectionCheckServer);
		this.internetConnectionCheck.setConnectivityCheckRate(this.internetConnectionCheckRate);
		this.internetConnectionCheck.setListener(this.internetConnectionStatusListener);
		this.internetConnectionCheck.setDebugMode(false);
		this.internetConnectionCheck.start();

		printLog(LogTopics.LOG_TOPIC_INIT, "Internet connectivity loop check started.");

		// adds a shutdownhook to handle the termination of the application
		Runtime.getRuntime().addShutdownHook(new Thread() {

			@Override
			public void run() {

				// adds a log
				printLog(LogTopics.LOG_TOPIC_TERM, "Termination request catched.");

				// interrupts the main loop
				DomoticCore.this.loopFlag = false;

				while (!DomoticCore.this.exit) {
					sleepSafe(TICK_TIME_MS);
				}

			}

		});

		// starts the main loop
		new MainLoop().run();

	}

	/**
	 * 
	 * Performs all the tasks needed to safely close the application.
	 * 
	 */
	private void terminate() {

		// unregister the device, so that client cannot connect to it
		unRegisterDeviceServices();

		// close the TCP interface
		this.tcpInterface.terminate();
		printLog(LogTopics.LOG_TOPIC_TERM, "TCP interface closed.");

		this.firebaseCloudUploader.terminate();
		printLog(LogTopics.LOG_TOPIC_INET_OUT, "Firebase cloud upload engine terminated.");

		// if specified, shuts down the video surveillance daemon
		if (this.hasVideoSurveillance && !this.videoSurveillanceDaemonShutdownCommand.equals("")) {

			try {

				String response = parseShellCommand(this.videoSurveillanceDaemonShutdownCommand);
				printLog(LogTopics.LOG_TOPIC_TERM, "Video surveillance daemon shut down. Server response: " + response);

			} catch (IOException | InterruptedException e) {

				exceptionLog_REDXTERM(this.getClass(), e);

			}

		}

		while (this.deviceRegistered && (!this.hasVideoSurveillance || this.videoSurveillanceRegistered > 0)) {

			sleepSafe(100);

		}

		// remove the Firebase ValueEventListeners
		goOffLine();

		printLog(LogTopics.LOG_TOPIC_TERM, "End of session");
		this.exit = true;

	}

	/**
	 * 
	 * Analyzes the content of the RemoteCommand object coming from a client.
	 * Performs operations accordingly and, if needed, provides a reply in the
	 * form of a RemoteCommand object.
	 *
	 * @param incomingCommand
	 *            - the RemoteCommand object coming from a client
	 * @return null or, if needed, a RemoteCommand object to be sent to the
	 *         requestor client.
	 */

	private RemoteCommand getReply(RemoteCommand incomingCommand) {

		String logContent = String.format("from:\'%s\' hdr:\'%s\' bdy:\'%s\'", incomingCommand.getReplyto(),
				incomingCommand.getHeader(), incomingCommand.getBody()).replace("\n", "\\n");

		printLog(LogTopics.LOG_TOPIC_INMSG, logContent);

		switch (incomingCommand.getHeader()) {

		case "__keepalive_timeout":

			return null;

		case "__quit":

			this.loopFlag = false;
			return null;

		case "__requestWelcomeMessage":

			// sends a reply with the name of this device
			return new RemoteCommand(ReplyPrefix.WELCOME_MESSAGE, encode(this.deviceName), "null");

		case "__requestUpTime":

			return new RemoteCommand(ReplyPrefix.UPTIME_MESSAGE, encode(getUptime()), "null");

		case "__requestFreeSpace":

			return new RemoteCommand(ReplyPrefix.FREE_SPACE_REPLY,
					encode(String.format("%.2f MBi\n", getFreeSpace("/"))), "null");

		case "__get_torrent_data_json":

			return new RemoteCommand(ReplyPrefix.TORRENT_DATA_REPLY,
					encode(new TransmissionDaemonComm(getTorrentsList()).getJSONData()), "null");

		case "__listTorrents":

			return new RemoteCommand(ReplyPrefix.TORRENTS_LIST.toString(), encode(getTorrentsList()), "null");

		case "__start_torrent":

			return new RemoteCommand(ReplyPrefix.TORRENT_STARTED.toString(),
					encode(startTorrent(incomingCommand.getBody())), "null");

		case "__stop_torrent":

			return new RemoteCommand(ReplyPrefix.TORRENT_STOPPED.toString(),
					encode(stopTorrent(incomingCommand.getBody())), "null");

		case "__remove_torrent":

			return new RemoteCommand(ReplyPrefix.TORRENT_REMOVED.toString(),
					encode(removeTorrent(incomingCommand.getBody())), "null");

		case "__delete_torrent":

			return new RemoteCommand(ReplyPrefix.TORRENT_REMOVED.toString(),
					encode(deleteTorrent(incomingCommand.getBody())), "null");

		case "__add_torrent":

			return new RemoteCommand(ReplyPrefix.TORRENT_ADDED.toString(),
					encode(addTorrent(incomingCommand.getBody())), "null");

		case "__beep":

			return null;

		case "__execute_command":

			return null;

		case "__get_homedir":

			String homeDirReply;

			try {

				homeDirReply = parseShellCommand("pwd");
				return new RemoteCommand(ReplyPrefix.HOMEDIR_RESPONSE, encode(homeDirReply), "null");

			} catch (IOException | InterruptedException e) {

				return null;

			}

		case "__get_directory_content":

			String directoryContentReply;

			try {

				directoryContentReply = GeneralUtilitiesLibrary
						.parseShellCommand(String.format("ls %s -al", incomingCommand.getBody()));
				return new RemoteCommand(ReplyPrefix.DIRECTORY_CONTENT_RESPONSE, encode(directoryContentReply), "null");

			} catch (IOException | InterruptedException e) {

				return null;

			}

		case "__get_camera_video_list":

			try {

				return new RemoteCommand(ReplyPrefix.VIDEO_DIRECTORY_CONTENT_RESPONSE, encode(parseShellCommand(
						String.format("ls %s -al", this.motionComm.getVideoFilesDirectory(incomingCommand.getBody())))),
						"null");

			} catch (IOException | InterruptedException e) {

				return null;

			}

		case "__get_file":

			uploadFileToStorage(incomingCommand.getBody(), incomingCommand.getReplyto(), false);

			return null;

		case "__wakeonlan":

			int deviceID = Integer.parseInt(incomingCommand.getBody());
			wakeDevice(deviceID);

			return null;

		case "__upload_file":

			uploadFileToDataSlots(incomingCommand.getBody(), incomingCommand.getReplyto());

			return null;

		case "__initialize_ssh":

			initializeSSHShell(incomingCommand.getReplyto());

			return null;

		case "__ssh_input_command":

			this.sshShells.get(incomingCommand.getReplyto())
					.insertInput(Base64.decodeBase64(incomingCommand.getBody()));

			return null;

		case "__ssh_special":

			switch (incomingCommand.getBody()) {

			case "keyBackspace":
				this.sshShells.get(incomingCommand.getReplyto()).keyBackspace();
				break;

			case "keyDelete":
				this.sshShells.get(incomingCommand.getReplyto()).keyDelete();
				break;

			case "keyUp":
				this.sshShells.get(incomingCommand.getReplyto()).keyUp();
				break;

			case "keyDown":
				this.sshShells.get(incomingCommand.getReplyto()).keyDown();
				break;

			case "keyRight":
				this.sshShells.get(incomingCommand.getReplyto()).keyRight();
				break;

			case "keyLeft":
				this.sshShells.get(incomingCommand.getReplyto()).keyLeft();
				break;

			}

			return null;

		case "__close_ssh":

			this.sshShells.get(incomingCommand.getReplyto()).disconnect();

			return null;

		case "__request_shot":

			this.motionComm.captureFrames(incomingCommand.getBody(), 1, "DBNode");

			return null;

		case "__request_shot_data":

			this.motionComm.captureFrames(incomingCommand.getBody(), 1, incomingCommand.getReplyto());

			return null;

		case "__request_shots":

			this.motionComm.captureFrames(incomingCommand.getBody(), 30, "DBNode");

			return null;

		case "__request_motion_event":

			this.motionComm.requestMotionEvent(incomingCommand.getBody(), 30);

			return null;

		case "__start_modet":

			this.motionComm.startModet(incomingCommand.getBody());

			return null;

		case "__stop_modet":

			this.motionComm.stopModet(incomingCommand.getBody());

			return null;

		case "__manage_vs_motion_event":

			processMotionEvent(incomingCommand.getBody());

			return null;

		case "__get_currenttimemillis":

			return new RemoteCommand(ReplyPrefix.REMOTE_CURRENT_TIME, encode("" + System.currentTimeMillis()),
					this.deviceName);

		case "__refresh_tmate":

			refreshTmate();
			return null;

		case "__start_streaming_request":

			if (this.youTubeComm != null && this.hasVideoSurveillance && this.motionComm != null) { // se è stata inizializzata l'interfaccia Youtube e si dispone della videosorveglianza, crea un canale di streaming

				// controlla che vi sia già una richiesta streaming in corso sul thread specificato.
				// Se non c'è nessuna richiesta, crea un nuovo streaming
				if (this.youTubeLiveStreamRequestors.containsValue(incomingCommand.getBody())) { // c'è già una richiesta in corso.

					// stampa un messaggio di log
					printLog(LogTopics.LOG_TOPIC_VSURV,
							"Live streaming already requested for camera ID: " + incomingCommand.getBody()
									+ ". Total active live streaming: " + this.youTubeLiveStreamRequestors.size());

				} else {

					// non c'è una richiesta in corso per il thread specificato.
					// registra la richiesta e avvia la creazione dello stream
					printLog(LogTopics.LOG_TOPIC_VSURV,
							String.format("Live streaming request registered for requestor:\"%s\", camera ID: \"%s\"",
									incomingCommand.getBody(), incomingCommand.getReplyto()));
					this.youTubeLiveStreamRequestors.put(incomingCommand.getReplyto(), incomingCommand.getBody());

					// aggiorna i dati relativi allo streaming
					setCameraLiveBroadcastStatus(incomingCommand.getBody(), "creating");
					setCameraLiveBroadcastData(incomingCommand.getBody(), "");

					String streamingTitle = String.format("Live from %s",
							this.motionComm.getCameraName(incomingCommand.getBody()));
					// crea un canale di streaming.
					// la risposta arriva sul callback implementato nel listener
					this.youTubeComm.createLiveStream(streamingTitle, incomingCommand.getReplyto(),
							incomingCommand.getBody());

				}

			}

			return null;

		case "__end_streaming_request":

			// rimuove il richiedente dalla mappa
			this.youTubeLiveStreamRequestors.remove(incomingCommand.getReplyto());

			// controlla se esiste ancora almeno un richiedente per il thread ID
			// specificato. se non esiste almeno un richiedente, termina lo streaming

			if (!this.youTubeLiveStreamRequestors.containsValue(incomingCommand.getBody())) {// non ci sono richiedenti per il thread ID specificato

				// stampa messaggio di log
				printLog(LogTopics.LOG_TOPIC_CMDEXEC, String.format(
						"No more requestors for camera ID %s. Streaming will terminate.", incomingCommand.getBody()));
				// ferma l'esecuzione dello streaming

				try {

					// ferma l'esecuzione in background dello script 'domotic-youtube_livestream'
					String command = "domotic-youtube_livestream_stop " + incomingCommand.getBody();

					printLog(LogTopics.LOG_TOPIC_SHEXEC, command);
					execShellCommand(command);

				} catch (IOException e) {

					exceptionLog_REDXTERM(this.getClass(), e);

				}

			} else {

				printLog(LogTopics.LOG_TOPIC_CMDEXEC,
						String.format("There are still requestors for camera ID %s. Streaming will continue.",
								incomingCommand.getBody()));

			}

			return null;

		case "__start_streaming_notification":

			// aggiorna il nodo del database Firebase con dati relativi allo
			// stato dello streaming
			setCameraLiveBroadcastStatus(incomingCommand.getBody(), "ready");

			return null;

		case "__end_streaming_notification":

			// aggiorna il nodo del database Firebase con dati relativi allo
			// stato dello streaming

			setCameraLiveBroadcastStatus(incomingCommand.getBody(), "idle");
			setCameraLiveBroadcastData(incomingCommand.getBody(), "");

			// rimuove tutti i richiedenti dalla mappa
			this.youTubeLiveStreamRequestors.values().remove(incomingCommand.getBody());

			// rimuove il live broadcast dalla mappa
			printLog(LogTopics.LOG_TOPIC_CMDEXEC, String.format("Removing Youtube live broadcast: \"%s\"",
					this.youTubeLiveBroadcasts.get(incomingCommand.getBody())));

			this.youTubeComm.deleteLiveBroadcast(this.youTubeLiveBroadcasts.get(incomingCommand.getBody()));

			return null;

		case "__connect_vpn":

			if (this.vpnConnectionConfigFilePath != null) {

				try {

					execShellCommand("domotic-connect_vpn");

				} catch (IOException e) {

					exceptionLog_REDXTERM(this.getClass(), e);

				}

			}

			return null;

		case "__disconnect_vpn":

			if (this.vpnConnectionConfigFilePath != null) {

				try {

					execShellCommand("domotic-disconnect_vpn");

				} catch (IOException e) {

					exceptionLog_REDXTERM(this.getClass(), e);

				}

			}

			return null;

		case "__request_log_on_db":

			return null;

		case "__update_status":

			switch (incomingCommand.getBody()) {

			case "general":
				updateDeviceStatus();
				break;

			case "network":
			case "vpn":
				updateNetworkStatus();
				break;

			case "static_data":
				return new RemoteCommand(ReplyPrefix.STATUS_TEXT_REPLY, encode(getStaticDeviceDataJSON()),
						this.deviceName);

			case "dynamic_data":
				return new RemoteCommand(ReplyPrefix.STATUS_TEXT_REPLY, encode(getStatusDeviceDataJSON()),
						this.deviceName);

			default:
				break;

			}

			return null;

		case "__provide_log":
			return new RemoteCommand(ReplyPrefix.LOG_REPLY,
					encode(GeneralUtilitiesLibrary.getFileAsBytes(LOGFILE_LOCATION)), this.deviceName);

			
		case "__get_firebase_cloud_queue_items":
			
			if (this.firebaseCloudUploader!=null){
			return new RemoteCommand(ReplyPrefix.LOG_REPLY,
					encode(this.firebaseCloudUploader.getUploadEngineQueue()), this.deviceName);
			} else {
				return new RemoteCommand(ReplyPrefix.LOG_REPLY,
						encode("No Firebase Cloud Uploader instance."), this.deviceName);
			
			}
			
		case "__notify_motion_event":
			if(this.telegramBotActive && this.motionComm!=null){
				
				// dispatch the message to all the user who subsribed the topic relevant to the frame update of this camera
				DomoticCore.this.sendToNotificationList(
						"Motion detected by " + this.motionComm.getCameraName(incomingCommand.getBody()) + " right now! The video of the event will be available shortly...",
						"motionupdates_camera_" + incomingCommand.getBody(), false, null, -1, 10000);
				
			}
			return null;
			
		default:

			return new RemoteCommand(ReplyPrefix.UNRECOGNIZED_COMMAND, "null", this.deviceName);

		} // <<<<< fine switch lettura comandi

	}

	/**
	 * Removes, if needed, a command from the queue in Firebase DB Node. Also,
	 * performs any operation, if needed, after command removal.
	 */
	private void removeCommand(String id, final int operationAfterRemoval) {

		if (id.startsWith(LOCAL_TCP_PREFIX)) {

			// Command is local (file://) or TCP (tcp://). local command has
			// already been deleted before the execution thread was started. TCP
			// command does not need to be removed.

			// if needed, performs an operation after the removal
			performOperationAfterRemoval(operationAfterRemoval);

		} else {

			// Command is remote, therefore it needs to be removed from the
			// Database

			// Remove the child from the <incomingCommands> Firebase node
			this.incomingCommands.child(id).removeValue(new CompletionListener() {

				@Override
				public void onComplete(DatabaseError error, DatabaseReference ref) {

					performOperationAfterRemoval(operationAfterRemoval);

				}

			});

		}

	}

	private void performOperationAfterRemoval(int operationId) {

		switch (operationId) {

		case REBOOT:
			// reboot the machine
			rebootMachine();
			break;

		case SHUTDOWN:
			// shutdown the machine
			shutDownMachine();
			break;

		}

	}

	private void replyToRemoteCommand(RemoteCommand rc, String commandID, HashMap<String, Object> params) {

		switch (rc.getHeader()) {
		// if header is "__shutdown" or "__reboot", remove the command before performing the operation

		case "__shutdown":
			printLog(LogTopics.LOG_TOPIC_MAIN, "Shutdown; Requested by: " + rc.getReplyto());
			removeCommand(commandID, SHUTDOWN);
			break;

		case "__reboot":
			printLog(LogTopics.LOG_TOPIC_MAIN, "Reboot; Requested by: " + rc.getReplyto());
			removeCommand(commandID, REBOOT);
			break;

		default:

			// recupera il messaggio di risposta
			RemoteCommand reply = getReply(rc);

			if (reply != null) { // a reply has to be sent

				// invia la risposta al dispositivo remoto
				sendMessageToDevice(reply, rc.getReplyto(), commandID, params);

			} else { // no reply has to be sent 

				// rimuove il comando immediatamente
				removeCommand(commandID, -1);

			}

		}

	}

	private void sendMessageToDevice(RemoteCommand message, String device, String idToRemove) {
		sendMessageToDevice(message, device, idToRemove, null);
	}

	private void sendMessageToDevice(final RemoteCommand message, final String device, final String idToRemove,
			HashMap<String, Object> params) {

		if (device.startsWith("tcp://")) {
			// reply to be sent over tcp interface

			// prepares and send the message
			String hostID = device.substring(6, device.length());
			byte[] data = (HEADER_REPLY + message.toString()).getBytes();
			this.tcpInterface.sendData(hostID, data);

			// prepares and prints a log message
			String sendStatus = "TCP OK.";
			printLog(LogTopics.LOG_TOPIC_OUTMSG, "to:\'" + device + "\'; hdr:\'" + message.getHeader() + "\'; bdy: "
					+ message.getBody().length() + "bytes; sts:\'" + sendStatus + "\'");

			// manages additional parameters, if any
			boolean disconnect = false;

			if (params != null) {
				if (params.containsKey("disconnect")) {
					disconnect = (boolean) params.get("disconnect");
				}
			}

			if (disconnect) {
				this.tcpInterface.closeConnection(hostID, false);
			}

		} else {

			// Reply to be sent over Firebase DB

			// ottiene una referenza al nodo del dispositivo che aveva
			// inviato il comando
			DatabaseReference databaseReference = FirebaseDatabase.getInstance()
					.getReference(getDatabaseNode(DatabaseNodes.INCOMING_COMMANDS)).child(device);

			// imposta il messaggio di risposta nel nodo, una volta completata l'operazione rimuove il comando
			databaseReference.child(getTimeStamp()).setValue(message, new CompletionListener() {

				@Override
				public void onComplete(DatabaseError error, DatabaseReference ref) {

					String sendStatus;
					if (error != null) {

						sendStatus = "ERROR [c=" + error.getCode() + ",m=" + error.getMessage() + "]";
						firebaseErrorLog_XTERM(error);

					} else {

						sendStatus = "OK";

					}

					// stampa un messaggio di log
					printLog(LogTopics.LOG_TOPIC_OUTMSG, "to:\'" + device + "\'; hdr:\'" + message.getHeader()
							+ "\'; bdy: " + message.getBody().length() + "bytes; sts:\'" + sendStatus + "\'");

					removeCommand(idToRemove, -1);

				}

			});

		}

	}

	private boolean connectToFirebaseApp() {

		FileInputStream serviceAccount = null;

		try {

			serviceAccount = new FileInputStream(this.jsonAuthFileLocation);
			FirebaseOptions options = new FirebaseOptions.Builder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.setDatabaseUrl(this.firebaseDatabaseURL).setStorageBucket(this.storageBucketAddress).build();

			FirebaseApp.initializeApp(options);
			serviceAccount.close();
			return true;

		} catch (IOException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
			return false;

		}

	}

	private void attachListeners() {

		this.incomingCommands.removeValue(new CompletionListener() {

			@Override
			public void onComplete(DatabaseError error, DatabaseReference ref) {

				if (error == null) {

					printLog(LogTopics.LOG_TOPIC_FIREBASE_DB, "Obsolete incoming message purged on Firebase node.");

					DomoticCore.this.incomingCommands
							.addChildEventListener(DomoticCore.this.incomingMessagesNodeListener);

					printLog(LogTopics.LOG_TOPIC_FIREBASE_DB,
							"Listener for incoming messages on Firebase node attached.");

				} else {

					firebaseErrorLog_XTERM(error);

				}

			}

		});

	}

	private void detachListeners() {

		this.incomingCommands.removeEventListener(this.incomingMessagesNodeListener);

		printLog(LogTopics.LOG_TOPIC_INCHK, "Listeners on Firebase nodes detached.");

	}

	private void retrieveServices() {

		printLog(LogTopics.LOG_TOPIC_INIT, "\'uptime\' check started.");

		if (this.allowDirectoryNavigation) {

			try {

				parseShellCommand("uptime");
				this.hasDirectoryNavigation = true;

			} catch (IOException | InterruptedException e) {

				this.hasDirectoryNavigation = false;

			}

		}

		if (this.allowTorrentManagement) {

			try {

				parseShellCommand("transmission-remote -n transmission:transmission -l");
				this.hasTorrentManagement = true;

			} catch (IOException | InterruptedException e) {

				this.hasTorrentManagement = false;

			}

		}

		if (this.allowVideoSurveillanceManagement) {

			this.hasVideoSurveillance = checkVideoSurveillance();

		}

	}

	private void uploadFileToStorage(String fileName, String requestor, boolean downloadOnRemoteDevice) {
		// TODO: implementare
	}

	private void notifyFileUpload(FileInCloudStorage file) {

		DatabaseReference cloudUploadedFiles = FirebaseDatabase.getInstance()
				.getReference("Users/lorenzofailla/CloudStorage");

		cloudUploadedFiles.child(file.getItemID()).setValueAsync(file);

	}

	/**
	 * 
	 * Performs the shutdown of the machine
	 *
	 */
	private void shutDownMachine() {

		try {

			execShellCommand("sudo shutdown -h now");

		} catch (IOException e) {

			printLog(LogTopics.LOG_TOPIC_EXCEPTION, e.getMessage());

		}

	}

	/**
	 * 
	 * Performs the reboot of the machine
	 *
	 */
	private void rebootMachine() {

		try {

			execShellCommand("sudo reboot");

		} catch (IOException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
		}

	}

	private boolean getConfiguration() {

		final List<String> wolDevList = new ArrayList<String>();
		final List<String> wolDevName = new ArrayList<String>();

		BufferedReader br;
		try {

			br = new BufferedReader(new FileReader(Defaults.CONFIG_FILE_LOCATION));
			int lineIndex = 0;
			String line;

			do {

				line = br.readLine();
				lineIndex += 1;

				if (line != null && line.length() > 0) {

					char firstChar = line.charAt(0);

					if ((firstChar == ';') || (firstChar == ' ') || (firstChar == '#')) {

						// the current line is not actionable or a comment, so
						// it will be skipped
						continue;
					}

					// divide la stringa in comando e argomento
					String[] lineProc = line.split("=");

					if (lineProc.length == 2) {

						String command = lineProc[0];
						String argument = lineProc[1];

						switch (command) {

						case "GoogleServicesGroupName":
							this.groupName = argument;
							break;

						case "FirebaseJSONKeyLocation":
							this.jsonAuthFileLocation = argument;
							break;

						case "FirebaseDBRootPath":
							this.firebaseDatabaseURL = argument;
							break;

						case "FirebaseStoragePath":
							this.storageBucketAddress = argument;
							break;

						case "FCMKey":
							this.notificationsEnabled = true;
							this.fcmServiceKey = argument;
							break;

						case "DeviceName":
							this.deviceName = argument;
							break;

						case "AllowDirectoryNavigation":
							this.hasDirectoryNavigation = argument.equals("yes");
							break;

						case "AllowTorrentManagement":
							this.allowTorrentManagement = argument.equals("yes");
							break;

						case "AllowVideoSurveillanceManagement":
							this.allowVideoSurveillanceManagement = argument.equals("yes");
							break;

						case "AllowSSH":
							this.allowSSH = argument.equals("yes");
							break;

						case "VideoSurveillanceServerAddress":
							this.videoSurveillanceServerAddress = argument;
							break;

						case "VideoSurveillanceServerControlPort":
							try {
								this.videoSurveillanceServerControlPort = Integer.parseInt(argument);
							} catch (NumberFormatException e) {
								this.videoSurveillanceServerControlPort = -1;
							}
							break;

						case "chmodDevice":
							try {

								parseShellCommand("sudo chmod 777 " + argument);
								printLog(LogTopics.LOG_TOPIC_INIT, "Successfully chmodded \'" + argument + "\'");

							} catch (InterruptedException e) {

								printLog(LogTopics.LOG_TOPIC_INIT,
										"Unable to chmod \'" + argument + "\': " + e.getMessage());
							}

							break;

						case "VideoSurveillanceDaemonAction":
							try {
								parseShellCommand(argument);
								printLog(LogTopics.LOG_TOPIC_INIT, "Successfully applied \'" + argument
										+ "\' command to VideoSurveillance daemon.");
							} catch (InterruptedException e) {

								printLog(LogTopics.LOG_TOPIC_INIT, "Warning! unable to apply \'" + argument
										+ "\' command to VideoSurveillance daemon: " + e.getMessage());
							}
							break;

						case "VideoSurveillanceDaemonShutdownCommand":

							this.videoSurveillanceDaemonShutdownCommand = argument;

							break;

						case "WOLDevice":
							String[] args = argument.split("_");

							if (args.length == 2) {
								wolDevName.add(args[0]);
								wolDevList.add(args[1]);
							} else {

								printLog(LogTopics.LOG_TOPIC_INIT,
										"Warning! Expected {Device Name}_{Device MAC Address} at line " + lineIndex
												+ ". Found: " + args.length + " splits. Skipping");
							}

							break;

						case "SSHUsername":
							this.sshUsername = argument;
							break;

						case "SSHPassword":
							this.sshPassword = argument;
							break;

						case "SSHHost":
							this.sshHost = argument;
							break;

						case "SSHPort":
							this.sshPort = Integer.parseInt(argument);
							break;

						case "YouTubeClientJSONLocation":
							this.youTubeJSONLocation = argument;
							break;

						case "YouTubeOAuthFolder":
							this.youTubeOAuthFolder = argument;
							break;

						case "VPNConfigFile":
							this.vpnConnectionConfigFilePath = argument;
							break;

						default:
							printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_INIT, "Unknown command \'" + command
									+ "\' at line \'" + lineIndex + "\'. Please check and try again.");
							br.close();
							return false;
						}

						printLogColor(LogUtilities.BGREENFG, LogTopics.LOG_TOPIC_INIT,
								String.format("Parameter \"%s\"; value=\"%s\"", command, argument));

					} else {

						// errore di sintassi
						printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_INIT, "Syntax error, please check line \'"
								+ lineIndex + "\' in configuration file and try again.");
						br.close();
						return false;

					}

				} else {
					//
					//

				}

			} while (line != null);

			br.close();
			boolean configurationComplete = true;

			if (this.groupName == "") {
				printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_INIT, "No group name specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.jsonAuthFileLocation == "") {
				printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_INIT,
						"No JSON auth file location specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.firebaseDatabaseURL == "") {
				printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_INIT,
						"No Firebase database location specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.storageBucketAddress == "") {
				printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_INIT,
						"No Firebase storage bucket address specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.allowVideoSurveillanceManagement && this.videoSurveillanceServerAddress == "") {
				printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_INIT,
						"No video surveillance server address specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.allowVideoSurveillanceManagement && this.videoSurveillanceServerControlPort == -1) {
				printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_INIT,
						"No video surveillance server control port specified. Cannot continue.");
				configurationComplete = false;

			}

			this.wolDevices = wolDevList.toArray(new String[0]);
			this.wolDeviceNames = wolDevName.toArray(new String[0]);

			return configurationComplete;

		} catch (IOException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
			printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_INIT,
					e.getMessage() + " Make sure configuration file exists and is readable at specified location: \'"
							+ Defaults.CONFIG_FILE_LOCATION + "\'");
			return false;

		}

	}

	private void registerDeviceServices() {

		Map<String, Object> deviceData = new HashMap<String, Object>();

		deviceData.put("Online", true);
		deviceData.put("DeviceName", this.deviceName);

		deviceData.put(STATIC_DATA_CHILDNODE, getStaticDeviceDataJSON());

		printLog(LogTopics.LOG_TOPIC_INIT, "Starting device static node data set on Firebase Database.");
		FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.DEVICE)).updateChildren(deviceData,
				new CompletionListener() {

					@Override
					public void onComplete(DatabaseError error, DatabaseReference ref) {
						printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_INIT, "Device static node data set.");
						DomoticCore.this.deviceRegistered = true;

						if (error != null) {
							firebaseErrorLog_XTERM(error);
						}

					}

				});

		if (this.hasVideoSurveillance) { // Device enabled for videosurveillance.

			if (!(this.youTubeJSONLocation.equals("") || this.youTubeOAuthFolder.equals(""))) { // inizializza un'istanza di YouTubeComm e assegna il listener

				printLog(LogTopics.LOG_TOPIC_INIT, "Checking Youtube credentials...");

				try {

					// initialize a new instance of YouTubeComm class
					this.youTubeComm = new YouTubeComm(APP_NAME, this.youTubeJSONLocation, this.youTubeOAuthFolder);

					// set the listener
					this.youTubeComm.setListener(this.youTubeCommListener);

					// print
					printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_INIT,
							"YouTube credentials successfully verified.");

				} catch (YouTubeNotAuthorizedException e) {

					exceptionLog_REDXTERM(YouTubeComm.class, e);

					printLogColor(LogUtilities.YELLOW, LogTopics.LOG_TOPIC_INIT,
							"Failed to verify Youtube credentials.");
					this.youTubeComm = null;
				}

			} else {

				this.youTubeComm = null;

				printLogColor(LogUtilities.YELLOW, LogTopics.LOG_TOPIC_INIT,
						"WARNING! Cannot Youtube credentials. Please make sure \"YouTubeJSONLocation\" and \"YouTubeOAuthFolder\" are specified in the configuration file.");
			}

			printLog(LogTopics.LOG_TOPIC_INIT, "Starting videocameras node data set on Firebase Database.");

			String[] threadIDs = this.motionComm.getThreadsIDs();
			int nOfThreads = threadIDs.length;

			for (int i = 0; i < nOfThreads; i++) {

				final String cameraID = threadIDs[i];

				printLog(LogTopics.LOG_TOPIC_INIT,
						String.format("Processing videocamera n.%d/%d; ID:\"%s\"", i + 1, nOfThreads, cameraID));

				HashMap<String, Object> cameraInfo = this.motionComm.getCameraInfo(cameraID);

				if (this.youTubeComm != null) {

					// inizializza l'HashMap contenente le informazioni dei
					// broadcast delle varie videocamera
					this.youTubeLiveBroadcasts = new HashMap<String, String>();
					this.youTubeLiveStreamRequestors = new HashMap<String, String>();

					// aggiorna il nodo di Firebase con lo stato del live
					// streaming broadcast
					cameraInfo.put("LiveStreamingBroadcastStatus", "idle");

				} else {

					// aggiorna il nodo di Firebase con lo stato del live
					// streaming broadcast
					cameraInfo.put("LiveStreamingBroadcastStatus", "not available");

				}

				// aggiorna il nodo di Firebase con lo stato del live streaming
				// broadcast
				cameraInfo.put("LiveStreamingBroadcastData", "");

				FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.VIDEOCAMERAS))
						.child(getVideoCameraFullID(cameraID)).setValue(cameraInfo, new CompletionListener() {

							@Override
							public void onComplete(DatabaseError error, DatabaseReference ref) {

								if (error == null) {
									DomoticCore.this.videoSurveillanceRegistered++;
									printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_INIT, String
											.format("Videocamera node ID %s set on Firebase Database.", cameraID));
								} else {
									firebaseErrorLog_XTERM(error);
								}

							}

						});

			}

			// Device registration sequence start
			printLog(LogTopics.LOG_TOPIC_INIT, "Device registration started.");
			this.deviceRegistered = false;

			int nOfVideoSurveillanceCameras;
			this.videoSurveillanceRegistered = 0;

			if (this.hasVideoSurveillance) {
				nOfVideoSurveillanceCameras = this.motionComm.getNOfThreads();
			} else {
				nOfVideoSurveillanceCameras = -1;
			}

			while (!this.tcpInitialized && !this.deviceRegistered
					&& (!this.hasVideoSurveillance || this.videoSurveillanceRegistered < nOfVideoSurveillanceCameras)
					&& !this.incomingMessagesCleared && !this.incomingFilesCleared) {

				printLog(LogTopics.LOG_TOPIC_INIT, "Waiting for registration to complete...");
				sleepSafe(1000);

			}

			printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_INIT, "Device registration successfully completed.");

		}

		// delete all previous incoming commands

		FirebaseDatabase.getInstance().getReference(

				getDatabaseNode(DatabaseNodes.COMMANDS_INCOMING_TOME)).removeValue(new CompletionListener() {

					@Override
					public void onComplete(DatabaseError error, DatabaseReference ref) {

						DomoticCore.this.incomingMessagesCleared = true;

						if (error != null) {
							firebaseErrorLog_XTERM(error);
						}

					}

				});

		// delete all previous incoming files

		FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.FILES_INCOMING_TOME))
				.removeValue(new CompletionListener() {

					@Override
					public void onComplete(DatabaseError error, DatabaseReference ref) {

						DomoticCore.this.incomingFilesCleared = true;

						if (error != null) {
							firebaseErrorLog_XTERM(error);
						}

					}

				});

	}

	private void unRegisterDeviceServices() {

		Map<String, Object> deviceData = new HashMap<String, Object>();

		deviceData.put("Online", false);

		FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.DEVICE)).updateChildren(deviceData,
				new CompletionListener() {

					@Override
					public void onComplete(DatabaseError error, DatabaseReference ref) {

						DomoticCore.this.deviceRegistered = false;
						if (error != null) {
							firebaseErrorLog_XTERM(error);
						}

					}

				});

		// rimuove i nodi delle videocamere associate a questo dispositivo

		FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.VIDEOCAMERAS))
				.orderByChild("OwnerDevice").equalTo(this.deviceName).getRef().removeValue(new CompletionListener() {

					@Override
					public void onComplete(DatabaseError error, DatabaseReference ref) {

						if (error != null) {

							firebaseErrorLog_XTERM(error);

						}

						DomoticCore.this.videoSurveillanceRegistered = 0;

					}

				});

	}

	private void uploadFileToDataSlots(final String fileName, final String deviceToReply) {

		InputStream inputStreamToUpload;
		File fileToUpload;

		DatabaseReference incomingFilesNode = FirebaseDatabase.getInstance()
				.getReference(String.format("Groups/%s/Devices/%s/IncomingFiles", this.groupName, deviceToReply));

		try {

			fileToUpload = new File(fileName);
			inputStreamToUpload = new FileInputStream(fileToUpload);

			byte[] bytes = new byte[65536];

			int read = 0;
			int bytesInLastSlot = 0;
			int slots = 0;

			Map<String, Object> slotData = new HashMap<String, Object>();

			while ((read = inputStreamToUpload.read(bytes)) != -1) {

				bytesInLastSlot = read;
				slots++;

				String dataString = Base64.encodeBase64String(bytes);

				slotData.put(slots + "", dataString);
				printLog(LogTopics.LOG_TOPIC_CMDEXEC,
						"Uploading file \'" + fileName + "' as dataslot. " + slots + " processed.");

			}

			inputStreamToUpload.close();

			Map<String, Object> fileData = new HashMap<String, Object>();
			fileData.put("name", fileToUpload.getName());
			fileData.put("slots", slots);
			fileData.put("bytesinlastslot", bytesInLastSlot);
			fileData.put("slotData", slotData);

			final String childTimeStamp = getTimeStamp();

			incomingFilesNode.child(childTimeStamp).updateChildren(fileData, new CompletionListener() {

				@Override
				public void onComplete(DatabaseError error, DatabaseReference ref) {

					if (error != null) {
						firebaseErrorLog_XTERM(error);
						printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_CMDEXEC,
								"Upload of file \'" + fileName + "' as dataslot terminated with error. Error code: \'"
										+ error.getCode() + "\', error message: \'" + error.getMessage() + "\'");

					} else {

						sendMessageToDevice(
								new RemoteCommand(ReplyPrefix.FILE_READY_FOR_DOWNLOAD, childTimeStamp, null),
								deviceToReply, null);

						printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_CMDEXEC,
								"Upload of file \'" + fileName + "' as dataslot successfully terminated.");

					}

				}

			});

		} catch (IOException e) {

			exceptionLog_REDXTERM(this.getClass(), e);

		}

	}

	// SSH Shell related methods and functions

	private void initializeSSHShell(final String remoteDev) {

		printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Initializing secure shell session with device \'" + remoteDev + "\'");

		this.sshShell = new SSHShell(this.sshHost, this.sshUsername, this.sshPassword, this.sshPort);
		this.sshShell.setListener(new SSHShellListener() {

			@Override
			public void onConnected() {
				printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_CMDEXEC,
						"Successfully connected secure shell session with device \'" + remoteDev + "\'");

				sendMessageToDevice(new RemoteCommand(ReplyPrefix.SSH_SHELL_READY, remoteDev, "null"), remoteDev, null);

			}

			@Override
			public void onDisconnected() {
				sendMessageToDevice(new RemoteCommand(ReplyPrefix.SSH_SHELL_CLOSED, remoteDev, "null"), remoteDev,
						null);
				removeShell(remoteDev);
				DomoticCore.this.sshShell = null;

			}

			@Override
			public void onCreated() {
				printLog(LogTopics.LOG_TOPIC_CMDEXEC,
						"Successfully created secure shell session with device \'" + remoteDev + "\'");
				DomoticCore.this.sshShell.connect();

			}

			@Override
			public void onError(Exception e) {

				exceptionLog_REDXTERM(this.getClass(), e);

			}

			@Override
			public void onOutputDataChanged(byte[] data) {
				FirebaseDatabase.getInstance()
						.getReference(String.format("Groups/%s/Devices/%s/SSHShells/%s/OutputData",
								DomoticCore.this.groupName, DomoticCore.this.deviceName, remoteDev))
						.child("" + System.currentTimeMillis()).setValueAsync(Base64.encodeBase64String(data));

			}

		});

		this.sshShell.initialize();
		this.sshShells.put(remoteDev, this.sshShell);

	}

	private void removeShell(String shellId) {

		this.sshShells.remove(shellId);

		FirebaseDatabase.getInstance()
				.getReference(
						String.format("/Groups/%s/Devices/%s/SSHShells/%s", this.groupName, this.deviceName, shellId))
				.removeValue(new CompletionListener() {

					@Override
					public void onComplete(DatabaseError error, DatabaseReference ref) {
						// SSH shell information has been successfully
						// removed
						// from Firebase Database

					}

				});

	}

	private boolean checkVideoSurveillance() {

		if (!MotionComm.isMotionInstalled()) {

			printLogColor(LogUtilities.YELLOW, LogTopics.LOG_TOPIC_VSURV,
					"\'motion\' is not installed on this host.You may install \'motion\' by typing \'sudo apt-get install motion\'Videosurveillance features are disabled on this host.");
			return false;
		}

		this.motionComm = new MotionComm(this.videoSurveillanceServerAddress, this.deviceName,
				this.videoSurveillanceServerControlPort);
		this.motionComm.setDebugMode(this.debugMode);

		this.motionComm.setListener(this.motionCommListener);

		if (this.motionComm.isHTMLOutputEnabled()) {

			printLogColor(LogUtilities.YELLOW, LogTopics.LOG_TOPIC_VSURV,
					"\'motion\' is installed on this host, but output in HTML format is enabled. For the domotic-motion interface to run properly, motion output has to be in plain format. You may enable the plain output by setting \'webcontrol_html_output off\' in your motion configuration file.\nVideosurveillance features are disabled on this host.");
			this.motionComm.setListener(null);
			this.motionComm = null;
			return false;

		}

		int nOfThreads = this.motionComm.getNOfThreads();

		if (nOfThreads < 1) {

			printLogColor(LogUtilities.YELLOW, LogTopics.LOG_TOPIC_VSURV, "No active threads found on motion daemon.");
			this.motionComm.setListener(null);
			this.motionComm = null;
			return false;

		} else {

			printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_VSURV,
					String.format("%d active threads found on motion daemon.", nOfThreads));
			return true;

		}

	}

	/**
	 * Called by a local command generated by motion daemon.
	 * 
	 * Uploads a video file of the motion event, as described in the params
	 * Metadata, into the proper position in the Firebase Cloud Storage. When
	 * the upload is completed, a data entry is generated into the Events node
	 * of the Firebase Database, and a real time notification is sent to the
	 * group topic, so that all devices may see it.
	 * 
	 */
	private void processMotionEvent(String params) {

		HashMap<String, Object> paramsMap = parseJSON(params);

		if (!paramsMap.containsKey("file_path") || !paramsMap.containsKey("thread_id")) {

			return;

		}

		//
		final String localVideoFilePath = paramsMap.get("file_path").toString();
		final String localImageFilePath = getEventJpegFileName(localVideoFilePath);

		String localVideoFileName = getShortName(localVideoFilePath);
		String localImageFileName = getShortName(localImageFilePath);

		final String videoCameraID = paramsMap.get("thread_id").toString();
		final String videoCameraFullID = getVideoCameraFullID(videoCameraID);

		final String eventVideoID = videoCameraFullID + "_" + localVideoFileName;
		final String eventThumbnailID = videoCameraFullID + "_" + localImageFileName;

		final String cameraName = DomoticCore.this.motionComm.getCameraName(videoCameraID);

		// set the position in the Firebase Cloud Storage
		String remoteVideoPosition = MOTION_EVENTS_STORAGE_CLOUD_URL.replaceAll("%%group_name&&", this.groupName)
				.replaceAll("%%file_name&&", eventVideoID);

		// set the position in the Firebase Cloud Storage
		String remoteImagePosition = MOTION_EVENTS_STORAGE_CLOUD_URL.replaceAll("%%group_name&&", this.groupName)
				.replaceAll("%%file_name&&", eventThumbnailID);

		// print log entry
		printLog(LogTopics.LOG_TOPIC_VSURV,
				String.format("Starting upload of video file for motion event on bucket: \"%s\"", remoteVideoPosition));

		// start the upload operation of the video
		this.firebaseCloudUploader.addToQueue(localVideoFilePath, remoteVideoPosition,
				new FirebaseUploadItemListener() {

					@Override
					public void onError(Exception e) {

						exceptionLog_REDXTERM(this.getClass(), e);

					}

					@Override
					public void onComplete(Blob info, String uploadID) {

						// upload of the video file completed

						// print log entry
						printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_VSURV,
								"Video file for motion event successfully uploaded (" + info.getSize() + " bytes).");

						// generate the HashMap to be put into the Firebase Database Events Node
						HashMap<String, Object> eventData = new HashMap<String, Object>();
						eventData.put("Timestamp", "" + System.currentTimeMillis()); // device id + thread id
						eventData.put("CameraFullID", videoCameraFullID); // device id + thread id
						eventData.put("Date", MotionComm.getDateFromMotionFileName(localVideoFilePath)); // event date
						eventData.put("Time", MotionComm.getTimeFromMotionFileName(localVideoFilePath)); // event time
						eventData.put("ThreadID", videoCameraID); // thread id
						eventData.put("CameraName", cameraName); // camera name
						eventData.put("VideoID", eventVideoID);// short file name of the video file of the event
						eventData.put("Device", DomoticCore.this.deviceName); // device id
						eventData.put("ThumbnailID", eventThumbnailID); // compressed data, string encoded, of the jpeg of the event

						eventData.put("NewItem", "true"); // boolean, string encoded
						eventData.put("LockedItem", "false"); // boolean, string encoded

						// write the HashMap in the position of the Events Node in the Firebase Database

						FirebaseDatabase.getInstance().getReference(getDatabaseNode(DatabaseNodes.MOTION_EVENTS))
								.child(eventVideoID.replaceAll("[.]", "_"))
								.setValue(eventData, new CompletionListener() {

									@Override
									public void onComplete(DatabaseError error, DatabaseReference ref) {

										// the Events Node in the Firebase Database has been successfully updated

										if (error == null) {

											// send a notification via Firebase Cloud Messaging service

											// set up the message data payload

											String notificationID;

											if (DomoticCore.this.notificationsEnabled) {

												JSONObject data = new JSONObject(); // payload
												data.put("eventID", eventVideoID);

												notificationID = sendFCM(DomoticCore.this.fcmServiceKey,
														"/topics/" + DomoticCore.this.groupName, "Motion detected",
														cameraName, data.toString());

											} else {

												notificationID = "N/A";

											}

											printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_VSURV,
													"Data for motion event successfully uploaded. Notification sent - "
															+ notificationID);

										} else {

											printLogColor(LogUtilities.RED, LogTopics.LOG_TOPIC_VSURV, String.format(
													"Error during motion event data upload: %s", error.getMessage()));

										}

									}

								});
						
						// set up the FirebaseCloudUploader and start the upload operation of the thumbnail image
						DomoticCore.this.firebaseCloudUploader.addToQueue(localImageFilePath, remoteImagePosition,
								new FirebaseUploadItemListener() {

									@Override
									public void onError(Exception e) {
										exceptionLog_REDXTERM(this.getClass(), e);

									}

									@Override
									public void onComplete(Blob info, String uploadID) {
										
										// print log entry
										printLogColor(LogUtilities.GREEN, LogTopics.LOG_TOPIC_VSURV,
												"Thumbnail file for motion event successfully uploaded (" + info.getSize()
														+ " bytes).");
										
										// deletes the local files
										new File(localImageFilePath).delete();
										new File(localVideoFilePath).delete();

									}

								});

						// manage the Telegram Bot notifications

						if (DomoticCore.this.telegramBotActive) { // the Telegram Bot is up

							// obtain a signed URL to easily download the video
							URL url = info.signUrl(5, TimeUnit.DAYS);

							JSONObject keyboard = new JSONObject();

							JSONObject line = new JSONObject();
							JSONObject button = new JSONObject()
									.put("action", "subscription_removal___motionupdates_camera_" + videoCameraID)
									.put("label", "Stop notifications.");
							line.append("buttons", button);
							keyboard.append("lines", line);

							// dispatch the message to all the user who subsribed the topic relevant to the frame update of this camera
							DomoticCore.this.sendToNotificationList(
									"<a href=\"" + url + "\">Motion detected</a> by <b>" + cameraName + "</b> at "
											+ getTimeStamp("HH.mm") + ".",
									"motionupdates_camera_" + videoCameraID, false, keyboard, 300000, -1);

						}

					}

				});	

	}

	private void refreshTmate() {

		//calls /usr/local/bin/refresh-tmate script, to refresh the tmate session

		try {

			parseShellCommand("/usr/local/bin/refresh-tmate");
			parseShellCommand("/usr/local/bin/tmate-addr");

			// print log
			printLog(LogTopics.LOG_TOPIC_CMDEXEC, String.format("tmate session refreshed"));

		} catch (IOException e) {

			// print log
			exceptionLog_REDXTERM(this.getClass(), e);

		} catch (InterruptedException e) {

			// print log
			exceptionLog_REDXTERM(this.getClass(), e);

		}

	}

	private String getVPNIPAddress() {

		try {

			return parseShellCommand("domotic-show_vpn_ip").replaceAll("\n", "").trim();

		} catch (IOException | InterruptedException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
			return "Error";

		}

	}

	// --------------------------------------------------------------------------------------------
	// @SECTION: Device status output
	// --------------------------------------------------------------------------------------------
	private String getStaticDeviceDataJSON() {

		JSONObject result = new JSONObject();

		JSONObject enabledInterfaces = new JSONObject();
		JSONObject wakeOnLanDevices = new JSONObject();
		JSONObject videoSurveillance = new JSONObject();

		enabledInterfaces.put("DirectoryNavigation", this.hasDirectoryNavigation);
		enabledInterfaces.put("TorrentManagement", this.hasTorrentManagement);
		enabledInterfaces.put("VideoSurveillanceManagement", this.hasVideoSurveillance);
		enabledInterfaces.put("WakeOnLanManagement", (this.wolDevices.length > 0));

		for (int i = 0; i < this.wolDevices.length; i++) {

			JSONObject wolDeviceData = new JSONObject();
			wolDeviceData.put("ID", i);
			wolDeviceData.put("Name", this.wolDeviceNames[i]);
			wolDeviceData.put("Address", this.wolDevices[i]);

			wakeOnLanDevices.append("Devices", wolDeviceData);

		}

		if (this.hasVideoSurveillance) {

			String[] IDs = this.motionComm.getThreadsIDs();

			for (String id : IDs) {

				JSONObject cameraData = new JSONObject();
				cameraData.put("ID", id);
				cameraData.put("Name", this.motionComm.getCameraName(id));
				cameraData.put("FullID", this.getVideoCameraFullID(id));

				videoSurveillance.append("Cameras", cameraData);

			}

		}

		result.put("DeviceName", this.deviceName);

		result.put("EnabledInterfaces", enabledInterfaces);
		result.put("WakeOnLan", wakeOnLanDevices);
		result.put("VideoSurveillance", videoSurveillance);

		return result.toString();

	}

	private String getNetworkDeviceDataJSON() {

		JSONObject result = new JSONObject();

		JSONObject networkStatus = new JSONObject();

		networkStatus.put("PublicIP", getPublicIPAddresses());
		networkStatus.put("LocalIP", getLocalIPAddresses());
		networkStatus.put("VPN", getVPNIPAddress());

		result.put("NetworkStatus", networkStatus);

		return result.toString();

	}

	private String getStatusDeviceDataJSON() {

		JSONObject result = new JSONObject();

		JSONObject generalStatus = new JSONObject();

		generalStatus.put("Uptime", getUptime());
		generalStatus.put("FreeSpace", getFreeSpace("/"));
		generalStatus.put("TotalSpace", getTotalSpace("/"));
		generalStatus.put("RunningSince", this.runningSince);
		generalStatus.put("LastUpdate", System.currentTimeMillis());

		result.put("GeneralStatus", generalStatus);

		if(this.firebaseCloudUploader!=null) {

			result.put("CloudUploaderEngine", this.firebaseCloudUploader.getUploadEngineStatus());

		}

		return result.toString();

	}
	
	private String getStatusMsg(){
		
		StringBuilder result=new StringBuilder();
		
		result.append("General\n");
		result.append(String.format(" Uptime: %s\n", getUptime()));
		result.append(String.format(" Mass storage: %d/%d MiB (%0.1f %% free)\n",getTotalSpace("/")-getFreeSpace("/"), getTotalSpace("/"), getFreeSpace("/")/getTotalSpace("/")*100.0));
		result.append(String.format(" Running since: %s", this.runningSince));
		
		result.append("Network\n");
		result.append(String.format(" Public IP: %s\n",  getPublicIPAddresses()));
		result.append(String.format(" Local IP: %s\n",  getLocalIPAddresses()));
		
		return result.toString();
		
		
	}

}