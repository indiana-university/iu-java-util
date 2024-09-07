package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.test.IuTestLogger;
import jakarta.json.JsonArray;
import jakarta.json.JsonString;

@SuppressWarnings("javadoc")
public class JwtTest {

	@Test
	public void testValidateClaimsMissingIss() {
		final var copy = mock(IuWebToken.class);
		final var ttl = Duration.ofSeconds(30L);
		final var token = new Jwt(copy);
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(NullPointerException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Missing iss claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsMissingSub() {
		final var iss = URI.create(IdGenerator.generateId());
		final var ttl = Duration.ofSeconds(30L);

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);

		final var token = new Jwt(copy);
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(NullPointerException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Missing sub claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsEmptyAud() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var ttl = Duration.ofSeconds(30L);

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(Set.of());

		final var token = new Jwt(copy);
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(IllegalArgumentException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Empty aud claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsNoAudMatch() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var ttl = Duration.ofSeconds(30L);

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(Set.of(URI.create(IdGenerator.generateId())));

		final var token = new Jwt(copy);
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(IllegalArgumentException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Token aud claim doesn't include " + audience, error.getMessage());
	}

	@Test
	public void testValidateClaimsMissingIat() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var audience = URI.create(IdGenerator.generateId());
		final var ttl = Duration.ofSeconds(30L);

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(IuIterable.iter(audience));

		final var token = new Jwt(copy);
		final var error = assertThrows(NullPointerException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Missing iat claim", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsInvalidIat() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().plusSeconds(30L);
		final var audience = URI.create(IdGenerator.generateId());

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		when(copy.getIssuedAt()).thenReturn(iat);

		final var error = assertThrows(IllegalArgumentException.class, () -> new Jwt(copy));
		assertEquals("Token iat claim must be no more than PT15S in the future", error.getMessage());
	}

	@Test
	public void testValidateClaimsMissingExp() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now();
		final var audience = URI.create(IdGenerator.generateId());
		final var ttl = Duration.ofSeconds(30L);

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(IuIterable.iter(audience));
		when(copy.getIssuedAt()).thenReturn(iat);

		final var token = new Jwt(copy);
		final var error = assertThrows(NullPointerException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Missing exp claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsInvalidNbf() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now();
		final var nbf = iat.plusSeconds(30L);
		final var audience = URI.create(IdGenerator.generateId());

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(IuIterable.iter(audience));
		when(copy.getIssuedAt()).thenReturn(iat);
		when(copy.getNotBefore()).thenReturn(nbf);

		final var error = assertThrows(IllegalArgumentException.class, () -> new Jwt(copy));
		assertEquals("Token nbf claim must be no more than PT15S in the future", error.getMessage());
	}

	@Test
	public void testValidateClaimsInvalidExp() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now();
		final var exp = iat.plusSeconds(60L);
		final var audience = URI.create(IdGenerator.generateId());
		final var ttl = Duration.ofSeconds(30L);

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(IuIterable.iter(audience));
		when(copy.getIssuedAt()).thenReturn(iat);
		when(copy.getExpires()).thenReturn(exp);

		final var token = new Jwt(copy);
		final var error = assertThrows(IllegalArgumentException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Token exp claim must be no more than PT30S in the future", error.getMessage());
	}

	@Test
	public void testValidateClaimsExpired() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().minusSeconds(60L);
		final var exp = iat.plusSeconds(30L);
		final var audience = URI.create(IdGenerator.generateId());

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(IuIterable.iter(audience));
		when(copy.getIssuedAt()).thenReturn(iat);
		when(copy.getExpires()).thenReturn(exp);

		final var error = assertThrows(IllegalArgumentException.class, () -> new Jwt(copy));
		assertEquals("Token is expired", error.getMessage());
	}

	@Test
	public void testValidClaims() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var nbf = iat.minusSeconds(1L);
		final var exp = iat.plusSeconds(1L);
		final var audience = URI.create(IdGenerator.generateId());
		final var ttl = Duration.ofSeconds(30L);

		final var copy = mock(IuWebToken.class);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(IuIterable.iter(audience));
		when(copy.getIssuedAt()).thenReturn(iat);
		when(copy.getExpires()).thenReturn(exp);
		when(copy.getNotBefore()).thenReturn(nbf);

		final var token = new Jwt(copy);
		assertDoesNotThrow(() -> token.validateClaims(audience, ttl));
	}

	@Test
	public void testBlankPropertiesToStringHashCodeAndEquals() {
		final var copy = mock(IuWebToken.class);
		when(copy.getAudience()).thenReturn(null);

		final var a = new Jwt(copy);
		assertNull(a.getTokenId());
		assertNull(a.getIssuer());
		assertNull(a.getAudience());
		assertNull(a.getSubject());
		assertNull(a.getIssuedAt());
		assertNull(a.getNotBefore());
		assertNull(a.getExpires());
		assertNull(a.getNonce());
		assertEquals(IuJson.object().build(), IuJson.parse(a.toString()));

		final var b = new Jwt(copy);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void testPropertiesToStringHashCodeAndEquals() {
		final Queue<Jwt> toCheck = new ArrayDeque<>();
		for (final var jti : IuIterable.iter(IdGenerator.generateId(), IdGenerator.generateId()))
			for (final var iss : IuIterable.iter(URI.create(IdGenerator.generateId()),
					URI.create(IdGenerator.generateId())))
				for (final var aud : IuIterable.iter(List.of(URI.create(IdGenerator.generateId())),
						List.of(URI.create(IdGenerator.generateId()), URI.create(IdGenerator.generateId()))))
					for (final var sub : IuIterable.iter(IdGenerator.generateId(), IdGenerator.generateId()))
						for (final var iat : IuIterable.iter(
								Instant.now().plusSeconds(ThreadLocalRandom.current().nextInt(-1000, 14)),
								Instant.now().plusSeconds(ThreadLocalRandom.current().nextInt(-1000, 14))))
							for (final var nbf : IuIterable.iter(
									Instant.now().plusSeconds(ThreadLocalRandom.current().nextInt(-1000, 14)),
									Instant.now().plusSeconds(ThreadLocalRandom.current().nextInt(-1000, 14))))
								for (final var exp : IuIterable.iter(
										Instant.now().plusSeconds(ThreadLocalRandom.current().nextInt(-14, 1000)),
										Instant.now().plusSeconds(ThreadLocalRandom.current().nextInt(-14, 1000))))
									for (final var nonce : IuIterable.iter(IdGenerator.generateId(),
											IdGenerator.generateId())) {
										final var copy = mock(IuWebToken.class);
										when(copy.getTokenId()).thenReturn(jti);
										when(copy.getIssuer()).thenReturn(iss);
										when(copy.getSubject()).thenReturn(sub);
										when(copy.getAudience()).thenReturn(aud);
										when(copy.getIssuedAt()).thenReturn(iat);
										when(copy.getExpires()).thenReturn(exp);
										when(copy.getNotBefore()).thenReturn(nbf);
										when(copy.getNonce()).thenReturn(nonce);
										toCheck.add(new Jwt(copy));
									}
		for (final var a : toCheck)
			for (final var b : toCheck)
				if (a == b) {
					assertNotEquals(a, new Object());
					assertEquals(a, b);

					final var asJson = IuJson.parse(a.toString()).asJsonObject();
					assertEquals(a.getTokenId(), asJson.getString("jti"));
					assertEquals(a.getIssuer(), URI.create(asJson.getString("iss")));
					assertEquals(a.getSubject(), asJson.getString("sub"));

					final var aud = a.getAudience().iterator();
					final var audAsJson = asJson.get("aud");
					if (audAsJson instanceof JsonString)
						assertEquals(aud.next(), URI.create(asJson.getString("aud")));
					else {
						final var audArray = (JsonArray) audAsJson;
						assertEquals(aud.next(), URI.create(audArray.getString(0)));
						assertEquals(aud.next(), URI.create(audArray.getString(1)));
					}
					assertFalse(aud.hasNext());

					assertEquals(a.getIssuedAt().getEpochSecond(), asJson.getInt("iat"));
					assertEquals(a.getExpires().getEpochSecond(), asJson.getInt("exp"));
					assertEquals(a.getNotBefore().getEpochSecond(), asJson.getInt("nbf"));
					assertEquals(a.getNonce(), asJson.getString("nonce"));
				} else {
					assertNotEquals(a, b);
					assertNotEquals(b, a);
					assertNotEquals(a.hashCode(), b.hashCode());
				}
	}

	@Test
	public void testRequiresValidToken() {
		final var error = assertThrows(IllegalArgumentException.class, () -> new Jwt("", null));
		assertEquals("Invalid token; must be enclosed in a compact JWS or JWE", error.getMessage());
	}

	@Test
	public void testSignAndVerify() {
		final var jti = IdGenerator.generateId();
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var nbf = iat.minusSeconds(1L);
		final var exp = iat.plusSeconds(1L);
		final var aud = IuIterable.iter(URI.create(IdGenerator.generateId()));
		final var nonce = IdGenerator.generateId();

		final var copy = mock(IuWebToken.class);
		when(copy.getTokenId()).thenReturn(jti);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(aud);
		when(copy.getIssuedAt()).thenReturn(iat);
		when(copy.getExpires()).thenReturn(exp);
		when(copy.getNotBefore()).thenReturn(nbf);
		when(copy.getNonce()).thenReturn(nonce);

		final var token = new Jwt(copy);
		final var issuerKey = WebKey.ephemeral(Algorithm.ES256);
		final var jwt = token.sign(Algorithm.ES256, issuerKey);
		class Box {
			WebSignedPayload jws;
		}
		final var box = new Box();
		try (final var mockWebSignedPayload = mockStatic(WebSignedPayload.class, a -> {
			box.jws = (WebSignedPayload) spy(a.callRealMethod());
			return box.jws;
		})) {
			final var verifyKey = issuerKey.wellKnown();
			assertEquals(token, new Jwt(jwt, verifyKey));
			verify(box.jws).verify(verifyKey);
		}
	}

	@Test
	public void testSignEncryptDecryptAndVerify() {
		final var jti = IdGenerator.generateId();
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var nbf = iat.minusSeconds(1L);
		final var exp = iat.plusSeconds(1L);
		final var aud = IuIterable.iter(URI.create(IdGenerator.generateId()));
		final var nonce = IdGenerator.generateId();

		final var copy = mock(IuWebToken.class);
		when(copy.getTokenId()).thenReturn(jti);
		when(copy.getIssuer()).thenReturn(iss);
		when(copy.getSubject()).thenReturn(sub);
		when(copy.getAudience()).thenReturn(aud);
		when(copy.getIssuedAt()).thenReturn(iat);
		when(copy.getExpires()).thenReturn(exp);
		when(copy.getNotBefore()).thenReturn(nbf);
		when(copy.getNonce()).thenReturn(nonce);

		final var token = new Jwt(copy);
		final var issuerKey = WebKey.ephemeral(Algorithm.EDDSA);
		final var audienceKey = WebKey.builder(Type.X25519).ephemeral(Algorithm.ECDH_ES).build();
		final var encryptKey = audienceKey.wellKnown();
		final var jwt = token.signAndEncrypt(Algorithm.EDDSA, issuerKey, Algorithm.ECDH_ES, Encryption.A128GCM,
				encryptKey);
		class Box {
			WebSignedPayload jws;
		}
		final var box = new Box();
		try (final var mockWebSignedPayload = mockStatic(WebSignedPayload.class, a -> {
			box.jws = (WebSignedPayload) spy(a.callRealMethod());
			return box.jws;
		})) {
			final var verifyKey = issuerKey.wellKnown();
			IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + encryptKey);
			assertEquals(token, new Jwt(jwt, verifyKey, audienceKey));
			verify(box.jws).verify(verifyKey);
		}
	}

}
