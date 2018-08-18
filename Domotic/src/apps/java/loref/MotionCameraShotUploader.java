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

import static apps.java.loref.GeneralUtilitiesLibrary.connectToFirebaseDatabase;
import static apps.java.loref.GeneralUtilitiesLibrary.compress;
import static apps.java.loref.GeneralUtilitiesLibrary.getFileAsBytes;
import static apps.java.loref.GeneralUtilitiesLibrary.getTimeStamp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import com.google.api.client.util.Base64;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import com.google.firebase.database.FirebaseDatabase;

public class MotionCameraShotUploader {

    private static final String DEFAULT_CONFIG_FILE_LOCATION = "/etc/domotic.conf";
    // private static final String DEFAULT_CONFIG_FILE_LOCATION =
    // "C:\\Users\\lore_f\\workspace\\Domotic\\etc\\domotic.conf";

    private String groupName;
    private String jsonAuthFileLocation;
    private String firebaseDatabaseURL;
    private String deviceName;

    private String configFileLocation;
    private String threadID;
    private String imgFileName;

    private boolean loop;
    private boolean uploadError;

    public static void main(String[] args) {

	String configFileLocation = "";
	String imageFileLocation = "";
	String threadID = "";

	boolean configFileLocationDefined = false;
	boolean imageFileLocationDefined = false;
	boolean threadIDdefined = false;

	//
	// process the arguments passed
	for (int i = 0; i < args.length; i++) {

	    switch (args[i]) {

	    case "-c":
		//
		// configuration file path
		i++;
		configFileLocation = args[i];

		configFileLocationDefined = true;

		break;

	    case "-i":
		//
		// image file
		i++;
		imageFileLocation = args[i];
		imageFileLocationDefined = true;
		break;

	    case "-t":
		//
		// threadID
		i++;
		try {

		    threadID = args[i];
		    threadIDdefined = true;

		} catch (NumberFormatException e) {

		    threadIDdefined = false;

		}

		break;

	    }

	}

	//
	// check the arguments passed

	if (!(imageFileLocationDefined && threadIDdefined)) {
	    //
	    //

	    String usageMessage = new StringBuilder().append("motion-shotuploader | by Lorenzo Failla\n").append("ERROR: no image file and/or thread id specified\n\n").append("Usage: motion-shotuploader -i {path to image file} -t {thread id} [-c {path to configuration file}]").toString();

	    System.out.println(usageMessage);

	} else {
	    //
	    //

	    MotionCameraShotUploader core;
	    if (configFileLocationDefined) {
		//
		core = new MotionCameraShotUploader(threadID, imageFileLocation, configFileLocation);

	    } else {
		//
		core = new MotionCameraShotUploader(threadID, imageFileLocation);

	    }

	    MotionCameraShotUploaderListener listener = new MotionCameraShotUploaderListener() {

		@Override
		public void upload(boolean result) {

		    core.removeListener();

		    if (result) {
			// System.out.println("finished");
			System.exit(0);
		    } else {
			System.out.println("Upload error");
			System.exit(1);
		    }
		    return;

		}

		@Override
		public void connected(boolean result) {

		    if (result) {
			// System.out.println("connected");
			core.start();
		    } else {
			System.out.println("Connection error");
			core.removeListener();
			System.exit(1);
			return;
		    }

		}

		@Override
		public void configured(boolean result) {

		    if (result) {
			// System.out.println("configured");
			core.connect();
		    } else {
			System.out.println("Configuration error");
			core.removeListener();
			System.exit(1);
			return;
		    }

		}

		@Override
		public void error(String message) {
		}

	    };

	    core.setListener(listener);
	    // System.out.println("listener atttached");

	    core.init();

	}

    }

    public MotionCameraShotUploader(String threadID, String imgFileName) {

	this.threadID = threadID;
	this.imgFileName = imgFileName;
	this.configFileLocation = DEFAULT_CONFIG_FILE_LOCATION;

    }

    public MotionCameraShotUploader(String threadID, String imgFileName, String configFileLocation) {

	this.threadID = threadID;
	this.imgFileName = imgFileName;
	this.configFileLocation = configFileLocation;

    }

    interface MotionCameraShotUploaderListener {

	void upload(boolean result);

	void connected(boolean result);

	void configured(boolean result);

	void error(String message);

    }

    private MotionCameraShotUploaderListener listener;

    public void setListener(MotionCameraShotUploaderListener listener) {

	this.listener = listener;

    }

    public void removeListener() {

	this.listener = null;

    }

    private boolean getConfig() {

	try {

	    BufferedReader reader = new BufferedReader(new FileReader(configFileLocation));

	    int groupNameDefined = 0;
	    int jsonAuthFileLocDefined = 0;
	    int firebaseDBURLDefined = 0;
	    int deviceNameDefined = 0;

	    String line;
	    while ((line = reader.readLine()) != null) {

		if (line != null && line.length() > 0 && line.charAt(0) != ';') {

		    String[] lineProc = line.split("=");

		    if (lineProc.length == 2) {

			String command = lineProc[0];
			String argument = lineProc[1];

			switch (command) {

			case "GoogleServicesGroupName":
			    groupName = argument;
			    groupNameDefined = 1;
			    break;

			case "FirebaseJSONKeyLocation":
			    jsonAuthFileLocation = argument;
			    jsonAuthFileLocDefined = 1;
			    break;

			case "FirebaseDBRootPath":
			    firebaseDatabaseURL = argument;
			    firebaseDBURLDefined = 1;
			    break;

			case "DeviceName":
			    deviceName = argument;
			    deviceNameDefined = 1;
			    break;

			}

		    }

		}

	    }

	    reader.close();

	    int result = groupNameDefined + jsonAuthFileLocDefined + firebaseDBURLDefined + deviceNameDefined;
	    return (result == 4);

	} catch (IOException e) {

	    if (listener != null) {
		listener.error(e.getMessage());

	    }

	    return false;
	}

    }

    public void init() {

	// attempts to get configuration
	boolean getConfigSuccess = getConfig();

	// notify the get configuration result
	if (listener != null) {
	    listener.configured(getConfigSuccess);
	    return;
	}

	if (!getConfigSuccess)
	    return;

    }

    public void connect() {
	// attempts to connect to Firebase DB
	boolean firebaseDBConnSuccess = connectToFirebaseDatabase(jsonAuthFileLocation, firebaseDatabaseURL);

	// notify the connection result
	if (listener != null) {
	    listener.connected(firebaseDBConnSuccess);
	}

	// connect to Firebase Database
	if (!firebaseDBConnSuccess)
	    return;

    }

    public void start() {

	File file = new File(imgFileName);
	String plainFileDataEncoded;
	String compressedFileDataEncoded;

	plainFileDataEncoded = Base64.encodeBase64String(getFileAsBytes(imgFileName));
	compressedFileDataEncoded = Base64.encodeBase64String(compress(getFileAsBytes(imgFileName)));

	/*
	 * String message = new
	 * StringBuilder().append("config file path: ").append(
	 * configFileLocation).append("\n")
	 * .append("image file path: ").append(imgFileName).append("\n").
	 * append("thread ID: ").append(threadID)
	 * .append("\n\n").append("Date: ").append(getDate(file.getName())).
	 * append("\n").append("Time: ")
	 * .append(getTime(file.getName())).append("\n")
	 * .append(String.format("File data size (plain: %d / compressed: %d)\n"
	 * , plainFileDataEncoded.length(),
	 * compressedFileDataEncoded.length()))
	 * .toString();
	 * 
	 * System.out.println(message);
	 */

	DatabaseReference ref = FirebaseDatabase.getInstance().getReference(String.format("/Groups/%s/VideoSurveillance/Shots", groupName));

	HashMap<String, Object> data = new HashMap<String, Object>();
	data.put("Date", getDate(file.getName()));
	data.put("Time", getTime(file.getName()));
	data.put("ImgData", compressedFileDataEncoded);
	data.put("Device", deviceName);
	data.put("ThreadID", threadID);

	// System.out.println("uploading...");

	loop = true;

	ref.child(getTimeStamp()).setValue(data, new CompletionListener() {

	    @Override
	    public void onComplete(DatabaseError error, DatabaseReference ref) {

		uploadError = (error == null);

		if (uploadError) {

		    DatabaseReference lastShotRef = FirebaseDatabase.getInstance().getReference(String.format("/Groups/%s/VideoSurveillance/AvailableCameras/%s-%s", groupName, deviceName, threadID));

		    lastShotRef.child("LastShotData").setValue(data, new CompletionListener() {

			@Override
			public void onComplete(DatabaseError error, DatabaseReference ref) {

			    uploadError = (error == null);
			    loop = false;

			}

		    });

		} else {

		    loop = false;

		}

	    }

	});

	while (loop) {
	    try {

		Thread.sleep(1000);

	    } catch (InterruptedException e) {

		if (listener != null)
		    listener.upload(false);

		return;

	    }

	}

	if (listener != null)
	    listener.upload(uploadError);

    }

    private String getDate(String fileName) {

	String[] split = fileName.split("-");

	if (split.length != 3)
	    return "NULL";

	if (split[1].length() != 14)
	    return "NULL";

	String year = split[1].substring(0, 4);
	String month = split[1].substring(4, 6);
	String day = split[1].substring(6, 8);

	return String.format("%s-%s-%s", year, month, day);

    }

    private String getTime(String fileName) {

	String[] split = fileName.split("-");

	if (split.length != 3)
	    return "NULL";

	if (split[1].length() != 14)
	    return "NULL";

	String hours = split[1].substring(8, 10);
	String minutes = split[1].substring(10, 12);
	String seconds = split[1].substring(12, 14);

	return String.format("%s.%s.%s", hours, minutes, seconds);

    }

}
