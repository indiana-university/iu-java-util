package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuAuthenticationRealm;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.auth.saml.IuSamlSession;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuSamlServiceProvider;

@SuppressWarnings("javadoc")
public class SamlSessionTest {
	private MockedStatic<IuPrincipalIdentity> mockPrincipalIdentity;

	@BeforeEach
	public void setup() throws Exception {
		IuTestLogger.allow("iu.auth.saml.SamlSession", Level.FINE);
		IuTestLogger.allow("iu.auth.saml.SamlSession", Level.INFO);
		mockPrincipalIdentity = mockStatic(IuPrincipalIdentity.class);

	}

	@AfterEach
	public void tearDown() throws Exception {
		mockPrincipalIdentity.close();
	}

	@Test
	public void testGetRequestUri() {
		URI requestUri = URI.create("https://sp.identityserver/?SAMLRequest=1233&RelayState=1223");
		SamlServiceProvider provider = mock(SamlServiceProvider.class);
		when(provider.isValidEntryPoint(any())).thenReturn(true);
		when(provider.getAuthnRequest(any(), any())).thenReturn(requestUri);
		URI postUri = URI.create("test://postUri");

		try (final var mockProvider = mockStatic(SamlServiceProvider.class)) {
			mockProvider.when(() -> SamlServiceProvider.withBinding(postUri)).thenReturn(provider);
			final var secret = WebKey.ephemeral(Encryption.A256GCM).getKey();
			URI entryPointUri = URI.create("test://entrypoint");
			IuSamlSession samlSession = new SamlSession(entryPointUri, postUri, () -> secret);
			assertEquals(requestUri, samlSession.getRequestUri());

		}

	}

	@Test
	public void testInvalidEntryPointURI() {
		SamlServiceProvider provider = mock(SamlServiceProvider.class);
		URI postUri = URI.create("test://postUri");

		try (final var mockProvider = mockStatic(SamlServiceProvider.class)) {
			mockProvider.when(() -> SamlServiceProvider.withBinding(postUri)).thenReturn(provider);
			final var secret = WebKey.ephemeral(Encryption.A256GCM).getKey();
			URI entryPointUri = URI.create("test://entrypoint");
			assertThrows(IllegalArgumentException.class, () -> new SamlSession(entryPointUri, postUri, () -> secret));
		}
	}

	@Test
	public void testIuAuthenticationExceptionVerifyResponse() throws IuAuthenticationException {
		URI requestUri = URI.create("https://sp.identityserver/?SAMLRequest=1233&RelayState=1223");
		SamlServiceProvider provider = mock(SamlServiceProvider.class);
		when(provider.isValidEntryPoint(any())).thenReturn(true);
		when(provider.getAuthnRequest(any(), any())).thenReturn(requestUri);
		URI postUri = URI.create("test://postUri");

		try (final var mockProvider = mockStatic(SamlServiceProvider.class)) {
			mockProvider.when(() -> SamlServiceProvider.withBinding(postUri)).thenReturn(provider);
			final var secret = WebKey.ephemeral(Encryption.A256GCM).getKey();
			URI entryPointUri = URI.create("test://entrypoint");
			IuSamlSession samlSession = new SamlSession(entryPointUri, postUri, () -> secret);
			assertThrows(IuAuthenticationException.class,
					() -> samlSession.verifyResponse("127.0.0.0", "", IdGenerator.generateId()));
		}
	}

	@Test
	public void testIuAuthenticationExceptionMisMatchRelayStateVerifyResponse()
			throws IuAuthenticationException, NoSuchFieldException, SecurityException, MalformedURLException,
			IllegalArgumentException, IllegalAccessException {
		File metaDataFile = new File("src/test/resource/metadata_sample.xml");
		final var cert = mock(X509Certificate.class);
		final var mockWebKey = mock(WebKey.class);
		when(mockWebKey.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		final var mockPkp = mock(IuPrivateKeyPrincipal.class);
		when(mockPkp.getEncryptJwk()).thenReturn(mockWebKey);
		when(mockPkp.getAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(metaDataFile.toPath().toUri().toURL());
		final var acsUri = URI.create("test://postUrl/");
		IuSamlServiceProviderMetadata config = getConfig(Arrays.asList(uri), "urn:iu:ess:sisjee", mockPkp,
				Arrays.asList(acsUri));
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";

		final var mockSamlPrincipal = mock(SamlPrincipal.class);

		SamlBuilder builder = new SamlBuilder(config);
		final var provider = mock(SamlServiceProvider.class);
		when(provider.isValidEntryPoint(any())).thenReturn(true);
		when(provider.getAuthnRequest(any(), any())).thenCallRealMethod();
		when(provider.verifyResponse(any(), any(), any())).thenReturn(mockSamlPrincipal);
		Field f;
		f = SamlServiceProvider.class.getDeclaredField("samlBuilder");
		f.setAccessible(true);
		f.set(provider, builder);

		f = SamlServiceProvider.class.getDeclaredField("postUri");
		f.setAccessible(true);
		f.set(provider, postUri);

		try (final var mockIuAuthenticationRealm = mockStatic(IuAuthenticationRealm.class);
				final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS);
				final var mockProvider = mockStatic(SamlServiceProvider.class)) {

			mockProvider.when(() -> SamlServiceProvider.withBinding(postUri)).thenReturn(provider);

			mockIuAuthenticationRealm.when(() -> IuAuthenticationRealm.of(realm)).thenReturn(config);
			mockAuthConfig.when(() -> AuthConfig.get(IuSamlServiceProvider.class)).thenReturn(Arrays.asList(provider));
			final var secret = WebKey.ephemeral(Encryption.A256GCM).getKey();

			URI entryPointUri = URI.create("test://entrypoint");
			IuSamlSession samlSession = new SamlSession(entryPointUri, postUri, () -> secret);
			URI requestUri = samlSession.getRequestUri();
			assertThrows(IuAuthenticationException.class,
					() -> samlSession.verifyResponse("127.0.0.0", "", IdGenerator.generateId()));

		}
	}

	@Test
	public void testSuccessVerifyResponse() throws MalformedURLException, IuAuthenticationException, NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);

		File metaDataFile = new File("src/test/resource/metadata_sample.xml");
		final var cert = mock(X509Certificate.class);

		final var mockWebKey = mock(WebKey.class);
		when(mockWebKey.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		final var mockPkp = mock(IuPrivateKeyPrincipal.class);
		when(mockPkp.getEncryptJwk()).thenReturn(mockWebKey);
		when(mockPkp.getAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(metaDataFile.toPath().toUri().toURL());
		final var acsUri = URI.create("test://postUrl/");
		IuSamlServiceProviderMetadata config = getConfig(Arrays.asList(uri), "urn:iu:ess:sisjee", mockPkp,
				Arrays.asList(acsUri));
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";

		final Queue<SamlAssertion> samlAssertions = new ArrayDeque<>();
		final var principalName = "foo";
		final var issueInstant = Instant.now();
		final var authnInstant = Instant.now();
		final var expires = authnInstant.plus(Duration.ofHours(12L));
		final var assertions = IuIterable.stream(samlAssertions).toArray(SamlAssertion[]::new);

		final var jsonbuilder = IuJson.object() //
				.add("iss", "https://sp.identityserver") //
				.add("aud", realm) //
				.add("sub", principalName) //
				.add("iat", issueInstant.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("auth_time", authnInstant.getEpochSecond()); //
		IuJson.add(jsonbuilder, "urn:oasis:names:tc:SAML:2.0:assertion", () -> assertions,
				IuJsonAdapter.of(SamlAssertion[].class, SamlAssertion.JSON));
		
		final var mockSamlPrincipal = mock(SamlPrincipal.class);
		when(mockSamlPrincipal.toString()).thenReturn(jsonbuilder.build().toString());

		SamlBuilder builder = new SamlBuilder(config);
		final var provider = mock(SamlServiceProvider.class);
		when(provider.isValidEntryPoint(any())).thenReturn(true);
		when(provider.getAuthnRequest(any(), any())).thenCallRealMethod();
		when(provider.verifyResponse(any(), any(), any())).thenReturn(mockSamlPrincipal);
		when(provider.getVerifyAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(provider.getVerifyKey()).thenReturn(WebKey.ephemeral(Algorithm.RSA_OAEP));

		Field f;
		f = SamlServiceProvider.class.getDeclaredField("samlBuilder");
		f.setAccessible(true);
		f.set(provider, builder);

		f = SamlServiceProvider.class.getDeclaredField("postUri");
		f.setAccessible(true);
		f.set(provider, postUri);

		try (final var mockIuAuthenticationRealm = mockStatic(IuAuthenticationRealm.class);
				final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS);
				final var mockProvider = mockStatic(SamlServiceProvider.class)) {
			mockProvider.when(() -> SamlServiceProvider.withBinding(postUri)).thenReturn(provider);
			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);

			mockIuAuthenticationRealm.when(() -> IuAuthenticationRealm.of(realm)).thenReturn(config);
			mockAuthConfig.when(() -> AuthConfig.get(IuSamlServiceProvider.class)).thenReturn(Arrays.asList(provider));
			final var secret = WebKey.ephemeral(Encryption.A256GCM).getKey();

			URI entryPointUri = URI.create("test://entrypoint");
			IuSamlSession samlSession = new SamlSession(entryPointUri, postUri, () -> secret);
			URI requestUri = samlSession.getRequestUri();
			String parameters[] = requestUri.getQuery().split("&");
			String relayState = parameters[1].split("=")[1];
			assertDoesNotThrow(() -> samlSession.verifyResponse("127.0.0.0", "", relayState));
			final var activatedSession = IuSamlSession.activate(samlSession.toString(), () -> secret);
			assertNotNull(activatedSession);
			final var iuSamlPrincipal = activatedSession.getPrincipalIdentity();
			assertNotNull(iuSamlPrincipal);
			activatedSession.getPrincipalIdentity();

		}
	}

	@Test
	public void testToString() throws MalformedURLException, NoSuchFieldException, SecurityException,
			IllegalArgumentException, IllegalAccessException {
		File metaDataFile = new File("src/test/resource/metadata_sample.xml");
		final var cert = mock(X509Certificate.class);

		final var mockWebKey = mock(WebKey.class);
		when(mockWebKey.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		final var mockPkp = mock(IuPrivateKeyPrincipal.class);
		when(mockPkp.getEncryptJwk()).thenReturn(mockWebKey);
		when(mockPkp.getAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(metaDataFile.toPath().toUri().toURL());
		final var acsUri = URI.create("test://postUrl/");
		IuSamlServiceProviderMetadata config = getConfig(Arrays.asList(uri), "urn:iu:ess:sisjee", mockPkp,
				Arrays.asList(acsUri));
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
	
		final Queue<SamlAssertion> samlAssertions = new ArrayDeque<>();
		final var principalName = "foo";
		final var issueInstant = Instant.now();
		final var authnInstant = Instant.now();
		final var expires = authnInstant.plus(Duration.ofHours(12L));
		final var assertions = IuIterable.stream(samlAssertions).toArray(SamlAssertion[]::new);

		final var jsonbuilder = IuJson.object() //
				.add("iss", "https://sp.identityserver") //
				.add("aud", realm) //
				.add("sub", principalName) //
				.add("iat", issueInstant.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("auth_time", authnInstant.getEpochSecond()); //
		IuJson.add(jsonbuilder, "urn:oasis:names:tc:SAML:2.0:assertion", () -> assertions,
				IuJsonAdapter.of(SamlAssertion[].class, SamlAssertion.JSON));

		final var mockSamlPrincipal = mock(SamlPrincipal.class);
		when(mockSamlPrincipal.toString()).thenReturn(jsonbuilder.build().toString());

		final var provider = mock(SamlServiceProvider.class);
		when(provider.isValidEntryPoint(any())).thenReturn(true);
		when(provider.getAuthnRequest(any(), any())).thenCallRealMethod();
		when(provider.verifyResponse(any(), any(), any())).thenReturn(mockSamlPrincipal);
		when(provider.getVerifyAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(provider.getVerifyKey()).thenReturn(WebKey.ephemeral(Algorithm.RSA_OAEP));
		try (final var mockIuAuthenticationRealm = mockStatic(IuAuthenticationRealm.class);
				final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS);
				final var mockProvider = mockStatic(SamlServiceProvider.class)) {
			mockProvider.when(() -> SamlServiceProvider.withBinding(postUri)).thenReturn(provider);

			mockIuAuthenticationRealm.when(() -> IuAuthenticationRealm.of(realm)).thenReturn(config);
			mockAuthConfig.when(() -> AuthConfig.get(IuSamlServiceProvider.class)).thenReturn(Arrays.asList(provider));
			final var secret = WebKey.ephemeral(Encryption.A128GCM).getKey();

			URI entryPointUri = URI.create("test://entrypoint");
			IuSamlSession samlSession = new SamlSession(entryPointUri, postUri, () -> secret);
			Field f;
			f = SamlSession.class.getDeclaredField("relayState");
			f.setAccessible(true);
			f.set(samlSession, IdGenerator.generateId());

			f = SamlSession.class.getDeclaredField("sessionId");
			f.setAccessible(true);
			f.set(samlSession, IdGenerator.generateId());
			assertNotNull(samlSession.toString());

			samlSession = new SamlSession(entryPointUri, postUri, () -> WebKey.ephemeral(Encryption.A192GCM).getKey());
			f = SamlSession.class.getDeclaredField("relayState");
			f.setAccessible(true);
			f.set(samlSession, IdGenerator.generateId());

			f = SamlSession.class.getDeclaredField("sessionId");
			f.setAccessible(true);
			f.set(samlSession, IdGenerator.generateId());
			assertNotNull(samlSession.toString());

			final var session = new SamlSession(entryPointUri, postUri, () -> new byte[8]);
			f = SamlSession.class.getDeclaredField("relayState");
			f.setAccessible(true);
			f.set(session, IdGenerator.generateId());

			f = SamlSession.class.getDeclaredField("sessionId");
			f.setAccessible(true);
			f.set(session, IdGenerator.generateId());
			assertThrows(IllegalStateException.class, () -> session.toString());

		}
	}

	@Test
	public void testGetPrincipalIdentityIuAuthenticationException() throws NoSuchFieldException, SecurityException,
			MalformedURLException, IllegalArgumentException, IllegalAccessException, IuAuthenticationException {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);

		File metaDataFile = new File("src/test/resource/metadata_sample.xml");
		final var cert = mock(X509Certificate.class);

		final var mockWebKey = mock(WebKey.class);
		when(mockWebKey.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		final var mockPkp = mock(IuPrivateKeyPrincipal.class);
		when(mockPkp.getEncryptJwk()).thenReturn(mockWebKey);
		when(mockPkp.getAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(metaDataFile.toPath().toUri().toURL());
		final var acsUri = URI.create("test://postUrl/");
		IuSamlServiceProviderMetadata config = getConfig(Arrays.asList(uri), "urn:iu:ess:sisjee", mockPkp,
				Arrays.asList(acsUri));

		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";

		final Queue<SamlAssertion> samlAssertions = new ArrayDeque<>();

		final var principalName = "foo";
		final var issueInstant = Instant.now();
		final var authnInstant = Instant.now();
		final var expires = authnInstant.plus(Duration.ofHours(12L));
		final var assertions = IuIterable.stream(samlAssertions).toArray(SamlAssertion[]::new);

		final var jsonbuilder = IuJson.object() //
				.add("iss", "https://sp.identityserver") //
				.add("aud", realm) //
				.add("sub", principalName) //
				.add("iat", issueInstant.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("auth_time", authnInstant.getEpochSecond()); //
		IuJson.add(jsonbuilder, "urn:oasis:names:tc:SAML:2.0:assertion", () -> assertions,
				IuJsonAdapter.of(SamlAssertion[].class, SamlAssertion.JSON));

		final var mockSamlPrincipal = mock(SamlPrincipal.class);
		when(mockSamlPrincipal.toString()).thenReturn(jsonbuilder.build().toString());

		SamlBuilder builder = new SamlBuilder(config);
		final var provider = mock(SamlServiceProvider.class);
		when(provider.isValidEntryPoint(any())).thenReturn(true);
		when(provider.getAuthnRequest(any(), any())).thenCallRealMethod();
		when(provider.verifyResponse(any(), any(), any())).thenReturn(mockSamlPrincipal);
		when(provider.getVerifyAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(provider.getVerifyKey()).thenReturn(WebKey.ephemeral(Algorithm.RSA_OAEP));

		Field f;
		f = SamlServiceProvider.class.getDeclaredField("samlBuilder");
		f.setAccessible(true);
		f.set(provider, builder);

		f = SamlServiceProvider.class.getDeclaredField("postUri");
		f.setAccessible(true);
		f.set(provider, postUri);

		try (final var mockIuAuthenticationRealm = mockStatic(IuAuthenticationRealm.class);
				final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS);
				final var mockProvider = mockStatic(SamlServiceProvider.class)) {
			mockProvider.when(() -> SamlServiceProvider.withBinding(postUri)).thenReturn(provider);
			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any()))
					.thenThrow(IuAuthenticationException.class);

			mockIuAuthenticationRealm.when(() -> IuAuthenticationRealm.of(realm)).thenReturn(config);
			mockAuthConfig.when(() -> AuthConfig.get(IuSamlServiceProvider.class)).thenReturn(Arrays.asList(provider));
			final var secret = WebKey.ephemeral(Encryption.A256GCM).getKey();

			URI entryPointUri = URI.create("test://entrypoint");
			IuSamlSession samlSession = new SamlSession(entryPointUri, postUri, () -> secret);
			URI requestUri = samlSession.getRequestUri();

			String parameters[] = requestUri.getQuery().split("&");
			String relayState = parameters[1].split("=")[1];
			assertDoesNotThrow(() -> samlSession.verifyResponse("127.0.0.0", "", relayState));
			final var activatedSession = IuSamlSession.activate(samlSession.toString(), () -> secret);
			assertNotNull(activatedSession);
			assertThrows(IuAuthenticationException.class, () -> activatedSession.getPrincipalIdentity());
		}
	}

	static IuSamlServiceProviderMetadata getConfig(List<URI> metadataUris, String serviceProviderEntityId,
			IuPrivateKeyPrincipal pkp, List<URI> acsUris) {
		final var config = new IuSamlServiceProviderMetadata() {

			@Override
			public String getServiceProviderEntityId() {
				return serviceProviderEntityId;
			}

			@Override
			public List<URI> getAcsUris() {
				return acsUris;
			}

			@Override
			public List<URI> getMetadataUris() {
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
			public String getIdentityProviderEntityId() {
				return "https://sp.identityserver";
			}

			@Override
			public IuPrivateKeyPrincipal getIdentity() {
				return pkp;
			}

			@Override
			public Iterable<URI> getEntryPointUris() {
				return IuIterable.iter(URI.create("test://entrypoint"));
			}

		};
		return config;
	}
}
