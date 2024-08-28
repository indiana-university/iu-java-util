package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.auth.jwt.IuWebToken;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class JwtTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testValidateClaimsMissingIss() {
		final var adapter = mock(JwtAdapter.class);
		final var claims = mock(JsonObject.class);
		final var ttl = Duration.ofSeconds(30L);
		final var token = new Jwt(adapter, claims);
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(NullPointerException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Missing iss claim", error.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testValidateClaimsMissingSub() {
		final var iss = URI.create(IdGenerator.generateId());
		final var adapter = mock(JwtAdapter.class);
		final var claims = mock(JsonObject.class);
		final var ttl = Duration.ofSeconds(30L);
		final var token = new Jwt(adapter, claims);
		when(adapter.getClaim(claims, "iss")).thenReturn(iss);
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(NullPointerException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Missing sub claim", error.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testValidateClaimsMissingAud() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var adapter = mock(JwtAdapter.class);
		final var claims = mock(JsonObject.class);
		final var ttl = Duration.ofSeconds(30L);
		final var token = new Jwt(adapter, claims);
		when(adapter.getClaim(claims, "iss")).thenReturn(iss);
		when(adapter.getClaim(claims, "sub")).thenReturn(sub);
		when(adapter.getClaim(claims, "aud")).thenReturn(Set.of());
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(IllegalArgumentException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Token aud claim doesn't include " + audience, error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsMissingIat() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var adapter = mock(JwtAdapter.class);
		final var claims = mock(JsonObject.class);
		final var ttl = Duration.ofSeconds(30L);
		final var token = new Jwt(adapter, claims);
		when(adapter.getClaim(claims, "iss")).thenReturn(iss);
		when(adapter.getClaim(claims, "sub")).thenReturn(sub);
		final var audience = URI.create(IdGenerator.generateId());
		when(adapter.getClaim(claims, "aud")).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(NullPointerException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Missing iat claim", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsInvalidIat() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().plusSeconds(30L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(IllegalArgumentException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Token iat claim must be no more than PT15S in the future", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsMissingExp() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now();
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(NullPointerException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Missing exp claim", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsInvalidNbf() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now();
		final var nbf = iat.plusSeconds(30L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		when(token.getNotBefore()).thenReturn(nbf);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(IllegalArgumentException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Token nbf claim must be no more than PT15S in the future", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsInvalidExp() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now();
		final var exp = iat.plusSeconds(60L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		when(token.getExpires()).thenReturn(exp);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(IllegalArgumentException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Token exp claim must be no more than PT30S in the future", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsExpired() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().minusSeconds(60L);
		final var exp = iat.plusSeconds(30L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		when(token.getExpires()).thenReturn(exp);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(IllegalArgumentException.class, () -> token.validateClaims(audience, ttl));
		assertEquals("Token is expired", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaims() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var nbf = iat.minusSeconds(1L);
		final var exp = iat.plusSeconds(1L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		when(token.getNotBefore()).thenReturn(nbf);
		when(token.getExpires()).thenReturn(exp);
		final var audToIgnore = URI.create(IdGenerator.generateId());
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audToIgnore, audience));
		assertDoesNotThrow(() -> token.validateClaims(audience, ttl));
	}

}
