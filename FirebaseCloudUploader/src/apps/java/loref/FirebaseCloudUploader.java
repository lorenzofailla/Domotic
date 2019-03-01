package apps.java.loref;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.google.firebase.cloud.StorageClient;

@SuppressWarnings("javadoc")
public class FirebaseCloudUploader {

	private enum Mode {
		BYTEARRAY, FILEPATHSTRING
	}

	private Mode mode;

	private String localFileName;
	private byte[] fileData;
	private String remotePosition;
	private String remoteFileName;

	// constructors
	public FirebaseCloudUploader(String localFileName, String remotePosition) {
		this.localFileName = localFileName;
		this.remoteFileName = localFileName;
		this.remotePosition = remotePosition;
		this.mode = Mode.FILEPATHSTRING;
	}
	
	public FirebaseCloudUploader(String localFileName, String remotePosition, String remoteFileName) {
		this.localFileName = localFileName;
		this.remoteFileName = remoteFileName;
		this.remotePosition = remotePosition;
		this.mode = Mode.FILEPATHSTRING;
	}

	public FirebaseCloudUploader(byte[] fileData, String remotePosition) {
		this.fileData = fileData;
		this.remotePosition = remotePosition;
		this.mode = Mode.BYTEARRAY;
	}

	// interface
	public interface FirebaseCloudUploaderListener {

		void onComplete(FirebaseCloudUploader uploader, Blob info, String fileShortName);

		void onError(FirebaseCloudUploader uploader, Exception e);

	}

	private FirebaseCloudUploaderListener listener;

	// subclasses

	private class FilePathUploadThread extends Thread {

		private FirebaseCloudUploader parent;

		public FilePathUploadThread(FirebaseCloudUploader parent) {
			this.parent = parent;
		}

		@Override
		public void run() {

			super.run();

			// initialize the <File> object
			File file = new File(FirebaseCloudUploader.this.localFileName);

			// initialize the storage bucket object
			Bucket storageBucket = StorageClient.getInstance().bucket();

			try {

				// initialize an inputstream reading the local file
				FileInputStream inputStream = new FileInputStream(file);

				// starts the upload operation
				Blob uploadInfo = storageBucket.create(FirebaseCloudUploader.this.remotePosition ,
						inputStream);

				if (FirebaseCloudUploader.this.listener != null)
					FirebaseCloudUploader.this.listener.onComplete(this.parent, uploadInfo, FirebaseCloudUploader.this.remotePosition);

				inputStream.close();

			} catch (IOException e) {

				if (FirebaseCloudUploader.this.listener != null)
					FirebaseCloudUploader.this.listener.onError(this.parent, e);

			}

		}

	}

	private class FileDataUploadThread extends Thread {

		private FirebaseCloudUploader parent;

		public FileDataUploadThread(FirebaseCloudUploader parent) {
			this.parent = parent;
		}

		@Override
		public void run() {

			super.run();

			// initialize the storage bucket object
			Bucket storageBucket = StorageClient.getInstance().bucket();

			// starts the upload operation
			Blob uploadInfo = storageBucket.create(FirebaseCloudUploader.this.remotePosition,
					FirebaseCloudUploader.this.fileData);

			if (FirebaseCloudUploader.this.listener != null)
				FirebaseCloudUploader.this.listener.onComplete(this.parent, uploadInfo,
						FirebaseCloudUploader.this.remotePosition);
		
		}

	}

	// methods
	public FirebaseCloudUploader setListener(FirebaseCloudUploaderListener listener) {
		this.listener = listener;
		return this;
	}

	public FirebaseCloudUploader startUpload() {

		if (FirebaseCloudUploader.this.mode == Mode.FILEPATHSTRING) {
			new FilePathUploadThread(this).start();
		} else {
			new FileDataUploadThread(this).start();
		}
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

}
