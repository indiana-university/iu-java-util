package iu.auth.oauth;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;

import edu.iu.auth.IuAuthenticationChallengeException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationCodeGrant;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;
import iu.auth.util.HttpUtils;

/**
 * OAuth authorization session implementation.
 */
public class AuthorizationSession implements IuAuthorizationSession {

	private final Collection<IuAuthorizationCodeGrant> grants = new ArrayDeque<>();

	private final IuAuthorizationClient client;
	private final URI entryPoint;

	/**
	 * Default constructor.
	 * 
	 * @param client     {@link IuAuthorizationClient}
	 * @param entryPoint {@link URI} to redirect the user agent to in order restart
	 *                   the authentication process.
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
	public IuAuthorizationCodeGrant getAuthorizationCodeGrant(String state) {
		final IuAuthorizationCodeGrant grant;
		synchronized (codeGrants) {
			grant = codeGrants.remove(state);
		}
//		IuAuthenticationChallengeException - if the state value cannot be tiedto a valid existing grant
		// IuAuthenticationRedirectException - if the state value is tied to anexpired
		// grant

		if (grant == null)
			throw new IuAuthenticationChallengeException(HttpUtils.createChallenge("Bearer", Map.of("realm", realm)));
		else
			return grant;
	}

}
