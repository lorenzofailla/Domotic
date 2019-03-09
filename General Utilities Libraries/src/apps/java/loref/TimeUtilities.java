package apps.java.loref;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

public class TimeUtilities {

	public static String formatEpochTime(long value) {

		return "";

	}

	/**
	 * TODO Put here a description of what this method does.
	 *
	 * @param format
	 * @return
	 */
	public static String getTimeStamp(String format) {

		GregorianCalendar gc = new GregorianCalendar();
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.format(gc.getTime());

	}

	/**
	 * TODO Put here a description of what this method does.
	 *
	 * @return
	 */
	public static String getTimeStamp() {

		return String.format("%d", System.currentTimeMillis());

	}

}
