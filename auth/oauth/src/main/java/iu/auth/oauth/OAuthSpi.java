package iu.auth.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.spi.IuOAuthSpi;

/**
 * {@link IuOAuthSpi} implementation.
 */
public class OAuthSpi implements IuOAuthSpi {

	private static final Map<String, IuAuthorizationClient> CLIENTS = new HashMap<>();

	/**
	 * Default constructor.
	 */
	public OAuthSpi() {
	}

	/**
	 * Gets an initialized authorization client.
	 * 
	 * @param realm Authorization realm
	 * @return client metadata
	 */
	static IuAuthorizationClient getClient(String realm) {
		final var client = CLIENTS.get(realm);
		if (realm == null)
			throw new IllegalStateException("Client metadata not initialzied for " + realm);
		return client;
	}

	@Override
	public void initialize(IuAuthorizationClient client) {
		final var realm = Objects.requireNonNull(client.getRealm(), "Missing realm");
		synchronized (CLIENTS) {
			if (CLIENTS.containsKey(realm))
				throw new IllegalStateException("Already initialized");
			CLIENTS.put(realm, client);
		}
	}

	@Override
	public AuthorizationSession createAuthorizationSession(String realm) {
		return new AuthorizationSession(realm, getClient(realm));
	}

}
