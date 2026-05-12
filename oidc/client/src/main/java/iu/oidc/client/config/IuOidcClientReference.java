package iu.oidc.client.config;

import java.lang.reflect.Type;
import java.net.URI;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
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
	default URI getResourceUri() {
		return getClient().getResourceUri();
	}

	/**
	 * Gets the redirect URI for handling authorization code return requests.
	 * 
	 * @return redirect URI
	 */
	default URI getRedirectUri() {
		return null;
	}

	/**
	 * Gets the scope parameter value to send to the token endpoint.
	 * 
	 * @return redirect URI
	 */
	default String getScope() {
		return null;
	}

	/**
	 * Gets root resource URIs for downstream APIs.
	 * 
	 * @return resource URIs
	 */
	default Iterable<URI> getApiResources() {
		return null;
	}

	/**
	 * Provides the session handler for passing state between authorization code
	 * redirects.
	 * 
	 * @return {@link IuSessionHandler}
	 */
	default IuSessionHandler getSessionHandler() {
		return null;
	}

	/**
	 * Gets an {@link IuJsonAdapter} for a generic type.
	 * 
	 * @param type type
	 * @return {@link IuJsonAdapter}
	 */
	default IuJsonAdapter<?> adaptJson(Type type) {
		return IuJsonAdapter.adapt(type, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES);
	}

	/**
	 * Gets an {@link IuJsonAdapter} for a class.
	 * 
	 * @param <T>  type
	 * @param type type class
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	default <T> IuJsonAdapter<T> adaptJson(Class<T> type) {
		return (IuJsonAdapter<T>) adaptJson((Type) type);
	}

}
