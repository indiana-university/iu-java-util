package edu.iu.auth.client;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.spi.IuAuthorizationClientSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Represents the client view of an OAuth 2.0 authorization grant.
 */
public interface IuAuthorizationGrant {

	/**
	 * Establishes an authorization grant using the client credentials flow.
	 * 
	 * @param request {@link IuAuthorizationRequest}
	 * @return {@link IuAuthorizationGrant}
	 * @throws IuAuthenticationException If a client credentials grant could not be
	 *                                   established
	 */
	static IuAuthorizationGrant clientCredentials(IuAuthorizationRequest request) throws IuAuthenticationException {
		return IuAuthSpiFactory.get(IuAuthorizationClientSpi.class).clientCredentials(request);
	}

	/**
	 * Establishes, verifies, and/or refreshes the authorization grant.
	 * 
	 * @return {@link IuBearerToken} as successfully authorized via the grant.
	 */
	IuBearerToken authorize();

}
