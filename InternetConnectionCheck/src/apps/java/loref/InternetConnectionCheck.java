/**
 * Copyright 2018 Lorenzo Failla
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package apps.java.loref;

import static apps.java.loref.GeneralUtilitiesLibrary.sleepSafe;
import static apps.java.loref.NetworkTools.checkInetConnection;

import static apps.java.loref.LogUtilities.debugLog_GRAYXTERM;


/**
 * Provides a simple and effective way to monitor the internet connectivity.
 *
 * @author lore_f. Created 23 dic 2018.
 */

@SuppressWarnings({ "javadoc", "unused" })
public class InternetConnectionCheck {

	private String connectivityTestServerAddress = Defaults.CONNECTIVITY_SERVER;

	private void setConnectivityTestServerAddress(String value) {
		this.connectivityTestServerAddress = value;
	}

	private boolean mainThreadActivity = false;

	private boolean getMainThreadActivity() {
		return this.mainThreadActivity;
	}

	private long lastTimeConnectionAvailable = -1;

	public long getLastTimeConnectionAvailable() {
		return this.lastTimeConnectionAvailable;
	}

	private long connectivityCheckRate = Defaults.CONNECTIVITY_CHECK_RATE;

	public void setConnectivityCheckRate(long value) {
		this.connectivityCheckRate = value;
	}

	private boolean connectionAvailable;
	private boolean connectionAvailableFlag;

	public boolean getConnectionAvailable() {
		return this.connectionAvailableFlag;
	}
	
	private boolean debugMode=false;
	
	public void setDebugMode(boolean value){
		this.debugMode = value;
	}

	private InternetConnectionStatusListener listener;

	public void setListener(InternetConnectionStatusListener value) {
		this.listener = value;
	}

	public InternetConnectionCheck() {
	}

	public InternetConnectionCheck(String connectivityServerAddress) {
		this.connectivityTestServerAddress = connectivityServerAddress;
	}

	private class MainThread extends Thread {

		@Override
		public void run() {

			InternetConnectionCheck.this.lastTimeConnectionAvailable = System.currentTimeMillis();

			InternetConnectionCheck.this.connectionAvailable = !checkInetConnection(
					InternetConnectionCheck.this.connectivityTestServerAddress);
			InternetConnectionCheck.this.connectionAvailableFlag = !(InternetConnectionCheck.this.connectionAvailable);

			while (InternetConnectionCheck.this.mainThreadActivity && (InternetConnectionCheck.this.listener != null)) {

				boolean connectionStatus = checkInetConnection(
						InternetConnectionCheck.this.connectivityTestServerAddress);
				
				if(InternetConnectionCheck.this.debugMode)
					debugLog_GRAYXTERM(this.getClass(), "Connection check result: " + connectionStatus+ ". Connection test server: " + InternetConnectionCheck.this.connectivityTestServerAddress);
				
				if (connectionStatus != InternetConnectionCheck.this.connectionAvailable) {

					if (connectionStatus) {

						InternetConnectionCheck.this.listener.onConnectionRestored(
								System.currentTimeMillis() - InternetConnectionCheck.this.lastTimeConnectionAvailable);
						InternetConnectionCheck.this.lastTimeConnectionAvailable = System.currentTimeMillis();

					} else {

						InternetConnectionCheck.this.listener.onConnectionLost();

					}
					
					InternetConnectionCheck.this.connectionAvailable = connectionStatus;
					InternetConnectionCheck.this.connectionAvailableFlag = InternetConnectionCheck.this.connectionAvailable;
				}

				sleepSafe(InternetConnectionCheck.this.connectivityCheckRate);
				if(InternetConnectionCheck.this.debugMode)
					debugLog_GRAYXTERM(this.getClass(), "Thread loop check. Sleep time: "+InternetConnectionCheck.this.connectivityCheckRate);
				
			}

		}

	}

	public void start() {

		this.mainThreadActivity = true;
		new MainThread().start();

		if(this.debugMode)
			debugLog_GRAYXTERM(this.getClass(), "Main thread started.");
			
			
	}

	public void stop() {

		this.mainThreadActivity = false;

		if(this.debugMode)
			debugLog_GRAYXTERM(this.getClass(), "Main thread stopped.");
		
	}

}
