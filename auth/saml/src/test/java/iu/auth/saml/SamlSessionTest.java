package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.config.IuSamlClient;
import edu.iu.auth.saml.IuSamlPrincipal;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class SamlSessionTest {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.auth.saml.SamlSession", Level.FINE);
	}

	@Test
	public void testGetAuthenticationRequest_invalidConfigurations() {
		final var uri = URI.create("test://entry/");
		final var serviceProviderIdentityId = "urn:iu:ess:example-test";
		URI entityId = URI.create("test://ldp/shibboleth");
		IuSamlClient client = mock(IuSamlClient.class);
		when(client.getApplicationUri()).thenReturn(uri);
		SamlProvider provider = mock(SamlProvider.class);
		when(provider.getClient()).thenReturn(client);
		when(client.getMetaDataUris()).thenReturn(Arrays.asList(entityId));

		try (final var mockSpi = mockStatic(SamlConnectSpi.class)) {
			mockSpi.when(() -> SamlConnectSpi.getProvider(serviceProviderIdentityId)).thenReturn(provider);
			SamlSession session = new SamlSession(serviceProviderIdentityId, uri);
			URI postUri = URI.create("");
			URI resourceUri = URI.create("");
			assertThrows(IllegalArgumentException.class,
					() -> session.getAuthenticationRequest(entityId, postUri, resourceUri));
			URI resourceUri1 = URI.create("test://entry/show");
			assertThrows(IllegalArgumentException.class,
					() -> session.getAuthenticationRequest(URI.create("test://ldp/"), postUri, resourceUri1));

		}
	}

	@Test
	public void testGetAuthenticationRequestAndAuthorization()
			throws URISyntaxException, UnknownHostException, IuAuthenticationException {
		final var uri = URI.create("test://entry/");
		final var serviceProviderIdentityId = "urn:iu:ess:example-test";
		String entityId = "test://ldp/";
		String postUrl = "test://postUrl";
		String ssoLocation = entityId + "profile/SAML2/Redirect/SSO";

		String str = "samlxml";
		Deflater deflater = new Deflater(Deflater.DEFLATED, true);

		ByteArrayOutputStream samlRequestBuffer = new ByteArrayOutputStream();
		IuException.unchecked(() -> {
			try (DeflaterOutputStream d = new DeflaterOutputStream(samlRequestBuffer, deflater)) {
				d.write(str.getBytes("UTF-8"));
			}
		});

		final Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("issueInstant", Instant.now());
		// claims.put("notBefore", conditions.getNotBefore());
		// claims.put("notOnOrAfter", conditions.getNotOnOrAfter());
		claims.put("authnInstant", Instant.now());
		SamlPrincipal id = new SamlPrincipal("test", "test", "test@iu.edu", entityId, serviceProviderIdentityId,
				claims);

		final Map<String, Object> claimsWithPlueOne = new LinkedHashMap<>();

		claimsWithPlueOne.put("issueInstant", Instant.now().plusSeconds(3600L));
		// claims.put("notBefore", conditions.getNotBefore());
		// claims.put("notOnOrAfter", conditions.getNotOnOrAfter());
		claimsWithPlueOne.put("authnInstant", Instant.now().plusSeconds(3600L));
		SamlPrincipal id1 = new SamlPrincipal("test", "test", "test@iu.edu", entityId, serviceProviderIdentityId,
				claimsWithPlueOne);

		SamlPrincipal samlPrincipal = null;

		IuSamlClient client = mock(IuSamlClient.class);
		when(client.getApplicationUri()).thenReturn(uri);
		when(client.getMetaDataUris()).thenReturn(Arrays.asList(new URI(entityId)));
		when(client.getAcsUris()).thenReturn(Arrays.asList(new URI(postUrl)));
		when(client.getAuthenticatedSessionTimeout()).thenReturn(Duration.ofHours(12L), Duration.ofMinutes(0));

		SamlProvider provider = mock(SamlProvider.class);
		when(provider.getClient()).thenReturn(client);
		when(provider.getSingleSignOnLocation(entityId)).thenReturn(ssoLocation);
		when(provider.getAuthRequest(any(URI.class), anyString(), anyString())).thenReturn(samlRequestBuffer);
		when(provider.authorize(any(InetAddress.class), any(URI.class), anyString(), anyString())).thenReturn(id, id1,
				samlPrincipal);

		try (final var mockSpi = mockStatic(SamlConnectSpi.class)) {
			mockSpi.when(() -> SamlConnectSpi.getProvider(serviceProviderIdentityId)).thenReturn(provider);
			SamlSession session = new SamlSession(serviceProviderIdentityId, uri);
			URI postUri = URI.create(postUrl);
			URI resourceUri = URI.create("test://entry/show");
			URI redirectUri = session.getAuthenticationRequest(URI.create(entityId), postUri, resourceUri);
			assertNotNull(redirectUri);
			assertTrue(redirectUri.getQuery().contains("SAMLRequest"));
			assertTrue(redirectUri.getQuery().contains("RelayState"));
			String parameters[] = redirectUri.getQuery().split("&");
			String relayState = parameters[1].split("=")[1];
			IuSamlPrincipal principal = session.authorize(InetAddress.getByName("127.0.0.0"), URI.create(postUrl), "",
					relayState);
			assertNotNull(principal);
			assertNotNull(session.getPrincipalIdentity());
			assertThrows(IuAuthenticationException.class, () -> session.getPrincipalIdentity());

			// verify current time is not after authnInstant time
			redirectUri = session.getAuthenticationRequest(URI.create(entityId), postUri, resourceUri);
			assertNotNull(redirectUri);
			assertTrue(redirectUri.getQuery().contains("SAMLRequest"));
			assertTrue(redirectUri.getQuery().contains("RelayState"));
			relayState = redirectUri.getQuery().split("&")[1].split("=")[1];

			principal = session.authorize(InetAddress.getByName("127.0.0.0"), URI.create(postUrl), "", relayState);
			assertThrows(IuAuthenticationException.class, () -> session.getPrincipalIdentity());

			// verify authorize method return principal as null

			redirectUri = session.getAuthenticationRequest(URI.create(entityId), postUri, resourceUri);
			assertNotNull(redirectUri);
			assertTrue(redirectUri.getQuery().contains("SAMLRequest"));
			assertTrue(redirectUri.getQuery().contains("RelayState"));
			String newRelayState = redirectUri.getQuery().split("&")[1].split("=")[1];

			assertThrows(IuAuthenticationException.class, () -> session.authorize(InetAddress.getByName("127.0.0.0"),
					URI.create(postUrl), "", newRelayState));

		}

	}

	@Test
	public void testAuthorize() throws UnknownHostException, IuAuthenticationException {
		final var uri = URI.create("test://entry/");
		final var serviceProviderIdentityId = "urn:iu:ess:example-test";
		final var postUrl = "test://postUrl";

		IuSamlClient client = mock(IuSamlClient.class);
		SamlProvider provider = mock(SamlProvider.class);
		when(provider.getClient()).thenReturn(client);

		try (final var mockSpi = mockStatic(SamlConnectSpi.class)) {
			mockSpi.when(() -> SamlConnectSpi.getProvider(serviceProviderIdentityId)).thenReturn(provider);
			SamlSession session = new SamlSession(serviceProviderIdentityId, uri);
			assertThrows(IuAuthenticationException.class, () -> session.authorize(InetAddress.getByName("127.0.0.0"),
					URI.create(postUrl), "", IdGenerator.generateId()));
		}
	}

}
