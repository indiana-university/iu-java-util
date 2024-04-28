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
import static org.mockito.Mockito.mockStatic;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature;

@SuppressWarnings({ "javadoc", "deprecation" })
public class AccessTokenVerifierTest {

	@Test
	public void testAccessTokens() throws Exception {
		assertAccessToken("RS256");
		assertAccessToken("RS384");
		assertAccessToken("RS512");
		assertAccessToken("ES256");
		assertAccessToken("ES384");
		assertAccessToken("ES512");
		assertAccessToken("PS256");
	}

	private void assertAccessToken(String algorithm) throws Exception {
		final var iss = IdGenerator.generateId();
		final var aud = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var iat = Instant.now();
		final var exp = iat.plus(Duration.ofSeconds(5L));

		final var jwk = WebKey.builder(WebKey.Algorithm.from(algorithm)).use(Use.SIGN).keyId("defaultSign").ephemeral()
				.build();
		final Iterable<? extends WebKey> jwks = Set.of(jwk);

		final String accessToken;
//
//		if (algorithm.startsWith("RS")) {
//			final var jwtSignAlgorithm = (Algorithm) Algorithm.class
//					.getMethod("RSA" + algorithm.substring(2), RSAPublicKey.class, RSAPrivateKey.class)
//					.invoke(null, jwk.getPublicKey(), jwk.getPrivateKey());
//			accessToken = JWT.create().withKeyId("defaultSign").withIssuer(iss).withAudience(aud).withIssuedAt(iat)
//					.withExpiresAt(exp).withClaim("nonce", nonce).sign(jwtSignAlgorithm);
//		} else if (algorithm.startsWith("ES")) {
//			final var jwtSignAlgorithm = (Algorithm) Algorithm.class
//					.getMethod("ECDSA" + algorithm.substring(2), ECPublicKey.class, ECPrivateKey.class)
//					.invoke(null, jwk.getPublicKey(), jwk.getPrivateKey());
//			accessToken = JWT.create().withKeyId("defaultSign").withIssuer(iss).withAudience(aud).withIssuedAt(iat)
//					.withExpiresAt(exp).withClaim("nonce", nonce).sign(jwtSignAlgorithm);
//		} else
		accessToken = WebSignature.builder(jwk.getAlgorithm()).key(jwk).keyId("defaultSign").compact()
				.sign(IuJson.object().add("iss", iss).add("aud", IuJson.array().add(aud))
						.add("iat", iat.getEpochSecond()).add("exp", iat.getEpochSecond()).add("nonce", nonce).build()
						.toString())
				.compact();

		final var uri = URI.create("test://localhost/" + IdGenerator.generateId());
		final var verifier = new AccessTokenVerifier(uri, iss);

		try (final var mockWeb = mockStatic(WebKey.class)) {
			mockWeb.when(() -> WebKey.readJwks(uri)).thenReturn(jwks);
			assertEquals(nonce, verifier.verify(aud, accessToken).getClaim("nonce").asString());
		}
	}

}
