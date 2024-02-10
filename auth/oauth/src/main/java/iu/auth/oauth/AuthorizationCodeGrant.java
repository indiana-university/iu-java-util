package iu.auth.oauth;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationRedirectException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationCodeGrant;
import edu.iu.auth.oauth.IuAuthorizationResponse;
import iu.auth.util.HttpUtils;

/**
 * {@link IuAuthorizationCodeGrant} implementation.
 */
class AuthorizationCodeGrant implements IuAuthorizationCodeGrant {

	private final Logger LOG = Logger.getLogger(AuthorizationCodeGrant.class.getName());

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
	public IuAuthorizationResponse authorize() {
		final String message;
		Throwable refreshFailure = null;
		if (response == null)
			message = "Authentication required, initiating authorization code flow for " + client.getRealm();
		else if (response.isExpired()) {
			final var refreshToken = response.getRefreshToken();
			if (refreshToken != null)
				try {
					final Map<String, Iterable<String>> tokenRequestParams = new LinkedHashMap<>();
					tokenRequestParams.put("grant_type", List.of("refresh_token"));
					tokenRequestParams.put("refresh_token", List.of(refreshToken));
					tokenRequestParams.put("scope", List.of(scope));

					final var tokenRequestBuilder = HttpRequest.newBuilder(client.getTokenEndpoint());
					tokenRequestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(tokenRequestParams)));
					tokenRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
					client.getCredentials().applyTo(tokenRequestBuilder);

					final var tokenResponse = HttpUtils.read(tokenRequestBuilder.build()).asJsonObject();
					return response = new AuthorizationResponse(this, client, tokenResponse);

				} catch (Throwable e) {
					LOG.log(Level.INFO, e, () -> "Refresh token failed");
					refreshFailure = e;
				}

			message = "Authenticated session has expired, initiating authorization code flow for " + client.getRealm();
		} else
			return response;

		final var authRequestParams = new LinkedHashMap<String, Iterable<String>>();
		authRequestParams.put("client_id", List.of(client.getCredentials().getName()));
		authRequestParams.put("response_type", List.of("code"));
		authRequestParams.put("redirect_uri", List.of(client.getRedirectUri().toString()));
		authRequestParams.put("scope", List.of(scope));
		authRequestParams.put("state", List.of(state));

		final var clientAttributes = client.getAuthorizationCodeAttributes();
		if (clientAttributes != null)
			for (final var clientAttributeEntry : clientAttributes.entrySet()) {
				final var name = clientAttributeEntry.getKey();
				if (authRequestParams.containsKey(name))
					throw new IllegalArgumentException("Illegal attempt to override standard auth attribute " + name);
				else
					authRequestParams.put(name, List.of(clientAttributeEntry.getValue()));
			}
		this.clientAttributes = clientAttributes;

		final var location = client.getAuthorizationEndpoint() + "?" + IuWebUtils.createQueryString(authRequestParams);
		LOG.fine(() -> message + "; Location: " + location);

		throw new IuAuthenticationRedirectException(location, refreshFailure);
	}

	@Override
	public IuAuthorizationResponse authorize(String code) {
		final Map<String, Iterable<String>> tokenRequestParams = new LinkedHashMap<>();
		tokenRequestParams.put("grant_type", List.of("authorization_code"));
		tokenRequestParams.put("code", List.of(code));
		tokenRequestParams.put("scope", List.of(scope));
		tokenRequestParams.put("redirect_uri", List.of(client.getRedirectUri().toString().toString()));

		final var authRequestBuilder = HttpRequest.newBuilder(client.getTokenEndpoint());
		authRequestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(tokenRequestParams)));
		authRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
		client.getCredentials().applyTo(authRequestBuilder);

		final var authResponse = HttpUtils.read(authRequestBuilder.build()).asJsonObject();
		return response = new AuthorizationResponse(this, client, authResponse);
	}

}
