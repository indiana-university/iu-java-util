package iu.oidc.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcTokenResponse;
import edu.iu.test.IuTestLogger;
import iu.jwt.spi.Init;
import iu.oidc.client.config.IuOidcClient;
import iu.oidc.client.config.IuOidcClientReference;
import iu.oidc.client.config.IuOidcProvider;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class OidcTokenGrantTest {

	static {
		edu.iu.crypt.Init.init();
		Init.init();
	}

	@Test
	void testTokenResponse() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());

		final var config = mock(IuOidcClientReference.class);
		when(config.getProvider()).thenReturn(provider);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.build());

		final var accessToken = IdGenerator.generateId();
		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var rb = mock(HttpRequest.Builder.class);
			assertDoesNotThrow(() -> a.accept(rb));
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("access_token", accessToken) //
				.add("expires_in", 1) //
				.build());

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};
		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());

		final var grant2 = new OidcTokenGrant(config, grant.getTokenResponse(), grant.getNotAfter()) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};
		assertEquals(accessToken, grant2.getTokenResponse().getAccessToken());

		assertDoesNotThrow(() -> Thread.sleep(1000L));
		final var accessToken2 = IdGenerator.generateId();
		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var rb = mock(HttpRequest.Builder.class);
			assertDoesNotThrow(() -> a.accept(rb));
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("access_token", accessToken2) //
				.add("expires_in", 1) //
				.build());
		assertEquals(accessToken2, grant.getTokenResponse().getAccessToken());
		assertEquals(accessToken2, grant2.getTokenResponse().getAccessToken());
	}

	@Test
	void testTokenAuth() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());

		final var scope = IdGenerator.generateId();
		final var config = mock(IuOidcClientReference.class);
		when(config.getScope()).thenReturn(scope);
		when(config.getProvider()).thenReturn(provider);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.build());

		final var accessToken = IdGenerator.generateId();
		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			final var rb = mock(HttpRequest.Builder.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(s -> {
					final var params = IuWebUtils.parseQueryString(s);
					assertEquals(scope, params.get("scope").iterator().next());
					return true;
				}))).thenReturn(bp);
				assertDoesNotThrow(() -> a.accept(rb));
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("access_token", accessToken) //
				.add("expires_in", 1) //
				.build());

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};
		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());
	}

	@Test
	void testClientAuthBasic() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());

		final var clientId = IdGenerator.generateId();
		final var clientSecret = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getClientSecret()).thenReturn(clientSecret);
		when(client.isUseBasicAuth()).thenReturn(true);

		final var scope = IdGenerator.generateId();
		final var config = mock(IuOidcClientReference.class);
		when(config.getScope()).thenReturn(scope);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.build());

		final var accessToken = IdGenerator.generateId();
		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			final var rb = mock(HttpRequest.Builder.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(s -> {
					final var params = IuWebUtils.parseQueryString(s);
					assertEquals(scope, params.get("scope").iterator().next());
					return true;
				}))).thenReturn(bp);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Authorization",
						"Basic " + IuText.base64(IuText.utf8(clientId + ":" + clientSecret)));
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("access_token", accessToken) //
				.add("expires_in", 1) //
				.build());

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
				addClientAuth(requestBuilder, params);
			}
		};
		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());
	}

	@Test
	void testClientSecretPost() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());

		final var clientId = IdGenerator.generateId();
		final var clientSecret = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getClientSecret()).thenReturn(clientSecret);

		final var scope = IdGenerator.generateId();
		final var config = mock(IuOidcClientReference.class);
		when(config.getScope()).thenReturn(scope);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.build());

		final var accessToken = IdGenerator.generateId();
		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			final var rb = mock(HttpRequest.Builder.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(s -> {
					final var params = IuWebUtils.parseQueryString(s);
					assertEquals(scope, params.get("scope").iterator().next());
					assertEquals(clientId, params.get("client_id").iterator().next());
					assertEquals(clientSecret, params.get("client_secret").iterator().next());
					return true;
				}))).thenReturn(bp);
				assertDoesNotThrow(() -> a.accept(rb));
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("access_token", accessToken) //
				.add("expires_in", 1) //
				.build());

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
				addClientAuth(requestBuilder, params);
			}
		};
		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());
	}

	@Test
	void testClientSecretJwt() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());

		final var clientId = IdGenerator.generateId();
		final var assertionJwk = WebKey.builder(Algorithm.HS256).ephemeral().build();
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getAssertionJwk()).thenReturn(assertionJwk);

		final var scope = IdGenerator.generateId();
		final var config = mock(IuOidcClientReference.class);
		when(config.getScope()).thenReturn(scope);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.build());

		final var accessToken = IdGenerator.generateId();
		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			final var rb = mock(HttpRequest.Builder.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(s -> {
					final var params = IuWebUtils.parseQueryString(s);
					assertEquals(scope, params.get("scope").iterator().next());
					assertEquals(clientId, params.get("client_id").iterator().next());
					assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
							params.get("client_assertion_type").iterator().next());

					final var assertion = params.get("client_assertion").iterator().next();
					final var token = WebToken.verify(assertion, assertionJwk);
					assertNotNull(token.getTokenId());
					assertEquals(URI.create(clientId), token.getIssuer());
					assertEquals(tokenEndpoint, token.getAudience().iterator().next());
					assertEquals(clientId, token.getSubject());
					token.validateClaims(URI.create(clientId), tokenEndpoint, Duration.ofMinutes(2L));
					return true;
				}))).thenReturn(bp);
				assertDoesNotThrow(() -> a.accept(rb));
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("access_token", accessToken) //
				.add("expires_in", 1) //
				.build());

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
				addClientAuth(requestBuilder, params);
			}
		};
		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());
	}

	@Test
	void testIdToken() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var jwksUri = URI.create(IdGenerator.generateId());

		final var keyId = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		IuHttpAware.mock.when(() -> IuHttp.get(jwksUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("keys", IuJson.array().add(IuJson.parse(issuerKey.wellKnown().toString()))) //
				.build());

		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getDecryptJwk()).thenReturn(null);
		when(client.getMaxAge()).thenReturn(null);

		final var scope = IdGenerator.generateId();
		final var config = mock(IuOidcClientReference.class);
		when(config.getScope()).thenReturn(scope);
		when(config.getProvider()).thenReturn(provider);
		when(config.getClient()).thenReturn(client);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("issuer", issuer.toString()) //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.add("jwks_uri", jwksUri.toString()) //
				.build());

		final var sub = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var sha = IuException.unchecked(() -> MessageDigest.getInstance("SHA-" + Algorithm.EDDSA.size));
		final var hash = sha.digest(IuText.ascii(accessToken));
		final var atHash = IuText.base64Url(Arrays.copyOfRange(hash, 0, Algorithm.EDDSA.size / 16));

		final var idToken = WebToken.builder() //
				.iss(issuer) //
				.aud(URI.create(clientId)) //
				.sub(sub) //
				.iat() //
				.exp(Instant.now().plusSeconds(1L)) //
				.claim("at_hash", atHash, String.class) //
				.claim("auth_time", Instant.now().minusSeconds(1L), Instant.class) //
				.build().sign("JWT", Algorithm.EDDSA, issuerKey);

		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			final var rb = mock(HttpRequest.Builder.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(s -> {
					final var params = IuWebUtils.parseQueryString(s);
					assertEquals(scope, params.get("scope").iterator().next());
					return true;
				}))).thenReturn(bp);
				assertDoesNotThrow(() -> a.accept(rb));
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("access_token", accessToken) //
				.add("id_token", idToken) //
				.add("expires_in", 1) //
				.build());

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};
		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());
		final var token = grant.getIdToken();
		assertEquals(issuer, token.getIssuer());
		assertEquals(sub, token.getSubject());
		assertEquals(URI.create(clientId), token.getAudience().iterator().next());
	}

	@Test
	void testMaxAge() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var jwksUri = URI.create(IdGenerator.generateId());

		final var keyId = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		IuHttpAware.mock.when(() -> IuHttp.get(jwksUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("keys", IuJson.array().add(IuJson.parse(issuerKey.wellKnown().toString()))) //
				.build());

		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getDecryptJwk()).thenReturn(null);
		when(client.getMaxAge()).thenReturn(Duration.ofSeconds(5L));

		final var scope = IdGenerator.generateId();
		final var config = mock(IuOidcClientReference.class);
		when(config.getScope()).thenReturn(scope);
		when(config.getProvider()).thenReturn(provider);
		when(config.getClient()).thenReturn(client);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("issuer", issuer.toString()) //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.add("jwks_uri", jwksUri.toString()) //
				.build());

		final var sub = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var sha = IuException.unchecked(() -> MessageDigest.getInstance("SHA-" + Algorithm.EDDSA.size));
		final var hash = sha.digest(IuText.ascii(accessToken));
		final var atHash = IuText.base64Url(Arrays.copyOfRange(hash, 0, Algorithm.EDDSA.size / 16));

		final var idToken = WebToken.builder() //
				.iss(issuer) //
				.aud(URI.create(clientId)) //
				.sub(sub) //
				.iat() //
				.exp(Instant.now().plusSeconds(1L)) //
				.claim("at_hash", atHash, String.class) //
				.claim("auth_time", Instant.now().minusSeconds(10L), Instant.class) //
				.build().sign("JWT", Algorithm.EDDSA, issuerKey);

		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			final var rb = mock(HttpRequest.Builder.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(s -> {
					final var params = IuWebUtils.parseQueryString(s);
					assertEquals(scope, params.get("scope").iterator().next());
					return true;
				}))).thenReturn(bp);
				assertDoesNotThrow(() -> a.accept(rb));
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("access_token", accessToken) //
				.add("id_token", idToken) //
				.add("expires_in", 1) //
				.build());

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};

		assertEquals("Authenticated session lifetime PT10S exceeds maximum PT5S",
				assertThrows(IllegalArgumentException.class, grant::getTokenResponse).getMessage());
	}

	@Test
	void testAtHash() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var jwksUri = URI.create(IdGenerator.generateId());

		final var keyId = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		IuHttpAware.mock.when(() -> IuHttp.get(jwksUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("keys", IuJson.array().add(IuJson.parse(issuerKey.wellKnown().toString()))) //
				.build());

		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getDecryptJwk()).thenReturn(null);

		final var scope = IdGenerator.generateId();
		final var config = mock(IuOidcClientReference.class);
		when(config.getScope()).thenReturn(scope);
		when(config.getProvider()).thenReturn(provider);
		when(config.getClient()).thenReturn(client);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("issuer", issuer.toString()) //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.add("jwks_uri", jwksUri.toString()) //
				.build());

		final var sub = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var sha = IuException.unchecked(() -> MessageDigest.getInstance("SHA-" + Algorithm.EDDSA.size));
		final var hash = sha.digest(IuText.ascii(IdGenerator.generateId()));
		final var atHash = IuText.base64Url(Arrays.copyOfRange(hash, 0, Algorithm.EDDSA.size / 16));

		final var idToken = WebToken.builder() //
				.iss(issuer) //
				.aud(URI.create(clientId)) //
				.sub(sub) //
				.iat() //
				.exp(Instant.now().plusSeconds(1L)) //
				.claim("at_hash", atHash, String.class) //
				.claim("auth_time", Instant.now().minusSeconds(10L), Instant.class) //
				.build().sign("JWT", Algorithm.EDDSA, issuerKey);

		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			final var rb = mock(HttpRequest.Builder.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(s -> {
					final var params = IuWebUtils.parseQueryString(s);
					assertEquals(scope, params.get("scope").iterator().next());
					return true;
				}))).thenReturn(bp);
				assertDoesNotThrow(() -> a.accept(rb));
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("access_token", accessToken) //
				.add("id_token", idToken) //
				.add("expires_in", 1) //
				.build());

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};

		assertEquals("at_hash mismatch",
				assertThrows(IllegalArgumentException.class, grant::getTokenResponse).getMessage());
	}

	@Test
	void testEncIdToken() {
		IuTestLogger.allow("iu.crypt", Level.FINE);

		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var jwksUri = URI.create(IdGenerator.generateId());

		final var keyId = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		IuHttpAware.mock.when(() -> IuHttp.get(jwksUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("keys", IuJson.array().add(IuJson.parse(issuerKey.wellKnown().toString()))) //
				.build());

		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());

		final var dkeyId = IdGenerator.generateId();
		final var decryptKey = WebKey.builder(WebKey.Type.X25519).algorithm(Algorithm.ECDH_ES).keyId(dkeyId).ephemeral()
				.build();
		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getDecryptJwk()).thenReturn(IuIterable.iter(decryptKey));

		final var scope = IdGenerator.generateId();
		final var config = mock(IuOidcClientReference.class);
		when(config.getScope()).thenReturn(scope);
		when(config.getProvider()).thenReturn(provider);
		when(config.getClient()).thenReturn(client);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("issuer", issuer.toString()) //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.add("jwks_uri", jwksUri.toString()) //
				.build());

		final var sub = IdGenerator.generateId();

		final var idToken = WebToken.builder() //
				.iss(issuer) //
				.aud(URI.create(clientId)) //
				.sub(sub) //
				.iat() //
				.exp(Instant.now().plusSeconds(1L)) //
				.claim("auth_time", Instant.now().minusSeconds(1L), Instant.class) //
				.claim("azp", clientId, String.class) //
				.build().signAndEncrypt("JWT", Algorithm.EDDSA, issuerKey, Algorithm.ECDH_ES, Encryption.A256GCM,
						decryptKey.wellKnown());

		IuHttpAware.mock.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			final var rb = mock(HttpRequest.Builder.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(s -> {
					final var params = IuWebUtils.parseQueryString(s);
					assertEquals(scope, params.get("scope").iterator().next());
					return true;
				}))).thenReturn(bp);
				assertDoesNotThrow(() -> a.accept(rb));
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("id_token", idToken) //
				.add("expires_in", 1) //
				.build());

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};
		final var token = grant.getIdToken();
		assertEquals(issuer, token.getIssuer());
		assertEquals(sub, token.getSubject());
		assertEquals(URI.create(clientId), token.getAudience().iterator().next());
	}

}
