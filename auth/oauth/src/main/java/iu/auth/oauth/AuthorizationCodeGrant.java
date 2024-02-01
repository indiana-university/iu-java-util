package iu.auth.oauth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationCodeGrant;
import edu.iu.auth.oauth.IuAuthorizationFailedException;
import edu.iu.auth.oauth.IuAuthorizationResponse;
import iu.auth.util.HttpUtils;

/**
 * {@link IuAuthorizationCodeGrant} implementation.
 */
class AuthorizationCodeGrant implements IuAuthorizationCodeGrant {

	private final IuAuthorizationClient client;
	private final String scope;
	private final String state = IdGenerator.generateId();

	private AuthorizationResponse response;

	/**
	 * Constructor.
	 * 
	 * @param client client
	 * @param scope  scope
	 */
	AuthorizationCodeGrant(IuAuthorizationClient client, String scope) {
		this.client = client;
		this.scope = scope;
	}

	@Override
	public String getClientId() {
		return client.getCredentials().getName();
	}

	@Override
	public String getScope() {
		return scope;
	}

	@Override
	public String getState() {
		return state;
	}

	@Override
	public URI getRedirectUri() {
		return client.getRedirectUri();
	}

	@Override
	public IuAuthorizationResponse authorize() throws IuAuthorizationFailedException {
		if (response == null || response.isExpired())
			return null;

		return response;

	}

	@Override
	public IuAuthorizationResponse authorize(String code) throws IuAuthorizationFailedException {
		final var authRequestBuilder = HttpRequest.newBuilder(client.getTokenEndpoint());
		authRequestBuilder.POST(BodyPublishers.ofString("grant_type=authorization_code" //
				+ "&code=" + IuException.unchecked(() -> URLEncoder.encode(code, "UTF-8")) //
				+ "&scope=" + IuException.unchecked(() -> URLEncoder.encode(scope, "UTF-8")) //
				+ "&redirect_uri="
				+ IuException.unchecked(() -> URLEncoder.encode(client.getRedirectUri().toString(), "UTF-8")) //
		));
		authRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
		client.getCredentials().applyTo(authRequestBuilder);
		return response = new AuthorizationResponse(client.getCredentials(),
				HttpUtils.read(authRequestBuilder.build()).asJsonObject());
	}

}
