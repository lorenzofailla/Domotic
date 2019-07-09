package apps.java.loref.FirebaseCloudUploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.google.firebase.cloud.StorageClient;

import apps.java.loref.FirebaseCloudUploader.UploadItems.DataStreamUploadItem;
import apps.java.loref.FirebaseCloudUploader.UploadItems.FileUploadItem;
import org.json.JSONObject;

import static apps.java.loref.LogUtilities.*;
import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;

@SuppressWarnings("javadoc")
public class FirebaseCloudUploader {

	private final static long DEFAULT_TICKTIME = 1000L;

	public enum Mode {
		BYTEARRAY, FILEPATHSTRING
	}

	private boolean debugMode = false;
	private boolean mainThreadActive = true;
	private boolean paused = true;

	public boolean isPaused() {
		return this.paused;
	}

	private UploadEngine coreEngine;

	public void setDebugMode(boolean value) {
		this.debugMode = value;
	}

	private ConcurrentHashMap<String, Object> uploadQueue = new ConcurrentHashMap<String, Object>();
	public ConcurrentHashMap<String, Object> getUploadQueue(){
		return this.uploadQueue;
	}

	// constructors
	public FirebaseCloudUploader() {
		this.coreEngine = new UploadEngine();
		this.coreEngine.start();

	}

	private FirebaseCloudUploaderListener listener;
	
	// initialize the storage bucket object
	private Bucket storageBucket;

	// subclasses

	private class UploadEngine extends Thread {

		FirebaseCloudUploader parent = FirebaseCloudUploader.this;

		@Override
		public void run() {

			if (this.parent.debugMode)
				debugLog_GRAYXTERM(this.getClass(), "Thread started.");

			while (this.parent.mainThreadActive) {

				if (!(this.parent.uploadQueue.isEmpty() || this.parent.paused)) {

					// retrieve the data of the next file to be uploaded
					Iterator<Entry<String, Object>> iterator = this.parent.uploadQueue.entrySet().iterator();
					
					if (this.parent.debugMode)
						debugLog_GRAYXTERM(this.getClass(), "Processing queue. Items count="
								+ this.parent.uploadQueue.size());

					while (this.parent.mainThreadActive && !this.parent.paused && iterator.hasNext() && this.parent.storageBucket!=null) {

						Object object = iterator.next().getValue();

						if (this.parent.debugMode)
							debugLog_GRAYXTERM(this.getClass(), object.getClass().toString());

						if (object.getClass().equals(DataStreamUploadItem.class)) {

							DataStreamUploadItem dataStreamUploadItem = (DataStreamUploadItem) object;

							if (this.parent.debugMode)
								debugLog_GRAYXTERM(this.getClass(), "Processing slot id='"
										+ dataStreamUploadItem.getID() + "'; mode=" + dataStreamUploadItem.getMode());

							try {

								// starts the upload operation - THIS BLOCKS THE OPERATION.
								// in case something goes wrong, a StorageException is raised and catched
								Blob uploadInfo = this.parent.storageBucket.create(dataStreamUploadItem.getRemotePosition(),
										dataStreamUploadItem.getFileData());

								// triggers the listeners
								if (FirebaseCloudUploader.this.listener != null)
									FirebaseCloudUploader.this.listener.onComplete(this.parent, uploadInfo,
											dataStreamUploadItem.getRemotePosition());

								// triggers the listeners
								if (dataStreamUploadItem.getListener() != null)
									dataStreamUploadItem.getListener().onComplete(uploadInfo,
											dataStreamUploadItem.getID());

								// prints a log for debug purposes
								if (this.parent.debugMode)
									debugLog_GRAYXTERM(this.getClass(), "Slot id='" + dataStreamUploadItem.getID()
											+ "' upload completed; mode=" + dataStreamUploadItem.getMode() + ".");

							} catch (Exception e) {

								exceptionLog_REDXTERM(UploadEngine.class, e);

								// triggers the listeners
								if (dataStreamUploadItem.getListener() != null)
									dataStreamUploadItem.getListener().onError(e);

							}

						} else if (object.getClass().equals(FileUploadItem.class)) {

							FileUploadItem fileUploadItem = (FileUploadItem) object;

							if (this.parent.debugMode)
								debugLog_GRAYXTERM(this.getClass(), "Processing slot id='" + fileUploadItem.getID()
										+ "'; mode=" + fileUploadItem.getMode() + "; path="+fileUploadItem.getLocalPath());

							File file = new File(fileUploadItem.getLocalPath());

							// initialize an inputstream reading the local file
							//FileInputStream inputStream;
							FileInputStream inputStream = null;
							try {

								inputStream = new FileInputStream(file);

								// starts the upload operation - THIS BLOCKS THE OPERATION.
								// in case something goes wrong, a StorageException is raised and catched
								Blob uploadInfo = this.parent.storageBucket.create(fileUploadItem.getRemotePosition(), inputStream);

								// closes the stream
								inputStream.close();

								if (FirebaseCloudUploader.this.listener != null)
									FirebaseCloudUploader.this.listener.onComplete(this.parent, uploadInfo,
											fileUploadItem.getRemotePosition());

								if (fileUploadItem.getListener() != null)
									fileUploadItem.getListener().onComplete(uploadInfo, fileUploadItem.getID());

							} catch (Exception e) {

								exceptionLog_REDXTERM(UploadEngine.class, e);

								// triggers the listeners
								if (fileUploadItem.getListener() != null)
									fileUploadItem.getListener().onError(e);

								// tries to close the FileInputStream object in any case
								try {
									if (inputStream != null)
										inputStream.close();
								} catch (IOException exception) { // block intentionally blank

								}

							}

						} else { //

							if (this.parent.debugMode)
								debugLog_GRAYXTERM(this.getClass(), "Unrecognized item.");

						}

						iterator.remove();

					}

				} else {

					sleepSafe(DEFAULT_TICKTIME);

				}

			}

			if (FirebaseCloudUploader.this.debugMode)

				debugLog_GRAYXTERM(this.getClass(), "Thread terminated.");

		}

	}

	// methods
	public FirebaseCloudUploader setListener(FirebaseCloudUploaderListener listener) {
		this.listener = listener;
		return this;
	}

	public static String getSignedUrl(Blob blob, int timeout, TimeUnit unit, String jsonSACFilePath) {

		try {

			return blob
					.signUrl(timeout, unit,
							SignUrlOption.signWith(
									ServiceAccountCredentials.fromStream(new FileInputStream(jsonSACFilePath))))
					.toString();

		} catch (IOException e) {

			return e.getMessage();

		}

	}

	public void terminate() {
		this.mainThreadActive = false;
	}

	public boolean isMainThreadActive() {
		return this.mainThreadActive;
	}

	public void pause() {
		this.paused = true;
	}

	public void resume() {
		
		this.storageBucket = StorageClient.getInstance().bucket();
		this.paused = false;
		
	}

	public String addToQueue(byte[] dataToUpload, String remotePosition, FirebaseUploadItemListener listener) {

		DataStreamUploadItem dataStreamUploadItem = new DataStreamUploadItem(dataToUpload, remotePosition);
		dataStreamUploadItem.setListener(listener);
		this.uploadQueue.put(dataStreamUploadItem.getID(), dataStreamUploadItem);
		
		// prints a log for debug purposes
		if (this.debugMode)
			debugLog_GRAYXTERM(this.getClass(), "Item added. Queue length=" + this.uploadQueue.size());
		
		return dataStreamUploadItem.getID();

	}

	public String addToQueue(String localPosition, String remotePosition, FirebaseUploadItemListener listener) {

		FileUploadItem fileUploadItem = new FileUploadItem(localPosition, remotePosition);
		fileUploadItem.setListener(listener);
		this.uploadQueue.put(fileUploadItem.getID(), fileUploadItem);
		
		// prints a log for debug purposes
		if (this.debugMode)
			debugLog_GRAYXTERM(this.getClass(), "Item added. Queue length=" + this.uploadQueue.size() + "; path="+fileUploadItem.getLocalPath() + "; ID="+fileUploadItem.getID());
		
		return fileUploadItem.getID();

	}

	public int getUploadEngineQueueLength() {
		return this.uploadQueue.size();
	}

	public String getUploadEngineQueue() {

		StringBuilder output = new StringBuilder();

		output.append(this.getUploadEngineQueueLength()).append(" items in queue.\n");

		// retrieve the data of the next file to be uploaded
		Iterator<Entry<String, Object>> iterator = this.uploadQueue.entrySet().iterator();

		// initialize a counter
		int c=0;
		
		while (iterator.hasNext()) {
			
			Object object = iterator.next().getValue();
			
			// increase the counter
			c++;
						
			if(object.getClass().equals(FileUploadItem.class)){
				
				FileUploadItem item = (FileUploadItem) object;
				output.append(c).append(".  FILE ").append(item.getLocalPath()).append(" -> ").append(item.getRemotePosition()).append("\n");
								
			} else if (object.getClass().equals(DataStreamUploadItem.class)) {
				
				DataStreamUploadItem item = (DataStreamUploadItem) object;
				output.append(c).append(".  DATASTREAM ").append(item.getFileData().length).append(" bytes -> ").append(item.getRemotePosition()).append("\n");
				
			}
			

		}

		return output.toString();

	}

	public JSONObject getUploadEngineStatus() {

		JSONObject clouduploader = new JSONObject();
		clouduploader.put("ItemsCount", this.getUploadEngineQueueLength());

		// retrieve the data of the next file to be uploaded
		Iterator<Entry<String, Object>> iterator = this.uploadQueue.entrySet().iterator();

		// initialize a counter
		int c=0;

		while (iterator.hasNext()) {

			Object object = iterator.next().getValue();

			JSONObject uploadItem = new JSONObject();

			// increase the counter
			c++;

			if(object.getClass().equals(FileUploadItem.class)){

				FileUploadItem item = (FileUploadItem) object;
				uploadItem.put("Type", "FILE").put("LocalPath",item.getLocalPath()).put("RemotePath",item.getRemotePosition());


			} else if (object.getClass().equals(DataStreamUploadItem.class)) {

				DataStreamUploadItem item = (DataStreamUploadItem) object;
				uploadItem.put("Type", "DATASTREAM").put("Datasize",item.getFileData().length).put("RemotePath",item.getRemotePosition());

			}

			clouduploader.append("Items", uploadItem);

		}

		return clouduploader;

	}

}
