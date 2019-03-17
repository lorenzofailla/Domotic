package apps.java.loref;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DatabaseReference.CompletionListener;

import static apps.java.loref.LogUtilities.printLog;

public class FirebaseDBUpdateLogger implements CompletionListener {
	
	private final static String DEFAULT_SUBJECT = "Firebase DB";
	private final static String DEFAULT_LOG_TOPIC = "FirebaseDBUpdateLogger";
		
	private String subject=DEFAULT_SUBJECT;
	private String logTopic=DEFAULT_LOG_TOPIC;
	
	private boolean negativeResultsOnly=false; 
	
	public FirebaseDBUpdateLogger(String subject) {
		this.subject=subject;

	}
	
	public FirebaseDBUpdateLogger(String logTopic, String subject) {
		this.logTopic=logTopic;
		this.subject=subject;

	}
	
	public FirebaseDBUpdateLogger(String logTopic, String subject, boolean negativeResultsOnly) {
		this.logTopic=logTopic;
		this.subject=subject;
		this.negativeResultsOnly=negativeResultsOnly;
	}

	
	@Override
	public void onComplete(DatabaseError error, DatabaseReference ref) {
		
		if(error==null && !negativeResultsOnly) {
	
			printLog(logTopic, subject + " successfully updated");
						
		} else {
			
			printLog(logTopic, "Error during update of " + subject + " . Message:\"" + error.getMessage() + "\"");
			
		}
		
	}

}
