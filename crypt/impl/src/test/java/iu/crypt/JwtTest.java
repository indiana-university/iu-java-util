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
package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;

import javax.crypto.AEADBadTagException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.test.IuTestLogger;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class JwtTest {

	@Test
	public void testNumericDate() {
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var jnow = Jwt.NUMERIC_DATE.toJson(now);
		assertEquals(now.getEpochSecond(), assertInstanceOf(JsonNumber.class, jnow).longValue());
		assertEquals(now, Jwt.NUMERIC_DATE.fromJson(IuJson.number(now.getEpochSecond())));
	}

	@Test
	public void testNullProperties() {
		final var jwt = new Jwt(IuJson.object().build());
		assertNull(jwt.getTokenId());
		assertNull(jwt.getIssuer());
		assertNull(jwt.getSubjectName());
		assertNull(jwt.getAudience());
		assertNull(jwt.getIssuedAt());
		assertNull(jwt.getNotBefore());
		assertNull(jwt.getExpires());
		assertNull(jwt.getNonce());
		assertFalse(jwt.isExpired());
	}

	@Test
	public void testNonNullProperties() {
		final var tokenId = IdGenerator.generateId();
		final var issuer = URI.create(IdGenerator.generateId());
		final var subject = IdGenerator.generateId();
		final var audience = URI.create(IdGenerator.generateId());
		final var issuedAt = Instant.now();
		final var notBefore = issuedAt.minusSeconds(30L);
		final var expires = issuedAt.plusSeconds(30L);
		final var nonce = IdGenerator.generateId();

		final var jwt = new Jwt(IuJson.object() //
				.add("jti", tokenId) //
				.add("iss", issuer.toString()) //
				.add("sub", subject) //
				.add("aud", IuJson.array().add(audience.toString()).build()) //
				.add("iat", issuedAt.getEpochSecond()) //
				.add("nbf", notBefore.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("nonce", nonce) //
				.build());

		assertEquals(tokenId, jwt.getTokenId());
		assertEquals(issuer, jwt.getIssuer());
		assertEquals(subject, jwt.getSubjectName());
		assertTrue(IuIterable.remaindersAreEqual(IuIterable.iter(audience).iterator(), jwt.getAudience().iterator()));
		assertEquals(issuedAt.truncatedTo(ChronoUnit.SECONDS), jwt.getIssuedAt());
		assertEquals(notBefore.truncatedTo(ChronoUnit.SECONDS), jwt.getNotBefore());
		assertEquals(expires.truncatedTo(ChronoUnit.SECONDS), jwt.getExpires());
		assertEquals(nonce, jwt.getNonce());
		assertFalse(jwt.isExpired());
	}

	@Test
	public void testIssueAt() {
		final var issuedAt = Instant.now().plusSeconds(30L);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> new Jwt(IuJson.object().add("iat", issuedAt.getEpochSecond()).build()));
		assertEquals("Token iat claim must be no more than PT15S in the future", error.getMessage());
	}

	@Test
	public void testExpired() {
		final var expires = Instant.now().minusSeconds(30L);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> new Jwt(IuJson.object().add("exp", expires.getEpochSecond()).build()));
		assertEquals("Token is expired", error.getMessage());
	}

	@Test
	public void testNotBefore() {
		final var notBefore = Instant.now().plusSeconds(30L);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> new Jwt(IuJson.object().add("nbf", notBefore.getEpochSecond()).build()));
		assertEquals("Token nbf claim must be no more than PT15S in the future", error.getMessage());
	}

	@Test
	public void testHashCodeEquals() {
		final var claims1 = mock(JsonObject.class);
		final var jwt1 = new Jwt(claims1);
		assertEquals(jwt1.hashCode(), claims1.hashCode());
		assertEquals(jwt1, jwt1);
		assertNotEquals(jwt1, new Object());

		final var claims2 = mock(JsonObject.class);
		final var jwt2 = new Jwt(claims2);
		assertNotEquals(jwt1, jwt2);
		assertNotEquals(jwt2, jwt1);
	}

	@Test
	public void testToString() {
		final var tokenId = IdGenerator.generateId();
		final var issuer = URI.create(IdGenerator.generateId());
		final var subject = IdGenerator.generateId();
		final var audience = URI.create(IdGenerator.generateId());
		final var issuedAt = Instant.now();
		final var notBefore = issuedAt.minusSeconds(30L);
		final var expires = issuedAt.plusSeconds(30L);
		final var nonce = IdGenerator.generateId();

		final var claims = IuJson.object() //
				.add("jti", tokenId) //
				.add("iss", issuer.toString()) //
				.add("sub", subject) //
				.add("aud", IuJson.array().add(audience.toString()).build()) //
				.add("iat", issuedAt.getEpochSecond()) //
				.add("nbf", notBefore.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("nonce", nonce).build();

		final var jwt = new Jwt(claims);
		assertNotEquals(jwt.toString(), claims.toString()); // pretty-printed
		assertEquals(claims, IuJson.parse(jwt.toString()));
	}

	@Test
	public void testSignAndVerify() {
		final var tokenId = IdGenerator.generateId();
		final var issuer = URI.create(IdGenerator.generateId());
		final var subject = IdGenerator.generateId();
		final var audience = URI.create(IdGenerator.generateId());
		final var issuedAt = Instant.now();
		final var notBefore = issuedAt.minusSeconds(30L);
		final var expires = issuedAt.plusSeconds(30L);
		final var nonce = IdGenerator.generateId();

		final var jwt = new Jwt(IuJson.object() //
				.add("jti", tokenId) //
				.add("iss", issuer.toString()) //
				.add("sub", subject) //
				.add("aud", IuJson.array().add(audience.toString()).build()) //
				.add("iat", issuedAt.getEpochSecond()) //
				.add("nbf", notBefore.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("nonce", nonce).build());
		final var issuerKey = WebKey.ephemeral(Algorithm.ES256);
		final var signed = jwt.sign("JWT", Algorithm.ES256, issuerKey);
		assertEquals(jwt, new Jwt(Jwt.verify(signed, issuerKey)));

		final var error = assertThrows(IllegalArgumentException.class,
				() -> Jwt.verify(signed, WebKey.ephemeral(Algorithm.ES256)));
		assertEquals("SHA256withECDSA verification failed", error.getMessage());
	}

	@Test
	public void testSignEncryptDecryptAndVerify() {
		final var tokenId = IdGenerator.generateId();
		final var issuer = URI.create(IdGenerator.generateId());
		final var subject = IdGenerator.generateId();
		final var audience = URI.create(IdGenerator.generateId());
		final var issuedAt = Instant.now();
		final var notBefore = issuedAt.minusSeconds(30L);
		final var expires = issuedAt.plusSeconds(30L);
		final var nonce = IdGenerator.generateId();

		final var jwt = new Jwt(IuJson.object() //
				.add("jti", tokenId) //
				.add("iss", issuer.toString()) //
				.add("sub", subject) //
				.add("aud", IuJson.array().add(audience.toString()).build()) //
				.add("iat", issuedAt.getEpochSecond()) //
				.add("nbf", notBefore.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("nonce", nonce).build());
		final var issuerKey = WebKey.ephemeral(Algorithm.ES256);
		final var audienceKey = WebKey.builder(Type.X25519).ephemeral(Algorithm.ECDH_ES).build();
		final var signed = jwt.signAndEncrypt("JWT", Algorithm.ES256, issuerKey, Algorithm.ECDH_ES, Encryption.A128GCM,
				audienceKey);

		IuTestLogger.allow(Jwe.class.getName(), Level.FINE);
		assertEquals(jwt, new Jwt(Jwt.decryptAndVerify(signed, issuerKey, audienceKey)));

		final var decryptError = assertThrows(IllegalStateException.class, () -> Jwt.decryptAndVerify(signed,
				WebKey.ephemeral(Algorithm.ES256), WebKey.builder(Type.X25519).ephemeral(Algorithm.ECDH_ES).build()));
		assertInstanceOf(AEADBadTagException.class, decryptError.getCause());

		final var error = assertThrows(IllegalArgumentException.class,
				() -> Jwt.decryptAndVerify(signed, WebKey.ephemeral(Algorithm.ES256), audienceKey));
		assertEquals("SHA256withECDSA verification failed", error.getMessage());
	}

	@Test
	public void testValidateMissingIssuer() {
		final var jwt = new Jwt(IuJson.object().build());
		final var error = assertThrows(NullPointerException.class, () -> jwt.validateClaims(null, null));
		assertEquals("Missing iss claim", error.getMessage());
	}

	@Test
	public void testValidateMissingSubject() {
		final var jwt = new Jwt(IuJson.object() //
				.add("iss", IdGenerator.generateId()) //
				.build());
		final var error = assertThrows(NullPointerException.class, () -> jwt.validateClaims(null, null));
		assertEquals("Missing sub claim", error.getMessage());
	}

	@Test
	public void testValidateMissingAudience() {
		final var jwt = new Jwt(IuJson.object() //
				.add("iss", IdGenerator.generateId()) //
				.add("sub", IdGenerator.generateId()) //
				.build());
		final var error = assertThrows(NullPointerException.class, () -> jwt.validateClaims(null, null));
		assertEquals("Missing aud claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsAudMismatch() {
		final var jwt = new Jwt(IuJson.object() //
				.add("iss", IdGenerator.generateId()) //
				.add("sub", IdGenerator.generateId()) //
				.add("aud", IdGenerator.generateId()) //
				.build());
		final var expectedAudience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(IllegalArgumentException.class,
				() -> jwt.validateClaims(expectedAudience, Duration.ofMinutes(2L)));
		assertEquals("Token aud claim doesn't include " + expectedAudience, error.getMessage());
	}

	@Test
	public void testValidateClaimsMissingIssuedAt() {
		final var audience = URI.create(IdGenerator.generateId());
		final var jwt = new Jwt(IuJson.object() //
				.add("iss", IdGenerator.generateId()) //
				.add("sub", IdGenerator.generateId()) //
				.add("aud", audience.toString()) //
				.build());
		final var error = assertThrows(NullPointerException.class,
				() -> jwt.validateClaims(audience, Duration.ofMinutes(2L)));
		assertEquals("Missing iat claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsMissingExpires() {
		final var audience = URI.create(IdGenerator.generateId());
		final var jwt = new Jwt(IuJson.object() //
				.add("iss", IdGenerator.generateId()) //
				.add("sub", IdGenerator.generateId()) //
				.add("aud", audience.toString()) //
				.add("iat", Instant.now().getEpochSecond()) //
				.build());
		final var error = assertThrows(NullPointerException.class,
				() -> jwt.validateClaims(audience, Duration.ofMinutes(2L)));
		assertEquals("Missing exp claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsInvalidExpires() {
		final var iat = Instant.now();
		final var exp = iat.plusSeconds(300L);
		final var audience = URI.create(IdGenerator.generateId());
		final var jwt = new Jwt(IuJson.object() //
				.add("iss", IdGenerator.generateId()) //
				.add("sub", IdGenerator.generateId()) //
				.add("aud", audience.toString()) //
				.add("iat", iat.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.build());
		final var error = assertThrows(IllegalArgumentException.class,
				() -> jwt.validateClaims(audience, Duration.ofMinutes(2L)));
		assertEquals("Token exp claim must be no more than PT2M in the future", error.getMessage());
	}

	@Test
	public void testValidateClaimsSuccess() {
		final var iat = Instant.now();
		final var exp = iat.plusSeconds(30L);
		final var audience = URI.create(IdGenerator.generateId());
		final var jwt = new Jwt(IuJson.object() //
				.add("iss", IdGenerator.generateId()) //
				.add("sub", IdGenerator.generateId()) //
				.add("aud", audience.toString()) //
				.add("iat", iat.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.build());
		assertDoesNotThrow(() -> jwt.validateClaims(audience, Duration.ofMinutes(2L)));
	}

}
