package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;

@SuppressWarnings("javadoc")
public class SpiTest {

	@Test
	public void testSamlSpi() {
		final var spi = new SamlConnectSpi();
		final var client = new IuSamlClient() {

			@Override
			public String getServiceProviderEntityId() {
				return "";
			}

			@Override
			public String getPrivateKey() {
				return "";
			}

			@Override
			public X509Certificate getCertificate() {
				return PemEncoded.parse("").next().asCertificate();
			}

			@Override
			public List<URI> getAcsUrls() {
				return anyList();
			}

			@Override
			public List<URI> getMetaDataUrls() {
				return anyList();
			}
		};

		IuSamlProvider provider = spi.getSamlProvider(client);
		assertNotNull(provider);
	}

}
