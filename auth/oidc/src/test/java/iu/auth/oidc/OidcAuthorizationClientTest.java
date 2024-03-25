/*
 * Copyright © 2024 Indiana University
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
package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.security.auth.Subject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import edu.iu.IdGenerator;
import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuText;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuBearerAuthCredentials;
import edu.iu.auth.oauth.IuTokenResponse;
import edu.iu.auth.oidc.IuOpenIdClient;
import edu.iu.client.IuHttp;
import edu.iu.crypt.WebKey;
import edu.iu.test.IuTestLogger;
import iu.auth.util.AccessTokenVerifier;
import iu.auth.util.WellKnownKeySet;
import iu.crypt.IuCrypt;
import jakarta.json.Json;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class OidcAuthorizationClientTest extends IuOidcTestCase {

	private JsonObject jwks;
	private Algorithm jwtSignAlgorithm;
	private AccessTokenVerifier idTokenVerifier;
	private String issuer;
	private URI authorizationEndpoint;
	private URI tokenEndpoint;
	private URI userinfoEndpoint;
	private JsonObject config;
	private IuApiCredentials credentials;
	private IuOpenIdClient idClient;
	private OidcAuthorizationClient client;
	private String clientId;
	private URI jwksUri;
	private KeyPair keyPair;

	@BeforeEach
	public void setup() throws Exception {
		final var rsaKeygen = KeyPairGenerator.getInstance("RSA");
		rsaKeygen.initialize(1024);
		keyPair = rsaKeygen.generateKeyPair();
		final var pub = (RSAPublicKey) keyPair.getPublic();
		final var jwkb = Json.createObjectBuilder();
		jwkb.add("kty", "RSA");
		jwkb.add("use", "sig");
		jwkb.add("kid", "defaultSign");
		jwkb.add("e", Base64.getUrlEncoder().encodeToString(pub.getPublicExponent().toByteArray()));
		jwkb.add("n", Base64.getUrlEncoder().encodeToString(pub.getModulus().toByteArray()));
		jwks = Json.createObjectBuilder().add("keys", Json.createArrayBuilder().add(jwkb)).build();
		jwtSignAlgorithm = Algorithm.RSA256(pub, (RSAPrivateKey) keyPair.getPrivate());

		issuer = IdGenerator.generateId();
		jwksUri = new URI("test:" + IdGenerator.generateId());
		authorizationEndpoint = new URI("test:" + IdGenerator.generateId());
		tokenEndpoint = new URI("test:" + IdGenerator.generateId());
		userinfoEndpoint = new URI("test:" + IdGenerator.generateId());

		config = Json.createObjectBuilder() //
				.add("authorization_endpoint", authorizationEndpoint.toString()) //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.add("userinfo_endpoint", userinfoEndpoint.toString()) //
				.add("jwks_uri", jwksUri.toString()) //
				.add("issuer", issuer) //
				.build();

		clientId = IdGenerator.generateId();
		credentials = mock(IuApiCredentials.class);
		when(credentials.getName()).thenReturn(clientId);

		idClient = mock(IuOpenIdClient.class, CALLS_REAL_METHODS);
		idTokenVerifier = new AccessTokenVerifier(issuer,
				new WellKnownKeySet(jwksUri, idClient::getTrustRefreshInterval));
		when(idClient.getCredentials()).thenReturn(credentials);
		when(idClient.getActivationInterval()).thenReturn(Duration.ofMillis(200L));
		when(idClient.getAuthenticatedSessionTimeout()).thenReturn(Duration.ofSeconds(2L));

		client = new OidcAuthorizationClient(config, idClient, idTokenVerifier);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testProperties() throws URISyntaxException {
		assertEquals(issuer, client.getRealm());
		assertEquals(authorizationEndpoint, client.getAuthorizationEndpoint());
		assertEquals(tokenEndpoint, client.getTokenEndpoint());
		client.getRedirectUri();
		verify(idClient).getRedirectUri();

		final var authAttr = client.getAuthorizationCodeAttributes();
		verify(idClient).getAuthorizationCodeAttributes();
		assertEquals(2, authAttr.size());
		assertEquals("2", authAttr.get("max_age"));
		IdGenerator.verifyId(authAttr.get("nonce"), 100L);

		when(idClient.getAuthenticatedSessionTimeout()).thenReturn(null);
		when(idClient.getAuthorizationCodeAttributes()).thenReturn(null, Map.of("foo", "bar"));
		final var authAttr2 = client.getAuthorizationCodeAttributes();
		assertEquals(1, authAttr2.size());
		IdGenerator.verifyId(authAttr2.get("nonce"), 100L);
		assertNotSame(authAttr.get("nonce"), authAttr2.get("nonce"));

		final var authAttr3 = client.getAuthorizationCodeAttributes();
		assertEquals(2, authAttr3.size());
		IdGenerator.verifyId(authAttr3.get("nonce"), 100L);
		assertEquals("bar", authAttr3.get("foo"));

		client.getAuthenticationTimeout();
		verify(idClient).getAuthenticationTimeout();
		client.getClientCredentialsAttributes();
		verify(idClient).getClientCredentialsAttributes();
		client.getCredentials();
		verify(idClient).getCredentials();
		client.getResourceUri();
		verify(idClient).getResourceUri();

		final var scope = client.getScope().iterator();
		assertEquals("openid", scope.next());
		assertFalse(scope.hasNext());

		when(idClient.getScope()).thenReturn(null, List.of("foo", "openid", "bar"));
		final var scope2 = client.getScope().iterator();
		assertEquals("openid", scope2.next());
		assertFalse(scope2.hasNext());

		final var scope3 = client.getScope().iterator();
		assertEquals("openid", scope3.next());
		assertEquals("foo", scope3.next());
		assertEquals("bar", scope3.next());
		assertFalse(scope3.hasNext());
	}

	@Test
	public void testRefreshUnsupported() {
		final var refreshResponse = mock(IuTokenResponse.class);
		final var originalResponse = mock(IuTokenResponse.class);
		assertThrows(UnsupportedOperationException.class, () -> client.verify(refreshResponse, originalResponse));
	}

	@Test
	public void testRequiresOpenId() {
		final var tokenResponse = mock(IuTokenResponse.class);
		assertThrows(IuAuthorizationFailedException.class, () -> client.verify(tokenResponse));
	}

	@Test
	public void testRequiresBearerAuth() {
		assertThrows(IllegalArgumentException.class, () -> client.activate(credentials));
	}

	@Test
	public void testSameSubPrincipalSkipsIdToken() throws Exception {
		final var accessToken = IdGenerator.generateId();
		final var tokenResponse = mock(IuTokenResponse.class);
		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		final var userinfo = Json.createObjectBuilder().add("principal", clientId).add("sub", clientId).build();
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			final var rb = mock(HttpRequest.Builder.class);
			mockHttp.when(() -> IuHttp.send(eq(userinfoEndpoint), argThat(a -> {
				a.accept(rb);
				return true;
			}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(userinfo);
			final var subject = client.verify(tokenResponse);
			verify(rb).header("Authorization", "Bearer " + accessToken);
			assertEquals(1, subject.getPrincipals().size());

			final var idPrincipal = subject.getPrincipals().iterator().next();
			assertEquals(clientId, idPrincipal.getName());
			// covers object methods
			assertNotEquals(idPrincipal, new Object());
			assertEquals(idPrincipal, idPrincipal);
			assertEquals("OIDC Principal ID [name=" + clientId + "]", idPrincipal.toString());

			final var bearer = mock(IuBearerAuthCredentials.class);
			when(bearer.getName()).thenReturn(clientId);
			when(bearer.getSubject()).thenReturn(subject);
			when(bearer.getAccessToken()).thenReturn(accessToken);
			client.activate(bearer);
			Thread.sleep(201L);
			IuTestLogger.expect("iu.auth.oidc.OidcAuthorizationClient", Level.FINER,
					"discarding invalid activation code", IllegalArgumentException.class);
			client.activate(bearer);
		}
	}

	@Test
	public void testMissingIdToken() throws Exception {
		final var accessToken = IdGenerator.generateId();
		final var tokenResponse = mock(IuTokenResponse.class);
		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		final var principal = IdGenerator.generateId();
		final var sub = IdGenerator.generateId();
		final var userinfo = Json.createObjectBuilder().add("principal", principal).add("sub", sub).build();
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.send(eq(userinfoEndpoint), any(), eq(IuHttp.READ_JSON_OBJECT)))
					.thenReturn(userinfo);
			assertThrows(IllegalStateException.class, () -> client.verify(tokenResponse));
		}
	}

	@Test
	public void testMissingIdTokenPrincipalMatchOnly() throws Exception {
		final var accessToken = IdGenerator.generateId();
		final var tokenResponse = mock(IuTokenResponse.class);
		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		final var principal = clientId;
		final var sub = IdGenerator.generateId();
		final var userinfo = Json.createObjectBuilder().add("principal", principal).add("sub", sub).build();
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.send(eq(userinfoEndpoint), any(), eq(IuHttp.READ_JSON_OBJECT)))
					.thenReturn(userinfo);
			assertThrows(IllegalStateException.class, () -> client.verify(tokenResponse));

			final var idcon = Class.forName(OidcAuthorizationClient.class.getName() + "$Id")
					.getDeclaredConstructor(OidcAuthorizationClient.class, String.class);
			idcon.setAccessible(true);
			final var id = (Principal) idcon.newInstance(client, clientId);
			final var credentials = mock(IuBearerAuthCredentials.class);
			when(credentials.getName()).thenReturn(clientId);
			when(credentials.getAccessToken()).thenReturn(accessToken);
			when(credentials.getSubject()).thenReturn(new Subject(true, Set.of(id, //
					new OidcClaim<>(clientId, "principal", clientId), //
					new OidcClaim<>(clientId, "sub", sub), //
					new OidcClaim<>(clientId, "aud", clientId), //
					new OidcClaim<>(clientId, "auth_time", Instant.now())), Set.of(), Set.of()));
			client.activate(credentials);
			Thread.sleep(idClient.getAuthenticatedSessionTimeout().toMillis() + 1L);

			IuTestLogger.expect(OidcAuthorizationClient.class.getName(), Level.FINER,
					"discarding invalid activation code", IllegalArgumentException.class);

			assertThrows(IuAuthenticationException.class, () -> client.activate(credentials));
		}
	}

	@Test
	public void testWrongAudInSession() throws Exception {
		final var accessToken = IdGenerator.generateId();
		final var tokenResponse = mock(IuTokenResponse.class);
		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		final var principal = clientId;
		final var sub = IdGenerator.generateId();
		final var userinfo = Json.createObjectBuilder().add("principal", principal).add("sub", sub).build();
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.send(eq(userinfoEndpoint), any(), eq(IuHttp.READ_JSON_OBJECT)))
					.thenReturn(userinfo);

			final var idcon = Class.forName(OidcAuthorizationClient.class.getName() + "$Id")
					.getDeclaredConstructor(OidcAuthorizationClient.class, String.class);
			idcon.setAccessible(true);
			final var id = (Principal) idcon.newInstance(client, clientId);
			final var credentials = mock(IuBearerAuthCredentials.class);
			when(credentials.getName()).thenReturn(clientId);
			when(credentials.getAccessToken()).thenReturn(accessToken);
			when(credentials.getSubject()).thenReturn(new Subject(true, Set.of(id, //
					new OidcClaim<>(clientId, "principal", clientId), //
					new OidcClaim<>(clientId, "sub", sub), //
					new OidcClaim<>(clientId, "aud", clientId), //
					new OidcClaim<>(clientId, "auth_time", Instant.now())), Set.of(), Set.of()));
			client.activate(credentials);
			client.activate(credentials);
			verify(idClient).activate(credentials);

			Thread.sleep(idClient.getAuthenticatedSessionTimeout().toMillis() + 1L);
			IuTestLogger.expect(OidcAuthorizationClient.class.getName(), Level.FINER,
					"discarding invalid activation code", IllegalArgumentException.class);

			final var credentials2 = mock(IuBearerAuthCredentials.class);
			when(credentials2.getName()).thenReturn(clientId);
			when(credentials2.getAccessToken()).thenReturn(accessToken);
			when(credentials2.getSubject()).thenReturn(new Subject(true, Set.of(id, //
					new OidcClaim<>(clientId, "principal", clientId), //
					new OidcClaim<>(clientId, "sub", sub), //
					new OidcClaim<>(clientId, "aud", IdGenerator.generateId()), //
					new OidcClaim<>(clientId, "auth_time", Instant.now())), Set.of(), Set.of()));
			assertThrows(IuAuthenticationException.class, () -> client.activate(credentials2));
		}
	}

	@Test
	public void testClaimMismatchInSession() throws Exception {
		final var accessToken = IdGenerator.generateId();
		final var tokenResponse = mock(IuTokenResponse.class);
		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		final var principal = clientId;
		final var sub = IdGenerator.generateId();
		final var userinfo = Json.createObjectBuilder().add("principal", principal).add("sub", sub).add("foo", "bar")
				.build();
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.send(eq(userinfoEndpoint), any(), eq(IuHttp.READ_JSON_OBJECT)))
					.thenReturn(userinfo);

			final var idcon = Class.forName(OidcAuthorizationClient.class.getName() + "$Id")
					.getDeclaredConstructor(OidcAuthorizationClient.class, String.class);
			idcon.setAccessible(true);
			final var id = (Principal) idcon.newInstance(client, clientId);
			final var credentials = mock(IuBearerAuthCredentials.class);
			when(credentials.getName()).thenReturn(clientId);
			when(credentials.getAccessToken()).thenReturn(accessToken);
			when(credentials.getSubject()).thenReturn(new Subject(true, Set.of(id, //
					new OidcClaim<>(clientId, "principal", clientId), //
					new OidcClaim<>(clientId, "sub", sub), //
					new OidcClaim<>(clientId, "aud", clientId), //
					new OidcClaim<>(clientId, "auth_time", Instant.now()), //
					new OidcClaim<>(clientId, "foo", "bar")), Set.of(), Set.of()));
			client.activate(credentials);
			client.activate(credentials);
			verify(idClient).activate(credentials);

			Thread.sleep(idClient.getAuthenticatedSessionTimeout().toMillis() + 1L);
			IuTestLogger.expect(OidcAuthorizationClient.class.getName(), Level.FINER,
					"discarding invalid activation code", IllegalArgumentException.class);

			final var credentials2 = mock(IuBearerAuthCredentials.class);
			when(credentials2.getName()).thenReturn(clientId);
			when(credentials2.getAccessToken()).thenReturn(accessToken);
			when(credentials2.getSubject()).thenReturn(new Subject(true, Set.of(id, //
					new OidcClaim<>(clientId, "principal", clientId), //
					new OidcClaim<>(clientId, "sub", sub), //
					new OidcClaim<>(clientId, "aud", clientId), //
					new OidcClaim<>(clientId, "auth_time", Instant.now()), //
					new OidcClaim<>(clientId, "foo", "baz")), Set.of(), Set.of()));
			assertThrows(IuAuthenticationException.class, () -> client.activate(credentials2));
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testRequiresRS256Token() throws Exception {
		final var accessToken = IdGenerator.generateId();

		final var authCodeAttributes = client.getAuthorizationCodeAttributes();
		final var nonce = authCodeAttributes.get("nonce");
		assertNotNull(nonce);

		final var tokenResponse = mock(IuTokenResponse.class);
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var sub = IdGenerator.generateId();
		final var idToken = JWT.create() //
				.withKeyId("defaultSign") //
				.withIssuer(issuer) //
				.withAudience(clientId) //
				.withSubject(sub) //
				.withIssuedAt(now) //
				.withExpiresAt(now.plusSeconds(1L)) //
				.sign(Algorithm.RSA512((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate()));

		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		when(tokenResponse.getTokenAttributes()).thenReturn((Map) Map.of("id_token", idToken));
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.parseJwks(jwks.toString())).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).then(a -> WebKey.parseJwks(jwks.toString()));
			assertThrows(IllegalArgumentException.class, () -> client.verify(tokenResponse));
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testVerifyAtHash() throws Exception {
		final var accessToken = IdGenerator.generateId();

		final var authCodeAttributes = client.getAuthorizationCodeAttributes();
		final var nonce = authCodeAttributes.get("nonce");
		assertNotNull(nonce);

		final var encodedHash = IuCrypt.sha256(IuText.utf8(accessToken));
		final var wrongAtHash = Base64.getUrlEncoder().withoutPadding().encodeToString(encodedHash);

		final var tokenResponse = mock(IuTokenResponse.class);
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var sub = IdGenerator.generateId();
		final var idToken = JWT.create() //
				.withKeyId("defaultSign") //
				.withIssuer(issuer) //
				.withAudience(clientId) //
				.withSubject(sub) //
				.withIssuedAt(now) //
				.withExpiresAt(now.plusSeconds(1L)) //
				.withClaim("at_hash", wrongAtHash) //
				.withClaim("auth_time", now) //
				.withClaim("nonce", nonce).sign(jwtSignAlgorithm);

		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		when(tokenResponse.getTokenAttributes()).thenReturn((Map) Map.of("id_token", idToken));
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.parseJwks(jwks.toString())).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).then(a -> WebKey.parseJwks(jwks.toString()));

			assertThrows(IllegalStateException.class, () -> client.verify(tokenResponse));
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testVerifyNonce() throws Exception {
		final var accessToken = IdGenerator.generateId();

		final var authCodeAttributes = client.getAuthorizationCodeAttributes();
		final var nonce = authCodeAttributes.get("nonce");
		assertNotNull(nonce);

		final var encodedHash = IuCrypt.sha256(IuText.utf8(accessToken));
		final var halfOfEncodedHash = Arrays.copyOf(encodedHash, (encodedHash.length / 2));
		final var atHash = Base64.getUrlEncoder().withoutPadding().encodeToString(halfOfEncodedHash);

		final var tokenResponse = mock(IuTokenResponse.class);
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var principal = IdGenerator.generateId();
		final var sub = IdGenerator.generateId();
		final var idToken = JWT.create() //
				.withKeyId("defaultSign") //
				.withIssuer(issuer) //
				.withAudience(clientId) //
				.withSubject(sub) //
				.withIssuedAt(now) //
				.withExpiresAt(now.plusSeconds(1L)) //
				.withClaim("at_hash", atHash) //
				.withClaim("auth_time", now) //
				.withClaim("nonce", IdGenerator.generateId()).sign(jwtSignAlgorithm);

		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		when(tokenResponse.getTokenAttributes()).thenReturn((Map) Map.of("id_token", idToken));
		final var userinfo = Json.createObjectBuilder().add("principal", principal).add("sub", sub).build();
		try (final var mockWebKey = mockStatic(WebKey.class); //
				final var mockHttp = mockStatic(IuHttp.class)) {
			mockWebKey.when(() -> WebKey.parseJwks(jwks.toString())).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).then(a -> WebKey.parseJwks(jwks.toString()));
			mockHttp.when(() -> IuHttp.send(eq(userinfoEndpoint), any(), eq(IuHttp.READ_JSON_OBJECT)))
					.thenReturn(userinfo);

			assertThrows(IllegalArgumentException.class, () -> client.verify(tokenResponse));
		}
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testAuthTimeout() throws Exception {
		final var accessToken = IdGenerator.generateId();

		final var authCodeAttributes = client.getAuthorizationCodeAttributes();
		final var nonce = authCodeAttributes.get("nonce");
		assertNotNull(nonce);

		final var encodedhash = IuCrypt.sha256(IuText.utf8(accessToken));
		final var halfOfEncodedHash = Arrays.copyOf(encodedhash, (encodedhash.length / 2));
		final var atHash = Base64.getUrlEncoder().withoutPadding().encodeToString(halfOfEncodedHash);

		final var tokenResponse = mock(IuTokenResponse.class);
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var principal = IdGenerator.generateId();
		final var sub = IdGenerator.generateId();
		final var idToken = JWT.create() //
				.withKeyId("defaultSign") //
				.withIssuer(issuer) //
				.withAudience(clientId) //
				.withSubject(sub) //
				.withIssuedAt(now) //
				.withExpiresAt(now.plusSeconds(1L)) //
				.withClaim("at_hash", atHash) //
				.withClaim("auth_time", now) //
				.withClaim("nonce", nonce).sign(jwtSignAlgorithm);

		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		when(tokenResponse.getTokenAttributes()).thenReturn((Map) Map.of("id_token", idToken));
		final var userinfo = Json.createObjectBuilder().add("principal", principal).add("sub", sub).build();
		try (final var mockWebKey = mockStatic(WebKey.class); //
				final var mockHttp = mockStatic(IuHttp.class)) {
			mockWebKey.when(() -> WebKey.parseJwks(jwks.toString())).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).then(a -> WebKey.parseJwks(jwks.toString()));
			mockHttp.when(() -> IuHttp.send(eq(userinfoEndpoint), any(), eq(IuHttp.READ_JSON_OBJECT)))
					.thenReturn(userinfo);

			Thread.sleep(2001L);
			assertEquals("Bearer realm=\"" + issuer
					+ "\" scope=\"openid\" error=\"invalid_token\" error_description=\"auth session timeout, must reauthenticate\"",
					assertThrows(IuAuthenticationException.class, () -> client.verify(tokenResponse)).getMessage());

			final var idcon = Class.forName(OidcAuthorizationClient.class.getName() + "$Id")
					.getDeclaredConstructor(OidcAuthorizationClient.class, String.class);
			idcon.setAccessible(true);
			final var id = (Principal) idcon.newInstance(client, clientId);
			Thread.sleep(201L);

			final var bearer = mock(IuBearerAuthCredentials.class);
			when(bearer.getName()).thenReturn(clientId);
			when(bearer.getSubject()).thenReturn(new Subject(true, Set.of(id, //
					new OidcClaim(clientId, "aud", clientId), //
					new OidcClaim(clientId, "auth_time", now) //
			), Set.of(), Set.of()));
			when(bearer.getAccessToken()).thenReturn(accessToken);
			IuTestLogger.expect("iu.auth.oidc.OidcAuthorizationClient", Level.FINER,
					"discarding invalid activation code", IllegalArgumentException.class);
			assertThrows(IuAuthenticationException.class, () -> client.activate(bearer));
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testValidIdToken() throws Exception {
		final var accessToken = IdGenerator.generateId();

		final var authCodeAttributes = client.getAuthorizationCodeAttributes();
		final var nonce = authCodeAttributes.get("nonce");
		assertNotNull(nonce);

		final var encodedhash = IuCrypt.sha256(IuText.utf8(accessToken));
		final var halfOfEncodedHash = Arrays.copyOf(encodedhash, (encodedhash.length / 2));
		final var atHash = Base64.getUrlEncoder().withoutPadding().encodeToString(halfOfEncodedHash);

		final var tokenResponse = mock(IuTokenResponse.class);
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var principal = IdGenerator.generateId();
		final var sub = IdGenerator.generateId();
		final var idToken = JWT.create() //
				.withKeyId("defaultSign") //
				.withIssuer(issuer) //
				.withAudience(clientId) //
				.withSubject(sub) //
				.withIssuedAt(now) //
				.withExpiresAt(now.plusSeconds(1L)) //
				.withClaim("at_hash", atHash) //
				.withClaim("auth_time", now) //
				.withClaim("nonce", nonce).sign(jwtSignAlgorithm);

		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		when(tokenResponse.getScope()).thenReturn(List.of("openid"));
		when(tokenResponse.getTokenAttributes()).thenReturn((Map) Map.of("id_token", idToken));
		final var userinfo = Json.createObjectBuilder() //
				.add("principal", principal) //
				.add("sub", sub) //
				.add("foo", "bar") //
				.add("bar", 34) //
				.build();
		try (final var mockWebKey = mockStatic(WebKey.class); //
				final var mockHttp = mockStatic(IuHttp.class)) {
			mockWebKey.when(() -> WebKey.parseJwks(jwks.toString())).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).then(a -> WebKey.parseJwks(jwks.toString()));
			final var rb = mock(HttpRequest.Builder.class);
			mockHttp.when(() -> IuHttp.send(eq(userinfoEndpoint), argThat(a -> {
				a.accept(rb);
				return true;
			}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(userinfo);

			final var subject = client.verify(tokenResponse);
			verify(rb).header("Authorization", "Bearer " + accessToken);

			final var id = subject.getPrincipals(IuPrincipalIdentity.class).iterator().next();
			assertEquals(principal, id.getName());

			IuPrincipalIdentity.verify(id, client.getRealm());

			final var principals = subject.getPrincipals();
			assertEquals(9, principals.size());
			final var principalIter = principals.iterator();
			assertEquals(principal, principalIter.next().getName());
			assertEquals(new OidcClaim<>(principal, "principal", principal), principalIter.next());
			assertEquals(new OidcClaim<>(principal, "sub", sub), principalIter.next());
			assertEquals(new OidcClaim<>(principal, "aud", clientId), principalIter.next());
			assertEquals(new OidcClaim<>(principal, "iat", now), principalIter.next());
			assertEquals(new OidcClaim<>(principal, "exp", now.plusSeconds(1L)), principalIter.next());
			assertEquals(new OidcClaim<>(principal, "auth_time", now), principalIter.next());
			assertEquals(new OidcClaim<>(principal, "foo", "bar"), principalIter.next());
			assertEquals(new OidcClaim<>(principal, "bar", "34"), principalIter.next());
			assertFalse(principalIter.hasNext());

			final var bearer = mock(IuBearerAuthCredentials.class);
			when(bearer.getName()).thenReturn(principal);
			when(bearer.getSubject()).thenReturn(subject);
			when(bearer.getAccessToken()).thenReturn(accessToken);
			client.activate(bearer);
			Thread.sleep(201L);
			IuTestLogger.expect("iu.auth.oidc.OidcAuthorizationClient", Level.FINER,
					"discarding invalid activation code", IllegalArgumentException.class);
			client.activate(bearer);
		}
	}

	@Test
	public void testWrongClient() throws Exception {
		final var issuer = IdGenerator.generateId();
		final var config = Json.createObjectBuilder() //
				.add("authorization_endpoint", authorizationEndpoint.toString()) //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.add("userinfo_endpoint", userinfoEndpoint.toString()) //
				.add("jwks_uri", jwksUri.toString()) //
				.add("issuer", issuer) //
				.build();
		final var idTokenVerifier = new AccessTokenVerifier(issuer,
				new WellKnownKeySet(jwksUri, idClient::getTrustRefreshInterval));

		final var client = new OidcAuthorizationClient(config, idClient, idTokenVerifier);
		final var idcon = Class.forName(OidcAuthorizationClient.class.getName() + "$Id")
				.getDeclaredConstructor(OidcAuthorizationClient.class, String.class);
		idcon.setAccessible(true);
		final var id = (IuPrincipalIdentity) idcon.newInstance(this.client, clientId);
		assertThrows(IllegalArgumentException.class, () -> IuPrincipalIdentity.verify(id, client.getRealm()));
	}
}
