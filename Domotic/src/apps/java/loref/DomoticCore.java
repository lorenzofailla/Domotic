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
import static apps.java.loref.GeneralUtilitiesLibrary.getUptime;
import static apps.java.loref.GeneralUtilitiesLibrary.parseLocalCommand;
import static apps.java.loref.GeneralUtilitiesLibrary.parseMeta;
import static apps.java.loref.GeneralUtilitiesLibrary.parseShellCommand;
import static apps.java.loref.GeneralUtilitiesLibrary.printErrorLog;
import static apps.java.loref.GeneralUtilitiesLibrary.printLog;
import static apps.java.loref.GeneralUtilitiesLibrary.readPlainTextFromFile;
import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;
import static apps.java.loref.GeneralUtilitiesLibrary.encode;
import static apps.java.loref.GeneralUtilitiesLibrary.getFreeSpace;

import static apps.java.loref.MotionComm.getEventJpegFileName;

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

import apps.java.loref.FirebaseCloudUploader.FirebaseCloudUploaderListener;
import apps.java.loref.SSHShell.SSHShellListener;

public class DomoticCore {

    /*
     * Constant values
     */

    private final static String APP_NAME = "Domotic";

    private final static String GROUP_NODE = "Groups";
    private final static String DEVICES_NODE = "Devices";
    private final static String STATUS_NODE = "Status";
    private final static String LOGS_NODE = "Logs";

    private final static String VIDEOSURVEILLANCE_NODE = "VideoSurveillance";
    private final static String AVAILABLE_CAMERAS_NODE = "AvailableCameras";
    private final static String VIDEO_STREAMING_NODE = "StreamingData";

    private final static String LOCAL_CMD_PREFIX = "file://";
    private final static String LOCAL_TCP_PREFIX = "tcp://";

    private final static long TICK_TIME_MS = 1000L; // milliseconds
    
    private final static int REBOOT = 1;
    private final static int SHUTDOWN = 2;

    private String firebaseDatabaseURL;
    private String jsonAuthFileLocation;
    private String thisDevice;
    private String groupName;
    private String storageBucketAddress;
    private SignUrlOption signOption;

    private boolean loopFlag = true;
    private final long runningSince = System.currentTimeMillis();
    private boolean readyToProcessLocalCommands = true;

    private boolean notificationsEnabled = false;
    private String fcmServiceKey = "";

    private boolean allowDirectoryNavigation = false;
    private boolean allowTorrentManagement = false;
    private boolean allowVideoSurveillanceManagement = false;
    private boolean allowSSH = false;

    private boolean hasDirectoryNavigation = false;
    private boolean hasTorrent = false;
    private boolean hasVideoSurveillance = false;

    /*
     * Device status periodical update
     */

    private Timer deviceStatusUpdateTimer;
    private long deviceStatusUpdateRate = DefaultConfigValues.DEVICE_STATUS_UPDATE_RATE;

    private class DeviceStatusUpdateTask extends TimerTask {

	@Override
	public void run() {
	    
	    updateDeviceStatus();

	}

    }

    private long firebaseDBUpdateTimeOut = DefaultConfigValues.FIREBASE_DB_UPDATE_TIMEOUT;
    private Timer firebaseDBUpdateTimeoutTimer;

    private class FirebaseDBUpdateTimeoutTask extends TimerTask {

	@Override
	public void run() {

	    /*
	     * manage a timeout issue when trying to update the device status
	     * over the
	     * Firebase DB node.
	     */

	    // print a log message
	    printLog(LogTopics.LOG_TOPIC_ERROR, "Timeout exceeded during device status update on Firebase DB node.");

	    // register a log entry message in the Firebase DB node.
	    LogEntry log = new LogEntry(getTimeStamp(), LogTopics.LOG_TOPIC_ERROR, "Timeout exceeded during device status update on Firebase DB node.");
	    FirebaseDatabase.getInstance().getReference(GROUP_NODE + "/" + groupName + "/" + DEVICES_NODE + "/" + thisDevice + "/" + LOGS_NODE).child(getTimeStamp()).setValueAsync(log);

	}

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

	    tcpInitialized = true;

	    // print a log
	    printLog(LogTopics.LOG_TOPIC_TCP, String.format("TCP Listener initialized on port: %d.", port));

	}

	@Override
	public void onConnected(String hostID) {
	    // print a log
	    printLog(LogTopics.LOG_TOPIC_TCP, String.format("New incoming TCP connection accepted: %s.", hostID));

	}

	@Override
	public void onCommand(String hostID, RemoteCommand command) {

	    new Thread() {

		public void run() {

		    replyToRemoteCommand(command, String.format("tcp://%s", hostID));

		}

	    }.start();

	}

	@Override
	public void onDisconnect(String hostID, boolean byTimeout) {
	    // print a log
	    printLog(LogTopics.LOG_TOPIC_TCP, String.format("Client %s disconnected. ByTimeout=%s", hostID, byTimeout));

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

	    GenericTypeIndicator<RemoteCommand> t = new GenericTypeIndicator<RemoteCommand>() {
	    };
	    RemoteCommand remoteCommand = snapshot.getValue(t);

	    // waits for a TICK, this is needed in order to avoid possible
	    // duplicate
	    // timestamps

	    sleepSafe(10);

	    // performs the needed operations according to the content of the
	    // incoming
	    // message
	    replyToRemoteCommand(remoteCommand, snapshot.getKey());

	}

	@Override
	public void onCancelled(DatabaseError error) {
	    // no action foreseen

	}

    };

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

	    printLog(LogTopics.LOG_TOPIC_VSURV, String.format("Frame image received. Camera ID: %s; Image bytes: %d; Destination: %s", cameraID, frameImageData.length, destination));

	    if (destination.startsWith("tcp://")) {
		/*
		 * sends the received camera data as a TCP REPLY
		 */

		RemoteCommand remoteCommand = new RemoteCommand(ReplyPrefix.FRAME_IMAGE_DATA.prefix(), encode(String.format("%s|data=%s", cameraID, Base64.encodeBase64String(compress(frameImageData)))), "null");

		sendMessageToDevice(remoteCommand, destination, "");

	    }

	    /*
	     * by default, stores the the received camera data into the relevant
	     * Firebase
	     * Database node.
	     */

	    if (!frameUploadReady.contains(cameraID)) {

		HashMap<String, Object> frameData = new HashMap<String, Object>();

		frameData.put("ImgData", Base64.encodeBase64String(compress(frameImageData)));

		frameData.put("Date", "-");
		frameData.put("Time", "-");

		frameUploadReady.add(cameraID);

		DatabaseReference ref = FirebaseDatabase.getInstance().getReference(String.format("Groups/%s/VideoSurveillance/AvailableCameras/%s-%s", groupName, thisDevice, cameraID));

		ref.child("LastShotData").setValue(frameData, new CompletionListener() {

		    @Override
		    public void onComplete(DatabaseError error, DatabaseReference ref) {

			if (error == null) {
			    printLog(LogTopics.LOG_TOPIC_VSURV, String.format("Frame successfully uploaded for camera id:%s.", cameraID));
			} else {
			    printLog(LogTopics.LOG_TOPIC_ERROR, String.format("Error found while uploading for camera id:%s. Error message:\"%s\".", cameraID, error.getMessage()));
			}

			frameUploadReady.remove(cameraID);

		    }

		});

	    }

	}

	@Override
	public void statusChanged(String cameraID) {

	    DatabaseReference ref = FirebaseDatabase.getInstance().getReference(String.format("Groups/%s/VideoSurveillance/AvailableCameras/%s-%s", groupName, thisDevice, cameraID));
	    ref.child("MoDetStatus").setValueAsync(motionComm.getThreadMoDetStatus(cameraID));

	}

    };

    private YouTubeComm youTubeComm = null;

    private YouTubeCommListener youTubeCommListener = new YouTubeCommListener() {

	@Override
	public void onLiveStreamDeleted(String broadcastID) {
	    // no action foreseen

	}

	@Override
	public void onLiveStreamCreated(String requestorID, String requestID, String liveStreamID, String liveBroadcastID) {
	    /*
	     * A live stream has been created
	     */

	    printLog(LogTopics.LOG_TOPIC_VSURV, String.format("Youtube live stream created for camera ID: %s, Live stream ID: %s, Broadcast ID: %s", requestID, liveStreamID, liveBroadcastID));

	    String inputStreamURL = motionComm.getStreamFullURL(requestID);
	    String shellCommand[] = new String[] { "/bin/sh", "-c", String.format("yt-stream %s %d %s %s %s", inputStreamURL, motionComm.getCameraStreamFPS(requestID), "05:00", liveStreamID, requestID) };

	    try {

		// printLog(LOG_TOPIC_SHEXEC, shellCommand);

		// lancia il comando bash per avviare lo streaming
		Runtime.getRuntime().exec(shellCommand);

		printLog(LogTopics.LOG_TOPIC_VSURV, String.format("Youtube live stream started for camera ID: %s, Live stream ID: %s, Broadcast ID: %s", requestID, liveStreamID, liveBroadcastID));

		// aggiorna i dati relativi allo streaming
		setCameraLiveBroadcastData(requestID, liveBroadcastID);

		// inserisce i dati relativi allo streaming
		youTubeLiveBroadcasts.put(requestID, liveBroadcastID);

		youTubeLiveStreamRequestors.put(requestorID, requestID);

	    } catch (IOException e) {

		// aggiorna i dati relativi allo streaming
		setCameraLiveBroadcastStatus(requestID, "idle");
		setCameraLiveBroadcastData(requestID, "");

		youTubeLiveStreamRequestors.remove(requestorID);

		printErrorLog(e);

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
	    youTubeLiveStreamRequestors.remove(requestorID);

	}

    };

    private void setCameraLiveBroadcastData(String cameraID, String broadcastData) {
	String cameraNode = AVAILABLE_CAMERAS_DB_NODE.replace("%%group_name&&", groupName);
	String cameraNodeChildKey = thisDevice + "-" + cameraID;

	FirebaseDatabase.getInstance().getReference(cameraNode).child(cameraNodeChildKey).child("LiveStreamingBroadcastData").setValueAsync(broadcastData);

    }

    private void setCameraLiveBroadcastStatus(String cameraID, String broadcastStatus) {
	String cameraNode = AVAILABLE_CAMERAS_DB_NODE.replace("%%group_name&&", groupName);
	String cameraNodeChildKey = thisDevice + "-" + cameraID;

	FirebaseDatabase.getInstance().getReference(cameraNode).child(cameraNodeChildKey).child("LiveStreamingBroadcastStatus").setValueAsync(broadcastStatus);

    }

    private class MainLoop implements Runnable {

	public MainLoop() {

	}

	@Override
	public void run() {

	    printLog(LogTopics.LOG_TOPIC_MAIN, "Session started");

	    attachFirebaseIncomingMessagesNodeListener();

	    deviceStatusUpdateTimer = new Timer();
	    deviceStatusUpdateTimer.scheduleAtFixedRate(new DeviceStatusUpdateTask(), 0, deviceStatusUpdateRate);

	    while (loopFlag) {

		/*
		 * Processa i comandi locali
		 */

		if (readyToProcessLocalCommands)
		    processLocalCommands();

		/* Dorme per il tempo TICK_TIME_MS */

		sleepSafe(TICK_TIME_MS);

	    }

	    detachFirebaseIncomingMessagesNodeListener();

	    deviceStatusUpdateTimer.cancel();
	    printLog(LogTopics.LOG_TOPIC_MAIN, "End of session");

	    System.exit(0);

	}

    };

    public DomoticCore() {

	printLog(LogTopics.LOG_TOPIC_INIT, "Domotic for linux desktop - by Lorenzo Failla");

	/*
	 * Retrieves all the parameters values from the configuration file
	 */

	printLog(LogTopics.LOG_TOPIC_INIT, "Configuration file reading started.");

	if (!getConfiguration())
	    System.exit(ExitCodes.EXIT_CODE___WRONG_CONFIGURATION);

	printLog(LogTopics.LOG_TOPIC_INIT, "Configuration file reading successfully completed.");

	/*
	 * Initialize the TCP interface
	 */
	tcpInterface.setListener(tcpInterfaceListener);
	tcpInterface.init();

	/*
	 * Attempts to delete all the local commands
	 */

	int[] localCommandPurgeResult = purgeLocalCommands();
	printLog(LogTopics.LOG_TOPIC_INIT, String.format("%d obsolete local command(s) found.", localCommandPurgeResult[0]));

	if (localCommandPurgeResult[1] > 0) {
	    printLog(LogTopics.LOG_TOPIC_INIT, String.format("[!] It was not possible to delete %d obsolete local command(s).\nCheck directory \"%s\" for issues", localCommandPurgeResult[1], DefaultConfigValues.LOCAL_COMMAND_DIRECTORY));
	    System.exit(ExitCodes.EXIT_CODE___UNABLE_TO_DELETE_LOCAL_COMMANDS);

	} else {

	    printLog(LogTopics.LOG_TOPIC_INIT, "All obsolete local command(s) have been purged");

	}

	// Available services probe
	printLog(LogTopics.LOG_TOPIC_INIT, "Available services probing started.");
	retrieveServices();
	printLog(LogTopics.LOG_TOPIC_INIT, "Available services probing completed.");

	// Firebase Database connection
	printLog(LogTopics.LOG_TOPIC_INIT, "Connection to Firebase Database started.");

	if (!connectToFirebaseDatabase())
	    System.exit(ExitCodes.EXIT_CODE___UNABLE_TO_CONNECT_TO_FIREBASE);

	incomingCommands = FirebaseDatabase.getInstance().getReference(String.format("/Groups/%s/Devices/%s/IncomingCommands", groupName, thisDevice));

	printLog(LogTopics.LOG_TOPIC_INIT, "Connection to Firebase Database successfully completed.");

	/*
	 * verifica di avere le credenziali per l'accesso a Youtube
	 */

	if (youTubeJSONLocation != "" && youTubeOAuthFolder != "") {

	    printLog(LogTopics.LOG_TOPIC_INIT, "Checking Youtube credentials...");

	    // inizializza uno YouTubeComm e assegna il listener
	    try {

		youTubeComm = new YouTubeComm(APP_NAME, youTubeJSONLocation, youTubeOAuthFolder);
		youTubeComm.setListener(youTubeCommListener);

		printLog(LogTopics.LOG_TOPIC_INIT, "Youtube credentials successfully verified.");

	    } catch (YouTubeNotAuthorizedException e) {

		printLog(LogTopics.LOG_TOPIC_INIT, "Failed to verify Youtube credentials. " + e.getMessage());
	    }

	} else {

	    printLog(LogTopics.LOG_TOPIC_INIT, "WARNING! Cannot Youtube credentials. Please make sure \"YouTubeJSONLocation\" and \"YouTubeOAuthFolder\" are specified in the configuration file.");
	}

	// Device registration
	printLog(LogTopics.LOG_TOPIC_INIT, "Device registration started.");
	deviceRegistered = false;

	int nOfVideoSurveillanceCameras;
	videoSurveillanceRegistered = 0;

	if (hasVideoSurveillance) {
	    nOfVideoSurveillanceCameras = motionComm.getNOfThreads();
	} else {
	    nOfVideoSurveillanceCameras = -1;
	}

	registerDeviceServices();

	while (!tcpInitialized && !deviceRegistered && (!hasVideoSurveillance || videoSurveillanceRegistered < nOfVideoSurveillanceCameras) && !incomingMessagesCleared && !incomingFilesCleared) {

	    try {

		Thread.sleep(100);

	    } catch (InterruptedException e) {

		printErrorLog(e);
		System.exit(1);

	    }

	}

	printLog(LogTopics.LOG_TOPIC_INIT, "Device registration successfully completed.");

	/* adds a shutdownhook to handle VM shutdown */
	Runtime.getRuntime().addShutdownHook(new Thread() {

	    public void run() {

		/* adds a log */
		printLog(LogTopics.LOG_TOPIC_TERM, "Termination request catched.");

		/* if specified, shuts down the video surveillance daemon */
		if (hasVideoSurveillance && !videoSurveillanceDaemonShutdownCommand.equals("")) {
		    try {

			parseShellCommand(videoSurveillanceDaemonShutdownCommand);
			printLog(LogTopics.LOG_TOPIC_TERM, "Successfully applied \'" + videoSurveillanceDaemonShutdownCommand + "\' command to VideoSurveillance daemon.");

		    } catch (IOException | InterruptedException e) {
			printErrorLog(e);
		    }
		}

		/*
		 * close the TCP interface
		 */

		tcpInterface.terminate();

		/* unregister the device, so that client cannot connect to it */
		unRegisterDeviceServices();

		while (deviceRegistered && (!hasVideoSurveillance || videoSurveillanceRegistered > 0)) {

		    try {

			Thread.sleep(100);

		    } catch (InterruptedException e) {
			printErrorLog(e);
		    }

		    /* terminates the main loop */
		    loopFlag = false;

		}

	    };

	});

	// starts the main loop
	new MainLoop().run();

    }

    private RemoteCommand getReply(RemoteCommand incomingCommand) {

	String logContent = String.format("from:\'%s\' hdr:\'%s\' bdy:\'%s\'", incomingCommand.getReplyto(), incomingCommand.getHeader(), incomingCommand.getBody()).replace("\n", "\\n");

	printLog(LogTopics.LOG_TOPIC_INMSG, logContent);

	switch (incomingCommand.getHeader()) {

	case "__keepalive_timeout":

	    return null;

	case "__quit":

	    loopFlag = false;
	    return null;

	case "__requestWelcomeMessage":

	    // sends a reply with the name of this device
	    return new RemoteCommand(ReplyPrefix.WELCOME_MESSAGE.prefix(), encode(thisDevice), "null");

	case "__requestUpTime":

	    return new RemoteCommand(ReplyPrefix.UPTIME_MESSAGE.prefix(), encode(getUptime()), "null");

	case "__requestFreeSpace":

	    return new RemoteCommand(ReplyPrefix.FREE_SPACE_REPLY.prefix(), encode(String.format("%.2f MBi\n", getFreeSpace("/"))), "null");

	case "__listTorrents":

	    return new RemoteCommand(ReplyPrefix.TORRENTS_LIST.toString(), encode(getTorrentsList()), "null");

	case "__start_torrent":

	    return new RemoteCommand(ReplyPrefix.TORRENT_STARTED.toString(), encode(startTorrent(incomingCommand.getBody())), "null");

	case "__stop_torrent":

	    return new RemoteCommand(ReplyPrefix.TORRENT_STOPPED.toString(), encode(stopTorrent(incomingCommand.getBody())), "null");

	case "__remove_torrent":

	    return new RemoteCommand(ReplyPrefix.TORRENT_REMOVED.toString(), encode(removeTorrent(incomingCommand.getBody())), "null");

	case "__add_torrent":

	    return new RemoteCommand(ReplyPrefix.TORRENT_ADDED.toString(), encode(addTorrent(incomingCommand.getBody())), "null");

	case "__beep":

	    return null;

	case "__execute_command":

	    return null;

	case "__get_homedir":

	    String homeDirReply;

	    try {

		homeDirReply = parseShellCommand("pwd");
		return new RemoteCommand(ReplyPrefix.HOMEDIR_RESPONSE.prefix(), encode(homeDirReply), "null");

	    } catch (IOException | InterruptedException e) {

		return null;

	    }

	case "__get_directory_content":

	    String directoryContentReply;

	    try {

		directoryContentReply = GeneralUtilitiesLibrary.parseShellCommand(String.format("ls %s -al", incomingCommand.getBody()));
		return new RemoteCommand(ReplyPrefix.DIRECTORY_CONTENT_RESPONSE.prefix(), encode(directoryContentReply), "null");

	    } catch (IOException | InterruptedException e) {

		return null;

	    }

	case "__get_file":

	    uploadFileToStorage(incomingCommand.getBody(), incomingCommand.getReplyto(), false);

	    return null;

	case "__wakeonlan":

	    int deviceId = Integer.parseInt(incomingCommand.getBody());

	    if (deviceId < wolDevices.length) {
		try {

		    parseShellCommand("wakeonlan " + wolDevices[deviceId]);
		    printLog(LogTopics.LOG_TOPIC_CMDEXEC, "\'wakeonlan\' command sent to device ID\'" + deviceId + "\':, MAC address:\'" + wolDevices[deviceId] + "\'");

		} catch (IOException | InterruptedException e) {

		    printErrorLog(e);

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

	    sshShells.get(incomingCommand.getReplyto()).insertInput(Base64.decodeBase64(incomingCommand.getBody()));

	    return null;

	case "__ssh_special":

	    switch (incomingCommand.getBody()) {

	    case "keyBackspace":
		sshShells.get(incomingCommand.getReplyto()).keyBackspace();
		break;

	    case "keyDelete":
		sshShells.get(incomingCommand.getReplyto()).keyDelete();
		break;

	    case "keyUp":
		sshShells.get(incomingCommand.getReplyto()).keyUp();
		break;

	    case "keyDown":
		sshShells.get(incomingCommand.getReplyto()).keyDown();
		break;

	    case "keyRight":
		sshShells.get(incomingCommand.getReplyto()).keyRight();
		break;

	    case "keyLeft":
		sshShells.get(incomingCommand.getReplyto()).keyLeft();
		break;

	    }

	    return null;

	case "__close_ssh":

	    sshShells.get(incomingCommand.getReplyto()).disconnect();

	    return null;

	case "__request_shot":

	    motionComm.captureFrames(incomingCommand.getBody(), 1, "DBNode");

	    return null;

	case "__request_shot_data":

	    motionComm.captureFrames(incomingCommand.getBody(), 1, incomingCommand.getReplyto());

	    return null;

	case "__request_shots":

	    motionComm.captureFrames(incomingCommand.getBody(), 30, "DBNode");

	    return null;

	case "__request_motion_event":

	    motionComm.requestMotionEvent(incomingCommand.getBody(), 30);

	    return null;

	case "__start_modet":

	    motionComm.startModet(incomingCommand.getBody());

	    return null;

	case "__stop_modet":

	    motionComm.stopModet(incomingCommand.getBody());

	    return null;

	case "__manage_vs_motion_event":

	    processMotionEvent(incomingCommand.getBody());

	    return null;

	case "__get_currenttimemillis":

	    return new RemoteCommand(ReplyPrefix.REMOTE_CURRENT_TIME.prefix(), encode("" + System.currentTimeMillis()), thisDevice);

	case "__refresh_tmate":

	    refreshTmate();
	    return null;

	case "__start_streaming_request":

	    // se � stata inizializzata l'interfaccia Youtube e si dispone
	    // della
	    // videosorveglianza, crea un canale di streaming
	    if (youTubeComm != null && hasVideoSurveillance && motionComm != null) {

		// controlla che vi sia gi� una richiesta streaming in corso
		// sul
		// thread specificato. se non c'� nessuna richiesta, crea un
		// nuovo streaming
		if (youTubeLiveStreamRequestors.containsValue(incomingCommand.getBody())) {

		    // c'� gi� una richiesta in corso. stampa un messaggio
		    // di
		    // log
		    printLog(LogTopics.LOG_TOPIC_VSURV, "Live streaming already requested for camera ID: " + incomingCommand.getBody());

		} else {

		    // non c'� una richiesta in corso per il thread
		    // specificato.
		    // registra la richiesta e avvia la creazione dello stream
		    printLog(LogTopics.LOG_TOPIC_VSURV, String.format("Live streaming request registered for requestor:\"%s\", camera ID: \"%s\"", incomingCommand.getBody(), incomingCommand.getReplyto()));
		    youTubeLiveStreamRequestors.put(incomingCommand.getReplyto(), incomingCommand.getBody());

		    // aggiorna i dati relativi allo streaming
		    setCameraLiveBroadcastStatus(incomingCommand.getBody(), "creating");
		    setCameraLiveBroadcastData(incomingCommand.getBody(), "");

		    String streamingTitle = String.format("Live from %s", motionComm.getCameraName(incomingCommand.getBody()));
		    // crea un canale di streaming.
		    // la risposta arriva sul callback implementato nel listener
		    youTubeComm.createLiveStream(streamingTitle, incomingCommand.getReplyto(), incomingCommand.getBody());

		}

	    }
	    return null;

	case "__end_streaming_request":

	    // rimuove il richiedente dalla mappa
	    youTubeLiveStreamRequestors.remove(incomingCommand.getReplyto());

	    // controlla se c'� ancora almeno un richiedente per il thread ID
	    // specificato. se non c'� almeno un richiedente rimasto, termina
	    // lo
	    // streaming

	    if (!youTubeLiveStreamRequestors.containsValue(incomingCommand.getBody())) {

		// non ci sono richiedenti per il thread ID specificato

		printLog(LogTopics.LOG_TOPIC_CMDEXEC, String.format("No more requestors for camera ID %s. Streaming will terminate.", incomingCommand.getBody()));
		// ferma l'esecuzione dello streaming

		try {
		    // ferma l'esecuzione in background di ffmpeg, chiamando il
		    // kill del process id relativo al thread contenuto nel
		    // corpo del messaggio

		    printLog(LogTopics.LOG_TOPIC_SHEXEC, "yt-stopstream " + incomingCommand.getBody());
		    parseShellCommand("yt-stopstream " + incomingCommand.getBody());

		} catch (IOException | InterruptedException e) {

		    printErrorLog(e);

		}

	    } else {

		printLog(LogTopics.LOG_TOPIC_CMDEXEC, String.format("There are still requestors for camera ID %s. Streaming will continue.", incomingCommand.getBody()));

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
	    youTubeLiveStreamRequestors.values().remove(incomingCommand.getBody());

	    // rimuove il live broadcast dalla mappa
	    printLog(LogTopics.LOG_TOPIC_CMDEXEC, String.format("Removing Youtube live broadcast: \"%s\"", youTubeLiveBroadcasts.get(incomingCommand.getBody())));

	    youTubeComm.deleteLiveBroadcast(youTubeLiveBroadcasts.get(incomingCommand.getBody()));

	    return null;

	case "__connect_vpn":

	    if (vpnConnectionConfigFilePath != null) {

		try {
		    execShellCommand("connect-vpn");
		    Thread.sleep(5000);
		    updateVPNStatus();

		} catch (IOException | InterruptedException e) {
		    printErrorLog(e);
		}

	    }

	    return null;

	case "__disconnect_vpn":

	    if (vpnConnectionConfigFilePath != null) {

		try {
		    execShellCommand("disconnect-vpn");
		    Thread.sleep(5000);
		    updateVPNStatus();

		} catch (IOException | InterruptedException e) {
		    printErrorLog(e);
		}

	    }

	    return null;
	    
	case "__update_status":
	    
	    updateDeviceStatus();
	    
	    return null;

	default:
	    return new RemoteCommand(ReplyPrefix.UNRECOGNIZED_COMMAND.prefix(), "null", thisDevice);

	} /* fine switch lettura comandi */

    }

    private void removeCommand(String id, int operationAfterRemoval) {
	/*
	 * Removes, if needed, a command from the queue in Firebase DB Node.
	 * Also,
	 * performs any operation, if needed, after command removal.
	 */

	if (id.startsWith(LOCAL_CMD_PREFIX) || id.startsWith(LOCAL_TCP_PREFIX)) {

	    /*
	     * Command is local (file://) or TCP (tcp://). local command has
	     * already been
	     * deleted before the execution thread was started. TCP command does
	     * not need to
	     * be removed.
	     */

	    // if needed, performs an operation after the removal
	    performOperationAfterRemoval(operationAfterRemoval);

	} else {

	    /*
	     * Command is remote, therefore it needs to be removed from the
	     * Database
	     */

	    // remove the child from the <incomingCommands> Firebase node
	    incomingCommands.child(id).removeValue(new CompletionListener() {

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

    private void replyToRemoteCommand(RemoteCommand rc, String commandID) {

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
		sendMessageToDevice(reply, rc.getReplyto(), commandID);

	    } else {
		//
		// il comando non prevede una risposta

		// rimuove il comando immediatamente
		removeCommand(commandID, -1);

	    }

	}

    }

    private void sendMessageToDevice(RemoteCommand message, String device, String idToRemove) {

	if (device.startsWith("tcp://")) {
	    /*
	     * la risposta dovr� essere inviata tramite interfaccia TCP
	     */

	    String hostID = device.substring(6, device.length());
	    byte[] data = (SocketResponder.HEADER_COMMAND_REPLY + message.toString()).getBytes();
	    tcpInterface.sendData(hostID, data);
	    String sendStatus = "TCP OK.";

	    // stampa un messaggio di log
	    printLog(LogTopics.LOG_TOPIC_OUTMSG, "to:\'" + device + "\' hdr:\'" + message.getHeader() + "\' bdy:\'" + message.getBody() + "\' sts:\'" + sendStatus + "\'");

	} else {
	    /*
	     * la risposta dovr� essere inviata tramite nodo DB Firebase
	     */

	    // ottiene una referenza al nodo del dispositivo che aveva
	    // inviato il comando
	    DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference(String.format("/Groups/%s/Devices/%s/IncomingCommands", groupName, device));

	    // imposta il messaggio di risposta nel nodo, una volta
	    // completata l'operazione rimuove il comando
	    databaseReference.child(getTimeStamp()).setValue(message, new CompletionListener() {

		@Override
		public void onComplete(DatabaseError error, DatabaseReference ref) {
		    //
		    //
		    String sendStatus;
		    if (error != null) {

			sendStatus = "ERROR [c=" + error.getCode() + ",m=" + error.getMessage() + "]";

		    } else {

			sendStatus = "OK";

		    }

		    // stampa un messaggio di log
		    printLog(LogTopics.LOG_TOPIC_OUTMSG, "to:\'" + device + "\' hdr:\'" + message.getHeader() + "\' bdy:\'" + message.getBody() + "\' sts:\'" + sendStatus + "\'");

		    removeCommand(idToRemove, -1);

		}

	    });

	}

    }

    private boolean connectToFirebaseDatabase() {

	FileInputStream serviceAccount = null;
	
	try {
	    
	    serviceAccount = new FileInputStream(jsonAuthFileLocation);
	    FirebaseOptions options = new FirebaseOptions.Builder().setCredentials(GoogleCredentials.fromStream(serviceAccount)).setDatabaseUrl(firebaseDatabaseURL).setStorageBucket(storageBucketAddress).build();

	    signOption = SignUrlOption.signWith(ServiceAccountCredentials.fromStream(new FileInputStream(jsonAuthFileLocation)));

	    FirebaseApp.initializeApp(options);
	    serviceAccount.close();
	    return true;

	} catch (IOException e) {

	    printErrorLog(e);
	    return false;

	} 
	
    }

    private void attachFirebaseIncomingMessagesNodeListener() {

	incomingCommands.removeValue(new CompletionListener() {

	    @Override
	    public void onComplete(DatabaseError error, DatabaseReference ref) {

		if (error == null) {

		    printLog(LogTopics.LOG_TOPIC_INCHK, "Obsolete incoming message purged on Firebase node.");

		    incomingCommands.addChildEventListener(incomingMessagesNodeListener);

		    printLog(LogTopics.LOG_TOPIC_INCHK, "Listener for incoming messages on Firebase node attached.");

		} else {

		    printLog(LogTopics.LOG_TOPIC_ERROR, "Error while removing the obsolete incoming messages on Firebase node. Message: " + error.getMessage() + ".");

		}

	    }

	});

    }

    private void detachFirebaseIncomingMessagesNodeListener() {

	incomingCommands.removeEventListener(incomingMessagesNodeListener);

	printLog(LogTopics.LOG_TOPIC_INCHK, "Listener for incoming messages on Firebase node detached.");

    }

    private void retrieveServices() {

	printLog(LogTopics.LOG_TOPIC_INIT, "\'uptime\' check started.");

	if (allowDirectoryNavigation) {

	    try {

		parseShellCommand("uptime");
		printLog(LogTopics.LOG_TOPIC_INIT, "\'uptime\' check successfully completed.");
		hasDirectoryNavigation = true;

	    } catch (IOException | InterruptedException e) {

		printLog(LogTopics.LOG_TOPIC_INIT, "\'uptime\' check failed. " + e.getMessage());
		hasDirectoryNavigation = false;

	    }

	}

	if (allowTorrentManagement) {

	    printLog(LogTopics.LOG_TOPIC_INIT, "\'transmission-daemon\' check started.");
	    try {
		parseShellCommand("transmission-remote -n transmission:transmission -l");
		printLog(LogTopics.LOG_TOPIC_INIT, "\'transmission-daemon\' successfully completed.");
		hasTorrent = true;

	    } catch (IOException | InterruptedException e) {

		printLog(LogTopics.LOG_TOPIC_INIT, "\'transmission-daemon\' successfully completed. " + e.getMessage());
		hasTorrent = false;

	    }

	}

	if (allowVideoSurveillanceManagement) {

	    printLog(LogTopics.LOG_TOPIC_INIT, "\'Video surveillance daemon [motion]\' check started.");

	    hasVideoSurveillance = checkVideoSurveillance();

	    if (hasVideoSurveillance) {

		printLog(LogTopics.LOG_TOPIC_INIT, "\'Video surveillance daemon [motion]\' check successfully completed.");

	    } else {

		printLog(LogTopics.LOG_TOPIC_INIT, "\'Video surveillance daemon [motion]\' check failed.");

	    }

	}

    }

    private String getTorrentsList() {

	try {

	    return parseShellCommand("transmission-remote -n transmission:transmission -l");

	} catch (IOException | InterruptedException e) {

	    return null;

	}

    }

    private String startTorrent(String torrentID) {

	try {

	    return parseShellCommand("transmission-remote -n transmission:transmission -t" + torrentID + " -s");

	} catch (IOException | InterruptedException e) {

	    return null;

	}

    }

    private String stopTorrent(String torrentID) {

	try {

	    return GeneralUtilitiesLibrary.parseShellCommand("transmission-remote -n transmission:transmission -t" + torrentID + " -S");

	} catch (IOException | InterruptedException e) {

	    return null;

	}

    }

    private String removeTorrent(String torrentID) {

	try {

	    return GeneralUtilitiesLibrary.parseShellCommand("transmission-remote -n transmission:transmission -t" + torrentID + " -r");

	} catch (IOException | InterruptedException e) {

	    return null;

	}

    }

    private String addTorrent(String torrentID) {

	try {

	    return GeneralUtilitiesLibrary.parseShellCommand("transmission-remote -n transmission:transmission -a " + torrentID);

	} catch (IOException | InterruptedException e) {

	    return null;

	}

    }

    private void uploadFileToStorage(String fileName, String requestor, boolean downloadOnRemoteDevice) {

	InputStream inputStreamToUpload;

	// inizializza un oggetto File che punta al nome passato in argomento
	File fileToUpload = new File(fileName);

	// inizializza la connessione allo storage
	Bucket storageBucket = StorageClient.getInstance().bucket();

	printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Upload of file\'" + fileToUpload.getName() + "\' to cloud storage started.");

	try {

	    // inizializza l'InputStream
	    inputStreamToUpload = new FileInputStream(fileName);

	    // apre un thread per effettuare l'upload
	    new Thread() {

		public void run() {

		    // inizia l'upload
		    Blob uploadInfo = storageBucket.create("Users/lorenzofailla/uploads/" + fileToUpload.getName(), inputStreamToUpload);

		    printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Upload of file\'" + fileToUpload.getName() + "\' to cloud storage successfully completed.");

		    // notifica l'upload del file
		    notifyFileUpload(new FileInCloudStorage(getTimeStamp().toString(), fileToUpload.getName(), requestor, uploadInfo.getMediaLink(), uploadInfo.getSize(), 0));

		}

	    }.start();

	} catch (FileNotFoundException e) {

	    printErrorLog(e);

	}

    }

    private void notifyFileUpload(FileInCloudStorage file) {

	DatabaseReference cloudUploadedFiles = FirebaseDatabase.getInstance().getReference("Users/lorenzofailla/CloudStorage");

	cloudUploadedFiles.child(file.getItemID()).setValueAsync(file);

    }

    private void shutDownMachine() {
	//
	// effettua lo shutdown della macchina

	try {

	    parseShellCommand("sudo shutdown -h now");

	} catch (IOException | InterruptedException e) {

	    printLog(LogTopics.LOG_TOPIC_EXCEPTION, e.getMessage());

	}

    }

    private void rebootMachine() {
	//
	// effettua il reboot della macchina

	try {

	    parseShellCommand("sudo reboot");

	} catch (IOException | InterruptedException e) {
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
			    groupName = argument;
			    break;

			case "FirebaseJSONKeyLocation":
			    jsonAuthFileLocation = argument;
			    break;

			case "FirebaseDBRootPath":
			    firebaseDatabaseURL = argument;
			    break;

			case "FirebaseStoragePath":
			    storageBucketAddress = argument;
			    break;

			case "FCMKey":
			    notificationsEnabled = true;
			    fcmServiceKey = argument;
			    break;

			case "DeviceName":
			    thisDevice = argument;
			    break;

			case "AllowDirectoryNavigation":
			    hasDirectoryNavigation = argument.equals("yes");
			    break;

			case "AllowTorrentManagement":
			    allowTorrentManagement = argument.equals("yes");
			    break;

			case "AllowVideoSurveillanceManagement":
			    allowVideoSurveillanceManagement = argument.equals("yes");
			    break;

			case "AllowSSH":
			    allowSSH = argument.equals("yes");
			    break;

			case "VideoSurveillanceServerAddress":
			    videoSurveillanceServerAddress = argument;
			    break;

			case "VideoSurveillanceServerControlPort":
			    try {
				videoSurveillanceServerControlPort = Integer.parseInt(argument);
			    } catch (NumberFormatException e) {
				videoSurveillanceServerControlPort = -1;
			    }
			    break;

			case "chmodDevice":
			    try {

				parseShellCommand("sudo chmod 777 " + argument);
				printLog(LogTopics.LOG_TOPIC_INIT, "Successfully chmodded \'" + argument + "\'");

			    } catch (InterruptedException e) {

				printLog(LogTopics.LOG_TOPIC_INIT, "Unable to chmod \'" + argument + "\': " + e.getMessage());
			    }

			    break;

			case "VideoSurveillanceDaemonAction":
			    try {
				parseShellCommand(argument);
				printLog(LogTopics.LOG_TOPIC_INIT, "Successfully applied \'" + argument + "\' command to VideoSurveillance daemon.");
			    } catch (InterruptedException e) {

				printLog(LogTopics.LOG_TOPIC_INIT, "Warning! unable to apply \'" + argument + "\' command to VideoSurveillance daemon: " + e.getMessage());
			    }
			    break;

			case "VideoSurveillanceDaemonShutdownCommand":

			    videoSurveillanceDaemonShutdownCommand = argument;

			    break;

			case "WOLDevice":
			    String[] args = argument.split("_");

			    if (args.length == 2) {
				wolDevName.add(args[0]);
				wolDevList.add(args[1]);
			    } else {

				printLog(LogTopics.LOG_TOPIC_INIT, "Warning! Expected {Device Name}_{Device MAC Address} at line " + lineIndex + ". Found: " + args.length + " splits. Skipping");
			    }

			    break;

			case "SSHUsername":
			    sshUsername = argument;
			    break;

			case "SSHPassword":
			    sshPassword = argument;
			    break;

			case "SSHHost":
			    sshHost = argument;
			    break;

			case "SSHPort":
			    sshPort = Integer.parseInt(argument);
			    break;

			case "CameraNames":
			    break;

			case "YouTubeClientJSONLocation":
			    youTubeJSONLocation = argument;
			    break;

			case "YouTubeOAuthFolder":
			    youTubeOAuthFolder = argument;
			    break;

			case "VPNConfigFile":
			    vpnConnectionConfigFilePath = argument;
			    break;

			default:
			    printLog(LogTopics.LOG_TOPIC_INIT, "Unknown command \'" + command + "\' at line \'" + lineIndex + "\'. Please check and try again.");
			    br.close();
			    return false;
			}

			printLog(LogTopics.LOG_TOPIC_INIT, String.format("Parameter \"%s\"; value=\"%s\"", command, argument));

		    } else {

			// errore di sintassi
			printLog(LogTopics.LOG_TOPIC_INIT, "Syntax error, please check line \'" + lineIndex + "\' in configuration file and try again.");
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

	    if (groupName == "") {
		printLog(LogTopics.LOG_TOPIC_INIT, "No group name specified. Cannot continue.");
		configurationComplete = false;
	    }

	    if (jsonAuthFileLocation == "") {
		printLog(LogTopics.LOG_TOPIC_INIT, "No JSON auth file location specified. Cannot continue.");
		configurationComplete = false;
	    }

	    if (firebaseDatabaseURL == "") {
		printLog(LogTopics.LOG_TOPIC_INIT, "No Firebase database location specified. Cannot continue.");
		configurationComplete = false;
	    }

	    if (storageBucketAddress == "") {
		printLog(LogTopics.LOG_TOPIC_INIT, "No Firebase storage bucket address specified. Cannot continue.");
		configurationComplete = false;
	    }

	    if (allowVideoSurveillanceManagement && videoSurveillanceServerAddress == "") {
		printLog(LogTopics.LOG_TOPIC_INIT, "No video surveillance server address specified. Cannot continue.");
		configurationComplete = false;
	    }

	    if (allowVideoSurveillanceManagement && videoSurveillanceServerControlPort == -1) {
		printLog(LogTopics.LOG_TOPIC_INIT, "No video surveillance server control port specified. Cannot continue.");
		configurationComplete = false;

	    }

	    wolDevices = wolDevList.toArray(new String[0]);
	    wolDeviceNames = wolDevName.toArray(new String[0]);

	    return configurationComplete;

	} catch (IOException e) {

	    printLog(LogTopics.LOG_TOPIC_INIT, e.getMessage() + " Make sure configuration file exists and is readable at specified location: \'" + DefaultConfigValues.CONFIG_FILE_LOCATION + "\'");
	    return false;

	}

    }

    private void registerDeviceServices() {

	DatabaseReference ref = FirebaseDatabase.getInstance().getReference(String.format("/Groups/%s", groupName));

	// crea struttura per i dati del dispositivo
	Map<String, Object> deviceData = new HashMap<String, Object>();
	deviceData.put("deviceName", thisDevice);
	deviceData.put("online", true);
	deviceData.put("hasDirectoryNavigation", hasDirectoryNavigation);
	deviceData.put("hasTorrentManagement", hasTorrent);
	deviceData.put("hasWakeOnLan", wolDevices.length > 0);
	deviceData.put("hasSSH", allowSSH);
	deviceData.put("hasVideoSurveillance", hasVideoSurveillance);

	// crea struttura per i dati dei dispositivi su cui � possibile fare
	// il
	// wake-on-lan
	Map<String, Object> wolDeviceIds = new HashMap<String, Object>();

	for (int i = 0; i < wolDevices.length; i++) {
	    Map<String, Object> wolDeviceData = new HashMap<String, Object>();
	    wolDeviceData.put("Id", "" + i);
	    wolDeviceData.put("Name", wolDeviceNames[i]);
	    wolDeviceData.put("Address", wolDevices[i]);

	    wolDeviceIds.put("" + i, wolDeviceData);

	}

	deviceData.put("WOLDevices", wolDeviceIds);

	if (hasVideoSurveillance) {
	    deviceData.put("cameraIDs", motionComm.getCamerasList());
	    deviceData.put("cameraNames", motionComm.getCamerasNames());

	}

	if (vpnConnectionConfigFilePath != null) {
	    updateVPNStatus();
	} else {
	    deviceData.put("VPNStatus", "<not-available>");
	}

	ref.child("Devices").child(thisDevice).updateChildren(deviceData, new CompletionListener() {

	    @Override
	    public void onComplete(DatabaseError error, DatabaseReference ref) {

		deviceRegistered = error == null;

	    }

	});

	/*
	 * update the data of the video surveillance cameras
	 */

	if (hasVideoSurveillance) {

	    String[] threadIDs = motionComm.getThreadsIDs();

	    for (int i = 0; i < threadIDs.length; i++) {

		HashMap<String, Object> cameraInfo = motionComm.getCameraInfo(threadIDs[i]);
		if (youTubeComm != null) {

		    // inizializza l'HashMap contenente le informazioni dei
		    // broadcast delle varie videocamera
		    youTubeLiveBroadcasts = new HashMap<String, String>();
		    youTubeLiveStreamRequestors = new HashMap<String, String>();

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

		ref.child("VideoSurveillance").child("AvailableCameras").child(String.format("%s-%s", thisDevice, threadIDs[i])).updateChildren(cameraInfo, new CompletionListener() {

		    @Override
		    public void onComplete(DatabaseError error, DatabaseReference ref) {

			if (error == null) {
			    videoSurveillanceRegistered++;
			}

		    }

		});

	    }

	}

	/* delete all previous incoming commands */

	ref.child(String.format("Devices/%s/IncomingCommands", thisDevice)).removeValue(new CompletionListener() {

	    @Override
	    public void onComplete(DatabaseError error, DatabaseReference ref) {

		incomingMessagesCleared = true;

	    }

	});

	/* delete all previous incoming files */

	ref.child(String.format("Devices/%s/IncomingFiles", thisDevice)).removeValue(new CompletionListener() {

	    @Override
	    public void onComplete(DatabaseError error, DatabaseReference ref) {

		incomingFilesCleared = true;

	    }

	});

    }

    private void unRegisterDeviceServices() {

	DatabaseReference ref = FirebaseDatabase.getInstance().getReference(String.format("/Groups/%s", groupName));

	Map<String, Object> deviceData = new HashMap<String, Object>();

	deviceData.put("online", false);
	deviceData.put("cameraIDs", null);
	deviceData.put("cameraNames", null);

	ref.child("Devices").child(thisDevice).updateChildren(deviceData, new CompletionListener() {

	    @Override
	    public void onComplete(DatabaseError error, DatabaseReference ref) {

		deviceRegistered = false;

	    }

	});

	/*
	 * rimuove le videocamere dal database
	 */
	if (hasVideoSurveillance) {

	    for (int i = 0; i < motionComm.getNOfThreads(); i++) {

		ref.child("VideoSurveillance").child("AvailableCameras").child(String.format("%s-%d", thisDevice, i)).removeValue(new CompletionListener() {

		    @Override
		    public void onComplete(DatabaseError error, DatabaseReference ref) {

			if (error == null) {
			    videoSurveillanceRegistered--;
			}

		    }

		});

	    }

	}

    }

    private void uploadFileToDataSlots(String fileName, String deviceToReply) {

	InputStream inputStreamToUpload;
	File fileToUpload;

	DatabaseReference incomingFilesNode = FirebaseDatabase.getInstance().getReference(String.format("Groups/%s/Devices/%s/IncomingFiles", groupName, deviceToReply));

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
		printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Uploading file \'" + fileName + "' as dataslot. " + slots + " processed.");

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
			printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Upload of file \'" + fileName + "' as dataslot terminated with error. Error code: \'" + error.getCode() + "\', error message: \'" + error.getMessage() + "\'");

		    } else {

			sendMessageToDevice(new RemoteCommand(ReplyPrefix.FILE_READY_FOR_DOWNLOAD.prefix(), childTimeStamp, null), deviceToReply, null);

			printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Upload of file \'" + fileName + "' as dataslot successfully terminated.");

		    }

		}

	    });

	} catch (IOException e) {

	    printErrorLog(e);

	}

    }

    /*
     * SSH Shell related methods and functions
     * 
     */

    private void initializeSSHShell(String remoteDev) {

	printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Initializing secure shell session with device \'" + remoteDev + "\'");

	sshShell = new SSHShell(sshHost, sshUsername, sshPassword, sshPort);
	sshShell.setListener(new SSHShellListener() {

	    @Override
	    public void onConnected() {
		printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Successfully connected secure shell session with device \'" + remoteDev + "\'");

		sendMessageToDevice(new RemoteCommand(ReplyPrefix.SSH_SHELL_READY.prefix(), remoteDev, "null"), remoteDev, null);

	    }

	    @Override
	    public void onDisconnected() {
		sendMessageToDevice(new RemoteCommand(ReplyPrefix.SSH_SHELL_CLOSED.prefix(), remoteDev, "null"), remoteDev, null);
		removeShell(remoteDev);
		sshShell = null;

	    }

	    @Override
	    public void onCreated() {
		printLog(LogTopics.LOG_TOPIC_CMDEXEC, "Successfully created secure shell session with device \'" + remoteDev + "\'");
		sshShell.connect();

	    }

	    @Override
	    public void onError(Exception e) {

		printErrorLog(e);

	    }

	    @Override
	    public void onOutputDataChanged(byte[] data) {
		FirebaseDatabase.getInstance().getReference(String.format("Groups/%s/Devices/%s/SSHShells/%s/OutputData", groupName, thisDevice, remoteDev)).child("" + System.currentTimeMillis()).setValueAsync(Base64.encodeBase64String(data));

	    }

	});

	sshShell.initialize();
	sshShells.put(remoteDev, sshShell);

    }

    private void removeShell(String shellId) {

	sshShells.remove(shellId);

	FirebaseDatabase.getInstance().getReference(String.format("/Groups/%s/Devices/%s/SSHShells/%s", groupName, thisDevice, shellId)).removeValue(new CompletionListener() {

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

	    printLog(LogTopics.LOG_TOPIC_WARNING, "\'motion\' is not installed on this host.You may install \'motion\' by typing \'sudo apt-get install motion\'Videosurveillance features are disabled on this host.");
	    return false;
	}

	motionComm = new MotionComm(videoSurveillanceServerAddress, thisDevice, videoSurveillanceServerControlPort);

	motionComm.setListener(motionCommListener);

	if (motionComm.isHTMLOutputEnabled()) {

	    printLog(LogTopics.LOG_TOPIC_WARNING, "\'motion\' is installed on this host, but output in HTML format is enabled. For the domotic-motion interface to run properly, motion output has to be in plain format. You may enable the plain output by setting \'webcontrol_html_output off\' in your motion configuration file.\nVideosurveillance features are disabled on this host.");
	    motionComm.setListener(null);
	    motionComm = null;
	    return false;

	}

	int nOfThreads = motionComm.getNOfThreads();
	// System.out.println(nOfThreads);
	if (nOfThreads < 1) {

	    printLog(LogTopics.LOG_TOPIC_WARNING, "No active threads found on motion daemon.");
	    motionComm.setListener(null);
	    motionComm = null;
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
	 * When the
	 * upload is completed, a data entry is generated into the Events node
	 * of the
	 * Firebase Database, and a real time notification is sent to the group
	 * topic,
	 * so that all devices may see it.
	 * 
	 */

	HashMap<String, String> paramsMap = parseMeta(params);

	if (!paramsMap.containsKey("file_path") || !paramsMap.containsKey("thread_id"))
	    return;

	// set the position in the Firebase Cloud Storage
	String remotePosition = MOTION_EVENTS_STORAGE_CLOUD_URL.replaceAll("%%group_name&&", groupName).replaceAll("%%device_name&&", thisDevice).replaceAll("%%thread_id&&", paramsMap.get("thread_id"));

	// retrieve the picture of the event, as provided by motion daemon, as
	// byte array, compressed ans string encoded
	String eventPictureData = Base64.encodeBase64String(compress(getFileAsBytes(getEventJpegFileName(paramsMap.get("file_path")))));

	// print log entry
	printLog(LogTopics.LOG_TOPIC_VSURV, String.format("Starting upload of video file for motion event on bucket: \"%s\"", remotePosition));

	// set up the FirebaseCloudUploader and start the upload operation
	FirebaseCloudUploader uploader = new FirebaseCloudUploader(paramsMap.get("file_path"), remotePosition).setListener(new FirebaseCloudUploaderListener() {

	    @Override
	    public void onError(FirebaseCloudUploader uploader, Exception e) {

		printErrorLog(e);

	    }

	    @Override
	    public void onComplete(FirebaseCloudUploader uploader, Blob info, String shortFileName) {

		/*
		 * upload of the video file completed
		 */

		// print log entry
		printLog(LogTopics.LOG_TOPIC_VSURV, "Video file for motion event successfully uploaded (" + info.getSize() + " bytes).");

		// retrieve the URL of the video file of the event
		String downloadURL = FirebaseCloudUploader.getSignedUrl(info, DEFAULT_MOTION_VIDEO_LINK_TIMEOUT, TimeUnit.DAYS, jsonAuthFileLocation);

		// generate the HashMap to be put into the Firebase Database
		// Events Node
		HashMap<String, Object> eventData = new HashMap<String, Object>();

		eventData.put("CameraFullID", thisDevice + "-" + paramsMap.get("thread_id")); // pair
		// device
		// id
		// +
		// thread
		// id
		eventData.put("Date", MotionComm.getDateFromMotionFileName(paramsMap.get("file_path"))); // event
		// date
		eventData.put("Time", MotionComm.getTimeFromMotionFileName(paramsMap.get("file_path"))); // event
		// time
		eventData.put("ThreadID", paramsMap.get("thread_id")); // thread
		// id
		eventData.put("CameraName", motionComm.getCameraName(paramsMap.get("thread_id"))); // camera
		// name
		eventData.put("VideoLink", shortFileName); // short file name of
		// the video file of
		// the event
		eventData.put("Device", thisDevice); // device id
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
		String eventsNode = MOTION_EVENTS_DB_NODE.replace("%%group_name&&", groupName);
		String eventsNodeChildKey = thisDevice + "-" + paramsMap.get("thread_id") + "-" + System.currentTimeMillis();

		// write the HashMap in the position of the Events Node in the
		// Firebase Database
		FirebaseDatabase.getInstance().getReference(eventsNode).child(eventsNodeChildKey).setValue(eventData, new CompletionListener() {

		    @Override
		    public void onComplete(DatabaseError error, DatabaseReference ref) {

			/*
			 * the Events Node in the Firebase Database has been
			 * successfully updated
			 */

			if (error == null) {

			    /*
			     * send a notification via Firebase Cloud Messaging
			     * service
			     */

			    // set up the message data payload

			    String notificationID;

			    if (notificationsEnabled) {

				JSONObject data = new JSONObject(); // payload
				data.put("eventID", eventsNodeChildKey);
				data.put("previewURL", downloadURL);
				notificationID = sendFCM(fcmServiceKey, "/topics/" + groupName, "Motion detected", motionComm.getCameraName(paramsMap.get("thread_id")), data.toString());

			    } else {

				notificationID = "N/A";

			    }

			    printLog(LogTopics.LOG_TOPIC_VSURV, "Data for motion event successfully uploaded. Notification sent - " + notificationID);

			} else {

			    printLog(LogTopics.LOG_TOPIC_ERROR, String.format("Error during motion event data upload: %s", error.getMessage()));

			}

		    }

		});

	    }

	}).startUpload();

    }

    private void processLocalCommands() {

	/*
	 * inhibit the system to call this method
	 */
	readyToProcessLocalCommands = false;

	/*
	 * obtain an array with all the local command files
	 */

	File[] localCmdFiles = getLocalCommandsFiles();

	/*
	 * if local command files are found, for each local command file an
	 * execution
	 * thread is created and the file is deleted
	 */

	if (localCmdFiles != null) {

	    for (File f : localCmdFiles) {

		/*
		 * create a new thread to execute the local command
		 */
		RemoteCommand command = parseLocalCommand(readPlainTextFromFile(f));

		new Thread() {

		    public void run() {

			replyToRemoteCommand(command, String.format("file://%s", f.getAbsolutePath()));

		    }

		}.start();

		/*
		 * delete the local command file
		 */

		f.delete();

	    }

	}

	/*
	 * allow the system to call this method
	 */
	readyToProcessLocalCommands = true;

    }

    private int[] purgeLocalCommands() {

	/*
	 * Deletes all the local commands. Returns a size 2 integer array:
	 * position 0 is
	 * the number of files to be deleted, position 1 is the number of not
	 * deleted
	 * files
	 */

	int[] result = { 0, 0 };
	int deletedFiles = 0;

	File[] obsoleteLocalCmdFiles = getLocalCommandsFiles();

	if (obsoleteLocalCmdFiles != null) {
	    for (File f : obsoleteLocalCmdFiles) {
		if (f.delete())
		    deletedFiles++;
		;
	    }

	    result[0] = obsoleteLocalCmdFiles.length;
	    result[1] = obsoleteLocalCmdFiles.length - deletedFiles;

	}

	return result;

    }

    private File[] getLocalCommandsFiles() {

	FilenameFilter filter = new FilenameFilter() {

	    @Override
	    public boolean accept(File dir, String name) {
		return name.toLowerCase().endsWith(".cmd");
	    }

	};

	File localCmdDirectory = new File(DefaultConfigValues.LOCAL_COMMAND_DIRECTORY);
	return localCmdDirectory.listFiles(filter);

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
	    printErrorLog(e);

	} catch (InterruptedException e) {

	    // print log
	    printErrorLog(e);

	}

    }

    private void updateVPNStatus() {

	try {
	    String vpnStatus = parseShellCommand("show-vpn-ip").replaceAll("\n", "");
	    String deviceNode = String.format("%s/%s/%s/%s", GROUP_NODE, groupName, DEVICES_NODE, thisDevice);

	    FirebaseDatabase.getInstance().getReference(deviceNode).child("VPNStatus").setValue(vpnStatus, new CompletionListener() {

		@Override
		public void onComplete(DatabaseError error, DatabaseReference ref) {

		    if (error != null) {
			printLog(LogTopics.LOG_TOPIC_ERROR, error.getMessage());
		    }

		}

	    });

	} catch (IOException e) {
	    printErrorLog(e);

	} catch (InterruptedException e) {
	    printErrorLog(e);

	}

    }

    private void updateDeviceStatus() {

	String uptimeReply = getUptime();
	double freespaceReply = getFreeSpace("/");

	HashMap<String, Object> deviceStatusData = new HashMap<String, Object>();
	deviceStatusData.put("Uptime", uptimeReply);
	deviceStatusData.put("FreeSpace", freespaceReply);
	deviceStatusData.put("RunningSince", runningSince);
	deviceStatusData.put("LastUpdate", System.currentTimeMillis());

	String deviceStatusDBNodePath = GROUP_NODE + "/" + groupName + "/" + DEVICES_NODE + "/" + thisDevice;

	DatabaseReference deviceStatusDBRef = FirebaseDatabase.getInstance().getReference(deviceStatusDBNodePath);
	deviceStatusDBRef.child(STATUS_NODE).setValue(deviceStatusData, new CompletionListener() {

	    @Override
	    public void onComplete(DatabaseError error, DatabaseReference ref) {

		if (error == null) {
		    firebaseDBUpdateTimeoutTimer.cancel();
		} else {
		    printLog(LogTopics.LOG_TOPIC_ERROR, "Unable to update device status in Firebase DB. Message=\"" + error.getMessage() + "\"");
		}

	    }

	});

	firebaseDBUpdateTimeoutTimer = new Timer();
	firebaseDBUpdateTimeoutTimer.schedule(new FirebaseDBUpdateTimeoutTask(), firebaseDBUpdateTimeOut);

    }

}
