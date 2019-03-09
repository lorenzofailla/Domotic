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

import java.io.File;
import java.io.IOException;
import static apps.java.loref.GeneralUtilitiesLibrary.parseShellCommand;
import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;

@SuppressWarnings("javadoc")
public class LinuxCommands {

	public static final String PUBLIC_IP_COMMAND = "curl ifconfig.co";
	public static final String LOCAL_IP_COMMAND = "hostname -I";

	public static final String ERROR = "[error]";

	public static String getUptime() {

		try {

			return parseShellCommand("uptime").replaceAll("[\n\r]", "");

		} catch (IOException | InterruptedException e) {

			exceptionLog_REDXTERM(LinuxCommands.class, e);
			return ERROR;

		}

	}

	public static String getLocalIPAddresses() {

		try {

			return parseShellCommand(LOCAL_IP_COMMAND).replaceAll("[\r\n]", "");

		} catch (IOException | InterruptedException e) {

			exceptionLog_REDXTERM(LinuxCommands.class, e);
			return ERROR;

		}

	}

	public static String getPublicIPAddresses() {

		try {

			return parseShellCommand(PUBLIC_IP_COMMAND).replaceAll("[\r\n]", "");

		} catch (IOException | InterruptedException e) {

			exceptionLog_REDXTERM(LinuxCommands.class, e);
			return ERROR;

		}

	}

	public static double getFreeSpace(String mountPosition) {

		long reply = new File(mountPosition).getUsableSpace();
		return reply / 1024.0 / 1024.0;

	}

	public static double getTotalSpace(String mountPosition) {

		long reply = new File(mountPosition).getTotalSpace();
		return reply / 1024.0 / 1024.0;

	}

}
