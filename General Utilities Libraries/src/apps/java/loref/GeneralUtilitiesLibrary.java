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

import static apps.java.loref.GeneralUtilitiesLibrary.parseShellCommand;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.json.JSONObject;

import com.google.api.client.util.Base64;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;

@SuppressWarnings({ "javadoc", "unused" })
public class GeneralUtilitiesLibrary {

	public static final long MAX_LOG_CONTENT_LENGTH = 128;

	public static void sleepSafe(long time) {

		try {

			Thread.sleep(time);

		} catch (InterruptedException e) {

			exceptionLog_REDXTERM(GeneralUtilitiesLibrary.class, e);

		}

	}

	public static String parseShellCommand(String command) throws IOException, InterruptedException {

		StringBuffer output = new StringBuffer();

		Process shellCommand;

		shellCommand = Runtime.getRuntime().exec(command);
		shellCommand.waitFor();

		BufferedReader reader = new BufferedReader(new InputStreamReader(shellCommand.getInputStream()));

		String line = "";

		while ((line = reader.readLine()) != null) {

			output.append(line + "\n");
			
		}

		return output.toString();

	}

	public static void execShellCommand(String command) throws IOException {

		Process shellCommand;

		shellCommand = Runtime.getRuntime().exec(command);

	}

	public static String parseHttpRequest(String httpRequest) {

		InputStreamReader inputStreamReader;
		StringBuilder stringBuilder = new StringBuilder();

		try {

			URL url = new URL(httpRequest);
			URLConnection connection = url.openConnection();
			connection.setConnectTimeout(5000);
			connection.connect();

			inputStreamReader = new InputStreamReader(connection.getInputStream());

			BufferedReader buf = new BufferedReader(inputStreamReader);
			String line;
			while ((line = buf.readLine()) != null) {
				stringBuilder.append(line);
				stringBuilder.append("\n");
			}

			return stringBuilder.toString();

		} catch (IOException e) {

			return "Error - " + e.getMessage();
		}

	}

	public static String getStringFromBytes(byte[] input){
		
		StringBuilder result = new StringBuilder();
		
		for (int i:input){
			
			result.append((char) i );
			
		}
		
		return result.toString();
		
	}
	
	public static byte[] getFileAsBytes(String fileLocation) {

		FileInputStream in;

		try {
			in = new FileInputStream(fileLocation);
		} catch (FileNotFoundException e) {
			return new byte[0];
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		int data;

		try {
			while ((data = in.read()) != -1) {

				out.write(data);
			}

			out.flush();
			in.close();

			return out.toByteArray();
		} catch (IOException e) {
			return new byte[0];
		}

	}

	public static String readPlainTextFromFile(File f) {

		FileInputStream in;

		try {

			in = new FileInputStream(f);

			ByteArrayOutputStream out = new ByteArrayOutputStream();

			int data;
			while ((data = in.read()) != -1) {

				out.write(data);
			}

			out.flush();
			in.close();

			return out.toString();

		} catch (FileNotFoundException e) {

			exceptionLog_REDXTERM(GeneralUtilitiesLibrary.class, e);
			return "ERROR: File not found";

		} catch (IOException e) {

			exceptionLog_REDXTERM(GeneralUtilitiesLibrary.class, e);
			return "ERROR: File not readable";
		}

	}
	
	public static String readPlainTextFromStream(InputStream inputStream) {

		BufferedInputStream in = new BufferedInputStream(inputStream);

		try {

			ByteArrayOutputStream out = new ByteArrayOutputStream();

			int data;
			while ((data = in.read()) != -1) {

				out.write(data);
			}

			out.flush();
			in.close();

			return out.toString();

		} catch (IOException e) {

			exceptionLog_REDXTERM(GeneralUtilitiesLibrary.class, e);
			return "ERROR: Stream not readable";
		}

	}

	public static String[] readLinesFromFile(File f) {

		String in = readPlainTextFromFile(f);

		if (!in.startsWith("ERROR")) {
			// input file reading did not succeed

			// returns a string array on each \n
			return in.split("[\n]");

		} else {
			// input file reading did not succeed

			// returns an empty string array
			return new String[0];

		}

	}

	public static RemoteCommand parseLocalCommand(String s) {

		String[] lines = s.split("\n");

		if (lines.length == 3) {
			return new RemoteCommand(lines[0], lines[1], lines[2]);
		} else
			return null;

	}

	public static boolean connectToFirebaseDatabase(String locationOfJSONAuthFile, String databaseURL) {

		File jsonAuthFileLocation = new File(locationOfJSONAuthFile);
		try {
			FileInputStream serviceAccount = new FileInputStream(jsonAuthFileLocation);
			FirebaseOptions options = new FirebaseOptions.Builder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount)).setDatabaseUrl(databaseURL)
					.build();

			FirebaseApp.initializeApp(options);

			serviceAccount.close();

			return true;

		} catch (IOException e) {

			exceptionLog_REDXTERM(GeneralUtilitiesLibrary.class, e);
			return false;

		}

	}

	public static byte[] ipAddressFromString(String ipAddress) {

		String[] address = ipAddress.split("[.]");

		if (address.length == 4) {

			byte[] result = new byte[4];
			for (int i = 0; i < address.length; i++) {
				result[i] = (byte) Integer.parseInt(address[i]);
			}

			return result;

		} else {

			return new byte[4];

		}

	}

	public static byte[] compress(byte[] data) {
		Deflater deflater = new Deflater();
		deflater.setInput(data);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
		deflater.finish();
		byte[] buffer = new byte[1024];

		while (!deflater.finished()) {
			int count = deflater.deflate(buffer); // returns the generated
			// code... index
			outputStream.write(buffer, 0, count);
		}

		try {
			outputStream.close();
			byte[] output = outputStream.toByteArray();

			return output;
		} catch (IOException e) {
			return new byte[0];
		}

	}

	public static byte[] decompress(byte[] data) throws IOException, DataFormatException {

		Inflater inflater = new Inflater();
		inflater.setInput(data);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);

		byte[] buffer = new byte[1024];

		while (!inflater.finished()) {

			int count = inflater.inflate(buffer);
			outputStream.write(buffer, 0, count);

		}

		outputStream.close();
		byte[] output = outputStream.toByteArray();

		return output;
	}

	public static HashMap<String, String> parseMeta(String code) {

		HashMap<String, String> result = new HashMap<>();

		String[] data = code.split("&");
		for (String datum : data) {

			String[] parse = datum.split("=");

			if (parse.length != 2)
				continue;

			result.put(parse[0], parse[1]);

		}

		return result;

	}

	public static HashMap<String, Object> parseJSON(String code) {

		HashMap<String, Object> result = new HashMap<>();
		JSONObject input = new JSONObject(code);

		Set<String> keys = input.keySet();
		for (String key : keys) {

			result.put(key, input.getString(key));

		}

		return result;

	}

	public static String encode(String raw) {

		return Base64.encodeBase64String(raw.getBytes());

	}

	public static String encode(byte[] raw) {

		return Base64.encodeBase64String(raw);

	}

}
