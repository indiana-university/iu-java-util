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
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
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
import edu.iu.crypt.WebTokenClaims;
import edu.iu.test.IuTestLogger;
import jakarta.json.JsonNumber;

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
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);
		when(claims.getAudience()).thenReturn(null);

		final var jwt = new Jwt(type, claims);

		assertNull(jwt.getTokenId());
		assertNull(jwt.getIssuer());
		assertNull(jwt.getSubject());
		assertNull(jwt.getAudience());
		assertNull(jwt.getIssuedAt());
		assertNull(jwt.getNotBefore());
		assertNull(jwt.getExpires());
		assertNull(jwt.getNonce());
		assertFalse(jwt.isExpired());
	}

	@Test
	public void testNonNullProperties() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);

		final var tokenId = IdGenerator.generateId();
		when(claims.getTokenId()).thenReturn(tokenId);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);
		final var audience = URI.create(IdGenerator.generateId());
		when(claims.getAudience()).thenReturn(Set.of(audience));
		final var issuedAt = Instant.now();
		when(claims.getIssuedAt()).thenReturn(issuedAt);
		final var notBefore = issuedAt.minusSeconds(30L);
		when(claims.getNotBefore()).thenReturn(notBefore);
		final var expires = issuedAt.plusSeconds(30L);
		when(claims.getExpires()).thenReturn(expires);
		final var nonce = IdGenerator.generateId();
		when(claims.getNonce()).thenReturn(nonce);

		final var jwt = new Jwt(type, claims);

		assertEquals(tokenId, jwt.getTokenId());
		assertEquals(issuer, jwt.getIssuer());
		assertEquals(subject, jwt.getSubject());
		assertTrue(IuIterable.remaindersAreEqual(IuIterable.iter(audience).iterator(), jwt.getAudience().iterator()));
		assertEquals(issuedAt.truncatedTo(ChronoUnit.SECONDS), jwt.getIssuedAt());
		assertEquals(notBefore.truncatedTo(ChronoUnit.SECONDS), jwt.getNotBefore());
		assertEquals(expires.truncatedTo(ChronoUnit.SECONDS), jwt.getExpires());
		assertEquals(nonce, jwt.getNonce());
		assertFalse(jwt.isExpired());
	}

	@Test
	public void testIssueAt() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);

		final var issuedAt = Instant.now().plusSeconds(30L);
		when(claims.getIssuedAt()).thenReturn(issuedAt);

		final var error = assertThrows(IllegalArgumentException.class, () -> new Jwt(type, claims));
		assertEquals("Token iat claim must be no more than PT15S in the future", error.getMessage());
	}

	@Test
	public void testExpired() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);

		final var expires = Instant.now().minusSeconds(30L);
		when(claims.getExpires()).thenReturn(expires);

		final var error = assertThrows(IllegalArgumentException.class, () -> new Jwt(type, claims));
		assertEquals("Token is expired", error.getMessage());
	}

	@Test
	public void testNotBefore() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);

		final var notBefore = Instant.now().plusSeconds(30L);
		when(claims.getNotBefore()).thenReturn(notBefore);

		final var error = assertThrows(IllegalArgumentException.class, () -> new Jwt(type, claims));
		assertEquals("Token nbf claim must be no more than PT15S in the future", error.getMessage());
	}

	@Test
	public void testHashCodeEqualsToString() {
		final Queue<Jwt> tokens = new ArrayDeque<>();
		for (final var tokenId : IuIterable.iter(IdGenerator.generateId(), IdGenerator.generateId()))
			for (final var issuer : IuIterable.iter(URI.create(IdGenerator.generateId()),
					URI.create(IdGenerator.generateId())))
				for (final var subject : IuIterable.iter(IdGenerator.generateId(), IdGenerator.generateId()))
					for (final var audience : IuIterable.iter(
							IuIterable.iter(URI.create(IdGenerator.generateId()), URI.create(IdGenerator.generateId())),
							IuIterable.iter(URI.create(IdGenerator.generateId()),
									URI.create(IdGenerator.generateId()))))
						for (final var issuedAt : IuIterable.iter(Instant.now(), Instant.now().minusSeconds(5L)))
							for (final var notBefore : IuIterable.iter(issuedAt.minusSeconds(30L),
									issuedAt.minusSeconds(40L)))
								for (final var expires : IuIterable.iter(issuedAt.plusSeconds(30L),
										issuedAt.plusSeconds(40L)))
									for (final var nonce : IuIterable.iter(IdGenerator.generateId(),
											IdGenerator.generateId())) {
										final var claims = mock(WebTokenClaims.class);
										when(claims.getTokenId()).thenReturn(tokenId);
										when(claims.getIssuer()).thenReturn(issuer);
										when(claims.getSubject()).thenReturn(subject);
										when(claims.getAudience()).thenReturn(audience);
										when(claims.getIssuedAt()).thenReturn(issuedAt);
										when(claims.getNotBefore()).thenReturn(notBefore);
										when(claims.getExpires()).thenReturn(expires);
										when(claims.getNonce()).thenReturn(nonce);

										final var token = new Jwt(IdGenerator.generateId(), claims);
										final var parsedClaims = IuJson.parse(token.toString()).asJsonObject();
										assertEquals(tokenId, parsedClaims.getString("jti"));
										assertEquals(issuer.toString(), parsedClaims.getString("iss"));
										assertEquals(subject, parsedClaims.getString("sub"));
										final var i = audience.iterator();
										assertEquals(IuJson.array().add(i.next().toString()).add(i.next().toString())
												.build(), parsedClaims.getJsonArray("aud"));
										assertEquals(issuedAt.getEpochSecond(),
												parsedClaims.getJsonNumber("iat").longValue());
										assertEquals(notBefore.getEpochSecond(),
												parsedClaims.getJsonNumber("nbf").longValue());
										assertEquals(expires.getEpochSecond(),
												parsedClaims.getJsonNumber("exp").longValue());
										assertEquals(nonce, parsedClaims.getString("nonce"));

										assertNotEquals(token, new Object());

										tokens.add(token);
									}

		for (final var a : tokens)
			for (final var b : tokens)
				if (a == b) {
					assertEquals(a, b);
					assertEquals(a.hashCode(), b.hashCode());
				} else {
					assertNotEquals(a, b);
					assertNotEquals(b, a);
					assertNotEquals(a.hashCode(), b.hashCode());
				}
	}

	@Test
	public void testOneAudienceFlattensToString() {
		final var audience = URI.create(IdGenerator.generateId());
		final var claims = mock(WebTokenClaims.class);
		when(claims.getAudience()).thenReturn(Set.of(audience));
		final var token = new Jwt(IdGenerator.generateId(), claims);
		assertEquals(audience.toString(),
				IuJson.parse(token.toString()).asJsonObject().getJsonString("aud").getString());
	}

	@Test
	public void testNullAudienceUndefined() {
		final var claims = mock(WebTokenClaims.class);
		when(claims.getAudience()).thenReturn(null);
		final var token = new Jwt(IdGenerator.generateId(), claims);
		assertFalse(IuJson.parse(token.toString()).asJsonObject().containsKey("aud"));
	}

	@Test
	public void testSignAndVerify() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);

		final var tokenId = IdGenerator.generateId();
		when(claims.getTokenId()).thenReturn(tokenId);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);
		final var audience = URI.create(IdGenerator.generateId());
		when(claims.getAudience()).thenReturn(Set.of(audience));
		final var issuedAt = Instant.now();
		when(claims.getIssuedAt()).thenReturn(issuedAt);
		final var notBefore = issuedAt.minusSeconds(30L);
		when(claims.getNotBefore()).thenReturn(notBefore);
		final var expires = issuedAt.plusSeconds(30L);
		when(claims.getExpires()).thenReturn(expires);
		final var nonce = IdGenerator.generateId();
		when(claims.getNonce()).thenReturn(nonce);

		final var jwt = new Jwt(type, claims);
		final var issuerKey = WebKey.ephemeral(Algorithm.ES256);
		final var signed = jwt.sign(Algorithm.ES256, issuerKey);
		assertEquals(jwt, new Jwt(signed, issuerKey));

		final var error = assertThrows(IllegalArgumentException.class,
				() -> new Jwt(signed, WebKey.ephemeral(Algorithm.ES256)));
		assertEquals("SHA256withECDSA verification failed", error.getMessage());
	}

	@Test
	public void testSignEncryptDecryptAndVerify() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);

		final var tokenId = IdGenerator.generateId();
		when(claims.getTokenId()).thenReturn(tokenId);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);
		final var audience = URI.create(IdGenerator.generateId());
		when(claims.getAudience()).thenReturn(Set.of(audience));
		final var issuedAt = Instant.now();
		when(claims.getIssuedAt()).thenReturn(issuedAt);
		final var notBefore = issuedAt.minusSeconds(30L);
		when(claims.getNotBefore()).thenReturn(notBefore);
		final var expires = issuedAt.plusSeconds(30L);
		when(claims.getExpires()).thenReturn(expires);
		final var nonce = IdGenerator.generateId();
		when(claims.getNonce()).thenReturn(nonce);

		final var jwt = new Jwt(type, claims);
		final var issuerKey = WebKey.ephemeral(Algorithm.ES256);
		final var audienceKey = WebKey.builder(Type.X25519).ephemeral(Algorithm.ECDH_ES).build();
		final var signed = jwt.signAndEncrypt(Algorithm.ES256, issuerKey, Algorithm.ECDH_ES, Encryption.A128GCM,
				audienceKey);

		IuTestLogger.allow(Jwe.class.getName(), Level.FINE);
		assertEquals(jwt, new Jwt(signed, issuerKey, audienceKey));

		final var decryptError = assertThrows(IllegalStateException.class, () -> new Jwt(signed,
				WebKey.ephemeral(Algorithm.ES256), WebKey.builder(Type.X25519).ephemeral(Algorithm.ECDH_ES).build()));
		assertInstanceOf(AEADBadTagException.class, decryptError.getCause());

		final var error = assertThrows(IllegalArgumentException.class,
				() -> new Jwt(signed, WebKey.ephemeral(Algorithm.ES256), audienceKey));
		assertEquals("SHA256withECDSA verification failed", error.getMessage());
	}

	@Test
	public void testInvalidToken() {
		final var invalidToken = IdGenerator.generateId();
		final var error = assertThrows(IllegalArgumentException.class, () -> new Jwt(invalidToken, (WebKey) null));
		assertEquals("Invalid token; must be enclosed in a compact JWS or JWE", error.getMessage());
	}

	@Test
	public void testValidateMissingIssuer() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);

		final var jwt = new Jwt(type, claims);
		final var error = assertThrows(NullPointerException.class, () -> jwt.validateClaims(null, null));
		assertEquals("Missing iss claim", error.getMessage());
	}

	@Test
	public void testValidateMissingSubject() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);

		final var jwt = new Jwt(type, claims);
		final var error = assertThrows(NullPointerException.class, () -> jwt.validateClaims(null, null));
		assertEquals("Missing sub claim", error.getMessage());
	}

	@Test
	public void testValidateMissingAudience() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);
		when(claims.getAudience()).thenReturn(null);

		final var jwt = new Jwt(type, claims);
		final var error = assertThrows(NullPointerException.class, () -> jwt.validateClaims(null, null));
		assertEquals("Missing aud claim", error.getMessage());
	}

	@Test
	public void testEmptyAudience() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);

		final var jwt = new Jwt(type, claims);
		final var error = assertThrows(IllegalArgumentException.class, () -> jwt.validateClaims(null, null));
		assertEquals("Empty aud claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsAudMismatch() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);
		final var audience = URI.create(IdGenerator.generateId());
		when(claims.getAudience()).thenReturn(Set.of(audience));

		final var jwt = new Jwt(type, claims);
		final var expectedAudience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(IllegalArgumentException.class,
				() -> jwt.validateClaims(expectedAudience, Duration.ofMinutes(2L)));
		assertEquals("Token aud claim doesn't include " + expectedAudience, error.getMessage());
	}

	@Test
	public void testValidateClaimsMissingIssuedAt() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);
		final var audience = URI.create(IdGenerator.generateId());
		when(claims.getAudience()).thenReturn(Set.of(audience));

		final var jwt = new Jwt(type, claims);
		final var error = assertThrows(NullPointerException.class,
				() -> jwt.validateClaims(audience, Duration.ofMinutes(2L)));
		assertEquals("Missing iat claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsMissingExpires() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);
		final var audience = URI.create(IdGenerator.generateId());
		when(claims.getAudience()).thenReturn(Set.of(audience));
		final var iat = Instant.now();
		when(claims.getIssuedAt()).thenReturn(iat);

		final var jwt = new Jwt(type, claims);
		final var error = assertThrows(NullPointerException.class,
				() -> jwt.validateClaims(audience, Duration.ofMinutes(2L)));
		assertEquals("Missing exp claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsInvalidExpires() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);
		final var audience = URI.create(IdGenerator.generateId());
		when(claims.getAudience()).thenReturn(Set.of(audience));
		final var iat = Instant.now();
		when(claims.getIssuedAt()).thenReturn(iat);
		final var exp = iat.plusSeconds(300L);
		when(claims.getExpires()).thenReturn(exp);

		final var jwt = new Jwt(type, claims);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> jwt.validateClaims(audience, Duration.ofMinutes(2L)));
		assertEquals("Token exp claim must be no more than PT2M in the future", error.getMessage());
	}

	@Test
	public void testValidateClaimsSuccess() {
		final var type = IdGenerator.generateId();
		final var claims = mock(WebTokenClaims.class);
		final var issuer = URI.create(IdGenerator.generateId());
		when(claims.getIssuer()).thenReturn(issuer);
		final var subject = IdGenerator.generateId();
		when(claims.getSubject()).thenReturn(subject);
		final var audience = URI.create(IdGenerator.generateId());
		when(claims.getAudience()).thenReturn(Set.of(audience));
		final var iat = Instant.now();
		when(claims.getIssuedAt()).thenReturn(iat);
		final var exp = iat.plusSeconds(30L);
		when(claims.getExpires()).thenReturn(exp);

		final var jwt = new Jwt(type, claims);
		assertDoesNotThrow(() -> jwt.validateClaims(audience, Duration.ofMinutes(2L)));
	}

}
