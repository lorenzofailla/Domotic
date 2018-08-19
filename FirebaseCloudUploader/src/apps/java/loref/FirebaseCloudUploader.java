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

public class FirebaseCloudUploader {

    private String localFileName;
    private String remotePosition;

    /* constructors */
    public FirebaseCloudUploader(String localFileName, String remotePosition) {
	this.localFileName = localFileName;
	this.remotePosition = remotePosition;
    }

    /* interface */
    public interface FirebaseCloudUploaderListener {

	void onComplete(FirebaseCloudUploader uploader, Blob info, String fileShortName);

	void onError(FirebaseCloudUploader uploader, Exception e);

    }

    private FirebaseCloudUploaderListener listener;

    /* subclasses */

    private class MainThread extends Thread {

	private FirebaseCloudUploader parent;

	public MainThread(FirebaseCloudUploader parent) {
	    this.parent = parent;
	}

	public void run() {

	    super.run();

	    // initialize the <File> object
	    File file = new File(localFileName);

	    // initialize the storage bucket object

	    Bucket storageBucket = StorageClient.getInstance().bucket();

	    try {

		// initialize an inputstream reading the local file
		FileInputStream inputStream = new FileInputStream(file);

		// starts the upload operation
		Blob uploadInfo = storageBucket.create(remotePosition + file.getName(), inputStream);

		if (listener != null)
		    listener.onComplete(parent, uploadInfo, file.getName());

		inputStream.close();

	    } catch (IOException e) {

		if (listener != null)
		    listener.onError(parent, e);

	    }

	}

    };

    /* methods */
    public FirebaseCloudUploader setListener(FirebaseCloudUploaderListener listener) {
	this.listener = listener;
	return this;
    }

    public FirebaseCloudUploader startUpload() {

	new MainThread(this).start();
	return this;

    };

    public static String getSignedUrl(Blob blob, int timeout, TimeUnit unit, String jsonSACFilePath) {

	try {

	    return blob.signUrl(timeout, unit, SignUrlOption.signWith(ServiceAccountCredentials.fromStream(new FileInputStream(jsonSACFilePath)))).toString();

	} catch (IOException e) {
	    
	    return e.getMessage();

	}

    }

}
