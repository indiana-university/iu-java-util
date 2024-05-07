package iu.auth.saml;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
import edu.iu.auth.saml.IuSamlSession;
import edu.iu.auth.spi.IuSamlSpi;

/**
 * SAML connect SPI implementation
 */
public class SamlConnectSpi implements IuSamlSpi {
	private static final Map<String, IuSamlProvider> PROVIDER = new HashMap<>();

	/**
	 * Default constructor.
	 */
	public SamlConnectSpi() {
	}

	@Override
	public IuSamlProvider getSamlProvider(IuSamlClient client) {
		Objects.requireNonNull(client.getAcsUris(), "Missing activation consumer uris");
		Objects.requireNonNull(client.getMetaDataUris(), "Missing metadata uris");
		Objects.requireNonNull(client.getAuthenticatedSessionTimeout(), "Missing authentication session timeout");
		final var serviceProviderEntityId = Objects.requireNonNull(client.getServiceProviderEntityId(),
				"Missing service provider entity Id");

		SamlProvider provider = new SamlProvider(client);
		synchronized (PROVIDER) {
			if (PROVIDER.containsKey(serviceProviderEntityId))
				throw new IllegalStateException("Already initialized");
			PROVIDER.put(serviceProviderEntityId, provider);
		}
		return provider;
	}

	/**
	 * Gets an initialized SAML client.
	 * 
	 * @param serviceProviderEntityId Authorization service provider entity id
	 * @return client metadata
	 */
	static SamlProvider getProvider(String serviceProviderEntityId) {
		final var provider = PROVIDER.get(serviceProviderEntityId);
		if (provider == null)
			throw new IllegalStateException("provider is not initialzied for " + serviceProviderEntityId);
		return (SamlProvider) provider;
	}

	@Override
	public IuSamlSession createAuthorizationSession(String realm, URI entryPoint) {
		return new SamlSession(realm, entryPoint);
	}

}
