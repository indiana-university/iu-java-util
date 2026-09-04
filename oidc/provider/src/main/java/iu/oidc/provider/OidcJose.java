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
package iu.oidc.provider;

import java.util.Objects;

import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebSignature;

/**
 * Signs and encrypts claims an OpenID Provider has already serialized.
 *
 * <p>
 * Takes the serialization and returns a JOSE compact serialization, so what a
 * provider publishes is secured the same way whatever the claims are: an ID
 * token and a UserInfo response differ in which claims they carry and in the
 * {@code typ} they declare, not in how they are signed or encrypted. Anything
 * else the provider issues as a JWT goes the same way.
 * </p>
 *
 * <p>
 * Deliberately not aware of what it is securing. The serialization is opaque
 * here, which is what keeps the claim model typed and JSON-free everywhere
 * above this: whatever renders claims decides how they print, and this decides
 * only how the bytes are protected. Signing is over exact bytes, so this has to
 * be the step after rendering and cannot be folded into it.
 * </p>
 *
 * <h2>Algorithms come from the keys</h2>
 *
 * <p>
 * The signature algorithm is the issuer key's own, and the key management
 * algorithm the audience key's own, rather than parameters of their own. A
 * provider selects an issuer key <em>by</em> algorithm from the keys it
 * publishes, so passing both would allow a pair that disagree &mdash; a token
 * signed with an algorithm no verifier would find the key for.
 * </p>
 *
 * <h2>What the header carries</h2>
 *
 * <p>
 * The signature header names the algorithm, the {@code typ}, and the issuer
 * key's {@code kid} when it has one. It does <em>not</em> carry the certificate
 * thumbprint or chain that {@link edu.iu.jwt.WebToken#sign(String,
 * edu.iu.crypt.WebKey.Algorithm, WebKey) WebToken.sign} adds: a relying party
 * verifies what this provider issues against the JWK Set the discovery document
 * publishes, resolving the key by {@code kid}, so a chain in the header names a
 * trust path nothing here asks anyone to follow. It also avoids emitting the
 * SHA-1 thumbprint that carrying one would require.
 * </p>
 */
public final class OidcJose {

	/**
	 * Signs serialized claims.
	 *
	 * @param serializedClaims claims, as whatever rendered them serialized them
	 * @param type             {@code typ} header value, naming what the claims are
	 * @param issuerKey        provider's own signing key, whose
	 *                         {@link WebKey#getAlgorithm() algorithm} signs
	 * @return JWS compact serialization
	 * @throws NullPointerException if the issuer key declares no algorithm
	 */
	public static String sign(String serializedClaims, String type, WebKey issuerKey) {
		final var algorithm = Objects.requireNonNull(issuerKey.getAlgorithm(), "Missing issuer key algorithm");
		final var builder = WebSignature.builder(algorithm).compact().key(issuerKey).type(type);

		final var keyId = issuerKey.getKeyId();
		if (keyId != null)
			builder.keyId(keyId);

		return builder.sign(serializedClaims).compact();
	}

	/**
	 * Signs serialized claims, then encrypts the signature to one audience.
	 *
	 * <p>
	 * Sign-then-encrypt, in that order: the signature is what the audience
	 * authenticates the claims by, so it has to be inside the encryption rather
	 * than over it. The encryption header declares a {@code cty} of {@code type},
	 * which is how the audience knows the plaintext is itself a JOSE object rather
	 * than the claims.
	 * </p>
	 *
	 * @param serializedClaims claims, as whatever rendered them serialized them
	 * @param type             {@code typ} of the signature and {@code cty} of the
	 *                         encryption, naming what the claims are
	 * @param issuerKey        provider's own signing key, whose
	 *                         {@link WebKey#getAlgorithm() algorithm} signs
	 * @param audienceKey      audience's public key, whose
	 *                         {@link WebKey#getAlgorithm() algorithm} manages the
	 *                         content encryption key
	 * @param encryption       content {@link Encryption}
	 * @return JWE compact serialization
	 * @throws NullPointerException if either key declares no algorithm
	 */
	public static String signAndEncrypt(String serializedClaims, String type, WebKey issuerKey, WebKey audienceKey,
			Encryption encryption) {
		final var algorithm = Objects.requireNonNull(audienceKey.getAlgorithm(), "Missing audience key algorithm");
		final var recipient = WebEncryption.builder(encryption).compact().addRecipient(algorithm).key(audienceKey)
				.contentType(type);

		final var keyId = audienceKey.getKeyId();
		if (keyId != null)
			recipient.keyId(keyId);

		return recipient.encrypt(sign(serializedClaims, type, issuerKey)).compact();
	}

	private OidcJose() {
	}

}
