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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuBadRequestException;
import edu.iu.IuDataStore;
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

	/** Stands in for whatever store a deployment binds. */
	private static final class MemoryDataStore implements IuDataStore {

		private final Map<String, byte[]> entries = new LinkedHashMap<>();

		@Override
		public Iterable<?> list() {
			return entries.keySet();
		}

		@Override
		public byte[] get(byte[] key) {
			return entries.get(IuText.base64Url(key));
		}

		@Override
		public void put(byte[] key, byte[] data) {
			if (data == null)
				entries.remove(IuText.base64Url(key));
			else
				entries.put(IuText.base64Url(key), data);
		}

		@Override
		public void put(byte[] key, byte[] value, Duration ttl) {
			put(key, value);
		}
	}

	private final MemoryDataStore data = new MemoryDataStore();
	private final GrantStore store = new GrantStore(data);
	private final WebKey issuerKey = WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).ephemeral()
			.build();

	/** Answers a grant carrying the detail an authorization endpoint records. */
	private static OidcGrant grant(String principalName) {
		final var grant = mock(OidcGrant.class);
		when(grant.getPrincipalName()).thenReturn(principalName);
		when(grant.getClientId()).thenReturn("some-client");
		when(grant.getScope()).thenReturn("openid profile");
		when(grant.getRedirectUri()).thenReturn(URI.create("https://client.example.iu.edu/callback"));
		return grant;
	}

	private String put(String type, OidcGrant grant) {
		return store.put(type, ISSUER, issuerKey, TTL, grant);
	}

	private OidcGrant take(String type, String reference) {
		// every redemption decrypts, which the crypt implementation narrates at FINE
		IuTestLogger.allow("iu.crypt", Level.FINE);
		return store.take(type, ISSUER, issuerKey, reference);
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

		IuTestLogger.expect(GrantStore.class.getName(), Level.INFO, "grant-reject:unknown:grant\\+jwt");
		assertEquals("invalid_grant; Unknown or expired grant+jwt reference",
				assertThrows(IuBadRequestException.class, () -> take(GrantStore.CODE, reference)).getMessage());
	}

	@Test
	void testARefreshTokenIsNotACode() {
		// filed under a digest of the type as well as the key, so presenting one where
		// the other is expected resolves to no entry at all
		final var reference = put(GrantStore.REFRESH, grant("someone"));

		IuTestLogger.expect(GrantStore.class.getName(), Level.INFO, "grant-reject:unknown:grant\\+jwt");
		assertThrows(IuBadRequestException.class, () -> take(GrantStore.CODE, reference));

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
				() -> store.take(GrantStore.CODE, ISSUER, elsewhere, reference)).getMessage());

		// and it was spent by being presented, so it can't be retried
		assertEquals(0, data.entries.size());
	}

	@Test
	void testTheReferenceIsTheContentEncryptionKey() {
		final var reference = put(GrantStore.CODE, grant("someone"));
		// 256 bits of A256GCM content encryption key, base64url encoded
		assertArrayEquals(new byte[32], new byte[IuText.base64Url(reference).length]);
	}

}
