package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.crypt.PemEncoded;
import edu.iu.test.IuTestLogger;

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

	@Test
	public void testSamlClient() {
		final var spi = new SamlConnectSpi();
		final var realm = "urn:iu:ess:sisjee-test";
		final var uri = URI.create("test://metaDataUrl/" + IdGenerator.generateId());
		final var client = getClient(Arrays.asList(uri), realm);
		SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
		assertNotNull(provider);
		assertThrows(IllegalStateException.class, () -> spi.getSamlProvider(client));
		assertNotNull(provider.getServiceProviderMetaData());
		provider = SamlConnectSpi.getProvider(realm);
		assertNotNull(provider);
		assertEquals(realm, provider.getClient().getServiceProviderEntityId());
	}

	@Test
	public void testSamlClientInvalidMetaData() {
		final var spi = new SamlConnectSpi();
		final var uri = URI.create("test://localhost/" + IdGenerator.generateId());
		final var client = getClient(Arrays.asList(uri), "urn:iu:ess:test");
		SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
		assertThrows(ServiceConfigurationError.class, () -> provider.getSingleSignOnLocation("test://"));
	}

	@Test
	public void testGetSingleSignOnLocation() throws IOException {
		IuTestLogger.allow("iu.auth.saml.SamlBuilder", Level.WARNING);
		final var spi = new SamlConnectSpi();
		File file = new File("src/test/resource/metadata_sample.xml");
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(file.toPath().toUri().toURL());

		// test valid SSO location for given identity provider entity
		{
			final var client = getClient(Arrays.asList(uri), "urn:iu:ess:thirdparty");

			SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
			String redirectLocation = provider.getSingleSignOnLocation("https://sp.identityserver");
			assertEquals("https://sp.identityserver/idp/profile/SAML2/Redirect/SSO", redirectLocation);
			assertThrows(IllegalArgumentException.class,
					() -> provider.getSingleSignOnLocation("https://sp.identityserver-test"));
			redirectLocation = provider.getSingleSignOnLocation("https://sp.identityserver");
			assertEquals("https://sp.identityserver/idp/profile/SAML2/Redirect/SSO", redirectLocation);
		}

		// test method return valid metadata resolver for valid metadata URI and log
		// message for invalid one

		final var metadataUri = URI.create("test://identityProvider/" + IdGenerator.generateId());
		{
			final var client = getClient(Arrays.asList(metadataUri), "urn:iu:ess:applyiu");
			SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
			assertThrows(ServiceConfigurationError.class,
					() -> provider.getSingleSignOnLocation("https://sp.identityserver"));
		}

		{
			final var client = getClient(Arrays.asList(uri, metadataUri), "urn:iu:ess:adrx");
			SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
			String redirectLocation = provider.getSingleSignOnLocation("https://sp.identityserver");
			assertEquals("https://sp.identityserver/idp/profile/SAML2/Redirect/SSO", redirectLocation);
		}

	}

	@Test
	public void testGetSingleSignOnLocationInvalidSamlProtocol() throws IOException {
		IuTestLogger.allow("iu.auth.saml.SamlBuilder", Level.WARNING);
		final var spi = new SamlConnectSpi();
		File file = new File("src/test/resource/metadata_invalid_protocol.xml");
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(file.toPath().toUri().toURL());

		final var client = getClient(Arrays.asList(uri), "urn:iu:ess:trex");
		SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
		assertThrows(IllegalStateException.class, () -> provider.getSingleSignOnLocation("https://sp.identityserver"));
	}

	@Test
	public void testGetSingleSignOnLocationInvalidSamlBinding() throws IOException {
		IuTestLogger.allow("iu.auth.saml.SamlBuilder", Level.WARNING);
		final var spi = new SamlConnectSpi();
		File file = new File("src/test/resource/metadata_invalid_binding.xml");
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(file.toPath().toUri().toURL());

		final var client = getClient(Arrays.asList(uri), "urn:iu:esshr:test");
		SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
		assertThrows(IllegalStateException.class, () -> provider.getSingleSignOnLocation("https://sp.identityserver"));
	}

	@Test
	public void testGetAuthenticationRequest() throws IOException {
		String resourceUri = "test://entry/show";
		final var uri = URI.create("test://localhost/" + IdGenerator.generateId());
		final var spi = new SamlConnectSpi();
		final var client = getClient(Arrays.asList(uri), "urn:iu:ess:iumobile");
		SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
		assertThrows(IllegalArgumentException.class,
				() -> provider.getAuthRequest(URI.create("test://iumobile"), IdGenerator.generateId(), resourceUri));
		assertNotNull(provider.getAuthRequest(URI.create("test://postUrl/"), IdGenerator.generateId(), resourceUri));
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

	private IuSamlClient getClient(List<URI> metadataUris, String serviceProviderEntityId) {
		final var client = new IuSamlClient() {

			@Override
			public String getServiceProviderEntityId() {
				return serviceProviderEntityId;
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
				final var acsUri = URI.create("test://postUrl/");
				return IuException.unchecked(() -> Arrays.asList(acsUri));
			}

			@Override
			public List<URI> getMetaDataUris() {
				return IuException.unchecked(() -> metadataUris);
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
		return client;
	}

}
