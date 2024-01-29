package iu.auth.oauth;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;

import edu.iu.IuException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationFailedException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationResponse;
import iu.auth.util.HttpUtils;

/**
 * Represents an OAuth client credenitals grant..
 */
class ClientCredentialsGrant implements IuAuthorizationGrant {

	private final IuAuthorizationClient client;
	private final String scope;

	private AuthorizationResponse response;

	/**
	 * Constructor.
	 * 
	 * @param client confidential client
	 * @param scope  authorization scope
	 */
	ClientCredentialsGrant(IuAuthorizationClient client, String scope) {
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
	public IuAuthorizationResponse authorize() throws IuAuthorizationFailedException {
		if (response == null || response.isExpired()) {
			final var authRequestBuilder = HttpRequest.newBuilder(client.getTokenEndpoint());
			authRequestBuilder.POST(BodyPublishers.ofString("grant_type=client_credentials" //
					+ "&scope=" + IuException.unchecked(() -> URLEncoder.encode(scope, "UTF-8")) //
					+ "&resource="
					+ IuException.unchecked(() -> URLEncoder.encode(client.getRedirectUri().toString(), "UTF-8")) //
					+ "&audience="
					+ IuException.unchecked(() -> URLEncoder.encode(client.getRedirectUri().toString(), "UTF-8")) //
			));
			authRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
			client.getCredentials().applyTo(authRequestBuilder);
			response = new AuthorizationResponse(client.getCredentials(),
					HttpUtils.read(authRequestBuilder.build()).asJsonObject());
		}
		return response;
	}

}
