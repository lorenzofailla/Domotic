package apps.java.loref.FirebaseCloudUploader.UploadItems;

import apps.java.loref.FirebaseCloudUploader.FirebaseCloudUploader.Mode;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f.
 *         Created 10 mar 2019.
 */
@SuppressWarnings("javadoc")
public class DataStreamUploadItem extends UploadItem {
	
	private byte[] fileData;
	
	public DataStreamUploadItem(byte[] data, String remotePosition) {
		
		setMode(Mode.BYTEARRAY);
		setID(this.getMode()+"_"+System.currentTimeMillis());
		this.fileData=data;
		setRemotePosition(remotePosition);
				
	}
	
	public byte[] getFileData(){
		
		return this.fileData;
		
	}
		
}
