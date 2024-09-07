
package iu.auth.client;

import java.time.Instant;

import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.client.IuAuthorizationGrant;
import edu.iu.auth.client.IuBearerToken;
import edu.iu.auth.oidc.IuAuthorizationRequest;

/**
 * Represents an OAuth client credentials grant.
 */
final class ClientCredentialsGrant implements IuAuthorizationGrant {
	static {
		IuObject.assertNotOpen(ClientCredentialsGrant.class);
	}

	private IuAuthorizationRequest request;

	protected String accessToken;
	protected Instant expires;
	protected String[] scope;

	@Override
	public IuBearerToken authorize() throws IuAuthenticationException {
		// TODO Auto-generated method stub
		return null;
	}

}
