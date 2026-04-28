package iu.oidc.provider.config;

import java.net.URI;

/**
 * IU remote client endpoint metadata.
 */
public interface IuClientEndpoint {

	/**
	 * Application redirect URI.
	 * 
	 * @return application redirect URI
	 */
	URI getRedirectUri();

	/**
	 * Client resource references.
	 * 
	 * @return resources
	 */
	Iterable<IuClientResource> getResources();

}
