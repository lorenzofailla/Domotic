package apps.java.loref;

import java.util.HashMap;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 10 mar 2019.
 */
@SuppressWarnings("javadoc")
abstract class UploadItem {

	private FirebaseCloudUploader.Mode uploadMode;
	private String id;
	private String remotePosition;
	
	FirebaseUploadItemListener listener;
	
	public void setListener(FirebaseUploadItemListener value){
		this.listener=value;
	}

	public FirebaseUploadItemListener getListener(){
		return this.listener;
	}
	
	public void setMode(FirebaseCloudUploader.Mode mode) {

		this.uploadMode = mode;

	}

	public FirebaseCloudUploader.Mode getMode() {

		return this.uploadMode;

	}

	public void setID(String value) {

		this.id = value;

	}
	
	public String getID(){
		return this.id;
	}

	public void setRemotePosition(String value) {
		this.remotePosition = value;
	}
	
	public String getRemotePosition() {
		return this.remotePosition;
	}

}
