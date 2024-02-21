package iu.auth.bundle;

import java.net.URI;

import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.spi.IuOAuthSpi;

/**
 * Delegating SPI implementation.
 */
public class OAuthSpiDelegate implements IuOAuthSpi {

	private static final IuOAuthSpi DELEGATE = Bootstrap.load(IuOAuthSpi.class);

	/**
	 * Default constructor.
	 */
	public OAuthSpiDelegate() {
	}

	@Override
	public IuAuthorizationGrant initialize(IuAuthorizationClient client) {
		return DELEGATE.initialize(client);
	}

	@Override
	public IuAuthorizationSession createAuthorizationSession(String realm, URI entryPoint) {
		return DELEGATE.createAuthorizationSession(realm, entryPoint);
	}

}
