package iu.auth.saml;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
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
		Objects.requireNonNull(client.getAcsUris(), "Missing activation consumer urls");
		Objects.requireNonNull(client.getMetaDataUris(), "Missing metadat urls");
		final var serviceProviderEntityId = Objects.requireNonNull(client.getServiceProviderEntityId(),
				"Missing service provider entity Id");

		synchronized (CLIENTS) {
			if (CLIENTS.containsKey(serviceProviderEntityId))
				throw new IllegalStateException("Already initialized");
			CLIENTS.put(serviceProviderEntityId, client);
		}
		return new SamlProvider(client);
	}

}
