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

public class DefaultConfigValues {

    public static final String CONFIG_FILE_LOCATION = "/etc/domotic/domotic.conf";
    public static final String LOCAL_COMMAND_DIRECTORY = "/var/lib/domotic";

    public static final String ERROR = "[error]";

    public final static long CONNECTION_CHECK_INTERVAL_TIMEOUT_LONG = 10000L;
    public final static long CONNECTION_CHECK_INTERVAL_TIMEOUT_SHORT = 10000L;

    public final static long DEVICE_STATUS_UPDATE_RATE = 60000L;
    public final static long DEVICE_NETWORK_STATUS_UPDATE_RATE = 3600000L;

    public final static long FIREBASE_DB_UPDATE_TIMEOUT = 10000L;

    public final static String CONNECTIVITY_TEST_SERVER_ADDRESS = "http://lorenzofailla.esy.es/domotic/domotic-connection-test.php";
    public final static long CONNECTIVITY_TEST_RATE = 10000L;

}
