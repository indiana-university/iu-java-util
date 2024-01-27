package iu.auth.oauth;

import edu.iu.auth.spi.IuOAuthSpi;

/**
 * {@link IuOAuthSpi} implementation.
 */
public class OAuthSpi implements IuOAuthSpi {

	@Override
	public AuthorizationSession createAuthorizationSession() {
		return new AuthorizationSession();
	}

}
