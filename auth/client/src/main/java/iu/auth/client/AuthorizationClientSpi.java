package iu.auth.client;

import java.util.HashMap;
import java.util.Map;

import edu.iu.auth.client.IuAuthorizationGrant;
import edu.iu.auth.client.IuAuthorizationRequest;
import edu.iu.auth.spi.IuAuthorizationClientSpi;

/**
 * {@link IuAuthorizationClientSpi} implementation.
 */
public class AuthorizationClientSpi implements IuAuthorizationClientSpi {

	private static final Map<AuthorizationRequest, ClientCredentialsGrant> GRANTS = new HashMap<>();

	@Override
	public IuAuthorizationGrant clientCredentials(IuAuthorizationRequest request) {
		final var req = AuthorizationRequest.from(request);

		var grant = GRANTS.get(req);
		if (grant == null) {
			grant = new ClientCredentialsGrant(req);
			synchronized (GRANTS) {
				GRANTS.put(req, grant);
			}
		}

		return grant;
	}

}
