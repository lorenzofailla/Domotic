package apps.java.loref;

import java.io.IOException;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.common.collect.Lists;

public class YouTubeActivation {

	private static final List<String> DEFAULT_SCOPES = Lists.newArrayList("https://www.googleapis.com/auth/youtube");
	private static final String DEFAULT_CREDENTIALDATASTORE = "createbroadcasts";

	public static void main(String[] args) {

		System.out.println("Youtube activation utility - by Lorenzo Failla");
		System.out.println("Checking credential...");

		try {

			Credential credential = Auth.getCredential(DEFAULT_SCOPES, DEFAULT_CREDENTIALDATASTORE);

			System.out.println("Credential successfully checked.");

		} catch (IOException e) {

			System.out.println("Credential check was not possible. Following exception was raised: " + e.getMessage());

		}

	}

}
