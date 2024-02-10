package iu.auth.oauth;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.IuWebUtils;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationFailedException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationResponse;
import iu.auth.util.HttpUtils;

/**
 * Represents an OAuth client credentials grant..
 */
class ClientCredentialsGrant implements IuAuthorizationGrant {

	private final static Logger LOG = Logger.getLogger(ClientCredentialsGrant.class.getName());

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
		final String message;
		if (response == null)
			message = "Authentication required, initiating client credentials flow for " + client.getRealm();
		else if (response.isExpired())
			message = "Authenticated session has expired, initiating client credentials flow for " + client.getRealm();
		else
			return response;

		LOG.fine(message);

		final Map<String, Iterable<String>> tokenRequestParams = new LinkedHashMap<>();
		tokenRequestParams.put("grant_type", List.of("client_credentials"));
		tokenRequestParams.put("scope", List.of(scope));

		final var clientAttributes = client.getClientCredentialsAttributes();
		if (clientAttributes != null)
			for (final var clientAttributeEntry : clientAttributes.entrySet()) {
				final var name = clientAttributeEntry.getKey();
				if (tokenRequestParams.containsKey(name))
					throw new IllegalArgumentException(
							"Illegal attempt to override standard client credentials attribute " + name);
				else
					tokenRequestParams.put(name, List.of(clientAttributeEntry.getValue()));
			}

		final var tokenRequestBuilder = HttpRequest.newBuilder(client.getTokenEndpoint());
		tokenRequestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(tokenRequestParams)));
		tokenRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
		client.getCredentials().applyTo(tokenRequestBuilder);

		final var tokenResponse = HttpUtils.read(tokenRequestBuilder.build()).asJsonObject();
		return response = new AuthorizationResponse(this, client, tokenResponse);
	}

}
