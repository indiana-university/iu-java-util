package iu.auth.client;

import java.net.http.HttpRequest.Builder;

import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.client.IuBearerToken;

/**
 * {@link IuBearerToken} implementation.
 */
public class BearerToken implements IuBearerToken {
	static {
		IuObject.assertNotOpen(BearerToken.class);
	}

	private final String accessToken;

	/**
	 * Constructor.
	 * 
	 * @param accessToken the access token
	 */
	public BearerToken(String accessToken) {
		this.accessToken = accessToken;
	}

	@Override
	public String getAccessToken() {
		return accessToken;
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) throws IuAuthenticationException {
		httpRequestBuilder.header("Authorization", "Bearer " + accessToken);
	}

}
