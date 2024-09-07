package edu.iu.auth.spi;

import java.net.URI;

import edu.iu.auth.client.IuAuthorizationGrant;
import edu.iu.auth.client.IuAuthorizationRequest;

/**
 * Authorization client service provider interface.
 */
public interface IuAuthClientSpi {

	/**
	 * Creates a new {@link IuAuthorizationGrant} for managing interactions with an
	 * authorization server on behalf of the remote client.
	 * 
	 * @param request {@link IuAuthorizationRequest}
	 * @return {@link IuAuthorizationGrant}
	 */
	IuAuthorizationGrant createClientCredentialsGrant(IuAuthorizationRequest request);

	/**
	 * Creates a new {@link IuAuthorizationGrant} for managing interactions with an
	 * authorization server on behalf of the remote client.
	 * 
	 * @param request     {@link IuAuthorizationRequest}
	 * @param redirectUri {@link URI} to return to after completing authorization
	 * @return {@link URI} to redirect the user to complete the authorization
	 *         process
	 */
	URI initiateAuthorizationCodeGrant(IuAuthorizationRequest request, URI redirectUri);

	/**
	 * Creates a new {@link IuAuthorizationGrant} for managing interactions with an
	 * authorization server on behalf of the remote client.
	 * 
	 * @param requestUri Incoming request {@link URI}
	 * @param code       Authorization code
	 * @param state      State parameter to verify the authorization code against
	 * 
	 * @return {@link IuAuthorizationGrant}
	 */
	IuAuthorizationGrant completeAuthorizationCodeGrant(URI requestUri, String code, String state);

}
