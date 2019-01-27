/**
 * Copyright 2018 Lorenzo Failla
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

package apps.java.loref;

import static apps.java.loref.FCMUtilities.sendFCM;
import static apps.java.loref.GeneralUtilitiesLibrary.compress;
import static apps.java.loref.GeneralUtilitiesLibrary.execShellCommand;
import static apps.java.loref.GeneralUtilitiesLibrary.getFileAsBytes;
import static apps.java.loref.GeneralUtilitiesLibrary.getTimeStamp;
import static apps.java.loref.GeneralUtilitiesLibrary.parseLocalCommand;
import static apps.java.loref.GeneralUtilitiesLibrary.parseJSON;
import static apps.java.loref.GeneralUtilitiesLibrary.parseShellCommand;
import static apps.java.loref.GeneralUtilitiesLibrary.printLog;
import static apps.java.loref.GeneralUtilitiesLibrary.readPlainTextFromFile;
import static apps.java.loref.GeneralUtilitiesLibrary.readLinesFromFile;
import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;
import static apps.java.loref.GeneralUtilitiesLibrary.encode;

import static apps.java.loref.MotionComm.getEventJpegFileName;

import static apps.java.loref.LinuxCommands.*;
import static apps.java.loref.TransmissionDaemonCommands.*;

import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;
import static apps.java.loref.LogUtilities.firebaseErrorLog_XTERM;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.google.api.client.util.Base64;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.protobuf.GeneratedMessageV3;

import apps.java.loref.FirebaseCloudUploader.FirebaseCloudUploaderListener;
import apps.java.loref.SSHShell.SSHShellListener;

import static apps.java.loref.DefaultConfigValues.*;

@SuppressWarnings({ "javadoc", "unused" })

public class DomoticCore {

	// Constant values

	private String firebaseDatabaseURL;
	private String jsonAuthFileLocation;
	private String thisDevice;
	private String groupName;
	private String storageBucketAddress;
	private SignUrlOption signOption;

	private boolean loopFlag = true;
	private boolean exit = false;
	private final long runningSince = System.currentTimeMillis();

	private boolean notificationsEnabled = false;
	private String fcmServiceKey = "";

	private boolean firebaseServicesEnabled = false;

	/**
	 * TODO Put here a description of what this method does.
	 *
	 * @param value
	 */
	private void setFirebaseServicesEnabled(boolean value) {

		this.firebaseServicesEnabled = value;

		if (this.firebaseServicesEnabled) {

			printLog(LogTopics.LOG_TOPIC_FIREBASE_DB, "Firebase services available.");

			// defines the database nodes
			this.incomingCommands = FirebaseDatabase.getInstance().getReference(String.format("/%s/%s/%s/%s/%s",
					GROUP_NODE, this.groupName, DEVICES_NODE, this.thisDevice, INCOMING_COMMANDS_NODE));

			// registers the device
			registerDeviceServices();

			// attiva i timer per i task periodici

			this.deviceStatusUpdateTimer = new Timer();
			this.deviceStatusUpdateTimer.scheduleAtFixedRate(new DeviceStatusUpdateTask(), 0,
					this.deviceStatusUpdateRate);

			this.deviceNetworkStatusUpdateTimer = new Timer();
			this.deviceNetworkStatusUpdateTimer.schedule(new DeviceNetworkStatusUpdateTask(), 0,
					this.deviceNetworkStatusUpdateRate);

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

	/*
	 * Periodical update of device GENERAL STATUS
	 */

	private boolean notifyUpdateTimeout = true;
	private Timer deviceStatusUpdateTimer;
	private long deviceStatusUpdateRate = DefaultConfigValues.DEVICE_STATUS_UPDATE_RATE;

	private class DeviceStatusUpdateTask extends TimerTask {

		@Override
		public void run() {

			updateDeviceStatus();

		}

	}

	private long firebaseDBUpdateTimeOut = DefaultConfigValues.FIREBASE_DB_UPDATE_TIMEOUT;
	private Timer firebaseDBUpdateTimeoutTimer = null;

	private class FirebaseDBUpdateTimeoutTask extends TimerTask {

		@Override
		public void run() {

			/*
			 * manage a timeout issue when trying to update the device status
			 * over the Firebase DB node.
			 */

			// print a log message
			printLog(LogTopics.LOG_TOPIC_ERROR, "Timeout exceeded during device status update on Firebase DB node.");

			detachListeners();
			printLog(LogTopics.LOG_TOPIC_INET_OUT, "Firebase listeners detached.");

			// cancella i timer per i task periodici
			DomoticCore.this.deviceStatusUpdateTimer.cancel();
			DomoticCore.this.deviceNetworkStatusUpdateTimer.cancel();

			printLog(LogTopics.LOG_TOPIC_INET_OUT, "Periodical update tasks cancelled.");

			int nOfApps = FirebaseApp.getApps().size();

			for (int i = 0; i < nOfApps; i++) {

				String appName = FirebaseApp.getApps().get(i).getName();
				FirebaseApp.getApps().get(i).delete();
				printLog(LogTopics.LOG_TOPIC_INET_OUT, "Firebase app: " + appName + " deleted.");

			}

			// start the internet connectivity check loop
			DomoticCore.this.internetConnectionCheck.start();

		}

	}

	private void updateDeviceStatus() {

		//		String uptimeReply = getUptime();
		//		double freeSpaceReply = getFreeSpace("/");
		//		double totalSpaceReply = getTotalSpace("/");
		//
		//		HashMap<String, Object> deviceStatusData = new HashMap<String, Object>();
		//		deviceStatusData.put("Uptime", uptimeReply);
		//		deviceStatusData.put("FreeSpace", freeSpaceReply);
		//		deviceStatusData.put("TotalSpace", totalSpaceReply);
		//		deviceStatusData.put("RunningSince", this.runningSince);
		//		deviceStatusData.put("LastUpdate", System.currentTimeMillis());

		String deviceStatusDBNodePath = GROUP_NODE + "/" + this.groupName + "/" + DEVICES_NODE + "/" + this.thisDevice;

		DatabaseReference deviceStatusDBRef = FirebaseDatabase.getInstance().getReference(deviceStatusDBNodePath);
		deviceStatusDBRef.child(STATUS_DATA_NODE).setValue(getStatusDeviceDataJSON(), new CompletionListener() {

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

	/*
	 * Periodical update of device NETWORK STATUS
	 */
	private long deviceNetworkStatusUpdateRate = DefaultConfigValues.DEVICE_NETWORK_STATUS_UPDATE_RATE;

	private Timer deviceNetworkStatusUpdateTimer;

	private class DeviceNetworkStatusUpdateTask extends TimerTask {

		@Override
		public void run() {

			updateNetworkStatus();

		}

	}

	private void updateNetworkStatus() {

		String refNode = GROUP_NODE + "/" + this.groupName + "/" + DEVICES_NODE + "/" + this.thisDevice + "/"
				+ NETWORK_DATA_NODE;
		FirebaseDatabase.getInstance().getReference(refNode).setValueAsync(getNetworkDeviceDataJSON());

	}

	private void updateTMateStatus() {

		HashMap<String, Object> tMateStatus = new HashMap<>();
		tMateStatus.put("SSHAddress", "later");
		tMateStatus.put("WebAddress", "later");

		String refNode = GROUP_NODE + "/" + this.groupName + "/" + DEVICES_NODE + "/" + this.thisDevice + "/"
				+ TMATE_DATA_NODE;
		FirebaseDatabase.getInstance().getReference(refNode).setValueAsync(tMateStatus);

	}

	/*
	 * VPN connection
	 */

	private String vpnConnectionConfigFilePath = null;

	/*
	 * TCP interface
	 */

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
		public void onCommand(String hostID, RemoteCommand command, HashMap<String, Object> params) {

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

	/*
	 * Internet connectivity loop check
	 */

	private String internetConnectionCheckServer = DefaultConfigValues.CONNECTIVITY_TEST_SERVER_ADDRESS;
	private long internetConnectionCheckRate = DefaultConfigValues.CONNECTIVITY_TEST_RATE;
	private InternetConnectionCheck internetConnectionCheck;
	private InternetConnectionStatusListener internetConnectionStatusListener = new InternetConnectionStatusListener() {

		@Override
		public void onConnectionRestored(long inactivityTime) {

			printLog(LogTopics.LOG_TOPIC_INET_IN,
					"Internet connectivity available after " + inactivityTime / 1000 + "secs.");

			// stops the internet connectivity check loop
			DomoticCore.this.internetConnectionCheck.stop();

			setFirebaseServicesEnabled(connectToFirebaseApp());

		}

		@Override
		public void onConnectionLost() {

			printLog(LogTopics.LOG_TOPIC_INET_OUT, "Internet connectivity not available.");

		}

	};

	/*
	 * Firebase database incoming message management
	 */

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

			// retrieve the incoming message in the form of a new RemoteCommand
			// instance

			RemoteCommand remoteCommand = snapshot.getValue(RemoteCommand.class);

			// waits for a TICK, this is needed in order to avoid possible
			// duplicate
			// timestamps

			sleepSafe(10);

			// performs the needed operations according to the content of the
			// incoming
			// message
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

	/*
	 * Firebase log
	 */

	private void firebaseLog(LogEntry log) {
		FirebaseDatabase.getInstance()
				.getReference(GROUP_NODE + "/" + this.groupName + "/" + LOGS_NODE + "/" + this.thisDevice)
				.child(getTimeStamp()).setValueAsync(log);
	}

	/*
	 * Device registration
	 */
	private boolean deviceRegistered;
	private int videoSurveillanceRegistered;
	private boolean incomingMessagesCleared;
	private boolean incomingFilesCleared;
	private DatabaseReference incomingCommands;

	/*
	 * WakeOnLan
	 */
	private String[] wolDevices;
	private String[] wolDeviceNames;

	/*
	 * SSH Shell
	 */
	private HashMap<String, SSHShell> sshShells = new HashMap<String, SSHShell>();
	private SSHShell sshShell;
	private String sshUsername;
	private String sshPassword;
	private String sshHost;
	private int sshPort;

	/*
	 * Videosurveillance
	 */
	private MotionComm motionComm;
	private String videoSurveillanceServerAddress = "";
	private int videoSurveillanceServerControlPort = -1;
	private String videoSurveillanceDaemonShutdownCommand = "";
	private List<String> frameUploadReady = new ArrayList<String>();

	private String youTubeJSONLocation = "";
	private String youTubeOAuthFolder = "";

	private final static String MOTION_EVENTS_STORAGE_CLOUD_URL = "Groups/%%group_name&&/Devices/%%device_name&&/VideoSurveillance/Events/%%thread_id&&/";
	private final static String MOTION_EVENTS_DB_NODE = "Groups/%%group_name&&/VideoSurveillance/Events";
	private final static String AVAILABLE_CAMERAS_DB_NODE = "Groups/%%group_name&&/VideoSurveillance/AvailableCameras";

	private final static int DEFAULT_MOTION_VIDEO_LINK_TIMEOUT = 5; // expiration
	// timeout
	// for the
	// download
	// link of a
	// motion
	// video
	// file
	// [days]

	private HashMap<String, String> youTubeLiveBroadcasts; // registra i
	// LiveBroadcast ID
	// delle varie
	// videocamere
	private HashMap<String, String> youTubeLiveStreamRequestors; // registra i
	// dispositivi
	// che
	// richiedono
	// un live
	// streaming

	private MotionCommListener motionCommListener = new MotionCommListener() {

		@Override
		public void onNewFrame(String cameraID, byte[] frameImageData, String destination) {

			printLog(LogTopics.LOG_TOPIC_VSURV,
					String.format("Frame image received. Camera ID: %s; Image bytes: %d; Destination: %s", cameraID,
							frameImageData.length, destination));

			if (destination.startsWith("tcp://")) {
				/*
				 * sends the received camera data as a TCP REPLY
				 */

				RemoteCommand remoteCommand = new RemoteCommand(ReplyPrefix.FRAME_IMAGE_DATA, encode(
						String.format("%s|data=%s", cameraID, Base64.encodeBase64String(compress(frameImageData)))),
						"null");

				sendMessageToDevice(remoteCommand, destination, "");

			}

			/*
			 * by default, stores the the received camera data into the relevant
			 * Firebase Database node.
			 */

			if (!DomoticCore.this.frameUploadReady.contains(cameraID)) {

				HashMap<String, Object> frameData = new HashMap<String, Object>();

				frameData.put("ImgData", Base64.encodeBase64String(compress(frameImageData)));

				frameData.put("Date", "-");
				frameData.put("Time", "-");

				DomoticCore.this.frameUploadReady.add(cameraID);

				DatabaseReference ref = FirebaseDatabase.getInstance()
						.getReference(String.format("Groups/%s/VideoSurveillance/AvailableCameras/%s-%s",
								DomoticCore.this.groupName, DomoticCore.this.thisDevice, cameraID));

				ref.child("LastShotData").setValue(frameData, new CompletionListener() {

					@Override
					public void onComplete(DatabaseError error, DatabaseReference ref) {

						if (error == null) {
							printLog(LogTopics.LOG_TOPIC_VSURV,
									String.format("Frame successfully uploaded for camera id:%s.", cameraID));
						} else {
							printLog(LogTopics.LOG_TOPIC_ERROR,
									String.format("Error found while uploading for camera id:%s. Error message:\"%s\".",
											cameraID, error.getMessage()));
						}

						DomoticCore.this.frameUploadReady.remove(cameraID);

					}

				});

			}

		}

		@Override
		public void statusChanged(String cameraID) {

			DatabaseReference ref = FirebaseDatabase.getInstance()
					.getReference(String.format("Groups/%s/VideoSurveillance/AvailableCameras/%s-%s",
							DomoticCore.this.groupName, DomoticCore.this.thisDevice, cameraID));
			ref.child("MoDetStatus").setValueAsync(DomoticCore.this.motionComm.getThreadMoDetStatus(cameraID));

		}

	};

	private YouTubeComm youTubeComm = null;

	private YouTubeCommListener youTubeCommListener = new YouTubeCommListener() {

		@Override
		public void onLiveStreamDeleted(String broadcastID) {
			// no action foreseen

		}

		@Override
		public void onLiveStreamCreated(String requestorID, String requestID, String liveStreamID,
				String liveBroadcastID) {
			/*
			 * A live stream has been created
			 */

			printLog(LogTopics.LOG_TOPIC_VSURV,
					String.format("Youtube live stream created for camera ID: %s, Live stream ID: %s, Broadcast ID: %s",
							requestID, liveStreamID, liveBroadcastID));

			String inputStreamURL = DomoticCore.this.motionComm.getStreamFullURL(requestID);
			String shellCommand[] = new String[] { "/bin/sh", "-c",
					String.format("yt-stream %s %d %s %s %s", inputStreamURL,
							DomoticCore.this.motionComm.getCameraStreamFPS(requestID), "05:00", liveStreamID,
							requestID) };

			try {

				// printLog(LOG_TOPIC_SHEXEC, shellCommand);

				// lancia il comando bash per avviare lo streaming
				Runtime.getRuntime().exec(shellCommand);

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
		public void onLiveBroadCastDeleted(String broadcastID) {
			// no action foreseen

		}

		@Override
		public void onLiveStreamNotCreated(String requestorID, String requestID) {

			// aggiorna i dati relativi allo streaming
			setCameraLiveBroadcastStatus(requestID, "idle");
			DomoticCore.this.youTubeLiveStreamRequestors.remove(requestorID);

		}

	};

	private void setCameraLiveBroadcastData(String cameraID, String broadcastData) {
		String cameraNode = AVAILABLE_CAMERAS_DB_NODE.replace("%%group_name&&", this.groupName);
		String cameraNodeChildKey = this.thisDevice + "-" + cameraID;

		FirebaseDatabase.getInstance().getReference(cameraNode).child(cameraNodeChildKey)
				.child("LiveStreamingBroadcastData").setValueAsync(broadcastData);

	}

	private void setCameraLiveBroadcastStatus(String cameraID, String broadcastStatus) {
		String cameraNode = AVAILABLE_CAMERAS_DB_NODE.replace("%%group_name&&", this.groupName);
		String cameraNodeChildKey = this.thisDevice + "-" + cameraID;

		FirebaseDatabase.getInstance().getReference(cameraNode).child(cameraNodeChildKey)
				.child("LiveStreamingBroadcastStatus").setValueAsync(broadcastStatus);

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
		System.out.println(GeneralUtilitiesLibrary.getTimeStamp("ddd dd/MMM/YYYY - hh.mm.ss"));
		System.out.println("--------------------------------------------------------------------------------");
		System.out.println("");
		System.out.println("");
		System.out.println("");
		printLog(LogTopics.LOG_TOPIC_INIT, "Domotic for linux desktop - by Lorenzo Failla");

		/*
		 * Retrieves all the parameters values from the configuration file
		 */

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
		this.tcpInterface.init();

		// Available services probe
		retrieveServices();
		printLog(LogTopics.LOG_TOPIC_INIT, "Available services probing completed.");

		// initializes and starts the Internet connectivity check loop
		this.internetConnectionCheck = new InternetConnectionCheck(
				DefaultConfigValues.CONNECTIVITY_TEST_SERVER_ADDRESS);
		this.internetConnectionCheck.setConnectivityCheckRate(DefaultConfigValues.CONNECTIVITY_TEST_RATE);
		this.internetConnectionCheck.setListener(this.internetConnectionStatusListener);
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

		// remove the Firebase ValueEventListeners
		detachListeners();
		printLog(LogTopics.LOG_TOPIC_TERM, "Firebase listeners detached.");

		// cancella i timer per i task periodici
		if (this.deviceStatusUpdateTimer != null)
			this.deviceStatusUpdateTimer.cancel();

		if (this.deviceNetworkStatusUpdateTimer != null)
			this.deviceNetworkStatusUpdateTimer.cancel();

		printLog(LogTopics.LOG_TOPIC_TERM, "Periodical tasks canceled.");

		// close the TCP interface
		this.tcpInterface.terminate();
		printLog(LogTopics.LOG_TOPIC_TERM, "TCP interface closed.");

		/* unregister the device, so that client cannot connect to it */
		unRegisterDeviceServices();

		while (this.deviceRegistered && (!this.hasVideoSurveillance || this.videoSurveillanceRegistered > 0)) {

			sleepSafe(100);

		}

		printLog(LogTopics.LOG_TOPIC_TERM, "Firebase device node status updated.");

		// if specified, shuts down the video surveillance daemon
		if (this.hasVideoSurveillance && !this.videoSurveillanceDaemonShutdownCommand.equals("")) {

			try {

				String response = parseShellCommand(this.videoSurveillanceDaemonShutdownCommand);
				printLog(LogTopics.LOG_TOPIC_TERM, "Video surveillance daemon shut down. --> " + response);

			} catch (IOException | InterruptedException e) {

				exceptionLog_REDXTERM(this.getClass(), e);

			}

		}

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
			return new RemoteCommand(ReplyPrefix.WELCOME_MESSAGE, encode(this.thisDevice), "null");

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

			int deviceId = Integer.parseInt(incomingCommand.getBody());

			if (deviceId < this.wolDevices.length) {
				try {

					parseShellCommand("wakeonlan " + this.wolDevices[deviceId]);
					printLog(LogTopics.LOG_TOPIC_CMDEXEC, "\'wakeonlan\' command sent to device ID\'" + deviceId
							+ "\':, MAC address:\'" + this.wolDevices[deviceId] + "\'");

				} catch (IOException | InterruptedException e) {

					exceptionLog_REDXTERM(this.getClass(), e);

				}
			}
			
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
					this.thisDevice);

		case "__refresh_tmate":

			refreshTmate();
			return null;

		case "__start_streaming_request":

			// se � stata inizializzata l'interfaccia Youtube e si dispone
			// della
			// videosorveglianza, crea un canale di streaming
			if (this.youTubeComm != null && this.hasVideoSurveillance && this.motionComm != null) {

				// controlla che vi sia gi� una richiesta streaming in corso
				// sul
				// thread specificato. se non c'� nessuna richiesta, crea un
				// nuovo streaming
				if (this.youTubeLiveStreamRequestors.containsValue(incomingCommand.getBody())) {

					// c'� gi� una richiesta in corso. stampa un messaggio
					// di
					// log
					printLog(LogTopics.LOG_TOPIC_VSURV,
							"Live streaming already requested for camera ID: " + incomingCommand.getBody());

				} else {

					// non c'� una richiesta in corso per il thread
					// specificato.
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

			// controlla se c'� ancora almeno un richiedente per il thread ID
			// specificato. se non c'� almeno un richiedente rimasto, termina
			// lo
			// streaming

			if (!this.youTubeLiveStreamRequestors.containsValue(incomingCommand.getBody())) {

				// non ci sono richiedenti per il thread ID specificato

				printLog(LogTopics.LOG_TOPIC_CMDEXEC, String.format(
						"No more requestors for camera ID %s. Streaming will terminate.", incomingCommand.getBody()));
				// ferma l'esecuzione dello streaming

				try {
					// ferma l'esecuzione in background di ffmpeg, chiamando il
					// kill del process id relativo al thread contenuto nel
					// corpo del messaggio

					printLog(LogTopics.LOG_TOPIC_SHEXEC, "yt-stopstream " + incomingCommand.getBody());
					parseShellCommand("yt-stopstream " + incomingCommand.getBody());

				} catch (IOException | InterruptedException e) {

					exceptionLog_REDXTERM(this.getClass(), e);

				}

			} else {

				printLog(LogTopics.LOG_TOPIC_CMDEXEC,
						String.format("There are still requestors for camera ID %s. Streaming will continue.",
								incomingCommand.getBody()));

			}

			return null;

		case "__start_streaming_notification":

			// questo comando � solitamente lanciato dall'host stesso, tramite
			// comando locale, avvisa che ffmpeg � stato correttamente
			// lanciato
			// in background

			// aggiorna il nodo del database Firebase con dati relativi allo
			// stato dello streaming
			setCameraLiveBroadcastStatus(incomingCommand.getBody(), "ready");

			return null;

		case "__end_streaming_notification":

			// questo comando � solitamente lanciato dall'host stesso, tramite
			// comando locale, avvisa che ffmpeg � terminato

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
						this.thisDevice);

			case "dynamic_data":
				return new RemoteCommand(ReplyPrefix.STATUS_TEXT_REPLY, encode(getStatusDeviceDataJSON()),
						this.thisDevice);

			default:
				break;

			}

			return null;

		case "__provide_log":
			return new RemoteCommand(ReplyPrefix.LOG_REPLY,
					encode(GeneralUtilitiesLibrary.getFileAsBytes(LOGFILE_LOCATION)), this.thisDevice);

		default:

			return new RemoteCommand(ReplyPrefix.UNRECOGNIZED_COMMAND, "null", this.thisDevice);

		} /* fine switch lettura comandi */

	}

	private void removeCommand(String id, int operationAfterRemoval) {
		/*
		 * Removes, if needed, a command from the queue in Firebase DB Node.
		 * Also, performs any operation, if needed, after command removal.
		 */

		if (id.startsWith(LOCAL_CMD_PREFIX) || id.startsWith(LOCAL_TCP_PREFIX)) {

			/*
			 * Command is local (file://) or TCP (tcp://). local command has
			 * already been deleted before the execution thread was started. TCP
			 * command does not need to be removed.
			 */

			// if needed, performs an operation after the removal
			performOperationAfterRemoval(operationAfterRemoval);

		} else {

			/*
			 * Command is remote, therefore it needs to be removed from the
			 * Database
			 */

			// remove the child from the <incomingCommands> Firebase node
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
		// se il comando � di shutdown o di reboot, elimina il comando prima
		// di
		// eseguire l'operazione

		case "__shutdown":
			printLog(LogTopics.LOG_TOPIC_MAIN, "Shutdown; Requested by: " + rc.getReplyto());
			removeCommand(commandID, SHUTDOWN);
			break;

		case "__reboot":
			printLog(LogTopics.LOG_TOPIC_MAIN, "Reboot; Requested by: " + rc.getReplyto());
			removeCommand(commandID, REBOOT);
			break;

		default:
			//
			// recupera il messaggio di risposta
			RemoteCommand reply = getReply(rc);

			if (reply != null) {
				//
				// risposta ottenuta

				// invia la risposta al dispositivo remoto
				sendMessageToDevice(reply, rc.getReplyto(), commandID, params);

			} else {
				//
				// il comando non prevede una risposta

				// rimuove il comando immediatamente
				removeCommand(commandID, -1);

			}

		}

	}

	private void sendMessageToDevice(RemoteCommand message, String device, String idToRemove) {
		sendMessageToDevice(message, device, idToRemove, null);
	}

	private void sendMessageToDevice(RemoteCommand message, String device, String idToRemove,
			HashMap<String, Object> params) {

		if (device.startsWith("tcp://")) {
			// reply to be sent over tcp interface

			// prepares and send the message
			String hostID = device.substring(6, device.length());
			byte[] data = (HEADER_REPLY + message.toString()).getBytes();
			this.tcpInterface.sendData(hostID, data);

			// prepares and prints a log message
			String sendStatus = "TCP OK.";
			printLog(LogTopics.LOG_TOPIC_OUTMSG, "to:\'" + device + "\' hdr:\'" + message.getHeader() + "\' bdy:\'"
					+ message.getBody() + "\' sts:\'" + sendStatus + "\'");

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
			// reply to be sent over Firebase DB

			// ottiene una referenza al nodo del dispositivo che aveva
			// inviato il comando
			DatabaseReference databaseReference = FirebaseDatabase.getInstance()
					.getReference(String.format("/Groups/%s/Devices/%s/IncomingCommands", this.groupName, device));

			// imposta il messaggio di risposta nel nodo, una volta
			// completata l'operazione rimuove il comando
			databaseReference.child(getTimeStamp()).setValue(message, new CompletionListener() {

				@Override
				public void onComplete(DatabaseError error, DatabaseReference ref) {

					String sendStatus;
					if (error != null) {

						sendStatus = "ERROR [c=" + error.getCode() + ",m=" + error.getMessage() + "]";

					} else {

						sendStatus = "OK";

					}

					// stampa un messaggio di log
					printLog(LogTopics.LOG_TOPIC_OUTMSG, "to:\'" + device + "\' hdr:\'" + message.getHeader()
							+ "\' bdy:\'" + message.getBody() + "\' sts:\'" + sendStatus + "\'");

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

			this.signOption = SignUrlOption
					.signWith(ServiceAccountCredentials.fromStream(new FileInputStream(this.jsonAuthFileLocation)));

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

					printLog(LogTopics.LOG_TOPIC_ERROR,
							"Error while removing the obsolete incoming messages on Firebase node. Message: "
									+ error.getMessage() + ".");

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

		InputStream inputStreamToUpload;

		// inizializza un oggetto File che punta al nome passato in argomento
		File fileToUpload = new File(fileName);

		// inizializza la connessione allo storage
		Bucket storageBucket = StorageClient.getInstance().bucket();

		printLog(LogTopics.LOG_TOPIC_CMDEXEC,
				"Upload of file\'" + fileToUpload.getName() + "\' to cloud storage started.");

		try {

			// inizializza l'InputStream
			inputStreamToUpload = new FileInputStream(fileName);

			// apre un thread per effettuare l'upload
			new Thread() {

				@Override
				public void run() {

					// inizia l'upload
					Blob uploadInfo = storageBucket.create("Users/lorenzofailla/uploads/" + fileToUpload.getName(),
							inputStreamToUpload);

					printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Upload of file\'" + fileToUpload.getName()
							+ "\' to cloud storage successfully completed.");

					// notifica l'upload del file
					notifyFileUpload(new FileInCloudStorage(getTimeStamp().toString(), fileToUpload.getName(),
							requestor, uploadInfo.getMediaLink(), uploadInfo.getSize(), 0));

				}

			}.start();

		} catch (FileNotFoundException e) {

			exceptionLog_REDXTERM(this.getClass(), e);

		}

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

			parseShellCommand("sudo shutdown -h now");

		} catch (IOException | InterruptedException e) {

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

			parseShellCommand("sudo reboot");

		} catch (IOException | InterruptedException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
		}

	}

	private boolean getConfiguration() {

		final List<String> wolDevList = new ArrayList<String>();
		final List<String> wolDevName = new ArrayList<String>();

		BufferedReader br;
		try {

			br = new BufferedReader(new FileReader(DefaultConfigValues.CONFIG_FILE_LOCATION));
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
							this.thisDevice = argument;
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

						case "CameraNames":
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
							printLog(LogTopics.LOG_TOPIC_INIT, "Unknown command \'" + command + "\' at line \'"
									+ lineIndex + "\'. Please check and try again.");
							br.close();
							return false;
						}

						printLog(LogTopics.LOG_TOPIC_INIT,
								String.format("Parameter \"%s\"; value=\"%s\"", command, argument));

					} else {

						// errore di sintassi
						printLog(LogTopics.LOG_TOPIC_INIT, "Syntax error, please check line \'" + lineIndex
								+ "\' in configuration file and try again.");
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
				printLog(LogTopics.LOG_TOPIC_INIT, "No group name specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.jsonAuthFileLocation == "") {
				printLog(LogTopics.LOG_TOPIC_INIT, "No JSON auth file location specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.firebaseDatabaseURL == "") {
				printLog(LogTopics.LOG_TOPIC_INIT, "No Firebase database location specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.storageBucketAddress == "") {
				printLog(LogTopics.LOG_TOPIC_INIT, "No Firebase storage bucket address specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.allowVideoSurveillanceManagement && this.videoSurveillanceServerAddress == "") {
				printLog(LogTopics.LOG_TOPIC_INIT, "No video surveillance server address specified. Cannot continue.");
				configurationComplete = false;
			}

			if (this.allowVideoSurveillanceManagement && this.videoSurveillanceServerControlPort == -1) {
				printLog(LogTopics.LOG_TOPIC_INIT,
						"No video surveillance server control port specified. Cannot continue.");
				configurationComplete = false;

			}

			this.wolDevices = wolDevList.toArray(new String[0]);
			this.wolDeviceNames = wolDevName.toArray(new String[0]);

			return configurationComplete;

		} catch (IOException e) {

			printLog(LogTopics.LOG_TOPIC_INIT,
					e.getMessage() + " Make sure configuration file exists and is readable at specified location: \'"
							+ DefaultConfigValues.CONFIG_FILE_LOCATION + "\'");
			return false;

		}

	}

	private void registerDeviceServices() {

		DatabaseReference ref = FirebaseDatabase.getInstance()
				.getReference(String.format("/Groups/%s", this.groupName));

		// crea struttura per i dati del dispositivo
		Map<String, Object> deviceData = new HashMap<String, Object>();
		Map<String, Object> allowedUsers = new HashMap<String, Object>();

		deviceData.put("DeviceName", this.thisDevice);
		deviceData.put("Online", true);
		deviceData.put(STATIC_DATA_NODE, getStaticDeviceDataJSON());

		ref.child("Devices").child(this.thisDevice).updateChildren(deviceData, new CompletionListener() {

			@Override
			public void onComplete(DatabaseError error, DatabaseReference ref) {

				DomoticCore.this.deviceRegistered = (error == null);
				if (error != null) {
					firebaseErrorLog_XTERM(error);
				}

			}

		});

		/*
		 * deviceData.put("hasDirectoryNavigation",
		 * this.hasDirectoryNavigation); deviceData.put("hasTorrentManagement",
		 * this.hasTorrentManagement); deviceData.put("hasWakeOnLan",
		 * this.wolDevices.length > 0); deviceData.put("hasSSH", this.allowSSH);
		 * deviceData.put("hasVideoSurveillance", this.hasVideoSurveillance);
		 * 
		 * // crea struttura per i dati dei dispositivi su cui � possibile
		 * fare // il // wake-on-lan Map<String, Object> wolDeviceIds = new
		 * HashMap<String, Object>();
		 * 
		 * for (int i = 0; i < this.wolDevices.length; i++) { Map<String,
		 * Object> wolDeviceData = new HashMap<String, Object>();
		 * wolDeviceData.put("Id", "" + i); wolDeviceData.put("Name",
		 * this.wolDeviceNames[i]); wolDeviceData.put("Address",
		 * this.wolDevices[i]);
		 * 
		 * wolDeviceIds.put("" + i, wolDeviceData);
		 * 
		 * }
		 * 
		 * deviceData.put("WOLDevices", wolDeviceIds);
		 * 
		 * if (this.hasVideoSurveillance) { deviceData.put("cameraIDs",
		 * this.motionComm.getCamerasList()); deviceData.put("cameraNames",
		 * this.motionComm.getCamerasNames());
		 * 
		 * }
		 * 
		 * if (this.vpnConnectionConfigFilePath != null) { updateVPNStatus(); }
		 * else { deviceData.put("VPNStatus", "<not-available>"); }
		 * 
		 * 
		 * 
		 */
		// update the data of the video surveillance cameras

		if (this.hasVideoSurveillance) {

			String[] threadIDs = this.motionComm.getThreadsIDs();

			for (int i = 0; i < threadIDs.length; i++) {

				HashMap<String, Object> cameraInfo = this.motionComm.getCameraInfo(threadIDs[i]);
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

				ref.child("VideoSurveillance").child("AvailableCameras")
						.child(String.format("%s-%s", this.thisDevice, threadIDs[i]))
						.updateChildren(cameraInfo, new CompletionListener() {

							@Override
							public void onComplete(DatabaseError error, DatabaseReference ref) {

								if (error == null) {
									DomoticCore.this.videoSurveillanceRegistered++;
								}

							}

						});

			}

		}

		// delete all previous incoming commands

		ref.child(String.format("Devices/%s/IncomingCommands", this.thisDevice)).removeValue(new CompletionListener() {

			@Override
			public void onComplete(DatabaseError error, DatabaseReference ref) {

				DomoticCore.this.incomingMessagesCleared = true;

				if (error != null) {
					firebaseErrorLog_XTERM(error);
				}

			}

		});

		// delete all previous incoming files

		ref.child(String.format("Devices/%s/IncomingFiles", this.thisDevice)).removeValue(new CompletionListener() {

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

		DatabaseReference ref = FirebaseDatabase.getInstance()
				.getReference(String.format("/Groups/%s", this.groupName));

		Map<String, Object> deviceData = new HashMap<String, Object>();

		deviceData.put("Online", false);

		ref.child("Devices").child(this.thisDevice).updateChildren(deviceData, new CompletionListener() {

			@Override
			public void onComplete(DatabaseError error, DatabaseReference ref) {

				DomoticCore.this.deviceRegistered = false;
				if (error != null) {
					firebaseErrorLog_XTERM(error);
				}

			}

		});

		// rimuove i nodi delle videocamere associate a questo dispositivo

		Query associatedCameras = ref.child("VideoSurveillance").child("AvailableCameras").orderByChild("OwnerDevice")
				.equalTo(this.thisDevice);
		associatedCameras.getRef().removeValue(new CompletionListener() {

			@Override
			public void onComplete(DatabaseError error, DatabaseReference ref) {

				if (error != null) {

					firebaseErrorLog_XTERM(error);

				}

				DomoticCore.this.videoSurveillanceRegistered = 0;

			}

		});

	}

	private void uploadFileToDataSlots(String fileName, String deviceToReply) {

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

			String childTimeStamp = getTimeStamp();

			incomingFilesNode.child(childTimeStamp).updateChildren(fileData, new CompletionListener() {

				@Override
				public void onComplete(DatabaseError error, DatabaseReference ref) {

					if (error != null) {
						printLog(LogTopics.LOG_TOPIC_CMDEXEC,
								"Upload of file \'" + fileName + "' as dataslot terminated with error. Error code: \'"
										+ error.getCode() + "\', error message: \'" + error.getMessage() + "\'");

					} else {

						sendMessageToDevice(
								new RemoteCommand(ReplyPrefix.FILE_READY_FOR_DOWNLOAD, childTimeStamp, null),
								deviceToReply, null);

						printLog(LogTopics.LOG_TOPIC_CMDEXEC,
								"Upload of file \'" + fileName + "' as dataslot successfully terminated.");

					}

				}

			});

		} catch (IOException e) {

			exceptionLog_REDXTERM(this.getClass(), e);

		}

	}

	/*
	 * SSH Shell related methods and functions
	 * 
	 */

	private void initializeSSHShell(String remoteDev) {

		printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Initializing secure shell session with device \'" + remoteDev + "\'");

		this.sshShell = new SSHShell(this.sshHost, this.sshUsername, this.sshPassword, this.sshPort);
		this.sshShell.setListener(new SSHShellListener() {

			@Override
			public void onConnected() {
				printLog(LogTopics.LOG_TOPIC_CMDEXEC,
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
								DomoticCore.this.groupName, DomoticCore.this.thisDevice, remoteDev))
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
						String.format("/Groups/%s/Devices/%s/SSHShells/%s", this.groupName, this.thisDevice, shellId))
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

			printLog(LogTopics.LOG_TOPIC_WARNING,
					"\'motion\' is not installed on this host.You may install \'motion\' by typing \'sudo apt-get install motion\'Videosurveillance features are disabled on this host.");
			return false;
		}

		this.motionComm = new MotionComm(this.videoSurveillanceServerAddress, this.thisDevice,
				this.videoSurveillanceServerControlPort);
		this.motionComm.setDebugMode(true);

		this.motionComm.setListener(this.motionCommListener);

		if (this.motionComm.isHTMLOutputEnabled()) {

			printLog(LogTopics.LOG_TOPIC_WARNING,
					"\'motion\' is installed on this host, but output in HTML format is enabled. For the domotic-motion interface to run properly, motion output has to be in plain format. You may enable the plain output by setting \'webcontrol_html_output off\' in your motion configuration file.\nVideosurveillance features are disabled on this host.");
			this.motionComm.setListener(null);
			this.motionComm = null;
			return false;

		}

		int nOfThreads = this.motionComm.getNOfThreads();
		// System.out.println(nOfThreads);
		if (nOfThreads < 1) {

			printLog(LogTopics.LOG_TOPIC_WARNING, "No active threads found on motion daemon.");
			this.motionComm.setListener(null);
			this.motionComm = null;
			return false;

		} else {

			printLog(LogTopics.LOG_TOPIC_INIT, String.format("%d active threads found on motion daemon.", nOfThreads));
			return true;
		}

	}

	private void processMotionEvent(String params) {

		/*
		 * Called by a local command generated by motion daemon.
		 * 
		 * Uploads a video file of the motion event, as described in the params
		 * Metadata, into the proper position in the Firebase Cloud Storage.
		 * When the upload is completed, a data entry is generated into the
		 * Events node of the Firebase Database, and a real time notification is
		 * sent to the group topic, so that all devices may see it.
		 * 
		 */

		HashMap<String, Object> paramsMap = parseJSON(params);

		if (!paramsMap.containsKey("file_path") || !paramsMap.containsKey("thread_id")) {

			return;

		}

		// set the position in the Firebase Cloud Storage
		String remotePosition = MOTION_EVENTS_STORAGE_CLOUD_URL.replaceAll("%%group_name&&", this.groupName)
				.replaceAll("%%device_name&&", this.thisDevice)
				.replaceAll("%%thread_id&&", paramsMap.get("thread_id").toString());

		// retrieve the picture of the event, as provided by motion daemon, as
		// byte array, compressed ans string encoded
		String eventPictureData = Base64.encodeBase64String(
				compress(getFileAsBytes(getEventJpegFileName(paramsMap.get("file_path").toString()))));

		// print log entry
		printLog(LogTopics.LOG_TOPIC_VSURV,
				String.format("Starting upload of video file for motion event on bucket: \"%s\"", remotePosition));

		// set up the FirebaseCloudUploader and start the upload operation
		FirebaseCloudUploader uploader = new FirebaseCloudUploader(paramsMap.get("file_path").toString(),
				remotePosition).setListener(new FirebaseCloudUploaderListener() {

					@Override
					public void onError(FirebaseCloudUploader uploader, Exception e) {

						exceptionLog_REDXTERM(this.getClass(), e);

					}

					@Override
					public void onComplete(FirebaseCloudUploader uploader, Blob info, String shortFileName) {

						/*
						 * upload of the video file completed
						 */

						// print log entry
						printLog(LogTopics.LOG_TOPIC_VSURV,
								"Video file for motion event successfully uploaded (" + info.getSize() + " bytes).");

						// retrieve the URL of the video file of the event
						String downloadURL = FirebaseCloudUploader.getSignedUrl(info, DEFAULT_MOTION_VIDEO_LINK_TIMEOUT,
								TimeUnit.DAYS, DomoticCore.this.jsonAuthFileLocation);

						// generate the HashMap to be put into the Firebase Database
						// Events Node
						HashMap<String, Object> eventData = new HashMap<String, Object>();

						eventData.put("CameraFullID", DomoticCore.this.thisDevice + "-" + paramsMap.get("thread_id")); // pair
						// device
						// id
						// +
						// thread
						// id
						eventData.put("Date",
								MotionComm.getDateFromMotionFileName(paramsMap.get("file_path").toString())); // event
						// date
						eventData.put("Time",
								MotionComm.getTimeFromMotionFileName(paramsMap.get("file_path").toString())); // event
						// time
						eventData.put("ThreadID", paramsMap.get("thread_id")); // thread
						// id
						eventData.put("CameraName",
								DomoticCore.this.motionComm.getCameraName(paramsMap.get("thread_id").toString())); // camera
						// name
						eventData.put("VideoLink", shortFileName); // short file name of
						// the video file of
						// the event
						eventData.put("Device", DomoticCore.this.thisDevice); // device
						// id
						eventData.put("eventPictureData", eventPictureData); // compressed
						// data,
						// string
						// encoded,
						// of the
						// jpeg of
						// the
						// event
						eventData.put("newItem", "true"); // boolean, string encoded
						eventData.put("lockedItem", "false"); // boolean, string encoded
						eventData.put("DownloadURL", downloadURL); // complete URL for
						// the download of
						// the video file of
						// the event

						// set the position of the Events Node in the Firebase Database
						String eventsNode = MOTION_EVENTS_DB_NODE.replace("%%group_name&&", DomoticCore.this.groupName);
						String eventsNodeChildKey = DomoticCore.this.thisDevice + "-" + paramsMap.get("thread_id") + "-"
								+ System.currentTimeMillis();

						// write the HashMap in the position of the Events Node in the
						// Firebase Database
						FirebaseDatabase.getInstance().getReference(eventsNode).child(eventsNodeChildKey)
								.setValue(eventData, new CompletionListener() {

									@Override
									public void onComplete(DatabaseError error, DatabaseReference ref) {

										/*
										 * the Events Node in the Firebase
										 * Database has been successfully
										 * updated
										 */

										if (error == null) {

											/*
											 * send a notification via Firebase
											 * Cloud Messaging service
											 */

											// set up the message data payload

											String notificationID;

											if (DomoticCore.this.notificationsEnabled) {

												JSONObject data = new JSONObject(); // payload
												data.put("eventID", eventsNodeChildKey);
												data.put("previewURL", downloadURL);
												notificationID = sendFCM(DomoticCore.this.fcmServiceKey,
														"/topics/" + DomoticCore.this.groupName, "Motion detected",
														DomoticCore.this.motionComm
																.getCameraName(paramsMap.get("thread_id").toString()),
														data.toString());

											} else {

												notificationID = "N/A";

											}

											printLog(LogTopics.LOG_TOPIC_VSURV,
													"Data for motion event successfully uploaded. Notification sent - "
															+ notificationID);

										} else {

											printLog(LogTopics.LOG_TOPIC_ERROR, String.format(
													"Error during motion event data upload: %s", error.getMessage()));

										}

									}

								});

					}

				}).startUpload();

	}

	private void refreshTmate() {

		/*
		 * calls /usr/local/bin/refresh-tmate script, to refresh the tmate
		 * session
		 */

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

			videoSurveillance.put("CameraIDs", this.motionComm.getCamerasList());
			videoSurveillance.put("CameraNames", this.motionComm.getCamerasNames());

		}

		result.put("DeviceName", this.thisDevice);

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

		return result.toString();

	}

}

/*
 * 
 * if (youTubeJSONLocation != "" && youTubeOAuthFolder != "") {
 * 
 * printLog(LogTopics.LOG_TOPIC_INIT, "Checking Youtube credentials...");
 * 
 * // inizializza uno YouTubeComm e assegna il listener try {
 * 
 * youTubeComm = new YouTubeComm(APP_NAME, youTubeJSONLocation,
 * youTubeOAuthFolder); youTubeComm.setListener(youTubeCommListener);
 * 
 * printLog(LogTopics.LOG_TOPIC_INIT,
 * "Youtube credentials successfully verified.");
 * 
 * } catch (YouTubeNotAuthorizedException e) {
 * 
 * printLog(LogTopics.LOG_TOPIC_INIT, "Failed to verify Youtube credentials. " +
 * e.getMessage()); }
 * 
 * } else {
 * 
 * printLog(LogTopics.LOG_TOPIC_INIT,
 * "WARNING! Cannot Youtube credentials. Please make sure \"YouTubeJSONLocation\" and \"YouTubeOAuthFolder\" are specified in the configuration file."
 * ); }
 * 
 * // Device registration printLog(LogTopics.LOG_TOPIC_INIT,
 * "Device registration started."); deviceRegistered = false;
 * 
 * int nOfVideoSurveillanceCameras; videoSurveillanceRegistered = 0;
 * 
 * if (hasVideoSurveillance) { nOfVideoSurveillanceCameras =
 * motionComm.getNOfThreads(); } else { nOfVideoSurveillanceCameras = -1; }
 * 
 * registerDeviceServices();
 * 
 * while (!tcpInitialized && !deviceRegistered && (!hasVideoSurveillance ||
 * videoSurveillanceRegistered < nOfVideoSurveillanceCameras) &&
 * !incomingMessagesCleared && !incomingFilesCleared) {
 * 
 * try {
 * 
 * Thread.sleep(100);
 * 
 * } catch (InterruptedException e) {
 * 
 * exceptionLog_REDXTERM(this.getClass(), e); System.exit(1);
 * 
 * }
 * 
 * }
 * 
 * printLog(LogTopics.LOG_TOPIC_INIT,
 * "Device registration successfully completed.");
 */
