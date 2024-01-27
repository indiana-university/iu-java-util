package iu.auth.oidc;

import java.net.URI;

import edu.iu.auth.oidc.IuOpenIdProvider;

/**
 * {@link IuOpenIdProvider} implementation.
 */
public class OpenIdProvider implements IuOpenIdProvider {

	private final OpenIdProviderConfiguration configuration;

	/**
	 * Constructor.
	 * 
	 * @param configUri Well-known configuration URI
	 */
	public OpenIdProvider(URI configUri) {
		configuration = new OpenIdProviderConfiguration(configUri);
	}

	@Override
	public OpenIdProviderConfiguration getConfiguration() {
		return configuration;
	}

}
