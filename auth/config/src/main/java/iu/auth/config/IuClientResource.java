package iu.auth.config;

import java.net.URI;

import edu.iu.auth.config.IuAuthorizationResource;

/**
 * Refers to an {@link IuAuthorizationResource} by endpoint URI and resource
 * name
 */
public interface IuClientResource extends IuAuthConfig {

	/**
	 * Gets the endpoint URI.
	 * 
	 * @return {@link URI}
	 */
	URI getEndpointUri();

	/**
	 * Gets the resource name.
	 * 
	 * @return {@link String}
	 */
	String getResourceName();

}
