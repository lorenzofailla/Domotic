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

/**
 * Provides default values of configuration parameters
 *
 * @author lore_f.
 *         Created 18 dic 2018.
 */

@SuppressWarnings({"javadoc"})
public class DefaultConfigValues {
    
    public static final String CONFIG_FILE_LOCATION = "/etc/domotic/domotic.conf";
    public static final String USERS_FILE_LOCATION = "/etc/domotic/users";

    public static final String ERROR = "[error]";

    public final static long CONNECTION_CHECK_INTERVAL_TIMEOUT_LONG = 10000L;
    public final static long CONNECTION_CHECK_INTERVAL_TIMEOUT_SHORT = 10000L;

    public final static long DEVICE_STATUS_UPDATE_RATE = 60000L;
    public final static long DEVICE_NETWORK_STATUS_UPDATE_RATE = 3600000L;
    public final static long VIDEOCAMERA_SHOT_UPDATE_RATE=1800000L;

    public final static long FIREBASE_DB_UPDATE_TIMEOUT = 10000L;

    public final static String CONNECTIVITY_TEST_SERVER_ADDRESS = "http://lorenzofailla.000webhostapp.com/domotic/domotic-connection-test.php";
    public final static long CONNECTIVITY_TEST_RATE = 10000L;
    
    public static final String HEADER_REPLY = "@REPLY";
    
    public final static String APP_NAME = "Domotic";

    public final static String GROUP_NODE = "Groups";
    
    public final static String DEVICES_NODE = "Devices";
    public final static String STATUS_DATA_CHILDNODE = "StatusData";
    public final static String NETWORK_DATA_CHILDNODE = "NetworkData";
    public final static String STATIC_DATA_CHILDNODE = "StaticData";
    
    
    public final static String MOTION_EVENTS_NODE = "MotionEvents";
    public final static String INCOMING_COMMANDS_NODE = "IncomingCommands";
    public final static String INCOMING_FILES_NODE = "IncomingFiles";
    
    public final static String VIDEO_CAMERAS_NODE = "VideoCameras";
    public final static String LASTSHOTDATA_CHILDNODE = "LastShotData";
    public final static String MODETSTATUS_CHILDNODE = "MoDetStatus";
    
    
    public final static String ONLINE_NODE = "online";

    public final static String LOGS_NODE = "Logs";

    public final static String VIDEOSURVEILLANCE_NODE = "VideoSurveillance";

    public final static String VIDEO_STREAMING_NODE = "StreamingData";

    public final static String MOTION_EVENTS_STORAGE_CLOUD_URL = "MotionEvents/%%group_name&&/%%file_name&&";
    public final static String VIDEOCAMERA_SHOT_CLOUD_URL = "Videocameras/%%group_name&&/%%device_name&&-%%thread_id&&/";
    
    public final static String LOCAL_TCP_PREFIX = "tcp://";
    
    public final static String LOGFILE_LOCATION = "/var/log/domotic.log";

    public final static long TICK_TIME_MS = 1000L;

    public final static int REBOOT = 1;
    public final static int SHUTDOWN = 2;

}
