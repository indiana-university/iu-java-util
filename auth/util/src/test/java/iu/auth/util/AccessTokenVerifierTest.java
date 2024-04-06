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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import edu.iu.IdGenerator;
import edu.iu.client.IuHttp;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class AccessTokenVerifierTest extends IuAuthUtilTestCase {

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

		try (final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.get(ROOT_URI, IuHttp.READ_JSON_OBJECT)).thenReturn(jwks);

			final var verifier = new AccessTokenVerifier(iss,
					new WellKnownKeySet(ROOT_URI, () -> Duration.ofMillis(100L)));
			assertThrows(IllegalStateException.class,
					() -> verifier.verify(aud, JWT.create().withKeyId("defaultSign").sign(Algorithm.none())));
		}
	}

	@Test
	public void testUnsupportedAlgorithm() throws Exception {
		final var iss = IdGenerator.generateId();
		final var aud = IdGenerator.generateId();
		final var jwks = WebKey.parseJwks(Json.createObjectBuilder()
				.add("keys", Json.createArrayBuilder()
						.add(Json.createObjectBuilder().add("kid", "defaultSign").add("kty", "oct").add("k", "abcd")))
				.build().toString());

		try (final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.get(eq(ROOT_URI), any())).thenReturn(jwks);

			final var verifier = new AccessTokenVerifier(iss,
					new WellKnownKeySet(ROOT_URI, () -> Duration.ofMillis(100L)));
			final var e = assertThrows(IllegalStateException.class,
					() -> verifier.verify(aud, JWT.create().withKeyId("defaultSign").sign(Algorithm.none())));
			assertInstanceOf(UnsupportedOperationException.class, e.getCause());
		}
	}

	@Test
	public void testInvalidKid() throws Exception {
		final var iss = IdGenerator.generateId();
		final var aud = IdGenerator.generateId();
		final var jwks = WebKey.parseJwks(Json.createObjectBuilder()
				.add("keys", Json.createArrayBuilder()
						.add(Json.createObjectBuilder().add("kid", "defaultSign").add("kty", "oct").add("k", "abcd")))
				.build().toString());

		try (final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.get(eq(ROOT_URI), any())).thenReturn(jwks);

			final var verifier = new AccessTokenVerifier(iss,
					new WellKnownKeySet(ROOT_URI, () -> Duration.ofMillis(100L)));
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

		final var issuerKey = WebKey.builder().keyId("defaultSign").use(Use.SIGN).type(type).pair(keyPair).build();

		final var issuerKeySet = new TokenIssuerKeySet(Set.of(issuerKey));
		final var jwtAlgorithm = issuerKeySet.getAlgorithm("defaultSign", algorithm);
		final var accessToken = JWT.create().withKeyId("defaultSign").withIssuer(iss).withAudience(aud)
				.withIssuedAt(iat).withExpiresAt(exp).withClaim("nonce", nonce).sign(jwtAlgorithm);

		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.readJwks(ROOT_URI)).then(a -> Stream.of(issuerKey.wellKnown()));

			final var verifier = new AccessTokenVerifier(iss,
					new WellKnownKeySet(ROOT_URI, () -> Duration.ofMillis(99L)));

			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
			// verify 2nd call uses cached keys
			mockWebKey.verify(() -> WebKey.readJwks(ROOT_URI));
			Thread.sleep(100L);

			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
			// verify cache refresh
			mockWebKey.verify(() -> WebKey.readJwks(ROOT_URI), times(2));

			mockWebKey.when(() -> WebKey.readJwks(ROOT_URI)).thenThrow(RuntimeException.class);
			Thread.sleep(100L);

			IuTestLogger.expect("iu.auth.util.WellKnownKeySet", Level.INFO, "JWT Algorithm initialization failure;.*",
					RuntimeException.class);
			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
		}
	}

}
