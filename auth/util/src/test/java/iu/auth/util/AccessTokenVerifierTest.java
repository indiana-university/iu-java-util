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
package iu.auth.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.security.AlgorithmParameters;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class AccessTokenVerifierTest {

	@Test
	public void testAlgorithmKey() {
		final var k1 = new AccessTokenVerifier.AlgorithmKey("foo", "bar");
		assertTrue(k1.hashCode() != 0);
		assertNotEquals(k1, new Object());
		assertEquals(k1, k1);
		final var k2 = new AccessTokenVerifier.AlgorithmKey("bar", "foo");
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
		final var k3 = new AccessTokenVerifier.AlgorithmKey("bar", "bar");
		assertNotEquals(k3, k2);
		assertNotEquals(k2, k3);
		final var k4 = new AccessTokenVerifier.AlgorithmKey("foo", "foo");
		assertNotEquals(k4, k2);
		assertNotEquals(k2, k4);
	}

	@Test
	public void testAccessTokens() throws Exception {
		assertAccessToken("RS256");
		assertAccessToken("RS384");
		assertAccessToken("RS512");
		assertAccessToken("ES256");
		assertAccessToken("ES384");
		assertAccessToken("ES512");
	}

	@Test
	public void testInvalidJwk() throws Exception {
		final var iss = IdGenerator.generateId();
		final var aud = IdGenerator.generateId();
		final var jwks = Json.createObjectBuilder().build();

		try (final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var uri = mock(URI.class);
			mockHttpUtils.when(() -> HttpUtils.read(uri)).thenReturn(jwks);

			final var verifier = new AccessTokenVerifier(uri, iss, () -> Duration.ofMillis(100L));
			assertThrows(IllegalStateException.class,
					() -> verifier.verify(aud, JWT.create().withKeyId("defaultSign").sign(Algorithm.none())));
		}
	}

	@Test
	public void testUnsupportedAlgorithm() throws Exception {
		final var iss = IdGenerator.generateId();
		final var aud = IdGenerator.generateId();
		final var jwks = Json.createObjectBuilder()
				.add("keys", Json.createArrayBuilder().add(Json.createObjectBuilder().add("kid", "defaultSign")))
				.build();

		try (final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var uri = mock(URI.class);
			mockHttpUtils.when(() -> HttpUtils.read(uri)).thenReturn(jwks);

			final var verifier = new AccessTokenVerifier(uri, iss, () -> Duration.ofMillis(100L));
			final var e = assertThrows(IllegalStateException.class,
					() -> verifier.verify(aud, JWT.create().withKeyId("defaultSign").sign(Algorithm.none())));
			assertInstanceOf(UnsupportedOperationException.class, e.getCause());
		}
	}

	@Test
	public void testInvalidKid() throws Exception {
		final var iss = IdGenerator.generateId();
		final var aud = IdGenerator.generateId();
		final var jwks = Json.createObjectBuilder()
				.add("keys", Json.createArrayBuilder().add(Json.createObjectBuilder().add("kid", "defaultSign")))
				.build();

		try (final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var uri = mock(URI.class);
			mockHttpUtils.when(() -> HttpUtils.read(uri)).thenReturn(jwks);

			final var verifier = new AccessTokenVerifier(uri, iss, () -> Duration.ofMillis(100L));
			assertThrows(IllegalStateException.class,
					() -> verifier.verify(aud, JWT.create().withKeyId("").sign(Algorithm.none())));
		}
	}

	@Test
	public void testInvalidECJWK() {
		assertThrows(IllegalArgumentException.class,
				() -> AccessTokenVerifier.toECPublicKey(Json.createObjectBuilder().add("kty", "").build()));
	}

	@Test
	public void testInvalidRSAJWK() {
		assertThrows(IllegalArgumentException.class,
				() -> AccessTokenVerifier.toRSAPublicKey(Json.createObjectBuilder().add("kty", "").build()));
	}

	@Test
	public void testECParameterSpec() throws NoSuchAlgorithmException, InvalidParameterSpecException {
		assertECParameterSpec("P-256", "secp256r1");
		assertECParameterSpec("P-384", "secp384r1");
		assertECParameterSpec("P-521", "secp521r1");
		assertThrows(IllegalArgumentException.class,
				() -> AccessTokenVerifier.getECParameterSpec(Json.createObjectBuilder().add("crv", "").build()));
	}

	private void assertECParameterSpec(String crv, String stdName)
			throws NoSuchAlgorithmException, InvalidParameterSpecException {
		class Box {
			ECGenParameterSpec spec;
		}
		final var box = new Box();
		try (final var mockAlgorithmParameters = mockStatic(AlgorithmParameters.class); //
				final var a = mockConstruction(ECGenParameterSpec.class, (spec, ctx) -> {
					assertEquals(stdName, ctx.arguments().get(0));
					box.spec = spec;
				})) {
			final var algParams = mock(AlgorithmParameters.class);
			mockAlgorithmParameters.when(() -> AlgorithmParameters.getInstance("EC")).thenReturn(algParams);

			AccessTokenVerifier.getECParameterSpec(Json.createObjectBuilder().add("crv", crv).build());
			verify(algParams).init(box.spec);
			verify(algParams).getParameterSpec(ECParameterSpec.class);
		}
	}

	private void assertAccessToken(String algorithm) throws Exception {
		final var iss = IdGenerator.generateId();
		final var aud = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var iat = Instant.now();
		final var exp = iat.plus(Duration.ofSeconds(5l));
		final JsonObject jwks;
		final String accessToken;
		if (algorithm.startsWith("RS")) {
			final var rsaKeygen = KeyPairGenerator.getInstance("RSA");
			rsaKeygen.initialize(1024);
			final var keyPair = rsaKeygen.generateKeyPair();
			final var pub = (RSAPublicKey) keyPair.getPublic();
			final var jwkb = Json.createObjectBuilder();
			jwkb.add("kty", "RSA");
			jwkb.add("use", "sig");
			jwkb.add("kid", "defaultSign");
			jwkb.add("e", Base64.getUrlEncoder().encodeToString(pub.getPublicExponent().toByteArray()));
			jwkb.add("n", Base64.getUrlEncoder().encodeToString(pub.getModulus().toByteArray()));
			jwks = Json.createObjectBuilder().add("keys", Json.createArrayBuilder().add(jwkb)).build();

			final var jwtSignAlgorithm = (Algorithm) Algorithm.class
					.getMethod("RSA" + algorithm.substring(2), RSAPublicKey.class, RSAPrivateKey.class)
					.invoke(null, pub, keyPair.getPrivate());
			accessToken = JWT.create().withKeyId("defaultSign").withIssuer(iss).withAudience(aud).withIssuedAt(iat)
					.withExpiresAt(exp).withClaim("nonce", nonce).sign(jwtSignAlgorithm);
		} else if (algorithm.startsWith("ES")) {
			final var ecKeygen = KeyPairGenerator.getInstance("EC");
			ecKeygen.initialize(new ECGenParameterSpec("secp256r1"));
			final var keyPair = ecKeygen.generateKeyPair();
			final var pub = (ECPublicKey) keyPair.getPublic();
			final var jwkb = Json.createObjectBuilder();
			jwkb.add("kty", "EC");
			jwkb.add("use", "sig");
			jwkb.add("kid", "defaultSign");
			jwkb.add("crv", "P-256");
			jwkb.add("x", Base64.getUrlEncoder().encodeToString(pub.getW().getAffineX().toByteArray()));
			jwkb.add("y", Base64.getUrlEncoder().encodeToString(pub.getW().getAffineY().toByteArray()));
			jwks = Json.createObjectBuilder().add("keys", Json.createArrayBuilder().add(jwkb)).build();

			final var jwtSignAlgorithm = (Algorithm) Algorithm.class
					.getMethod("ECDSA" + algorithm.substring(2), ECPublicKey.class, ECPrivateKey.class)
					.invoke(null, pub, keyPair.getPrivate());
			accessToken = JWT.create().withKeyId("defaultSign").withIssuer(iss).withAudience(aud).withIssuedAt(iat)
					.withExpiresAt(exp).withClaim("nonce", nonce).sign(jwtSignAlgorithm);
		} else
			throw new AssertionFailedError();

		try (final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var uri = mock(URI.class);
			mockHttpUtils.when(() -> HttpUtils.read(uri)).thenReturn(jwks);

			final var verifier = new AccessTokenVerifier(uri, iss, () -> Duration.ofMillis(99L));

			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
			// verify 2nd call uses cached keys
			mockHttpUtils.verify(() -> HttpUtils.read(uri));
			Thread.sleep(100L);

			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
			// verify cache refresh
			mockHttpUtils.verify(() -> HttpUtils.read(uri), times(2));

			mockHttpUtils.when(() -> HttpUtils.read(uri)).thenThrow(RuntimeException.class);
			Thread.sleep(100L);

			IuTestLogger.expect("iu.auth.util.AccessTokenVerifier", Level.INFO,
					"JWT Algorithm initialization failure;.*", RuntimeException.class);
			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
		}
	}

}
