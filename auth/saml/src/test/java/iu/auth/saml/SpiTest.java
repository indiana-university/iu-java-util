package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockConstruction;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceConfigurationError;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
import edu.iu.crypt.PemEncoded;

@SuppressWarnings("javadoc")
public class SpiTest {

	private static X509Certificate certificate;

	@BeforeAll
	static void setup() {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl genrsa | tee /tmp/k
		// $ openssl req -days 410 -x509 -key /tmp/k
		final var certText = "-----BEGIN CERTIFICATE-----\r\n" //
				+ "MIID5TCCAs2gAwIBAgIUDSy2fR7Mli1vvbswCfNcW8crSZYwDQYJKoZIhvcNAQEL\r\n"
				+ "BQAwgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtC\r\n"
				+ "bG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQL\r\n"
				+ "DAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5cHQtdGVzdDAgFw0yNDAzMTAx\r\n"
				+ "OTIxNDlaGA8yMTI0MDMxMTE5MjE0OVowgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQI\r\n"
				+ "DAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFu\r\n"
				+ "YSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEt\r\n"
				+ "Y3J5cHQtdGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALL0kKuy\r\n"
				+ "9h1E6AqPrFu3dUvOb3f2fPjyqlyStGGk4P8rUljJd+QyubAapIF2Sq420a9Q7atp\r\n"
				+ "EBZiLeC0fV8VbrBTYSFp2Up3rxUcEVkDZKpCjbwZ16RZIenGZWYBkLQh5P/VjrUG\r\n"
				+ "HCD9QSnTy08yBLAFrnOzBRL0mLoLmRVbam47QUV98pNAsmZF0wxsrSp6pmMSnHGY\r\n"
				+ "zlWFX9/vnrSWGMSKy229hYKMfSbY76sJNt605JWK19A3NjgeMT0rWZcCHnpv1s63\r\n"
				+ "DWx2ZQuKVNgTZm5oftLPQ6Dj4PwqEo9aMqahnIYw8t37zbq3ZsZgL+4Hcu866YAe\r\n"
				+ "W0GhvZVeOd89zS8CAwEAAaNTMFEwHQYDVR0OBBYEFNVoqadb2L5DK9+5yJ3WPxQs\r\n"
				+ "Dv/dMB8GA1UdIwQYMBaAFNVoqadb2L5DK9+5yJ3WPxQsDv/dMA8GA1UdEwEB/wQF\r\n"
				+ "MAMBAf8wDQYJKoZIhvcNAQELBQADggEBADzre/3bkFb36eYUeTrun+334+9v3VM2\r\n"
				+ "S6Sa2ycrUqquA0igkVI7Bf6odne+rW8z3YVxlRLBqOfuqCz/XShv+NiSvGTe4oGd\r\n"
				+ "rZv1Uz6s8SaUgbrOD7CphrUpkXl10jLiOwK77bBQBXXIjiTgReVQlZj3ni9ysvUP\r\n"
				+ "j05uY1zNDU631DQSHUZkPDAv4t5rCS9atoznIGDLgkSRDYLSbGoX7/1qSUg/yZvl\r\n"
				+ "vJ2qfMhgmuzhrTOF4rGNOZmJ/eMarqBu3oRBdpsZzdGQehAoEqoVTgrnhZ7KdWKE\r\n"
				+ "U++EQOj4ZKOR2YyYTXuYGLNZZiJZs9U6GmI32qLnxQIlhl6wxDKvjMs=\r\n" //
				+ "-----END CERTIFICATE-----\r\n";

		final var pem = PemEncoded.parse(certText);
		certificate = pem.next().asCertificate();
	}

	/**
	 * Test is currently failing with MalformedURLException for getMetaDataUris
	 * TODO fix Exception
	 */
	@Test
	@Disabled
	public void testSamlClient() {
		final var spi = new SamlConnectSpi();
		final var realm = "urn:iu:ess:sisjee-test";
		final var client = new IuSamlClient() {

			@Override
			public String getServiceProviderEntityId() {
				return realm;
			}

			@Override
			public String getPrivateKey() {
				return "";
			}

			@Override
			public X509Certificate getCertificate() {
				return certificate;
			}

			@Override
			public List<URI> getAcsUris() {
				return IuException.unchecked(() -> Arrays.asList(new URI("test://postUrl")));
			}

			@Override
			public List<URI> getMetaDataUris() {
				final var uri = URI.create("test://metaDataUrl/" + IdGenerator.generateId());

				return Arrays.asList(uri);
			}

			@Override
			public List<String> getAllowedRange() {
				return Arrays.asList("");
			}

			@Override
			public Duration getAuthenticatedSessionTimeout() {
				return Duration.ofMinutes(2L);
			}

			@Override
			public URI getApplicationUri() {
				final var uri = URI.create("test://localhost/" + IdGenerator.generateId());

				return IuException.unchecked(() -> uri);
			}

		};

		IuSamlProvider provider = spi.getSamlProvider(client);
		assertNotNull(provider);
		assertThrows(IllegalStateException.class, () -> spi.getSamlProvider(client));
		assertNotNull(provider.getServiceProviderMetaData());
		provider = SamlConnectSpi.getProvider(realm);
		assertNotNull(provider);

	}

	@Test
	public void testSamlClientInvalidMetaData() {
		final var spi = new SamlConnectSpi();
		final var client = new IuSamlClient() {

			@Override
			public String getServiceProviderEntityId() {
				return "urn:iu:ess:test";
			}

			@Override
			public String getPrivateKey() {
				return "";
			}

			@Override
			public X509Certificate getCertificate() {
				return certificate;
			}

			@Override
			public List<URI> getAcsUris() {
				final var uri = URI.create("test://postUrl/" + IdGenerator.generateId());
				return IuException.unchecked(() -> Arrays.asList(uri));
			}

			@Override
			public List<URI> getMetaDataUris() {
				final var uri = URI.create("test://localhost/" + IdGenerator.generateId());
				return IuException.unchecked(() -> Arrays.asList(uri));
			}

			@Override
			public List<String> getAllowedRange() {
				return Arrays.asList("");
			}

			@Override
			public Duration getAuthenticatedSessionTimeout() {
				return Duration.ofMinutes(2L);
			}

			@Override
			public URI getApplicationUri() {
				return IuException.unchecked(() -> new URI("test://"));
			}

		};

		assertThrows(ServiceConfigurationError.class, () -> spi.getSamlProvider(client));

	}

	@Test
	public void testCreateSession() {
		final var spi = new SamlConnectSpi();
		final var realm = IdGenerator.generateId();
		final var entryPoint = IuException.unchecked(() -> new URI("http://foo"));
		try (final var mockSamlSession = mockConstruction(SamlSession.class)) {
			final var samlSession = spi.createAuthorizationSession(realm, entryPoint);
			assertSame(samlSession, mockSamlSession.constructed().get(0));
		}

		assertThrows(NullPointerException.class, () -> SamlConnectSpi.getProvider(realm));
	}

}
