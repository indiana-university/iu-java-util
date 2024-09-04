package edu.iu.auth.client;

import java.net.URI;

/**
 * Encapsulates authorization request parameters.
 */
public interface IuAuthorizationRequest {

	/**
	 * Gets the client id.
	 * 
	 * @return client id
	 */
	String getClientId();

	/**
	 * Gets the endpoint URI.
	 * 
	 * @return Endpoint {@link URI}
	 */
	URI getEndpointUri();

	/**
	 * Gets the requested scope(s).
	 * 
	 * @return Requested scope(s);
	 */
	Iterable<String> getScope();

}
