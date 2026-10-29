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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuBadRequestException;
import edu.iu.IuText;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class GrantStoreTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	private static final URI ISSUER = URI.create("https://example.iu.edu/oidc");
	private static final Duration TTL = Duration.ofMinutes(1L);

	private final MemoryDataStore data = new MemoryDataStore();
	private final GrantStore store = new GrantStore(data);
	private final WebKey issuerKey = WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).ephemeral()
			.build();

	/** Answers a grant carrying the detail an authorization endpoint records. */
	private static OidcGrant grant(String principalName) {
		return grant(principalName, null);
	}

	/** Answers a grant belonging to one line of descent. */
	private static OidcGrant grant(String principalName, String family) {
		final var grant = mock(OidcGrant.class);
		when(grant.getPrincipalName()).thenReturn(principalName);
		when(grant.getClientId()).thenReturn("some-client");
		when(grant.getScope()).thenReturn("openid profile");
		when(grant.getRedirectUri()).thenReturn(URI.create("https://client.example.iu.edu/callback"));
		when(grant.getFamily()).thenReturn(family);
		return grant;
	}

	private String put(String type, OidcGrant grant) {
		return store.put(type, ISSUER, issuerKey, TTL, grant);
	}

	private OidcGrant take(String type, String reference) {
		// every redemption decrypts, which the crypt implementation narrates at FINE
		IuTestLogger.allow("iu.crypt", Level.FINE);

		// a grant built directly, rather than by the authorization endpoint, carries no
		// family and says so; the tests that do give one expect the record instead
		IuTestLogger.allow(GrantStore.class.getName(), Level.FINE, "grant-nofamily:.*");

		return store.take(type, ISSUER, issuerKey, reference, TTL);
	}

	@Test
	void testADataStoreIsRequired() {
		assertEquals("Missing data store",
				assertThrows(NullPointerException.class, () -> new GrantStore(null)).getMessage());
	}

	@Test
	void testATimeToLiveIsRequired() {
		final var grant = grant("someone");
		assertEquals("Missing grant+jwt token time to live", assertThrows(NullPointerException.class,
				() -> store.put(GrantStore.CODE, ISSUER, issuerKey, null, grant)).getMessage());
	}

	@Test
	void testTheReferenceRedeemsTheGrant() {
		final var reference = put(GrantStore.CODE, grant("someone"));

		final var redeemed = take(GrantStore.CODE, reference);
		assertEquals("someone", redeemed.getPrincipalName());
		assertEquals("some-client", redeemed.getClientId());
		assertEquals("openid profile", redeemed.getScope());
		assertEquals(URI.create("https://client.example.iu.edu/callback"), redeemed.getRedirectUri());
		assertNull(redeemed.getNonce());
	}

	@Test
	void testTheStoreHoldsSomethingItCannotRead() {
		final var reference = put(GrantStore.CODE, grant("someone"));

		// the entry is filed under a digest of the reference, not the reference, and
		// what it holds is encrypted to the key the reference carries
		final var filed = data.entries.keySet().iterator().next();
		assertNotEquals(reference, filed);
		assertEquals(1, data.entries.size());
	}

	@Test
	void testAReferenceIsGoodOnce() {
		final var reference = put(GrantStore.CODE, grant("someone"));
		take(GrantStore.CODE, reference);

		// a second presentation is a replay, not an expiry: the entry is still there,
		// as a tombstone, so the two are told apart rather than collapsed
		IuTestLogger.expect(GrantStore.class.getName(), Level.WARNING, "grant-reject:replayed:grant\\+jwt");
		assertEquals("invalid_grant; Replayed grant+jwt reference",
				assertThrows(IuBadRequestException.class, () -> take(GrantStore.CODE, reference)).getMessage());
	}

	@Test
	void testAReplayRevokesEveryReferenceInTheLine() {
		// what rotation alone cannot do: refusing the replay leaves whatever the
		// attacker rotated to still live, so the line itself has to be revoked
		final var family = IdGenerator.generateId();
		final var code = put(GrantStore.CODE, grant("someone", family));
		final var refresh = put(GrantStore.REFRESH, grant("someone", family));

		assertEquals("someone", take(GrantStore.CODE, code).getPrincipalName());

		IuTestLogger.expect(GrantStore.class.getName(), Level.WARNING, "grant-reject:replayed:grant\\+jwt:" + family);
		IuTestLogger.expect(GrantStore.class.getName(), Level.WARNING, "grant-revoke:" + family);
		assertEquals("invalid_grant; Replayed grant+jwt reference",
				assertThrows(IuBadRequestException.class, () -> take(GrantStore.CODE, code)).getMessage());

		// the refresh token descending from that same authorization goes with it,
		// though it was never itself presented twice
		IuTestLogger.expect(GrantStore.class.getName(), Level.WARNING, "grant-reject:revoked:refresh\\+jwt:" + family);
		assertEquals("invalid_grant; Revoked refresh+jwt reference",
				assertThrows(IuBadRequestException.class, () -> take(GrantStore.REFRESH, refresh)).getMessage());
	}

	@Test
	void testAReplayOfAnUnreadableReferenceRevokesNothing() {
		// spent before it was decrypted, so no family was ever learned; the replay is
		// still refused, but there is no line to name
		final var elsewhere = WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).ephemeral().build();
		final var reference = put(GrantStore.CODE, grant("someone", IdGenerator.generateId()));

		IuTestLogger.expect(GrantStore.class.getName(), Level.WARNING, "grant-reject:unverified:grant\\+jwt",
				IllegalArgumentException.class);
		IuTestLogger.allow("iu.crypt", Level.FINE);
		assertThrows(IuBadRequestException.class,
				() -> store.take(GrantStore.CODE, ISSUER, elsewhere, reference, TTL));

		IuTestLogger.expect(GrantStore.class.getName(), Level.WARNING, "grant-reject:replayed:grant\\+jwt");
		assertEquals("invalid_grant; Replayed grant+jwt reference",
				assertThrows(IuBadRequestException.class, () -> take(GrantStore.CODE, reference)).getMessage());
	}

	@Test
	void testAnEmptyEntryIsNotMistakenForATombstone() {
		// a tombstone is a leading NUL, so an entry with no bytes at all -- which this
		// class never writes, and only a corrupted store would hold -- must not read as
		// one. Treating it as spent would report a corrupt store as a replay and revoke
		// a line over it
		final var reference = put(GrantStore.CODE, grant("someone"));
		data.entries.replaceAll((key, value) -> new byte[0]);

		// refused as unverifiable rather than as a replay, which is the distinction:
		// the entry was read, it just was not a tombstone
		IuTestLogger.allow(GrantStore.class.getName(), Level.WARNING, "grant-reject:unverified:.*");
		assertEquals("invalid_grant; Unverified grant+jwt reference",
				assertThrows(IuBadRequestException.class, () -> take(GrantStore.CODE, reference)).getMessage());
	}

	@Test
	void testAGrantWithNoFamilyStillRedeems() {
		// written before a family was carried: nothing to revoke it as part of, but it
		// is still a grant this provider issued
		final var reference = put(GrantStore.CODE, grant("someone"));
		assertEquals("someone", take(GrantStore.CODE, reference).getPrincipalName());
	}

	@Test
	void testARefreshTokenIsNotACode() {
		// filed under a digest of the type as well as the key, so presenting one where
		// the other is expected resolves to no entry at all
		final var reference = put(GrantStore.REFRESH, grant("someone"));

		IuTestLogger.expect(GrantStore.class.getName(), Level.INFO, "grant-reject:unknown:grant\\+jwt");
		assertThrows(IuBadRequestException.class, () -> take(GrantStore.CODE, reference));

		// and presenting it under the wrong type spends nothing, so the refresh token
		// it actually is still redeems
		assertEquals("someone", take(GrantStore.REFRESH, reference).getPrincipalName());
	}

	@Test
	void testAMissingReferenceIsRefused() {
		IuTestLogger.expect(GrantStore.class.getName(), Level.INFO, "grant-reject:malformed:grant\\+jwt",
				NullPointerException.class);
		assertEquals("invalid_grant; Malformed grant+jwt reference",
				assertThrows(IuBadRequestException.class, () -> take(GrantStore.CODE, null)).getMessage());
	}

	@Test
	void testAMalformedReferenceIsRefused() {
		IuTestLogger.expect(GrantStore.class.getName(), Level.INFO, "grant-reject:malformed:grant\\+jwt",
				IllegalArgumentException.class);
		assertEquals("invalid_grant; Malformed grant+jwt reference",
				assertThrows(IuBadRequestException.class, () -> take(GrantStore.CODE, "not base 64 url!")).getMessage());
	}

	@Test
	void testAnEntryThisProviderDidntSignIsRefused() {
		// the entry resolves, so reading it is not the question: what fails is whether
		// this provider issued what it holds
		final var reference = put(GrantStore.CODE, grant("someone"));
		final var elsewhere = WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).ephemeral().build();

		IuTestLogger.expect(GrantStore.class.getName(), Level.WARNING, "grant-reject:unverified:grant\\+jwt",
				IllegalArgumentException.class);
		IuTestLogger.allow("iu.crypt", Level.FINE);
		assertEquals("invalid_grant; Unverified grant+jwt reference", assertThrows(IuBadRequestException.class,
				() -> store.take(GrantStore.CODE, ISSUER, elsewhere, reference, TTL)).getMessage());

		// and it was spent by being presented, so it can't be retried: the entry is a
		// tombstone now, and presenting the same reference again reads as a replay
		IuTestLogger.expect(GrantStore.class.getName(), Level.WARNING, "grant-reject:replayed:grant\\+jwt");
		assertEquals("invalid_grant; Replayed grant+jwt reference", assertThrows(IuBadRequestException.class,
				() -> store.take(GrantStore.CODE, ISSUER, elsewhere, reference, TTL)).getMessage());
	}

	@Test
	void testTheReferenceIsTheContentEncryptionKey() {
		final var reference = put(GrantStore.CODE, grant("someone"));
		// 256 bits of A256GCM content encryption key, base64url encoded
		assertArrayEquals(new byte[32], new byte[IuText.base64Url(reference).length]);
	}

}
