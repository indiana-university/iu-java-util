package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlPrincipal;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class SamlSessionTest {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.auth.saml.SamlSession", Level.FINE);
	}


	@Test
	public void testGetAuthenticationRequest_invalidResourceUri() {
		final var uri = URI.create("test://entry/" + IdGenerator.generateId());
		final var serviceProviderIdentityId = "urn:iu:ess:example-test";
		IuSamlClient client = mock(IuSamlClient.class);
		when(client.getApplicationUri()).thenReturn(uri);
		SamlProvider provider = mock(SamlProvider.class);
		when(provider.getClient()).thenReturn(client);

		try (final var mockSpi = mockStatic(SamlConnectSpi.class)) {
			mockSpi.when(() -> SamlConnectSpi.getProvider(serviceProviderIdentityId)).thenReturn(provider);
			SamlSession session = new SamlSession(serviceProviderIdentityId, uri);
			URI entityId = URI.create("");
			URI postUri = URI.create("");
			URI resourceUri = URI.create("");
			assertThrows(IllegalArgumentException.class, () -> session.getAuthenticationRequest(entityId, postUri, resourceUri));

		}
	}

	@Test
	public void testGetAuthenticationRequest_invalidEntityId() throws URISyntaxException {
		final var uri = URI.create("test://entry/");
		String entityId = "test://ldp/";

		final var serviceProviderIdentityId = "urn:iu:ess:example-test";

		IuSamlClient client = mock(IuSamlClient.class);
		when(client.getApplicationUri()).thenReturn(uri);
		SamlProvider provider = mock(SamlProvider.class);
		when(provider.getClient()).thenReturn(client);
		when(client.getMetaDataUris()).thenReturn(Arrays.asList(new URI(entityId)));

		try (final var mockSpi = mockStatic(SamlConnectSpi.class)) {
			mockSpi.when(() -> SamlConnectSpi.getProvider(serviceProviderIdentityId)).thenReturn(provider);
			SamlSession session = new SamlSession(serviceProviderIdentityId, uri);
			URI postUri = URI.create("");
			URI resourceUri = URI.create("test://entry/show");
			assertThrows(IllegalArgumentException.class, () -> session.getAuthenticationRequest(URI.create("test://"), postUri, resourceUri));
		}
	}

	@Test
	public void testGetAuthenticationRequestAndAuthorization() throws URISyntaxException, UnknownHostException, IuAuthenticationException {
		final var uri = URI.create("test://entry/");
		final var serviceProviderIdentityId = "urn:iu:ess:example-test";
		String entityId = "test://ldp/";
		String postUrl = "test://postUrl";
		String ssoLocation = entityId + "profile/SAML2/Redirect/SSO";

		String str = "samlxml";
		final Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("issueInstant", Instant.now());
		//claims.put("notBefore", conditions.getNotBefore());
		//claims.put("notOnOrAfter", conditions.getNotOnOrAfter());
		claims.put("authnInstant", Instant.now());

		Deflater deflater = new Deflater(Deflater.DEFLATED, true);

		ByteArrayOutputStream samlRequestBuffer = new ByteArrayOutputStream();
		IuException.unchecked(() -> {
			try (DeflaterOutputStream d = new DeflaterOutputStream(samlRequestBuffer, deflater)) {
				d.write(str.getBytes("UTF-8"));
			}
		});

		SamlPrincipal id = new SamlPrincipal("test", "test", "test@iu.edu", entityId,serviceProviderIdentityId, claims);
		SamlPrincipal samlPrincipal = null;

		IuSamlClient client = mock(IuSamlClient.class);
		when(client.getApplicationUri()).thenReturn(uri);
		when(client.getMetaDataUris()).thenReturn(Arrays.asList(new URI(entityId)));

		when(client.getAcsUris()).thenReturn(Arrays.asList(new URI(postUrl)));
		SamlProvider provider = mock(SamlProvider.class);
		when(provider.getClient()).thenReturn(client);
		when(provider.getSingleSignOnLocation(entityId)).thenReturn(ssoLocation);
		when(provider.getAuthRequest(any(URI.class),anyString(),anyString() )).thenReturn(samlRequestBuffer);
		when(provider.authorize(any(InetAddress.class), any(URI.class), anyString(), anyString())).thenReturn(id, samlPrincipal);

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
			IuSamlPrincipal principal = session.authorize(InetAddress.getByName("127.0.0.0"), URI.create(postUrl),
					"", relayState);
			assertNotNull(principal);
			
			assertThrows(IuAuthenticationException.class, ()-> session.getPrincipalIdentity());
			
		}

	}


	@Test public void testAuthorize() throws UnknownHostException, IuAuthenticationException { 
		final var uri =	URI.create("test://entry/"); 
		final var serviceProviderIdentityId = "urn:iu:ess:example-test"; 
		final var postUrl = "test://postUrl";

		IuSamlClient client = mock(IuSamlClient.class); SamlProvider provider =
				mock(SamlProvider.class); when(provider.getClient()).thenReturn(client);

		try (final var mockSpi = mockStatic(SamlConnectSpi.class)) { 
			mockSpi.when(()->	SamlConnectSpi.getProvider(serviceProviderIdentityId)).thenReturn(provider);
			SamlSession session = new SamlSession(serviceProviderIdentityId, uri);
			assertThrows(IuAuthenticationException.class, () -> session.authorize(InetAddress.getByName("127.0.0.0"), URI.create(postUrl),
							"", IdGenerator.generateId())); 
		} 
	}


	/*@Test
	public void testGetPrincipalIdentity() throws IuAuthenticationException {
		final var uri = URI.create("test://entry/");
		final var serviceProviderIdentityId = "urn:iu:ess:example-test";
		IuSamlClient client = mock(IuSamlClient.class);
		SamlProvider provider = mock(SamlProvider.class);
		when(provider.getClient()).thenReturn(client);
		when(client.getAuthenticatedSessionTimeout()).thenReturn(Duration.ofHours(12L), 
				Duration.ofMinutes(0));


		try (final var mockSpi = mockStatic(SamlConnectSpi.class)) {
			mockSpi.when(() -> SamlConnectSpi.getProvider(serviceProviderIdentityId)).thenReturn(provider);
			SamlSession session = new SamlSession(serviceProviderIdentityId, uri);

			assertThrows(NullPointerException.class, () -> session.getPrincipalIdentity());
			//assertThrows(IuAuthenticationException.class, () -> session.getPrincipalIdentity());
		}
	}*/





}
