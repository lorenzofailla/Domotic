package apps.java.loref;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.google.firebase.cloud.StorageClient;

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
	
	public boolean isPaused(){
		return this.paused;
	}

	private UploadEngine coreEngine;

	public void setDebugMode(boolean value) {
		this.debugMode = value;
	}

	private HashMap<String, Object> uploadQueue = new HashMap<>();

	// constructors
	public FirebaseCloudUploader() {
		this.coreEngine = new UploadEngine();
		this.coreEngine.start();

	}

	private FirebaseCloudUploaderListener listener;

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

					while (this.parent.mainThreadActive && !this.parent.paused && iterator.hasNext()) {

						Object object = iterator.next().getValue();

						if (this.parent.debugMode)
							debugLog_GRAYXTERM(this.getClass(), object.getClass().toString());

						if (object.getClass().equals(DataStreamUploadItem.class)) {

							DataStreamUploadItem dataStreamUploadItem = (DataStreamUploadItem) object;

							if (this.parent.debugMode)
								debugLog_GRAYXTERM(this.getClass(), "Processing slot id='"
										+ dataStreamUploadItem.getID() + "'; mode=" + dataStreamUploadItem.getMode());

							// initialize the storage bucket object
							Bucket storageBucket = StorageClient.getInstance().bucket();

							// starts the upload operation
							Blob uploadInfo = storageBucket.create(dataStreamUploadItem.getRemotePosition(),
									dataStreamUploadItem.getFileData());

							if (FirebaseCloudUploader.this.listener != null)
								FirebaseCloudUploader.this.listener.onComplete(this.parent, uploadInfo,
										dataStreamUploadItem.getRemotePosition());
							
							if(dataStreamUploadItem.getListener()!=null)
								dataStreamUploadItem.getListener().onComplete(uploadInfo,
												dataStreamUploadItem.getID());
															
							
						} else if (object.getClass().equals(FileUploadItem.class)) {

							FileUploadItem fileUploadItem = (FileUploadItem) object;

							if (this.parent.debugMode)
								debugLog_GRAYXTERM(this.getClass(), "Processing slot id='" + fileUploadItem.getID()
										+ "'; mode=" + fileUploadItem.getMode());

							File file = new File(fileUploadItem.getLocalPath());

							// initialize an inputstream reading the local file
							FileInputStream inputStream;
							try {

								inputStream = new FileInputStream(file);

								// initialize the storage bucket object
								Bucket storageBucket = StorageClient.getInstance().bucket();

								// starts the upload operation
								Blob uploadInfo = storageBucket.create(fileUploadItem.getRemotePosition(), inputStream);

								if (FirebaseCloudUploader.this.listener != null)
									FirebaseCloudUploader.this.listener.onComplete(this.parent, uploadInfo,
											fileUploadItem.getRemotePosition());

								if (fileUploadItem.getListener() != null)
									fileUploadItem.getListener().onComplete(uploadInfo,
											fileUploadItem.getID());

							} catch (FileNotFoundException e) {

								exceptionLog_REDXTERM(UploadEngine.class, e);

							}

						} else { //

							if (this.parent.debugMode)
								debugLog_GRAYXTERM(this.getClass(), "Unrecognized item.");

						}

						iterator.remove();
						this.parent.uploadQueue.remove(object);

					}

				} else {

					if (this.parent.debugMode)
						debugLog_GRAYXTERM(this.getClass(), "Thread sleeping. Items in queue="
								+ this.parent.uploadQueue.size() + "; paused=" + this.parent.paused);

					sleepSafe(DEFAULT_TICKTIME);

				}

			}

			if (FirebaseCloudUploader.this.debugMode)
				debugLog_GRAYXTERM(this.getClass(), "Thread terminated.");

		}

	}

	//	private class FilePathUploadThread extends Thread {
	//
	//		private FirebaseCloudUploader parent;
	//
	//		public FilePathUploadThread(FirebaseCloudUploader parent) {
	//			this.parent = parent;
	//		}
	//
	//		@Override
	//		public void run() {
	//
	//			super.run();
	//
	//			// initialize the <File> object
	//			File file = new File(FirebaseCloudUploader.this.localFileName);
	//
	//			// initialize the storage bucket object
	//			Bucket storageBucket = StorageClient.getInstance().bucket();
	//
	//			try {
	//
	//				// initialize an inputstream reading the local file
	//				FileInputStream inputStream = new FileInputStream(file);
	//
	//				// starts the upload operation
	//				Blob uploadInfo = storageBucket.create(FirebaseCloudUploader.this.remotePosition, inputStream);
	//
	//				if (FirebaseCloudUploader.this.listener != null)
	//					FirebaseCloudUploader.this.listener.onComplete(this.parent, uploadInfo,
	//							FirebaseCloudUploader.this.remotePosition);
	//
	//				inputStream.close();
	//
	//			} catch (IOException e) {
	//
	//				if (FirebaseCloudUploader.this.listener != null)
	//					FirebaseCloudUploader.this.listener.onError(this.parent, e);
	//
	//			}
	//
	//		}
	//
	//	}
	//
	//	private class FileDataUploadThread extends Thread {
	//
	//		private FirebaseCloudUploader parent;
	//
	//		public FileDataUploadThread(FirebaseCloudUploader parent) {
	//			this.parent = parent;
	//		}
	//
	//		@Override
	//		public void run() {
	//
	//			super.run();
	//
	//			// initialize the storage bucket object
	//			Bucket storageBucket = StorageClient.getInstance().bucket();
	//
	//			// starts the upload operation
	//			Blob uploadInfo = storageBucket.create(FirebaseCloudUploader.this.remotePosition,
	//					FirebaseCloudUploader.this.fileData);
	//
	//			if (FirebaseCloudUploader.this.listener != null)
	//				FirebaseCloudUploader.this.listener.onComplete(this.parent, uploadInfo,
	//						FirebaseCloudUploader.this.remotePosition);
	//
	//		}
	//
	//	}

	// methods
	public FirebaseCloudUploader setListener(FirebaseCloudUploaderListener listener) {
		this.listener = listener;
		return this;
	}

	//	public FirebaseCloudUploader startUpload() {
	//
	//		if (FirebaseCloudUploader.this.mode == Mode.FILEPATHSTRING) {
	//			new FilePathUploadThread(this).start();
	//		} else {
	//			new FileDataUploadThread(this).start();
	//		}
	//		return this;
	//
	//	}

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
		this.paused = false;
	}

	public String addToQueue(byte[] dataToUpload, String remotePosition, FirebaseUploadItemListener listener) {

		DataStreamUploadItem dataStreamUploadItem = new DataStreamUploadItem(dataToUpload, remotePosition);
		dataStreamUploadItem.setListener(listener);
		this.uploadQueue.put(dataStreamUploadItem.getID(), dataStreamUploadItem);
		return dataStreamUploadItem.getID();

	}

	public String addToQueue(String localPosition, String remotePosition, FirebaseUploadItemListener listener) {

		FileUploadItem fileUploadItem = new FileUploadItem(localPosition, remotePosition);
		fileUploadItem.setListener(listener);
		this.uploadQueue.put(fileUploadItem.getID(), fileUploadItem);
		return fileUploadItem.getID();

	}

}
