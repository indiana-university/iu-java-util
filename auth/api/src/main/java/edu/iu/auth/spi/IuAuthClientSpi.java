package edu.iu.auth.spi;

import java.net.URI;

import edu.iu.auth.client.IuAuthorizationGrant;
import edu.iu.auth.client.IuAuthorizationSession;

/**
 * Authorization client service provider interface.
 */
public interface IuAuthClientSpi {

	/**
	 * Creates a new {@link IuAuthorizationSession} for managing interactions with
	 * an authorization server.
	 * 
	 * @param clientId    Client ID
	 * @param resourceUri Root resource API
	 * @param scope       Scopes to request access for
	 * 
	 * @return {@link IuAuthorizationSession}
	 */
	IuAuthorizationSession createAuthorizationSession(String clientId, URI resourceUri, String... scope);

	/**
	 * Creates a new {@link IuAuthorizationGrant} for managing interactions with an
	 * authorization server on behalf of the remote client.
	 * 
	 * @param clientId    Client ID
	 * @param resourceUri Root resource API
	 * @param scope       Scopes to request access for
	 * 
	 * @return {@link IuAuthorizationGrant}
	 */
	IuAuthorizationGrant createAuthorizationGrant(String clientId, URI resourceUri, String... scope);

}
