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
package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.auth.oauth.OAuthClient;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class OidcIdTokenTest {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
		IuTestLogger.allow("iu.crypt", Level.FINE);
	}

	@Test
	public void testVerifySignedToken() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var authTime = iat;
		final var exp = iat.plusSeconds(900L);
		final var clientId = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var role = IdGenerator.generateId();
		final var name = IdGenerator.generateId();
		final var email = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var atHash = IuException.unchecked(() -> IuText.base64Url(
				Arrays.copyOfRange(MessageDigest.getInstance("SHA-512").digest(IuText.ascii(accessToken)), 0, 32)));

		final var claims = IuJson.object() //
				.add("iss", iss.toString()) //
				.add("sub", sub) //
				.add("aud", aud.toString()) //
				.add("iat", iat.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.add("azp", clientId) //
				.add("nonce", nonce) //
				.add("roles", IuJson.array().add(role)) //
				.add("auth_time", authTime.getEpochSecond()) //
				.add("at_hash", atHash) //
				.add("name", name) //
				.add("email", email) //
				.build();

		final var key = WebKey.ephemeral(Algorithm.EDDSA);
		final var idToken = WebSignature.builder(Algorithm.EDDSA) //
				.compact() //
				.key(key) //
				.sign(IuText.utf8(claims.toString())) //
				.compact();

		final var client = mock(OAuthClient.class);
		when(client.getClientId()).thenReturn(clientId);
		final var maxAge = Duration.ofHours(12L);
		final var ttl = Duration.ofMinutes(15L);

		final var verified = OidcIdToken.verify(idToken, key, client, nonce, accessToken, maxAge);
		assertDoesNotThrow(() -> verified.validateClaims(aud, ttl));
		assertEquals(accessToken, verified.getAccessToken());
		assertEquals(name, verified.getFullName());
		assertEquals(email, verified.getEmail());
		assertEquals(role, verified.getRoles().iterator().next());

		final var bearer = verified.getBearerToken();
		assertEquals(sub, bearer.getName());
		assertEquals(iss.toString(), bearer.getIssuer());
		assertEquals(iat, bearer.getIssuedAt());
		assertEquals(authTime, bearer.getAuthTime());
		assertEquals(exp, bearer.getExpires());
		final var rb = mock(HttpRequest.Builder.class);
		assertDoesNotThrow(() -> bearer.applyTo(rb));
		verify(rb).header("Authorization", "Bearer " + accessToken);
	}

	@Test
	public void testDecryptAndVerifyMinimalToken() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var exp = iat.plusSeconds(900L);

		final var claims = IuJson.object() //
				.add("iss", iss.toString()) //
				.add("sub", sub) //
				.add("aud", aud.toString()) //
				.add("iat", iat.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.build();

		final var key = WebKey.ephemeral(Algorithm.EDDSA);
		final var idToken = WebSignature.builder(Algorithm.EDDSA) //
				.compact() //
				.key(key) //
				.sign(IuText.utf8(claims.toString())) //
				.compact();

		final var audKey = WebKey.ephemeral(Algorithm.ECDH_ES_A256KW);
		final var encryptedIdToken = WebEncryption.builder(Encryption.A256GCM) //
				.compact() //
				.addRecipient(Algorithm.ECDH_ES_A256KW) //
				.key(audKey) //
				.encrypt(idToken) //
				.compact();

		final var ttl = Duration.ofMinutes(15L);

		final var verified = OidcIdToken.decryptAndVerify(encryptedIdToken, key, audKey, null, null, null, null);
		assertDoesNotThrow(() -> verified.validateClaims(aud, ttl));
	}

	@Test
	public void testTokenWithUnexpectedNonce() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var exp = iat.plusSeconds(900L);
		final var nonce = IdGenerator.generateId();

		final var claims = IuJson.object() //
				.add("iss", iss.toString()) //
				.add("sub", sub) //
				.add("aud", aud.toString()) //
				.add("iat", iat.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.add("nonce", nonce) //
				.build();

		final var key = WebKey.ephemeral(Algorithm.EDDSA);
		final var idToken = WebSignature.builder(Algorithm.EDDSA) //
				.compact() //
				.key(key) //
				.sign(IuText.utf8(claims.toString())) //
				.compact();

		final var ttl = Duration.ofMinutes(15L);
		final var verified = OidcIdToken.verify(idToken, key, null, null, null, null);
		final var err = assertThrows(IllegalArgumentException.class, () -> verified.validateClaims(aud, ttl));
		assertEquals("Unexpected nonce claim", err.getMessage());
	}

	@Test
	public void testTokenWithMissingNonce() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var exp = iat.plusSeconds(900L);
		final var nonce = IdGenerator.generateId();

		final var claims = IuJson.object() //
				.add("iss", iss.toString()) //
				.add("sub", sub) //
				.add("aud", aud.toString()) //
				.add("iat", iat.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.build();

		final var key = WebKey.ephemeral(Algorithm.EDDSA);
		final var idToken = WebSignature.builder(Algorithm.EDDSA) //
				.compact() //
				.key(key) //
				.sign(IuText.utf8(claims.toString())) //
				.compact();

		final var ttl = Duration.ofMinutes(15L);
		final var verified = OidcIdToken.verify(idToken, key, null, nonce, null, null);
		final var err = assertThrows(IllegalArgumentException.class, () -> verified.validateClaims(aud, ttl));
		assertEquals("Expected nonce claim", err.getMessage());
	}

	@Test
	public void testTokenWithExpiredAuth() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var authTime = iat.minus(Duration.ofHours(13L));
		final var exp = iat.plusSeconds(900L);

		final var claims = IuJson.object() //
				.add("iss", iss.toString()) //
				.add("sub", sub) //
				.add("aud", aud.toString()) //
				.add("iat", iat.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.add("auth_time", authTime.getEpochSecond()) //
				.build();

		final var key = WebKey.ephemeral(Algorithm.EDDSA);
		final var idToken = WebSignature.builder(Algorithm.EDDSA) //
				.compact() //
				.key(key) //
				.sign(IuText.utf8(claims.toString())) //
				.compact();

		final var maxAge = Duration.ofHours(12L);
		final var ttl = Duration.ofMinutes(15L);
		final var verified = OidcIdToken.verify(idToken, key, null, null, null, maxAge);
		final var err = assertThrows(IllegalArgumentException.class, () -> verified.validateClaims(aud, ttl));
		assertEquals("Authenticated session lifetime PT13H exceeds maximum PT12H", err.getMessage());
	}

	@Test
	public void testTokenWithInvalidAccessToken() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var exp = iat.plusSeconds(900L);
		final var accessToken = IdGenerator.generateId();
		final var fakeAtHash = IdGenerator.generateId();

		final var claims = IuJson.object() //
				.add("iss", iss.toString()) //
				.add("sub", sub) //
				.add("aud", aud.toString()) //
				.add("iat", iat.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.add("at_hash", fakeAtHash) //
				.build();

		final var key = WebKey.ephemeral(Algorithm.EDDSA);
		final var idToken = WebSignature.builder(Algorithm.EDDSA) //
				.compact() //
				.key(key) //
				.sign(IuText.utf8(claims.toString())) //
				.compact();

		final var ttl = Duration.ofMinutes(15L);
		final var verified = OidcIdToken.verify(idToken, key, null, null, accessToken, null);
		final var err = assertThrows(IllegalArgumentException.class, () -> verified.validateClaims(aud, ttl));
		assertEquals("at_hash mismatch", err.getMessage());
	}

	@Test
	public void testTokenWithAuthDetails() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var exp = iat.plusSeconds(900L);
		final var authType = IdGenerator.generateId();
		final var a = IdGenerator.generateId();
		final var authDetails = IuJson.object() //
				.add("type", authType) //
				.add("a", a) //
				.build();

		final var claims = IuJson.object() //
				.add("iss", iss.toString()) //
				.add("sub", sub) //
				.add("aud", aud.toString()) //
				.add("iat", iat.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.add("authorization_details", IuJson.array().add(authDetails)) //
				.build();

		final var key = WebKey.ephemeral(Algorithm.EDDSA);
		final var idToken = WebSignature.builder(Algorithm.EDDSA) //
				.compact() //
				.key(key) //
				.sign(IuText.utf8(claims.toString())) //
				.compact();

		final var ttl = Duration.ofMinutes(15L);
		final var verified = OidcIdToken.verify(idToken, key, null, null, null, null);
		assertDoesNotThrow(() -> verified.validateClaims(aud, ttl));
	}

}
