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

import java.net.URI;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import edu.iu.IuIterable;
import edu.iu.crypt.WebKey;

/**
 * Verifies JWT access tokens as signed using an RSA or ECDSA public key from a
 * well-known JWKS key set.
 * 
 * @deprecated TODO
 */
public class AccessTokenVerifier {

	private final URI keysetUri;
	private final String issuer;

	/**
	 * Constructor.
	 * 
	 * @param keysetUri       JWKS URL
	 * @param issuer          expected issuer
	 * @param refreshInterval max time to reuse a configured {@link Algorithm}
	 *                        before refreshing from the JWKS URL.
	 */
	public AccessTokenVerifier(URI keysetUri, String issuer) {
		this.keysetUri = keysetUri;
		this.issuer = issuer;
	}

	/**
	 * Verifies a JWT access token.
	 * 
	 * <p>
	 * Verifies:
	 * </p>
	 * <ul>
	 * <li>The use of a strong signature algorithm: RSA or ECDSA</li>
	 * <li>The RSA or ECDSA signature is valid</li>
	 * <li>The iss claim matches the configured issuer</li>
	 * <li>The aud claim includes the audience</li>
	 * </ul>
	 * 
	 * @param audience expected audience claim
	 * @param token    JWT access token
	 * @return Parsed JWT, can be used to perform additional verification
	 */
	@SuppressWarnings("exports")
	public DecodedJWT verify(String audience, String token) {
		final var decoded = JWT.decode(token);
		final var kid = decoded.getKeyId();
		final var alg = decoded.getAlgorithm();
		final var verifier = JWT //
				.require(getAlgorithm(kid, alg)) //
				.withIssuer(issuer) //
				.withAudience(audience) //
				.withClaimPresence("iat") //
				.withClaimPresence("exp") //
				.acceptLeeway(15L) //
				.build();

		return verifier.verify(token);
	}

	private Algorithm getAlgorithm(String kid, String alg) {
		final var jwk = IuIterable.filter(WebKey.readJwks(keysetUri), a -> kid.equals(a.getKeyId())).iterator().next();
		switch (alg) {
		case "ES256":
			return Algorithm.ECDSA256((ECPublicKey) jwk.getPublicKey());
		case "ES384":
			return Algorithm.ECDSA384((ECPublicKey) jwk.getPublicKey());
		case "ES512":
			return Algorithm.ECDSA512((ECPublicKey) jwk.getPublicKey());
		case "RS256":
			return Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
		case "RS384":
			return Algorithm.RSA384((RSAPublicKey) jwk.getPublicKey(), null);
		case "RS512":
			return Algorithm.RSA512((RSAPublicKey) jwk.getPublicKey(), null);
		default:
			throw new UnsupportedOperationException("Unsupported JWT algorithm " + alg);
		}
	}

}
