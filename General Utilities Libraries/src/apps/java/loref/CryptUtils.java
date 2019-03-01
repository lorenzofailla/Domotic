package apps.java.loref;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Provides static methods for basic crypting.
 *
 * @author lore_f.
 *         Created 21 dic 2018.
 */
public class CryptUtils {
    
    @SuppressWarnings("javadoc")
    public static void main(String[] args){
	
	System.out.println(args.length + " parameters passed.");
	
	for (int i=0; i<args.length; i++){
	    
	    System.out.println("Parameter n. "+i);
	    System.out.println("Input text: "+ args[i]);
	    System.out.println("Output text: "+ getSHA256HashString(args[i],' '));
	    System.out.println("Output text: "+ getSHA256HashString(args[i]));
	}
	
    }
    
    /**
     * Provides the SHA256 checksum of the input byte array, in the form of a byte array.
     *
     * @param the input byte array
     * @return the SHA256 checksum, in the form of a byte array; or null if some error occurs.
     */
    private static byte[] getSHA256Hash(byte[] input) {

	MessageDigest digest;
	try {

	    digest = MessageDigest.getInstance("SHA-256");
	    return digest.digest(input);

	} catch (NoSuchAlgorithmException exception) {

	    return null;

	}

    }

    
    /**
     * Provides the SHA256 checksum of a given input String, in the form of a HEX String.
     *
     * @param input an input string
     * @return the SHA256 checksum, in the form of a HEX String
     */
    public static String getSHA256HashString(String input) {

	return bytesToHex(getSHA256Hash(input.getBytes()));

    }
    
    /**
     * Provides the SHA256 checksum of a given input String, in the form of a HEX String.
     *
     * @param input an input string
     * @param separator a character that divide each HEX 2-digit code
     * @return the SHA256 checksum, in the form of a HEX String
     */
    public static String getSHA256HashString(String input, char separator) {

	return bytesToHex(getSHA256Hash(input.getBytes()), separator);

    }

    private static String bytesToHex(byte[] hash) {
	
	return bytesToHex(hash, '\0');
	
    }
        
    private static String bytesToHex(byte[] hash, char separator) {

	StringBuffer hexString = new StringBuffer();

	for (int i = 0; i < hash.length; i++) {

	    String hex = Integer.toHexString(0xff & hash[i]);
	    if (hex.length() == 1)
		hexString.append('0');

	    hexString.append(hex);
	    
	    if(separator!='\0' && i < hash.length-1)
		 hexString.append(separator);

	}
		
	return hexString.toString();
	
    }
    
}
