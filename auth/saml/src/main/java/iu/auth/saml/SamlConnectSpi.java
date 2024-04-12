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
	private static final Map<String, IuSamlClient> CLIENTS = new HashMap<>();

	/**
	 * Default constructor.
	 */
	public SamlConnectSpi() {
	}

	@Override
	public IuSamlProvider getSamlProvider(IuSamlClient client) {
		Objects.requireNonNull(client.getAcsUris(), "Missing activation consumer uris");
		Objects.requireNonNull(client.getMetaDataUris(), "Missing metadata uris");
		final var serviceProviderEntityId = Objects.requireNonNull(client.getServiceProviderEntityId(),
				"Missing service provider entity Id");

		synchronized (CLIENTS) {
			if (CLIENTS.containsKey(serviceProviderEntityId))
				throw new IllegalStateException("Already initialized");
			CLIENTS.put(serviceProviderEntityId, client);
		}
		return new SamlProvider(client);
	}

	@Override
	public IuSamlSession createAuthorizationSession(String realm, URI entryPoint) {
		return new SamlSession(realm, entryPoint);
	}

}
