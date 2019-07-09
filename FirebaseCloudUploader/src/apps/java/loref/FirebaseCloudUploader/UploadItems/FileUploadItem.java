package apps.java.loref.FirebaseCloudUploader.UploadItems;

import apps.java.loref.FirebaseCloudUploader.FirebaseCloudUploader.Mode;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f.
 *         Created 12 mar 2019.
 */
@SuppressWarnings("javadoc")
public class FileUploadItem extends UploadItem{
	
	private String localPath=null;
			
	public FileUploadItem(String localPosition, String remotePosition){
		
		setMode(Mode.FILEPATHSTRING);
		setID(this.getMode()+"_"+System.currentTimeMillis());
		this.localPath = localPosition;
		setRemotePosition(remotePosition);
				
	}
	
	public String getLocalPath(){
		return this.localPath;
	}
		
}
