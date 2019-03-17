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

import static apps.java.loref.GeneralUtilitiesLibrary.parseShellCommand;
import static apps.java.loref.GeneralUtilitiesLibrary.execShellCommand;

import java.io.IOException;

public class TransmissionDaemonCommands {

	public static String getTorrentsList() {

		try {

			return parseShellCommand("transmission-remote -n transmission:transmission -l");

		} catch (IOException | InterruptedException e) {

			return "error - " + e.getMessage();

		}

	}

	public static String startTorrent(String torrentID) {

		try {

			execShellCommand("transmission-remote -n transmission:transmission -t" + torrentID + " -s");
			return "torrent id: " + torrentID + "started.";

		} catch (IOException e) {

			return "error - " + e.getMessage();

		}

	}

	public static String stopTorrent(String torrentID) {

		try {

			execShellCommand("transmission-remote -n transmission:transmission -t" + torrentID + " -S");
			return "torrent id: " + torrentID + "stopped.";

		} catch (IOException e) {

			return "error - " + e.getMessage();

		}

	}

	public static String removeTorrent(String torrentID) {

		try {

			execShellCommand("transmission-remote -n transmission:transmission -t" + torrentID + " -r");
			return "torrent id: " + torrentID + "removed.";

		} catch (IOException e) {

			return "error - " + e.getMessage();

		}

	}
	
	public static String deleteTorrent(String torrentID) {

		try {

			execShellCommand("transmission-remote -n transmission:transmission -t" + torrentID + " -rad");
			return "torrent id: " + torrentID + "removed.";

		} catch (IOException e) {

			return "error - " + e.getMessage();

		}

	}

	public static String addTorrent(String torrentID) {

		try {

			execShellCommand("transmission-remote -n transmission:transmission -a " + torrentID);
			return "torrent id: " + torrentID + "added.";

		} catch (IOException e) {

			return "error - " + e.getMessage();

		}

	}

}
