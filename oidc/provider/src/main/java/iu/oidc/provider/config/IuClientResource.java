package iu.oidc.provider.config;

import java.net.URI;

/**
 * Client resource metadata.
 */
public interface IuClientResource {

	/**
	 * Gets the application resource URI.
	 * 
	 * @return application resource URI
	 */
	URI getUri();

	/**
	 * Gets the resource code for remote attribute configuration.
	 * 
	 * @return resource code
	 */
	String getCode();

}
