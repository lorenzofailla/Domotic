package apps.java.loref;

import com.google.firebase.database.DatabaseError;

/**
 * Provides static methods for log and debug messages effective printing.
 *
 * @author lore_f.
 *         Created 09 dic 2018.
 */

public final class LogUtilities {
    
    public static final String GRAY="[1;47m";
    public static final String CYAN="[1;46m";
    public static final String YELLOW="[1;43m";
    

    @SuppressWarnings("javadoc")
    public static void exceptionLog(Class<?> c, Exception e) {

	System.out.println("EXCEPTION RAISED! " + c.getName() + " [" + e.getClass().getName() + "] :: " + e.getMessage());

    }
    
    @SuppressWarnings("javadoc")
	public static void exceptionLog_REDXTERM(Class<?> c, Exception e) {

		System.out.println((char) 27 + "[1;41mEXCEPTION RAISED!" + (char) 27 + "[0m " + c.getName() + " ["
				+ e.getClass().getName() + "] :: " + e.getMessage());

	}
    
    @SuppressWarnings("javadoc")
	public static void exceptionLog_REDXTERM(Class<?> c, Exception e, String message) {

		System.out.println((char) 27 + "[1;41mEXCEPTION RAISED!" + (char) 27 + "[0m " + c.getName() + " ["
				+ e.getClass().getName() + "] :: " + e.getMessage() + " - " + message);

	}

    @SuppressWarnings("javadoc")
    public static void debugLog(Class<?> c, String message) {

	System.out.println("DEBUG" + c.getName() + " :: " + message);

    }
    
    @SuppressWarnings("javadoc")
    public static void debugLog_XTERM(Class<?> c, String colorXTermSeq, String message) {

	System.out.println((char)27 + colorXTermSeq + "DEBUG"  + (char)27 + "[0m " + c.getName() + " :: " + message);

    }
    
    @SuppressWarnings("javadoc")
    public static void debugLog_GRAYXTERM(Class<?> c, String message) {

	System.out.println((char)27 + GRAY + "DEBUG"  + (char)27 + "[0m " + c.getName() + " :: " + message);

    }
    
    @SuppressWarnings("javadoc")
    public static void firebaseErrorLog_XTERM(DatabaseError e) {

	System.out.println((char)27 + YELLOW + "FIREBASE"  + (char)27 + "[0m " + e.getMessage() + " :: " + e.getDetails());

    }
   
}
