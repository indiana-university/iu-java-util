package iu.auth.oauth;

import java.util.HashMap;
import java.util.Map;

import edu.iu.auth.oauth.IuAuthorizationCodeGrant;
import edu.iu.auth.oauth.IuAuthorizationFailedException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;

/**
 * OAuth authorization session implementation.
 */
public class AuthorizationSession implements IuAuthorizationSession {

	private record ClientGrantKey(String realm, String scope) {
	}

	private static final Map<ClientGrantKey, ClientCredentialsGrant> CLIENT_GRANTS = new HashMap<>();

	private final Map<String, IuAuthorizationCodeGrant> codeGrants = new HashMap<>();

	/**
	 * Default constructor.
	 */
	public AuthorizationSession() {
	}

	@Override
	public IuAuthorizationGrant getClientCredentialsGrant(String realm, String scope) {
		ClientCredentialsGrant grant;
		synchronized (CLIENT_GRANTS) {
			final var k = new ClientGrantKey(realm, scope);
			grant = CLIENT_GRANTS.get(k);
			if (grant == null)
				grant = new ClientCredentialsGrant(OAuthSpi.getClient(realm), scope);
			CLIENT_GRANTS.put(k, grant);
		}
		
		return grant;
	}

	@Override
	public IuAuthorizationCodeGrant createAuthorizationCodeGrant(String realm, String scope) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuAuthorizationCodeGrant getAuthorizationCodeGrant(String state) throws IuAuthorizationFailedException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}
