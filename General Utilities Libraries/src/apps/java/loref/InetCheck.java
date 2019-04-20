/**
 * 
 */
package apps.java.loref;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;

/**
 * @author lore_f
 *
 */

public class InetCheck {

	private static final long DEFAULT_INET_CONNECTION_CHECK_INTERVAL_LONG = 300000L;
	private static final long DEFAULT_INET_CONNECTION_CHECK_INTERVAL_SHORT = 30000L;

	private final static String DEFAULT_HOST = "www.google.com";
	private final static int DEFAULT_TIMEOUT = 5000;

	private boolean lastInetConnectionStatus = false;
	private boolean inetConnectionStatus;

	private boolean continueLoop = true;
	private long tickTime;

	private String host = DEFAULT_HOST;
	private int timeOut = DEFAULT_TIMEOUT;
	private boolean persistentNotification = false;
	private long longInterval = DEFAULT_INET_CONNECTION_CHECK_INTERVAL_LONG;
	private long shortInterval = DEFAULT_INET_CONNECTION_CHECK_INTERVAL_SHORT;

	private InetCheckListener listener;
	
	private long totalCountTime=0L;
	private long availableCountTime=0L;

	public void setListener(InetCheckListener l) {
		this.listener = l;
	}

	public InetCheck() {

	}

	public InetCheck(InetCheckListener l) {

		this.listener = l;

	}

	public static boolean checkInetConnection() {
		return isReachableByPing(DEFAULT_HOST, DEFAULT_TIMEOUT);
	}

	public static boolean checkInetConnection(String host) {
		return isReachableByPing(host, DEFAULT_TIMEOUT);
	}

	public static boolean checkInetConnection(String host, int timeOut) {
		return isReachableByPing(host, timeOut);
	}

	private static boolean isReachableByPing(String host, int timeOut) {

		try {

			InetAddress inetAddress = InetAddress.getByName(host);
			return inetAddress.isReachable(timeOut);

		} catch (UnknownHostException e) {

			return false;

		} catch (IOException e) {

			return false;

		}

	}

	public void init() {

		this.tickTime = this.shortInterval;
		this.mainLoop.start();

	}

	private Thread mainLoop = new Thread() {

		public void run() {

			// registra lo stato precedente, in modo che la prima volta possa
			// generare una notifica
			InetCheck.this.lastInetConnectionStatus = !isReachableByPing(InetCheck.this.host, InetCheck.this.timeOut);

			while (InetCheck.this.continueLoop) {

				// controlla lo stato della connessione internet
				InetCheck.this.inetConnectionStatus = isReachableByPing(InetCheck.this.host, InetCheck.this.timeOut);

				// se ci sono le condizioni, notifica lo stato della connessione
				// tramite il listener
				if (InetCheck.this.listener != null && InetCheck.this.persistentNotification) {

					InetCheck.this.listener.onCheck(InetCheck.this.inetConnectionStatus);

				}
				
				// update the counter of the total time
				InetCheck.this.totalCountTime+=InetCheck.this.tickTime;

				if (InetCheck.this.inetConnectionStatus) {

					// la connessione internet è presente

					// se ci sono le condizioni, notifica la variazione dello
					// stato della connessione tramite il listener

					if (InetCheck.this.listener != null && !InetCheck.this.lastInetConnectionStatus) {

						InetCheck.this.listener.onConnectionRestored();

					}
					
					// update the counter of the available time (assume the connection has been available all the time)
					InetCheck.this.availableCountTime+=InetCheck.this.tickTime;

					// imposta il prossimo controllo
					InetCheck.this.tickTime = InetCheck.this.longInterval;

				} else {

					// la connessione internet non � presente

					// se ci sono le condizioni, notifica la variazione dello
					// stato della connessione tramite il listener

					if (InetCheck.this.listener != null && InetCheck.this.lastInetConnectionStatus) {

						InetCheck.this.listener.onConnectionLost();

					}

					// imposta il prossimo controllo
					InetCheck.this.tickTime = InetCheck.this.shortInterval;

				}

				// registra lo stato della connessione
				InetCheck.this.lastInetConnectionStatus = InetCheck.this.inetConnectionStatus;

				sleepSafe(InetCheck.this.tickTime);

			}

		}

	};

	public boolean getConnectionStatus() {
		return this.inetConnectionStatus;
	}

	public void terminate() {
		this.continueLoop = false;
	}
	
	public double getAvailabilityPercentage() {
		return (double) 1.0 * this.availableCountTime / this.totalCountTime;
	}

	/*
	 * Getters / Setters
	 */

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getTimeOut() {
		return this.timeOut;
	}

	public void setTimeOut(int timeOut) {
		this.timeOut = timeOut;
	}

	public boolean isPersistentNotification() {
		return this.persistentNotification;
	}

	public void setPersistentNotification(boolean persistentNotification) {
		this.persistentNotification = persistentNotification;
	}

	public long getLongInterval() {
		return this.longInterval;
	}

	public void setLongInterval(long longInterval) {
		this.longInterval = longInterval;
	}

	public long getShortInterval() {
		return this.shortInterval;
	}

	public void setShortInterval(long shortInterval) {
		this.shortInterval = shortInterval;
	}

	public static void main(String[] args) {

		InetCheckListener inetCheckListener = new InetCheckListener() {

			@Override
			public void onConnectionRestored() {
				System.out.println("Connection OK");

			}

			@Override
			public void onConnectionLost() {
				System.out.println("Connection KO");

			}

			@Override
			public void onCheck(boolean status) {
				System.out.println("Connection check: " + status);

			}
			
		};

		InetCheck inetCheck = new InetCheck();
		inetCheck.setListener(inetCheckListener);
		inetCheck.setLongInterval(10000);
		inetCheck.setShortInterval(10000);
		inetCheck.setPersistentNotification(true);
		inetCheck.init();

	}

}
