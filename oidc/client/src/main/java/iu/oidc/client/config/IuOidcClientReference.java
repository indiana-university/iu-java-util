package iu.oidc.client.config;

import java.lang.reflect.Type;
import java.net.URI;

import edu.iu.client.IuJsonAdapter;
import edu.iu.session.IuSessionHandler;

/**
 * Provides client application resources to OIDC components.
 * 
 * <p>
 * This is not intended to be a structural interface. Implementations should
 * pass live references to stored configurations rather than static views of the
 * same data.
 * </p>
 */
public interface IuOidcClientReference {

	/**
	 * Gets OIDC client configuration.
	 * 
	 * @return OIDC client configuration
	 */
	IuOidcClient getClient();

	/**
	 * Gets OIDC provider configuration.
	 * 
	 * @return OIDC provider configuration
	 */
	IuOidcProvider getProvider();

	/**
	 * Gets the resource URI.
	 * 
	 * @return resource URI
	 */
	URI getResourceUri();

	/**
	 * Gets the redirect URI for handling authorization code return requests.
	 * 
	 * @return redirect URI
	 */
	URI getRedirectUri();

	/**
	 * Gets the scope parameter value to send to the token endpoint.
	 * 
	 * @return redirect URI
	 */
	String getScope();

	/**
	 * Gets root resource URIs for downstream APIs.
	 * 
	 * @return resource URIs
	 */
	Iterable<URI> getApiResources();

	/**
	 * Provides the session handler for passing state between authorization code
	 * redirects.
	 * 
	 * @return {@link IuSessionHandler}
	 */
	IuSessionHandler getSessionHandler();

	/**
	 * Gets an {@link IuJsonAdapter} for a generic type.
	 * 
	 * @param type type
	 * @return {@link IuJsonAdapter}
	 */
	IuJsonAdapter<?> adaptJson(Type type);

	/**
	 * Gets an {@link IuJsonAdapter} for a class.
	 * 
	 * @param <T>  type
	 * @param type type class
	 * @return {@link IuJsonAdapter}
	 */
	<T> IuJsonAdapter<T> adaptJson(Class<T> type);

}
