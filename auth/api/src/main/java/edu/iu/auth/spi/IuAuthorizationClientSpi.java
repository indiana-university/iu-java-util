package edu.iu.auth.spi;

import edu.iu.auth.client.IuAuthorizationGrant;
import edu.iu.auth.client.IuAuthorizationRequest;

/**
 * Service provider interface for the IU Authorization Client.
 */
public interface IuAuthorizationClientSpi {

	/**
	 * Implements
	 * {@link IuAuthorizationGrant#clientCredentials(IuAuthorizationRequest)}.
	 * 
	 * @param request {@link IuAuthorizationRequest}
	 * @return {@link IuAuthorizationGrant}
	 */
	IuAuthorizationGrant clientCredentials(IuAuthorizationRequest request);

}
