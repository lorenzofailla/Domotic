package apps.java.loref.TelegramBotComm.TelegramBotMessages;

import apps.java.loref.TelegramBotComm.SendMode;
import apps.java.loref.TelegramBotComm.TelegramBotComm;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 20 apr 2019.
 */
@SuppressWarnings("javadoc")
public class TelegramBotMessage {
	
	public TelegramBotMessage(){}
	
	public TelegramBotMessage(String recipientID) {
		this.recipientID = recipientID;
		this.key = recipientID + "_" + TelegramBotComm.getUniqueID();
	}
	
	private String key;
	
	public String getKey() {
		return this.key;
	}

	protected void setKey(String key) {
		this.key = key;
	}
	
	private String tag="";
	
	public String getTag() {
		return this.tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	private String recipientID;

	public String getRecipientID() {
		return this.recipientID;
		
	}

	public void setRecipientID(String recipientID) {
		this.recipientID = recipientID;
		this.key = recipientID + "_" + TelegramBotComm.getUniqueID();
		
	}

	private SendMode mode = SendMode.NEW;

	public SendMode getMode() {
		return this.mode;
	}

	public void setMode(SendMode mode) {
		this.mode = mode;
	}
	
	private String messageID;

	public String getMessageID() {
		return this.messageID;
	}

	protected void setMessageID(String messageID) {
		this.messageID = messageID;
	}
	
	public void setMessageCompoundID(String compoundID){
		
		String[] compoundParts=compoundID.split("[_]");
		if(compoundParts.length==2){
			if(compoundParts[0].equals(this.recipientID)){
				this.messageID=compoundParts[1];
			}
		}
		
	}
	
	private String chatID;

	public String getChatID() {
		return this.chatID;
	}

	protected void setChatID(String chatID) {
		this.chatID = chatID;
	}
	
}
