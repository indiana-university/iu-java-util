
package iu.auth.client;

import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.client.IuHttp;

/**
 * Represents an OAuth client credentials grant.
 */
final class ClientCredentialsGrant extends AbstractGrant {
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(ClientCredentialsGrant.class.getName());

	/**
	 * Constructor.
	 * 
	 * @param realm authentication realm
	 */
	ClientCredentialsGrant(String realm) {
		super(realm);
	}

	@Override
	public final IuApiCredentials authorize(URI resourceUri) {
		final var client = OAuthSpi.getClient(realm);
		if (!IuWebUtils.isRootOf(client.getResourceUri(), resourceUri))
			throw new IllegalArgumentException("Invalid resource URI for this client");

		final var activatedCredentials = getAuthorizedCredentials();
		if (activatedCredentials != null)
			return activatedCredentials;

		if (isExpired())
			LOG.fine("Authorized session has expired, initiating client credentials flow for " + client.getRealm());
		else
			LOG.fine(() -> "Authorization required, initiating client credentials flow for " + client.getRealm());

		final Map<String, Iterable<String>> tokenRequestParams = new LinkedHashMap<>();
		tokenRequestParams.put("grant_type", List.of("client_credentials"));

		if (validatedScope != null)
			tokenRequestParams.put("scope", List.of(validatedScope));

		final var clientAttributes = client.getClientCredentialsAttributes();
		if (clientAttributes != null)
			for (final var clientAttributeEntry : clientAttributes.entrySet()) {
				final var name = clientAttributeEntry.getKey();
				if (name.equals("grant_type") || name.equals("scope"))
					throw new IllegalArgumentException(
							"Illegal attempt to override standard client credentials attribute " + name);
				else
					tokenRequestParams.put(name, List.of(clientAttributeEntry.getValue()));
			}

		return IuException.unchecked(() -> authorize(new TokenResponse(client.getScope(), clientAttributes,
				IuHttp.send(IuAuthenticationException.class, client.getTokenEndpoint(), tokenRequestBuilder -> {
					tokenRequestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(tokenRequestParams)));
					tokenRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
					client.getCredentials().applyTo(tokenRequestBuilder);
				}, JSON_OBJECT_NOCACHE))));
	}

}
