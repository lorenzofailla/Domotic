package apps.java.loref;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.Credential.AccessMethod;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTubeScopes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

/**
 * Shared class used by every sample. Contains methods for authorizing a user
 * and caching credentials.
 */
public class Auth {

	public static List<String> DEFAULT_SCOPES = Arrays.asList(YouTubeScopes.YOUTUBE);

	/**
	 * Define a global instance of the HTTP transport.
	 */
	public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

	/**
	 * Define a global instance of the JSON factory.
	 */
	public static final JsonFactory JSON_FACTORY = new JacksonFactory();

	/**
	 * This is the directory that will be used under the user's home directory
	 * where OAuth tokens will be stored.
	 */

	// @formatter:off

	//private static final String CREDENTIALS_DIRECTORY = "/etc/domotic/.oauth-token";
	//private static final String CLIENT_SECRETS_FILE_PATH = "/etc/domotic/client_secret.json";

	private static final String CREDENTIALS_DIRECTORY = "C:\\Users\\lore_f\\.oauth-token";
	private static final String CLIENT_SECRETS_FILE_PATH = "C:\\Users\\lore_f\\Google-API_keys\\client_secret_410796746391-j68vlpi0pjn5cerf7q7rmfpjvofufu0g.apps.googleusercontent.com.json";

	// @formatter:on

	/**
	 * Authorizes the installed application to access user's protected data.
	 *
	 * @param scopes
	 *            list of scopes needed to run youtube upload.
	 * @param credentialDatastore
	 *            name of the credential datastore to cache OAuth tokens
	 */
	public static Credential getCredential(List<String> scopes, String dataStoreID) throws IOException {

		// Load client secrets.
		Reader clientSecretReader = new InputStreamReader(new FileInputStream(CLIENT_SECRETS_FILE_PATH));
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, clientSecretReader);

		// This creates the credentials datastore at
		// ~/.oauth-credentials/${credentialDatastore}
		FileDataStoreFactory fileDataStoreFactory = new FileDataStoreFactory(new File(CREDENTIALS_DIRECTORY));
		DataStore<StoredCredential> datastore = fileDataStoreFactory.getDataStore(dataStoreID);

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecrets, scopes).setCredentialDataStore(datastore).build();

		// Build the local server and bind it to port 8080
		LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setPort(-1).build();

		// Authorize
		return new AuthorizationCodeInstalledApp(flow, localReceiver).authorize(flow.getClientId());

	}

	public static boolean removeCredential(List<String> scope, String credentialDatastore) {

		// This creates the credentials datastore at
		// ~/.oauth-credentials/${credentialDatastore}
		FileDataStoreFactory fileDataStoreFactory;
		try {

			fileDataStoreFactory = new FileDataStoreFactory(new File(CREDENTIALS_DIRECTORY));
			fileDataStoreFactory.getDataStore(credentialDatastore).clear();

			return true;

		} catch (IOException e) {

			return false;

		}

	}

	public static boolean isAuthorized(String credentialDatastore) {

		/*
		 * checks if exists an OAuth stored credential for the given
		 * credentialDatastore
		 */

		FileDataStoreFactory fileDataStoreFactory;
		try {

			// Load client secrets.
			Reader clientSecretReader = new InputStreamReader(new FileInputStream(CLIENT_SECRETS_FILE_PATH));
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, clientSecretReader);

			String clientID = clientSecrets.getDetails().getClientId();

			fileDataStoreFactory = new FileDataStoreFactory(new File(CREDENTIALS_DIRECTORY));
			DataStore<StoredCredential> datastore = fileDataStoreFactory.getDataStore(credentialDatastore);

			return datastore.keySet().contains(clientID);

		} catch (IOException e) {

			return false;
		}

	}

}