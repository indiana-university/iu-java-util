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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuBadRequestException;
import edu.iu.IuDataStore;
import edu.iu.IuDigest;
import edu.iu.IuText;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.jwt.WebToken;

/**
 * Hands a grant from one endpoint of this provider to another, behind an opaque
 * reference the client carries.
 *
 * <p>
 * An authorization code and a refresh token are both references of this kind.
 * The grant is signed with an issuer key and encrypted to a content encryption
 * key generated for that one reference, then written to a data store. The
 * reference handed to the client <em>is</em> that content encryption key.
 * </p>
 *
 * <p>
 * That arrangement is the point: the store holds a token it cannot read, filed
 * under a digest that cannot be reversed into the key that would read it.
 * Whoever holds the reference can find the entry and decrypt it; reading the
 * store is not enough. The signature is what tells the reading endpoint the
 * grant is one this provider issued, and not one written into the store by
 * something else.
 * </p>
 *
 * <p>
 * The wrapped token names this provider as both issuer and audience, rather
 * than the client's redirect URI: unlike an access or ID token, a reference of
 * this kind is never shared outside the provider &mdash; the client only ever
 * carries the opaque reference, and only {@link #take} ever reads what it
 * wraps. There is nothing external for it to be addressed to.
 * </p>
 *
 * <p>
 * Each kind of reference is filed under a digest of its {@link #put type} as
 * well as its key, so a refresh token cannot be presented where a code is
 * expected: it doesn't merely fail to verify, it doesn't resolve to an entry at
 * all. {@link #take} deletes the entry it read, so a reference is good once.
 * </p>
 *
 * <h2>Getting one</h2>
 *
 * <p>
 * The data store arrives through the constructor rather than by injection, so
 * this class has no opinion about how a deployment obtains one and nothing here
 * depends on a container. A deployment binds the store where its container
 * binds resources &mdash; typically the same store it keeps authenticated
 * sessions in, rather than a second connection to the same server &mdash; and
 * constructs one of these once the store is available, sharing it between the
 * authorization and token endpoints so the two cannot drift apart on how a
 * reference is filed. Nothing here is stateful, since every reference carries
 * its own key, so sharing costs nothing.
 * </p>
 */
public final class GrantStore {

	private static final Logger LOG = Logger.getLogger(GrantStore.class.getName());

	/** {@code typ} of the token an authorization code refers to. */
	public static final String CODE = "grant+jwt";

	/** {@code typ} of the token a refresh token refers to. */
	public static final String REFRESH = "refresh+jwt";

	/** Content encryption every reference's own key is generated for. */
	private static final Encryption ENCRYPTION = Encryption.A256GCM;

	/**
	 * Files the digest a reference of one kind resolves through.
	 *
	 * <p>
	 * The type is digested along with the key, so the same key presented as a
	 * different kind of reference resolves to nothing.
	 * </p>
	 *
	 * @param type      reference type
	 * @param secretKey content encryption key the reference carries
	 * @return store key
	 */
	private static byte[] storeKey(String type, byte[] secretKey) {
		final var typeBytes = IuText.utf8(type);
		final var keyed = new byte[typeBytes.length + secretKey.length];
		System.arraycopy(typeBytes, 0, keyed, 0, typeBytes.length);
		System.arraycopy(secretKey, 0, keyed, typeBytes.length, secretKey.length);
		return IuDigest.sha256(keyed);
	}

	private final IuDataStore store;

	/**
	 * Creates a grant store over one data store.
	 *
	 * @param store store references are filed in
	 */
	public GrantStore(IuDataStore store) {
		this.store = Objects.requireNonNull(store, "Missing data store");
	}

	/**
	 * Writes a grant and answers the reference that redeems it.
	 *
	 * @param type      reference type, either {@link #CODE} or {@link #REFRESH}
	 * @param issuer    this provider's issuer identifier, named as both issuer and
	 *                  audience since the wrapped token is never read by anything
	 *                  but this provider
	 * @param issuerKey key the grant is signed with
	 * @param ttl       how long the reference remains redeemable, which is also how
	 *                  long the entry lives, so a grant that is never redeemed
	 *                  isn't left behind for the store to accumulate
	 * @param grant     grant to hand over
	 * @return opaque reference
	 */
	public String put(String type, URI issuer, WebKey issuerKey, Duration ttl, OidcGrant grant) {
		Objects.requireNonNull(ttl, "Missing " + type + " token time to live");

		// declares DIRECT so the reference's own key is what encrypts the content,
		// rather than wrapping a second key the reference would also have to carry
		final var secretKey = WebKey.builder(Algorithm.DIRECT).ephemeral(ENCRYPTION).build();

		final var claims = WebToken.builder() //
				.jti() //
				.iss(issuer) //
				.sub(grant.getPrincipalName()) //
				.aud(issuer) //
				.iat() //
				.exp(Instant.now().plus(ttl)) //
				.claim("grant", grant, OidcGrant.class) //
				.build() //
				.toString();

		final var token = OidcJose.signAndEncrypt(claims, type, issuerKey, secretKey, ENCRYPTION);

		final var key = secretKey.getKey();
		store.put(storeKey(type, key), IuText.utf8(token), ttl);

		return IuText.base64Url(key);
	}

	/**
	 * Reads the grant a reference redeems, and spends the reference.
	 *
	 * <p>
	 * The entry is deleted before the token is verified, so a reference is spent by
	 * being presented rather than by being accepted. A malformed or unverifiable
	 * token cannot be retried against the same entry.
	 * </p>
	 *
	 * @param type      reference type, either {@link #CODE} or {@link #REFRESH}
	 * @param issuer    this provider's issuer identifier, which the token must name
	 *                  as both issuer and audience
	 * @param issuerKey key the grant was signed with
	 * @param reference reference presented by the client
	 * @return redeemed grant
	 * @throws IuBadRequestException if the reference resolves to no entry, or the
	 *                               token it names doesn't verify against this
	 *                               provider's issuer, audience, and expiry
	 */
	public OidcGrant take(String type, URI issuer, WebKey issuerKey, String reference) {
		final byte[] secretKey;
		try {
			secretKey = IuText.base64Url(Objects.requireNonNull(reference));
		} catch (Exception e) {
			LOG.log(Level.INFO, e, () -> "grant-reject:malformed:" + type);
			throw new IuBadRequestException("invalid_grant; Malformed " + type + " reference");
		}

		final var key = storeKey(type, secretKey);
		final var stored = store.get(key);
		if (stored == null) {
			LOG.info(() -> "grant-reject:unknown:" + type);
			throw new IuBadRequestException("invalid_grant; Unknown or expired " + type + " reference");
		}

		// spent by being presented, so an unverifiable token can't be retried
		store.put(key, null);

		final WebToken token;
		try {
			token = WebToken.decryptAndVerify(IuText.utf8(stored), issuerKey,
					WebKey.builder(Type.RAW).key(secretKey).build());
			token.validateClaims(issuer, issuer, Duration.between(token.getIssuedAt(), token.getExpires()));
		} catch (Exception e) {
			// the entry was ours to read, so a failure here is a tampered or misdirected
			// token rather than an unlucky guess
			LOG.log(Level.WARNING, e, () -> "grant-reject:unverified:" + type);
			throw new IuBadRequestException("invalid_grant; Unverified " + type + " reference");
		}

		return token.getClaim("grant", OidcGrant.class);
	}

}
