package apps.java.loref;

import java.io.File;
import java.io.FileInputStream;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.CdnSettings;
import com.google.api.services.youtube.model.LiveBroadcast;
import com.google.api.services.youtube.model.LiveBroadcastContentDetails;
import com.google.api.services.youtube.model.LiveBroadcastListResponse;
import com.google.api.services.youtube.model.LiveBroadcastSnippet;
import com.google.api.services.youtube.model.LiveBroadcastStatus;
import com.google.api.services.youtube.model.LiveStream;
import com.google.api.services.youtube.model.LiveStreamListResponse;
import com.google.api.services.youtube.model.LiveStreamSnippet;
import com.google.api.services.youtube.model.LiveStreamStatus;
import com.google.common.collect.Lists;

import static apps.java.loref.LogUtilities.exceptionLog_REDXTERM;
import static apps.java.loref.LogUtilities.debugLog_GRAYXTERM;

@SuppressWarnings("javadoc")
public class YouTubeComm {

	private static final String LIVEBROADCASTSTATUS_COMPLETE = "complete";
	private static final String LIVEBROADCASTSPRIVACYSTATUS_UNLISTED = "unlisted";

	private static final List<String> DEFAULT_SCOPES = Lists.newArrayList("https://www.googleapis.com/auth/youtube");
	private static final String DEFAULT_CREDENTIALDATASTORE = "createbroadcasts";

	public static final JsonFactory JSON_FACTORY = new JacksonFactory();

	private String clientSecretJSONPath;
	private String oauthDirectoryPath;
	private String credentialDatastore;
	private String appName;

	private List<String> scopes;

	private YouTube youTube;

	private boolean debugMode = false;

	public void setDebugMode(boolean status) {
		this.debugMode = status;
	}

	private YouTubeCommListener listener;

	public void setListener(YouTubeCommListener l) {
		this.listener = l;
	}

	public YouTubeComm(String appName, String clientSecretJSONPath, String oauthDirectoryPath)
			throws YouTubeNotAuthorizedException {

		this.clientSecretJSONPath = clientSecretJSONPath;
		this.oauthDirectoryPath = oauthDirectoryPath;
		this.scopes = DEFAULT_SCOPES;
		this.credentialDatastore = DEFAULT_CREDENTIALDATASTORE;
		this.appName = appName;

		if (this.debugMode) {

			System.out.println(this.clientSecretJSONPath);
			System.out.println(this.oauthDirectoryPath);
			System.out.println(this.appName);

		}

		if (!isAuthorized()) {

			throw new YouTubeNotAuthorizedException("Client is not Authorized");

		}

		if (this.debugMode) {

			System.out.println("Client Authorized");
		}

		// inizializza le credenziali di accesso
		Credential credential = getCredential();
		if (credential == null) {

			throw new YouTubeNotAuthorizedException("Cannot retrieve OAuth credential.");

		}

		if (this.debugMode) {
			debugLog_GRAYXTERM(this.getClass(), "Credential obtained");
		}

		// inizializza l'istanza dell'interfaccia YouTube
		this.youTube = new YouTube.Builder(new NetHttpTransport(), JSON_FACTORY, credential)
				.setApplicationName(this.appName).build();

		if (this.debugMode) {

			debugLog_GRAYXTERM(this.getClass(), "YouTubeComm object created.");
		}

	}

	public List<LiveBroadcast> getLiveBroadcastsList() {

		YouTube.LiveBroadcasts.List liveBroadcastsListRequest;

		try {

			liveBroadcastsListRequest = this.youTube.liveBroadcasts().list("id,snippet,contentDetails,status");
			liveBroadcastsListRequest.setBroadcastType("all").setBroadcastStatus("all");
			LiveBroadcastListResponse liveBroadcastListResponse = liveBroadcastsListRequest.execute();

			return liveBroadcastListResponse.getItems();

		} catch (IOException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
			return null;

		}

	}

	public List<LiveStream> getLiveStreamList() {

		YouTube.LiveStreams.List liveStreamsListRequest;

		try {

			liveStreamsListRequest = this.youTube.liveStreams().list("id,snippet,cdn,status");
			liveStreamsListRequest.setMine(true);
			LiveStreamListResponse liveStreamListResponse = liveStreamsListRequest.execute();

			return liveStreamListResponse.getItems();

		} catch (IOException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
			return null;

		}

	}

	/**
	 * Elimina i LiveBroadcast il cui LifeCycleStatus è in stato "complete".
	 * Restituisce il numero di LiveBroadcast trovati in stato "complete".
	 */
	public int deleteCompletedBroadcasts() {



		// contatore dei LiveBroadcast trovati in stato "complete"
		int nOfCompleteLiveBroadcasts = 0;

		// ottiene una lista di tutti i live broadcast
		List<LiveBroadcast> liveBroadcastsList = getLiveBroadcastsList();

		// se siamo in debug mode stampa un messaggio di log
		if (this.debugMode) {
			debugLog_GRAYXTERM(YouTubeComm.class, liveBroadcastsList.size() + " total live broadcast found.");
		}

		// esamina tutti gli elementi della lista
		for (LiveBroadcast l : liveBroadcastsList) {

			if (l.getStatus().getLifeCycleStatus().equals(LIVEBROADCASTSTATUS_COMPLETE)) {
				// il LiveBroadcast è in LifeCycleStatus "complete"

				// incrementa il contatore
				nOfCompleteLiveBroadcasts++;

				// elimina il LiveBroadcast
				deleteLiveBroadcast(l.getId());

			}

		}

		// se siamo in debug mode stampa un messaggio di log
		if (this.debugMode) {
			debugLog_GRAYXTERM(YouTubeComm.class, nOfCompleteLiveBroadcasts + " completed live broadcast found.");
		}

		return nOfCompleteLiveBroadcasts;

	}

	/**
	 * Elimina il LiveStream il cui ID è passato in argomento. L'eliminazione
	 * viene fatta all'interno di un thread.
	 */
	public void deleteLiveStream(String livestreamID) {

		new Thread() {

			@Override
			public void run() {

				try {

					YouTubeComm.this.youTube.liveStreams().delete(livestreamID).execute();

					// richiama il callback
					if (YouTubeComm.this.listener != null) {
						YouTubeComm.this.listener.onLiveStreamDeleted(livestreamID);
					}

					// se siamo in debug mode, stampa un messaggio di log
					if (YouTubeComm.this.debugMode) {
						debugLog_GRAYXTERM(YouTubeComm.class,
								"Live stream \"" + livestreamID + "\" successfully deleted.");
					}

				} catch (IOException e) {

					exceptionLog_REDXTERM(YouTubeComm.class, e);

				}

			}

		}.start();

	}

	/**
	 * Elimina il LiveBroadcast il cui ID è passato in argomento, e il
	 * LiveStream associato. L'eliminazione viene fatta all'interno di un
	 * thread.
	 */
	public void deleteLiveBroadcast(final String broadcastID) {

		new Thread() {

			@Override
			public void run() {

				try {

					// recupera l'ID del live stream associato, e lo elimina
					deleteLiveStream(getBoundLiveStreamID(broadcastID));

					// esegue la richiesta per eliminare il Broadcast ID
					YouTubeComm.this.youTube.liveBroadcasts().delete(broadcastID).execute();

					// richiama il callback
					if (YouTubeComm.this.listener != null) {
						YouTubeComm.this.listener.onLiveBroadCastDeleted(broadcastID);
					}

					// se siamo in debug mode, stampa un messaggio di log
					if (YouTubeComm.this.debugMode) {
						debugLog_GRAYXTERM(YouTubeComm.class,
								"Live broadcast \"" + broadcastID + "\" successfully deleted.");
					}

				} catch (IOException e) {

					exceptionLog_REDXTERM(YouTubeComm.class, e);

				}

			}

		}.start();

	}

	/**
	 * recupera l'ID del LiveStream associato al LiveBroadCast il cui ID è
	 * passato in argomento restituisce una stringa vuota in caso non sia stato
	 * possibile trovare l'ID
	 */
	public String getBoundLiveStreamID(String broadcastID) {

		YouTube.LiveBroadcasts.List request;
		try {
			request = this.youTube.liveBroadcasts().list("id,contentDetails");

			request.setId(broadcastID);
			LiveBroadcastListResponse response = request.execute();

			return response.getItems().get(0).getContentDetails().getBoundStreamId();

		} catch (IOException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
			return "";

		}

	}

	/**
	 * checks if exists an OAuth stored credential for the actual
	 * credentialDatastore and Client ID as per client secrets JSON.
	 * 
	 * returns true if an OAuth stored credential exists, false otherwise.
	 */
	public boolean isAuthorized() {

		// inizializza il FileDataStoreFactory
		FileDataStoreFactory fileDataStoreFactory;

		try {

			// carica i secrets del client
			Reader clientSecretReader = new InputStreamReader(new FileInputStream(this.clientSecretJSONPath));
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, clientSecretReader);

			// recupera il client id dai secrets
			String clientID = clientSecrets.getDetails().getClientId();

			// recupera il DataStore per il credentialDataStore applicabile
			fileDataStoreFactory = new FileDataStoreFactory(new File(this.oauthDirectoryPath));
			DataStore<StoredCredential> datastore = fileDataStoreFactory.getDataStore(this.credentialDatastore);

			// controlla che il set di keys del DataStore contenga il client id
			return datastore.keySet().contains(clientID);

		} catch (IOException e) {

			exceptionLog_REDXTERM(this.getClass(), e);
			return false;
		}

	}

	private Credential getCredential() {

		// Load client secrets.
		Reader clientSecretReader;
		try {

			clientSecretReader = new InputStreamReader(new FileInputStream(this.clientSecretJSONPath));
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, clientSecretReader);

			FileDataStoreFactory fileDataStoreFactory = new FileDataStoreFactory(new File(this.oauthDirectoryPath));
			DataStore<StoredCredential> datastore = fileDataStoreFactory.getDataStore(this.credentialDatastore);

			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(new NetHttpTransport(),
					JSON_FACTORY, clientSecrets, this.scopes).setCredentialDataStore(datastore).build();

			// Build the local server and bind it to port 8080
			LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setPort(-1).build();

			// Authorize
			return new AuthorizationCodeInstalledApp(flow, localReceiver).authorize(flow.getClientId());

		} catch (IOException e) {

			return null;

		}

	}

	/**
	 * inizia la creazione di un nuovo live stream e di un broadcast associato
	 * al termine della procedura viene richiamata la funzione di callback,
	 * contenente le informazioni della chiave dello stream e dell'ID del
	 * broadcast
	 * 
	 * @param title
	 * @param requestorID
	 * @param requestID
	 * 
	 */
	public void createLiveStream(String title, String requestorID, String requestID) {

		new Thread() {

			@Override
			public void run() {

				LiveStream returnedStream = null;
				LiveBroadcast returnedBroadcast = null;

				// crea lo snippet per il LiveBroadcast, e ne imposta il titolo
				LiveBroadcastSnippet broadcastSnippet = new LiveBroadcastSnippet();
				broadcastSnippet.setTitle(title);
				broadcastSnippet.setScheduledStartTime(new DateTime(System.currentTimeMillis(), 1));

				// crea lo status per il LiveBroadcast, e imposta lo stato della privacy su "unlisted"
				LiveBroadcastStatus status = new LiveBroadcastStatus();
				status.setPrivacyStatus(LIVEBROADCASTSPRIVACYSTATUS_UNLISTED);

				LiveBroadcastContentDetails liveBroadcastContentDetails = new LiveBroadcastContentDetails();
				liveBroadcastContentDetails.setEnableAutoStart(true);
				liveBroadcastContentDetails.setEnableDvr(false);
				liveBroadcastContentDetails.setEnableEmbed(true);

				LiveBroadcast broadcast = new LiveBroadcast();
				broadcast.setKind("youtube#liveBroadcast");
				broadcast.setSnippet(broadcastSnippet);
				broadcast.setStatus(status);
				broadcast.setContentDetails(liveBroadcastContentDetails);

				// costruisce ed esegue la richiesta API per aggiungere il LiveBroadcast

				try {

					YouTube.LiveBroadcasts.Insert liveBroadcastInsert = YouTubeComm.this.youTube.liveBroadcasts()
							.insert("snippet,status,contentDetails", broadcast);
					returnedBroadcast = liveBroadcastInsert.execute();

					// se siamo in debug mode, stampa un messaggio di log
					if (YouTubeComm.this.debugMode) {
						debugLog_GRAYXTERM(YouTubeComm.class,
								"Live broadcast \"" + returnedBroadcast.getId() + "\" successfully created.");
					}

				} catch (IOException e) {

					exceptionLog_REDXTERM(this.getClass(), e);

					if (YouTubeComm.this.listener != null)
						YouTubeComm.this.listener.onLiveStreamNotCreated(requestorID, requestID);

					return;

				}

				// crea uno snippet per il LiveStream, e ne imposta il titolo
				LiveStreamSnippet streamSnippet = new LiveStreamSnippet();
				streamSnippet.setTitle(title);

				// definisce i settaggi CDN dello stream
				CdnSettings cdnSettings = new CdnSettings();
				// cdnSettings.setFormat("1080p");
				cdnSettings.setResolution("variable");
				cdnSettings.setFrameRate("variable");
				cdnSettings.setIngestionType("rtmp");

				// crea il LiveStream e importa lo snippet ed i settaggi CND
				// creati
				LiveStream stream = new LiveStream();
				stream.setKind("youtube#liveStream");
				stream.setSnippet(streamSnippet);
				stream.setCdn(cdnSettings);

				// costruisce ed esegue la richiesta API per aggiungere il
				// LiveBroadcast
				try {

					YouTube.LiveStreams.Insert liveStreamInsert = YouTubeComm.this.youTube.liveStreams()
							.insert("snippet,cdn", stream);
					returnedStream = liveStreamInsert.execute();

					// se siamo in debug mode, stampa un messaggio di log
					if (YouTubeComm.this.debugMode) {
						debugLog_GRAYXTERM(YouTubeComm.class,
								"Live stream \"" + returnedStream.getId() + "\" successfully created.");
					}

				} catch (IOException e) {

					exceptionLog_REDXTERM(this.getClass(), e);

					if (YouTubeComm.this.listener != null)
						YouTubeComm.this.listener.onLiveStreamNotCreated(requestorID, requestID);

					return;
				}

				// costruisce ed esegue la richiesta API per collegare il
				// LiveBroadcast al Livestream creati.
				try {

					YouTube.LiveBroadcasts.Bind liveBroadcastBind = YouTubeComm.this.youTube.liveBroadcasts()
							.bind(returnedBroadcast.getId(), "id,contentDetails");
					liveBroadcastBind.setStreamId(returnedStream.getId());
					returnedBroadcast = liveBroadcastBind.execute();

					// se siamo in debug mode, stampa un messaggio di log
					if (YouTubeComm.this.debugMode) {
						debugLog_GRAYXTERM(YouTubeComm.class, "Live stream \"" + returnedStream.getId()
								+ "\" successfully bound to live broadcast \"" + returnedBroadcast.getId() + "\".");
					}

					// chiama la funzione di callback
					if (YouTubeComm.this.listener != null) {
						YouTubeComm.this.listener.onLiveStreamCreated(requestorID, requestID,
								returnedStream.getCdn().getIngestionInfo().getStreamName(), returnedBroadcast.getId());
					}

				} catch (IOException e) {

					exceptionLog_REDXTERM(this.getClass(), e);

					if (YouTubeComm.this.listener != null)
						YouTubeComm.this.listener.onLiveStreamNotCreated(requestorID, requestID);

				}

			}

		}.start();

	}

}
