package apps.java.loref;

import com.google.cloud.storage.Blob;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 10 mar 2019.
 */
public interface FirebaseUploadItemListener {

	void onComplete(Blob info, String itemID);
	void onError(Exception e);

}
