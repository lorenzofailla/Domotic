package apps.java.loref;

import com.google.cloud.storage.Blob;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 10 mar 2019.
 */
public interface FirebaseCloudUploaderListener {

	void onComplete(FirebaseCloudUploader uploader, Blob info, String fileShortName);

	void onError(FirebaseCloudUploader uploader, Exception e);

}
