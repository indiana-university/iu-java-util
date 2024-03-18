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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.io.StringReader;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import edu.iu.IdGenerator;
import edu.iu.auth.session.IuSessionProviderKey;
import edu.iu.auth.session.IuSessionProviderKey.Type;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class AccessTokenVerifierTest {

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

			final var verifier = new AccessTokenVerifier(iss, new WellKnownKeySet(uri, () -> Duration.ofMillis(100L)));
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

			final var verifier = new AccessTokenVerifier(iss, new WellKnownKeySet(uri, () -> Duration.ofMillis(100L)));
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

			final var verifier = new AccessTokenVerifier(iss, new WellKnownKeySet(uri, () -> Duration.ofMillis(100L)));
			assertThrows(IllegalStateException.class,
					() -> verifier.verify(aud, JWT.create().withKeyId("").sign(Algorithm.none())));
		}
	}

	private void assertAccessToken(String algorithm) throws Exception {
		final var iss = IdGenerator.generateId();
		final var aud = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var iat = Instant.now();
		final var exp = iat.plus(Duration.ofSeconds(5l));

		final Type type;
		final KeyPair keyPair;
		if (algorithm.startsWith("RS")) {
			final var rsaKeygen = KeyPairGenerator.getInstance("RSA");
			rsaKeygen.initialize(1024);
			keyPair = rsaKeygen.generateKeyPair();
			type = Type.RSA;
		} else if (algorithm.startsWith("ES")) {
			final var ecKeygen = KeyPairGenerator.getInstance("EC");
			ecKeygen.initialize(new ECGenParameterSpec("secp256r1"));
			keyPair = ecKeygen.generateKeyPair();
			type = Type.EC_P256;
		} else
			throw new AssertionFailedError();

		final var issuerKey = new IuSessionProviderKey() {
			@Override
			public String getId() {
				return "defaultSign";
			}

			@Override
			public Usage getUsage() {
				return Usage.SIGN;
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public PublicKey getPublic() {
				return keyPair.getPublic();
			}

			@Override
			public PrivateKey getPrivate() {
				return keyPair.getPrivate();
			}
		};

		final var issuerKeySet = new TokenIssuerKeySet(List.of(issuerKey));
		final var jwtAlgorithm = issuerKeySet.getAlgorithm("defaultSign", algorithm);
		final var accessToken = JWT.create().withKeyId("defaultSign").withIssuer(iss).withAudience(aud)
				.withIssuedAt(iat).withExpiresAt(exp).withClaim("nonce", nonce).sign(jwtAlgorithm);

		try (final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var uri = mock(URI.class);
			mockHttpUtils.when(() -> HttpUtils.read(uri))
					.thenReturn(Json.createReader(new StringReader(issuerKeySet.publish())).readObject());

			final var verifier = new AccessTokenVerifier(iss, new WellKnownKeySet(uri, () -> Duration.ofMillis(99L)));

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

			IuTestLogger.expect("iu.auth.util.WellKnownKeySet", Level.INFO, "JWT Algorithm initialization failure;.*",
					RuntimeException.class);
			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
		}
	}

}
