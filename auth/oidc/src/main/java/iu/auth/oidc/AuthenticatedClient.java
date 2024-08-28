package iu.auth.oidc;

import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationCredentials;

/**
 * Represents a successfully authorized grant according to client configuration
 * rules.
 * 
 * @param clientId    Client ID
 * @param client      {@link IuAuthorizationClient}
 * @param credentials {@link IuAuthorizationCredentials} used to authenticate the client
 * @param jti         Unique token ID
 * @param nonce       One-time number
 */
record AuthenticatedClient(String clientId, IuAuthorizationClient client, IuAuthorizationCredentials credentials, String jti,
		String nonce) {
}
