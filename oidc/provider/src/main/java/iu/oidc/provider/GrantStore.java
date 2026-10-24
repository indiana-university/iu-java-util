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
import java.util.Arrays;
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
 * all. {@link #take} replaces the entry it read with a tombstone, so a
 * reference is good once.
 * </p>
 *
 * <h2>A replay revokes the line, not just the reference</h2>
 *
 * <p>
 * The tombstone is what makes a second presentation legible. Deleting the entry
 * would make a replay indistinguishable from an expiry, and that difference
 * matters: a reference presented twice means someone other than the party it was
 * issued to is holding it. Every reference descending from one authorization
 * &mdash; the code, and each refresh token rotated out of it &mdash; shares a
 * {@link OidcGrant#getFamily() family}, and a replay revokes the family rather
 * than only the reference replayed. Without that, rotation refuses the
 * presentation an attacker loses the race on while leaving the one they won
 * still live, and the legitimate client is locked out with no way to tell that
 * from expiry.
 * </p>
 *
 * <p>
 * A tombstone is written before the grant is decrypted, so a reference stays
 * spent whether or not it verified, and is named with its family only once the
 * grant has been read &mdash; the family rides inside the ciphertext, which a
 * replay never gets far enough to reach. A grant recorded before families were
 * carried reads {@code null} and is redeemed normally; there is simply no line
 * to revoke it as part of.
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
	 * Marks a store entry as a spent reference rather than a stored grant.
	 *
	 * <p>
	 * A grant is always a compact JWS inside a JWE, so it is text and never begins
	 * with a NUL. That is what lets one entry carry either without a second read to
	 * tell which it is.
	 * </p>
	 */
	private static final byte[] SPENT = new byte[] { 0 };

	/** Prefix distinguishing a family revocation key from a reference. */
	private static final String REVOKED = "revoked";

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

	/**
	 * Files the revocation record for one family.
	 *
	 * <p>
	 * Digested like a reference key, and prefixed so it can never collide with one:
	 * a family identifier is not a content encryption key and must not be able to
	 * pass for the digest of one.
	 * </p>
	 *
	 * @param family family identifier
	 * @return store key
	 */
	private static byte[] revokedKey(String family) {
		return IuDigest.sha256(IuText.utf8(REVOKED + ' ' + family));
	}

	/**
	 * Builds the tombstone naming the family a spent reference belonged to.
	 *
	 * @param family family identifier
	 * @return tombstone value
	 */
	private static byte[] spentMarker(String family) {
		final var familyBytes = IuText.utf8(family);
		final var marker = new byte[SPENT.length + familyBytes.length];
		System.arraycopy(SPENT, 0, marker, 0, SPENT.length);
		System.arraycopy(familyBytes, 0, marker, SPENT.length, familyBytes.length);
		return marker;
	}

	/**
	 * Determines whether a store entry is a tombstone rather than a stored grant.
	 *
	 * @param stored entry read from the store
	 * @return true if the reference has already been presented; else false
	 */
	private static boolean isSpent(byte[] stored) {
		return stored.length >= SPENT.length //
				&& stored[0] == SPENT[0];
	}

	/**
	 * Reads the family a tombstone names.
	 *
	 * @param stored tombstone read from the store
	 * @return family identifier; {@code null} when the reference was spent before
	 *         its grant could be read, so no family was ever learned
	 */
	private static String spentFamily(byte[] stored) {
		if (stored.length <= SPENT.length)
			return null;

		return IuText.utf8(Arrays.copyOfRange(stored, SPENT.length, stored.length));
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
	 * The entry is overwritten with a tombstone before the token is verified, so a
	 * reference is spent by being presented rather than by being accepted. A
	 * malformed or unverifiable token cannot be retried against the same entry.
	 * The read of what was stored and the write of the tombstone are one atomic
	 * {@link IuDataStore#getAndPut store operation}, so two concurrent
	 * presentations of the same reference can never both read a live grant before
	 * either writes the tombstone &mdash; the second always reads the tombstone
	 * the first just wrote, and only the first ever redeems.
	 * </p>
	 *
	 * <p>
	 * A tombstone rather than a deletion, because the two outcomes it distinguishes
	 * are not the same: a reference that resolves to nothing has expired or was
	 * never issued, while one that resolves to a tombstone <em>has been presented
	 * before</em> &mdash; so someone other than the party it was issued to is
	 * holding it. That second case revokes every reference descending from the same
	 * authorization, which is the point: rotation alone refuses the replay while
	 * leaving whatever the attacker rotated to still live.
	 * </p>
	 *
	 * @param type      reference type, either {@link #CODE} or {@link #REFRESH}
	 * @param issuer    this provider's issuer identifier, which the token must name
	 *                  as both issuer and audience
	 * @param issuerKey    key the grant was signed with
	 * @param reference    reference presented by the client
	 * @param tombstoneTtl how long a spent reference is remembered, which bounds
	 *                     the window a replay is still detectable in; at least as
	 *                     long as the longest-lived reference a deployment issues
	 * @return redeemed grant
	 * @throws IuBadRequestException if the reference resolves to no entry, has
	 *                               already been presented, belongs to a revoked
	 *                               family, or the token it names doesn't verify
	 *                               against this provider's issuer, audience, and
	 *                               expiry
	 */
	public OidcGrant take(String type, URI issuer, WebKey issuerKey, String reference, Duration tombstoneTtl) {
		final byte[] secretKey;
		try {
			secretKey = IuText.base64Url(Objects.requireNonNull(reference));
		} catch (Exception e) {
			LOG.log(Level.INFO, e, () -> "grant-reject:malformed:" + type);
			throw new IuBadRequestException("invalid_grant; Malformed " + type + " reference");
		}

		final var key = storeKey(type, secretKey);

		// spent by being presented, so an unverifiable token can't be retried, and
		// atomically so two concurrent presentations can't both read the grant before
		// either writes the tombstone. Written as a tombstone rather than deleted so
		// the replay check below is distinguishable from an expiry, and before
		// verification so that stays true either way
		final var stored = store.getAndPut(key, SPENT, tombstoneTtl);
		if (stored == null) {
			LOG.info(() -> "grant-reject:unknown:" + type);
			throw new IuBadRequestException("invalid_grant; Unknown or expired " + type + " reference");
		}

		// A reference presented twice is a replay: whoever holds it is not the only
		// party that does. Revoking the line it belongs to is what keeps the attacker
		// from keeping the token they rotated to -- refusing this presentation alone
		// would leave them holding a live one and the legitimate client locked out.
		// A racing presentation that lost the getAndPut above reads the tombstone this
		// one just wrote and lands here too, which is exactly the point: only one of
		// the two ever redeems, and the other is refused rather than silently ignored
		if (isSpent(stored)) {
			final var family = spentFamily(stored);
			LOG.warning(() -> "grant-reject:replayed:" + type + (family == null ? "" : ":" + family));

			if (family != null)
				revoke(family, tombstoneTtl);

			throw new IuBadRequestException("invalid_grant; Replayed " + type + " reference");
		}

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

		final var grant = token.getClaim("grant", OidcGrant.class);

		final var family = grant.getFamily();
		if (family == null)
			// recorded before a family was carried; nothing to revoke it as part of
			LOG.fine(() -> "grant-nofamily:" + type);
		else {
			// named on the tombstone so a later replay of this same reference knows what
			// to revoke -- the family is inside the ciphertext, which a replay never
			// gets far enough to read
			store.put(key, spentMarker(family), tombstoneTtl);

			if (store.get(revokedKey(family)) != null) {
				LOG.warning(() -> "grant-reject:revoked:" + type + ":" + family);
				throw new IuBadRequestException("invalid_grant; Revoked " + type + " reference");
			}
		}

		return grant;
	}

	/**
	 * Revokes every reference descending from one authorization.
	 *
	 * @param family family identifier
	 */
	private void revoke(String family, Duration tombstoneTtl) {
		LOG.warning(() -> "grant-revoke:" + family);
		store.put(revokedKey(family), SPENT, tombstoneTtl);
	}

}
