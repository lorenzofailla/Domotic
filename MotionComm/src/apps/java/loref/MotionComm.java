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

import static apps.java.loref.GeneralUtilitiesLibrary.parseHttpRequest;
import static apps.java.loref.GeneralUtilitiesLibrary.parseShellCommand;
import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;
import static apps.java.loref.GeneralUtilitiesLibrary.getStringFromBytes;

import static apps.java.loref.LogUtilities.debugLog_XTERM;
import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;
import static apps.java.loref.LogUtilities.CYAN;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;

import net.sf.jipcam.axis.MjpegFrame;
import net.sf.jipcam.axis.MjpegInputStream;

@SuppressWarnings("javadoc")
public class MotionComm {

	// constants
	private final static int DATA_SIZE_THRESHOLD = 2048;
	private final static long DATA_SIZE_THRESHOLD_WAIT_TIME_MS = 500L;
	private final static String DEFAULT_PROTOCOL = "http://";

	// subclasses
	private class StopMotionEmulation extends TimerTask {

		private String cameraID;

		StopMotionEmulation(String cameraID) {
			this.cameraID = cameraID;
		}

		@Override
		public void run() {

			setParameter(this.cameraID, "emulate_motion", "off");

		}

	}

	
	/**
	 * 
	 * This class manages the frame capture from a camera, using the
	 * CameraDataStream class for input data
	 *
	 * @author lore_f. Created 10 feb 2019.
	 */
	private class FrameCapturer {

		// process

		private static final int DEFAULT_FRAME_WIDTH = 640;
		private static final int DEFAULT_FRAME_HEIGHT = 480;

		private String cameraID;
		private String destination;
		private int framesToBeCaptured = 0;
		private int capturedFrames = 0;

		// getters and setters

		public void setFramesToBeCaptured(int value) {
			this.framesToBeCaptured = value;
		}

		// constructors

		public FrameCapturer(String cameraID, String destination) {

			this.cameraID = cameraID;
			this.destination = destination;
			
		}

		// methods
		public void addFramesToCapture(int value) {

			this.framesToBeCaptured += value;

		}
		
		public void startFrameCapture() {

			new Thread() {

				@Override
				public void run() {

					boolean continueLooping = true;
					
					BufferedInputStream stream;

					try {

						// apre lo stream
						stream = new BufferedInputStream(new URL( MotionComm.this.protocol + MotionComm.this.host + ":"
								+ getStreamPort(FrameCapturer.this.cameraID)).openStream());
											
						// Initialize the MJPEG Input Stream
						MjpegInputStream mjpegInputStream = new MjpegInputStream(stream);

						while (continueLooping) {

							if (FrameCapturer.this.capturedFrames < FrameCapturer.this.framesToBeCaptured) {

								if (MotionComm.this.debugMode) {
									debugLog_XTERM(FrameCapturer.class, LogUtilities.CYAN, "Waiting for frame...");
								}

								MjpegFrame mjpegFrame = mjpegInputStream.readMjpegFrame();

								if (MotionComm.this.debugMode) {
									debugLog_XTERM(FrameCapturer.class, LogUtilities.CYAN,
											"Frame captured. Number: " + mjpegFrame.getSequence() + "; Header: "
													+ mjpegFrame.getHeaderBytes().length + " bytes; Image: "
													+ mjpegFrame.getJpegBytes().length + " bytes.");
								}

								if (mjpegFrame != null) {

									if (MotionComm.this.motionCommListener != null) {
										
										MotionComm.this.motionCommListener.onNewFrame(FrameCapturer.this.cameraID,
												mjpegFrame.getJpegBytes(), FrameCapturer.this.destination);
										
									}

									// incrementa il contatore dei frames catturati
									FrameCapturer.this.capturedFrames++;

									if (MotionComm.this.debugMode) {
										debugLog_XTERM(FrameCapturer.class, LogUtilities.CYAN,
												"FrameCapturer - Frame catturati: " + FrameCapturer.this.capturedFrames
														+ "; da catturare: " + FrameCapturer.this.framesToBeCaptured);
									}

								}

							} else { // target n. of frames has been reached.

								if (MotionComm.this.debugMode) { // Prints the debug message
									debugLog_XTERM(FrameCapturer.class, LogUtilities.CYAN,
											"FrameCapturer - Numero di fotogrammi raggiunto");
								}
								
								mjpegInputStream.close();
								
								// set the looping flag to false
								continueLooping = false;
								
								// remove the current instance
								MotionComm.this.removeFrameCapturer(FrameCapturer.this.cameraID, FrameCapturer.this.destination);
								
							}

						}

					} catch (IOException | IllegalArgumentException e) {

						if (MotionComm.this.debugMode) { // Prints the error message
							exceptionLog_REDXTERM(FrameCapturer.class, e);
							
						}

					} 

				}

			}.start();

		}

	}

	// getters and setters

	private String host;
	private int port;
	private String owner;
	private String protocol = DEFAULT_PROTOCOL;
	private boolean debugMode = false;

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setDebugMode(boolean value) {
		this.debugMode = value;

		debugLog_XTERM(MotionComm.class, CYAN, "Debug mode set to: " + value);
	}

	private String baseRequestURL;
	private MotionCommListener motionCommListener;

	private HashMap<String, FrameCapturer> camerasFrameCapturers = new HashMap<String, FrameCapturer>();

	// constructors

	public MotionComm(String host, String owner, int port) {

		this.host = host;
		this.port = port;
		this.owner = owner;

		this.baseRequestURL = this.protocol + this.host + ":" + this.port;

	}

	// interface

	// methods

	public void setListener(MotionCommListener listener) {
		this.motionCommListener = listener;
	}

	/**
	 * restituisce il numero di thread attivi corrispondenti ad una videocamera
	 * 
	 * ad esempio, questa è la risposta di motion con 2 videocamere:
	 * 
	 * Motion 4.0 Running [3] Cameras 0 1 2
	 * 
	 * la funzione restituisce 2, ovvero il numero di righe della risposta -2,
	 * in quanto la prima riga è la risposta ed il thread 0 è sempre il thread
	 * principale
	 * 
	 */
	public int getNOfThreads() {

		String httpResponse = parseHttpRequest(this.baseRequestURL);
		String[] responseLines = httpResponse.split("\n");

		int output = responseLines.length - 2;

		if (this.debugMode) {
			debugLog_XTERM(MotionComm.class, CYAN, "Number of threads requested. Output: " + output);
		}

		return output;

	}

	/**
	 * Returns a String array with the thread ids of the current motion
	 * instance.
	 * 
	 * For instance, if the reply to the HTTP call
	 * {server_address}:{server_port} is the following:
	 * 
	 * Motion 4.0 Running [3] Cameras 0 1 2
	 * 
	 * Then this function will return {"1","2"}
	 * 
	 */
	public String[] getThreadsIDs() {

		String httpResponse = parseHttpRequest(this.baseRequestURL);
		String[] responseLines = httpResponse.split("\n");

		String[] out = new String[responseLines.length - 2];
		for (int i = 0; i < responseLines.length - 2; i++) {
			out[i] = responseLines[i + 2];
		}
		return out;

	}

	public static boolean isHTMLOutputEnabled(String host, int port) {

		String httpResponse = parseHttpRequest(host + ":" + port);
		return httpResponse.charAt(0) == '<';

	}

	public boolean isHTMLOutputEnabled() {

		String httpResponse = parseHttpRequest(this.host + ":" + this.port);
		return httpResponse.charAt(0) == '<';

	}

	public boolean requestShot(String threadID) {
		// {server_address}:{server_port}/{thread_ID}/action/snapshot

		String request = new StringBuilder().append(this.baseRequestURL).append("/").append(threadID)
				.append("/action/snapshot").toString();

		String[] responseLines = parseHttpRequest(request).split("\n");

		if (responseLines.length == 2) {

			return responseLines[1].equals("Done");

		} else {

			return false;

		}

	}

	public boolean requestVideo(String threadID) {

		String request = new StringBuilder().append(this.baseRequestURL).append("/").append(threadID)
				.append("/action/makemovie").toString();

		String[] responseLines = parseHttpRequest(request).split("\n");

		if (responseLines.length == 2) {

			return responseLines[1].equals("Done");

		} else {

			return false;

		}

	}

	public static boolean isMotionInstalled() {

		try {
			parseShellCommand("motion -h");
			return true;

		} catch (IOException | InterruptedException e) {
			exceptionLog_REDXTERM(MotionComm.class, e);
			return false;

		}

	}

	public String getThreadMoDetStatus(String threadID) {

		String request = new StringBuilder().append(this.baseRequestURL).append("/").append(threadID)
				.append("/detection/status").toString();

		String[] responseLines = parseHttpRequest(request).split(" ");

		if (responseLines.length == 5) {

			return responseLines[4].replaceAll("\n", "");

		} else {

			return "";

		}

	}

	public HashMap<String, Object> getCameraInfo(String cameraID) {

		/* retrieve the information of the i-th thread */
		HashMap<String, Object> cameraInfo = new HashMap<String, Object>();
		cameraInfo.put("MoDetStatus", getThreadMoDetStatus(cameraID));
		cameraInfo.put("OwnerDevice", this.owner);
		cameraInfo.put("ThreadID", cameraID);
		cameraInfo.put("CameraName", getParameter(cameraID, "camera_name"));
		cameraInfo.put("StreamFPS", getCameraStreamFPS(cameraID));
		cameraInfo.put("StreamPort", getStreamPort(cameraID));

		return cameraInfo;

	}

	public String getCamerasList() {
		return getCamerasList(";");
	}

	public String getCamerasList(String regex) {

		/*
		 * Restituisce una lista, separata dalla sequenza di caratteri _regex,
		 * contenente gli id dei thread corrispondenti alle videocamere
		 * 
		 * ad esempio, se questa è la risposta di motion con 2 videocamere:
		 * 
		 * Motion 4.0 Running [3] Cameras 0 1 2
		 * 
		 * la funzione restituisce "1{_regex}2"
		 * 
		 */

		String[] IDs = getThreadsIDs();

		StringBuilder out = new StringBuilder();
		for (int i = 0; i < IDs.length; i++) {
			out.append(IDs[i]);

			if (i < getNOfThreads() - 1)
				out.append(regex);

		}

		return out.toString();

	}

	public String getCamerasNames() {

		return getCamerasNames(";");

	}

	public String getCamerasNames(String regex) {

		String[] IDs = getThreadsIDs();

		StringBuilder out = new StringBuilder();
		for (int i = 0; i < IDs.length; i++) {
			out.append(getParameter(IDs[i], "camera_name"));

			if (i < getNOfThreads() - 1)
				out.append(regex);

		}

		return out.toString();

	}

	public void captureFrames(String cameraID, int framesToCapture, String destination) {

		FrameCapturer frameCapturer;
		String frameCapturerID = cameraID + "_" + destination;

		/*
		 * Definisce il FrameCapturer. Cerca un FrameCapturer attivo per il
		 * cameraID passato in argomento. Se c'è un FrameCapturer attivo per il
		 * cameraID passato in argomento, aumenta il numero di fotogrammi da
		 * catturare. Altrimenti, crea un nuovo FrameCapturer
		 * 
		 */

		if (this.camerasFrameCapturers.containsKey(frameCapturerID)) {

			// esiste un FrameCapturer attivo per il cameraID passato in
			// argomento
			frameCapturer = this.camerasFrameCapturers.get(frameCapturerID);
			frameCapturer.addFramesToCapture(framesToCapture);

			if (this.debugMode)
				debugLog_XTERM(MotionComm.class, LogUtilities.CYAN,
						"MotionComm - FrameCapturer TROVATO per cameraID: " + cameraID);

		} else {

			// crea un nuovo FrameCapturer per il cameraID passato in argomento
			frameCapturer = new FrameCapturer(cameraID,  destination);
			this.camerasFrameCapturers.put(frameCapturerID, frameCapturer);

			frameCapturer.setFramesToBeCaptured(framesToCapture);
			frameCapturer.startFrameCapture();

			if (this.debugMode)
				debugLog_XTERM(MotionComm.this.getClass(), LogUtilities.CYAN,
						"MotionComm - FrameCapturer CREATO per cameraID: " + cameraID);

		}

	}
	
	private String getFrameCapturerID(String cameraID, String destination){
		
		return cameraID + "_" + destination;
		
	}
	
	private void removeFrameCapturer(String cameraID, String destination){
		
		if (this.camerasFrameCapturers.containsKey(getFrameCapturerID(cameraID,destination))){
			
			this.camerasFrameCapturers.remove(getFrameCapturerID(cameraID,destination));
			
		}
		
		
	}

	private String getParameter(String cameraID, String parameterID) {

		String request = String.format("%s/%s/config/get?query=%s", this.baseRequestURL, cameraID, parameterID);

		if (this.debugMode)
			debugLog_XTERM(MotionComm.class, CYAN, "getParameter() request=\"" + request + "\"");

		String[] responseLines = parseHttpRequest(request).split("\n");

		if (responseLines.length == 2) {

			String reply[] = responseLines[0].replaceAll(" ", "").split("=");

			if (reply.length == 2) {
				return reply[1];
			} else {
				return "";
			}

		} else {

			return "";

		}

	}

	private String getStreamPort(String cameraID) {

		return getParameter(cameraID, "stream_port");

	}

	public String getVideoFilesDirectory(String cameraID) {

		return getParameter(cameraID, "target_dir");

	}

	/*
	 * Restituisce l'indirizzo completo del flusso video della camera il cui ID
	 * è passato in argomento Ad esempio:
	 * 
	 */
	public String getStreamURL(String cameraID) {

		return this.host + ":" + getStreamPort(cameraID);

	}

	public String getStreamFullURL(String cameraID) {

		return this.protocol + getStreamURL(cameraID);

	}

	public boolean startModet(String cameraID) {

		String request = String.format("%s/%s/detection/start", this.baseRequestURL, cameraID);

		String[] responseLines = parseHttpRequest(request).split("\n");

		if (responseLines.length == 2 && responseLines[1].equals("Done")) {

			if (this.motionCommListener != null) {
				this.motionCommListener.statusChanged(cameraID);
			}

			return true;

		} else {

			return false;

		}

	}

	public boolean stopModet(String cameraID) {

		String request = String.format("%s/%s/detection/pause", this.baseRequestURL, cameraID);

		String[] responseLines = parseHttpRequest(request).split("\n");

		if (responseLines.length == 2 && responseLines[1].equals("Done")) {

			if (this.motionCommListener != null) {
				this.motionCommListener.statusChanged(cameraID);
			}

			return true;

		} else {

			return false;

		}

	}

	public boolean setParameter(String cameraID, String parameter, String value) {
		// {server_address}:{server_port}/0/config/set?emulate_motion=off

		String request = String.format("%s/%s/config/set?%s=%s", this.baseRequestURL, cameraID, parameter, value);

		String[] responseLines = parseHttpRequest(request).split("\n");

		if (responseLines.length == 2) {

			String reply = responseLines[0];

			boolean result = reply.equals(String.format("%s = %s", parameter, value))
					&& responseLines[1].equals("Done");

			if (this.debugMode) {

				debugLog_XTERM(MotionComm.class, CYAN,
						"Parameter \"" + parameter + "\" set to value: \"" + value + "\". Success=" + result);

			}

			return result;

		} else {

			return false;

		}

	}

	public void requestMotionEvent(String cameraID, int durationSecs) {

		// imposta il parametro "emulate_motion" a "on"
		if (setParameter(cameraID, "emulate_motion", "on")) {

			long delay = durationSecs * 1000;
			new Timer().schedule(new StopMotionEmulation(cameraID), delay);

		}

	}

	public static String getDateFromMotionFileName(String fileName) {

		String[] split = fileName.split("-");

		if (split.length != 2)
			return "NULL";

		if (split[1].length() != 18)
			return "NULL";

		String year = split[1].substring(0, 4);
		String month = split[1].substring(4, 6);
		String day = split[1].substring(6, 8);

		return String.format("%s-%s-%s", year, month, day);

	}

	public static String getTimeFromMotionFileName(String fileName) {

		String[] split = fileName.split("-");

		if (split.length != 2)
			return "NULL";

		if (split[1].length() != 18)
			return "NULL";

		String hours = split[1].substring(8, 10);
		String minutes = split[1].substring(10, 12);
		String seconds = split[1].substring(12, 14);

		return String.format("%s.%s.%s", hours, minutes, seconds);

	}

	public String getCameraName(String cameraID) {
		return getParameter(cameraID, "camera_name");
	}

	/**
	 * 
	 * given the filename of the video file returns the filename of the
	 * relevant jpeg shot video file name shall be in the format
	 * xx-yyyyMMddhhmmss.* this method will return the first occurrence of
	 * xx-yyyyMMdd*.jpg, in order to locate the jpeg shot file generated by
	 * motion daemon, which is in the form xx-yyyyMMddhhmmss-ff.jpg
	 * 
	 * null is returned is video file name is not in the expected format, or
	 * if no file named xx-yyyyMMdd*.jpg is found.
	 * 
	 * @param aviFileName
	 * @return
	 * 
	 */
	public static String getEventJpegFileName(String aviFileName) {

		File file = new File(aviFileName);

		String[] shortName = file.getName().split("-");

		if (shortName.length < 2)
			return null;

		String datePart = shortName[1].substring(0, 8);

		FilenameFilter filter = new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().startsWith(shortName[0] + "-" + datePart)
						&& name.toLowerCase().endsWith("jpg");
			}

		};

		File localCmdDirectory = new File(file.getParent());
		try {
			return localCmdDirectory.listFiles(filter)[0].getAbsolutePath();
		} catch (IndexOutOfBoundsException e) {
			return null;
		}

	}
		
	public static String getShortName(String fullPathName) {
		
		File file = new File(fullPathName);
		return file.getName();
		
	}

	public int getCameraStreamFPS(String cameraID) {

		return Integer.parseInt(getParameter(cameraID, "stream_maxrate"));

	}
	public int getCameraRateFPS(String cameraID) {

		return Integer.parseInt(getParameter(cameraID, "framerate"));

	}

}
