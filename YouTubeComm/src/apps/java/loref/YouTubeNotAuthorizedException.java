package apps.java.loref;

public class YouTubeNotAuthorizedException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 3774035755423013209L;
    
    private static final String DEFAULT_MESSAGE = "Youtube OAuth procedure failed.";
    
    private String message;
    
    public YouTubeNotAuthorizedException(){
	message=DEFAULT_MESSAGE;
	
    };
    
    public YouTubeNotAuthorizedException(String message){
	this.message=message;
	
    };
    
    public String getMessage(){
	return this.message;
    }
    
    

}
