package apps.java.loref.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.cloud.storage.Blob;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredential;
import com.google.firebase.auth.FirebaseCredentials;

import apps.java.loref.FirebaseCloudUploader;
import apps.java.loref.FirebaseCloudUploader.FirebaseCloudUploaderListener;;

public class UploadTest {

    public static void main(String[] args) throws IOException {

	FileInputStream serviceAccount = new FileInputStream("C:\\Users\\lore_f\\Firebase_Keys\\domotic-28a5e-firebase-adminsdk-vxto2-9037ff05a1.json");
	FirebaseOptions options = new FirebaseOptions.Builder()
		.setCredential(FirebaseCredentials.fromCertificate(serviceAccount))
		.setStorageBucket("domotic-28a5e.appspot.com")
		.build();

	FirebaseApp.initializeApp(options);

	serviceAccount.close();

	FirebaseCloudUploaderListener listener = new FirebaseCloudUploaderListener() {

	    @Override
	    public void onError(FirebaseCloudUploader uploader, Exception e) {
		System.out.println("Error: " + e.getMessage());

	    }

	    @Override
	    public void onComplete(FirebaseCloudUploader uploader, Blob info, String fileShortName) {
		System.out.println("Upload completed.");
		uploader.setListener(null);
		System.exit(0);

	    }

	};

	String remoteLocation = "Groups/lorenzofailla/Devices/lorenzofailla-home/VideoSurveillance/Events/0/";
	String localFile = "C:\\Users\\lore_f\\Documents\\CI Lorenzo Failla.pdf";
	FirebaseCloudUploader uploader;
	uploader = new FirebaseCloudUploader(localFile, remoteLocation).setListener(listener).startUpload();

	System.out.println("Upload started.");

    }

}
