package iu.auth.client;

import java.net.URI;

import edu.iu.auth.client.IuAuthorizationGrant;
import edu.iu.auth.client.IuAuthorizationSession;
import edu.iu.auth.spi.IuAuthClientSpi;

/**
 * {@link IuAuthClientSpi} service provider implementation.
 */
public class AuthClientSpi implements IuAuthClientSpi {

	@Override
	public IuAuthorizationSession createAuthorizationSession(String clientId, URI resourceUri, String... scope) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IuAuthorizationGrant createAuthorizationGrant(String clientId, URI resourceUri, String... scope) {
		// TODO Auto-generated method stub
		return null;
	}

}
