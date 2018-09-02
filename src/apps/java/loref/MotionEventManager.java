/**
 * 
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import com.google.api.client.util.Base64;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import com.google.firebase.database.FirebaseDatabase;

/*
 * This class writes a child with header "__manage_vs_motion_event" into the
 * "IncomingCommands" node of the host device.
 */

public class MotionEventManager {

    private static final String DEFAULT_CONFIG_FILE_LOCATION = "/etc/domotic.conf";

    // @formatter:off
    // private static final String DEFAULT_CONFIG_FILE_LOCATION = "C:\\Users\\lore_f\\git\\Domotic\\etc\\domotic.conf";
    // @formatter:on

    private String deviceName;
    private String configFileLocation;
    private String threadID;
    private String vidFileName;

    public static void main(String[] args) {

	String configFileLocation = "";
	String videoFileLocation = "";
	String threadID = "";

	boolean configFileLocationDefined = false;
	boolean videoFileLocationDefined = false;
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

	    case "-v":
		//
		// image file
		i++;
		videoFileLocation = args[i];
		videoFileLocationDefined = true;
		break;

	    case "-t":
		//
		// threadID
		i++;

		threadID = args[i];
		threadIDdefined = true;

		break;

	    }

	}

	//
	// check the arguments passed

	if (!(videoFileLocationDefined && threadIDdefined)) {
	    //
	    // argument not consistent with call spec

	    String usageMessage = new StringBuilder().append("motion-videouploader | by Lorenzo Failla\n").append("ERROR: no video file and/or thread id specified\n\n").append("Usage: motion-videouploader -v {path to video" + " file} -t {thread id} [-c {path to configuration file}] [-n]").toString();

	    System.out.println(usageMessage);

	} else {
	    //
	    // arguments ok

	    // initialize the core class
	    MotionEventManager core;
	    if (configFileLocationDefined) {
		//
		core = new MotionEventManager(threadID, videoFileLocation, configFileLocation);

	    } else {
		//
		core = new MotionEventManager(threadID, videoFileLocation);

	    }

	    core.init();
	    core.start();

	}

    }

    public MotionEventManager(String threadID, String imgFileName) {

	this.threadID = threadID;
	this.vidFileName = imgFileName;
	this.configFileLocation = DEFAULT_CONFIG_FILE_LOCATION;

    }

    public MotionEventManager(String threadID, String imgFileName, String configFileLocation) {

	this.threadID = threadID;
	this.vidFileName = imgFileName;
	this.configFileLocation = configFileLocation;

    }

    private boolean getConfig() {

	try {

	    BufferedReader reader = new BufferedReader(new FileReader(configFileLocation));

	    int groupNameDefined = 0;
	    int deviceNameDefined = 0;

	    String line;
	    while ((line = reader.readLine()) != null) {

		if (line != null && line.length() > 0 && line.charAt(0) != ';') {

		    String[] lineProc = line.split("=");

		    if (lineProc.length == 2) {

			String command = lineProc[0];
			String argument = lineProc[1];

			switch (command) {

			case "DeviceName":
			    deviceName = argument;
			    deviceNameDefined = 1;
			    break;

			}

		    }

		}

	    }

	    reader.close();

	    int result = groupNameDefined + deviceNameDefined;

	    return (result == 2);

	} catch (IOException e) {

	    return false;
	}

    }

    public void init() {

	// attempts to get configuration
	boolean getConfigSuccess = getConfig();

	if (!getConfigSuccess) {

	    String errMsg = new StringBuilder().append("ERROR: could not read configuration file at: ").append(configFileLocation).toString();

	    System.out.println(errMsg);

	    return;
	}
    }

    public void start() {

	File outputDirectory = new File(DomoticCore.DEFAULT_LOCAL_COMMAND_DIRECTORY);
	if (!outputDirectory.exists())
	    outputDirectory.mkdirs();

	String commandFileName = new StringBuilder().append(DomoticCore.DEFAULT_LOCAL_COMMAND_DIRECTORY).append(File.separatorChar).append(deviceName).append('-').append(System.currentTimeMillis()).append(".cmd").toString();

	String commandBody = new StringBuilder().append("event_type=").append("motion").append('&').append("file_path=").append(vidFileName).append('&').append("thread_id=").append(threadID).toString();

	File commandFile = new File(commandFileName);

	try {

	    commandFile.createNewFile();

	    FileWriter fileWriter = new FileWriter(commandFile);

	    String content = new StringBuilder().append("__manage_vs_motion_event").append('\n').append(commandBody).append('\n').append(deviceName).toString();

	    fileWriter.write(content);

	    fileWriter.flush();

	    fileWriter.close();

	} catch (IOException e) {
	    System.out.println(e.getMessage());
	}

	return;

    }

}
