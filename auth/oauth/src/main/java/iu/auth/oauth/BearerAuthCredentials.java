package iu.auth.oauth;

import java.net.http.HttpRequest.Builder;

import edu.iu.auth.oauth.IuBearerAuthCredentials;

/**
 * {@link IuBearerAuthCredentials} implementation.
 */
public class BearerAuthCredentials implements IuBearerAuthCredentials {

	private final String accessToken;

	/**
	 * Constructor.
	 * 
	 * @param accessToken access token
	 */
	public BearerAuthCredentials(String accessToken) {
		this.accessToken = accessToken;
	}

	@Override
	public String getName() {
		// TODO parse & validate JWT, return sub claim
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String getAccessToken() {
		return accessToken;
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) {
		httpRequestBuilder.header("Authorization", "Bearer " + accessToken);
	}

}
