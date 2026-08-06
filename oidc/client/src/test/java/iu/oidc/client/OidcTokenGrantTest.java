/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.oidc.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.client.HttpException;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcProviderMetadata;
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
	
	@BeforeEach
	void setup() {
		IuTestLogger.allow(OidcTokenGrant.class.getName(), Level.FINE, "OIDC token response .*");
	}

	@Test
	void testTokenResponse() throws IOException {
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
		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
	void testInvalidInitialTokenResponse() throws IOException {
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getTokenEndpoint()).thenReturn(tokenEndpoint);
		final var provider = mock(IuOidcProvider.class);
		when(provider.getMetadata()).thenReturn(metadata);

		final var config = mock(IuOidcClientReference.class);
		when(config.getProvider()).thenReturn(provider);
		when(config.adaptJson(IuOidcTokenResponse.class)).thenReturn(
				IuJsonAdapter.adapt(IuOidcTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		final var accessToken = IdGenerator.generateId();
		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> true),
				eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
					.add("access_token", accessToken) //
					.add("expires_in", 60L) //
					.build());

		IuTestLogger.expect(OidcTokenGrant.class.getName(), Level.INFO, "initial token response invalid",
				IllegalArgumentException.class);
		final var initialResponse = mock(IuOidcTokenResponse.class);
		final var grant = new OidcTokenGrant(config, initialResponse, Instant.now().plusSeconds(60L)) {
			private int validationCount;

			@Override
			public WebToken validateTokenResponse(IuOidcTokenResponse response) {
				if (validationCount++ == 0)
					throw new IllegalArgumentException("invalid initial response");
				return null;
			}

			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};

		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testTokenErrorResponseBodyLogging() throws IOException {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();

		final var config = mock(IuOidcClientReference.class);
		when(config.getProvider()).thenReturn(provider);

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.build());

		final var body = "invalid_grant";
		final HttpResponse<InputStream> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(400);
		when(response.body()).thenReturn(new ByteArrayInputStream(IuText.utf8(body)));
		final var error = new HttpException(response, "token request failed");
		IuHttpAware.mock
				.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> true),
						eq(IuHttp.READ_JSON_OBJECT)))
				.thenThrow(error);

		IuTestLogger.expect(OidcTokenGrant.class.getName(), Level.INFO,
				"OIDC token error 400 BAD REQUEST; body =" + body);

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};

		// the error response propagates as-is, so callers can handle it as an IOException
		assertSame(error, assertThrows(HttpException.class, grant::getTokenResponse));
	}

	@Test
	void testTokenErrorWithoutResponse() throws IOException {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getIssuer()).thenReturn(issuer);
		final var metadataUri = provider.getMetadataUri();

		final var config = mock(IuOidcClientReference.class);
		when(config.getProvider()).thenReturn(provider);

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.build());

		// a connection failure carries no response, so there is no body to log
		final var error = new HttpException("HTTP connection failed", new IOException());
		IuHttpAware.mock
				.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> true),
						eq(IuHttp.READ_JSON_OBJECT)))
				.thenThrow(error);

		final var grant = new OidcTokenGrant(config) {
			@Override
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
			}
		};

		assertSame(error, assertThrows(HttpException.class, grant::getTokenResponse));
		assertEquals(0, error.getSuppressed().length);
	}

	@Test
	void testTokenAuth() throws IOException {
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
		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
	void testClientAuthBasic() throws IOException {
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
		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) throws IOException {
				addClientAuth(requestBuilder, params);
			}
		};
		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());
	}

	@Test
	void testClientSecretPost() throws IOException {
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
		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) throws IOException {
				addClientAuth(requestBuilder, params);
			}
		};
		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());
	}

	@Test
	void testClientSecretJwt() throws IOException {
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
		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
			protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) throws IOException {
				addClientAuth(requestBuilder, params);
			}
		};
		assertEquals(accessToken, grant.getTokenResponse().getAccessToken());
	}

	@Test
	void testIdToken() throws IOException {
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

		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
	void testMaxAge() throws IOException {
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

		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
	void testAtHash() throws IOException {
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

		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
	void testEncIdToken() throws IOException {
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

		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> {
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
