package iu.auth.oauth;

import java.util.HashMap;
import java.util.Map;

import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationCodeGrant;
import edu.iu.auth.oauth.IuAuthorizationFailedException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;

/**
 * OAuth authorization session implementation.
 */
public class AuthorizationSession implements IuAuthorizationSession {

	private final Map<String, ClientCredentialsGrant> clientGrants = new HashMap<>();
	private final Map<String, IuAuthorizationCodeGrant> codeGrants = new HashMap<>();

	private final String realm;
	private final IuAuthorizationClient client;

	/**
	 * Default constructor.
	 * 
	 * @param realm  realm
	 * @param client {@link IuAuthorizationClient}
	 */
	public AuthorizationSession(String realm, IuAuthorizationClient client) {
		this.realm = realm;
		this.client = client;
	}

	@Override
	public IuAuthorizationGrant getClientCredentialsGrant(String scope) {
		ClientCredentialsGrant grant;
		synchronized (clientGrants) {
			grant = clientGrants.get(scope);
			if (grant == null)
				grant = new ClientCredentialsGrant(client, scope);
			clientGrants.put(scope, grant);
		}

		return grant;
	}

	@Override
	public IuAuthorizationCodeGrant createAuthorizationCodeGrant(String scope) {
		final var grant = new AuthorizationCodeGrant(client, scope);
		synchronized (codeGrants) {
			codeGrants.put(grant.getState(), grant);
		}
		return grant;
	}

	@Override
	public IuAuthorizationCodeGrant getAuthorizationCodeGrant(String state) throws IuAuthorizationFailedException {
		final IuAuthorizationCodeGrant grant;
		synchronized (codeGrants) {
			grant = codeGrants.remove(state);
		}
		
		if (grant == null)
			throw new IuAuthorizationFailedException(401, realm, "invalid_state", state, null);
		else
			return grant;
	}

}
