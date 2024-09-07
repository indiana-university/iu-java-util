package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;

@SuppressWarnings("javadoc")
public class JwtBuilderTest {

	@Test
	public void testProperties() {
		final var jti = IdGenerator.generateId();
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var nbf = iat.minusSeconds(1L);
		final var exp = iat.plusSeconds(1L);
		final var aud = IuIterable.iter(URI.create(IdGenerator.generateId()));
		final var nonce = IdGenerator.generateId();

		final var builder = new JwtBuilder();
		builder.setTokenId(jti);
		builder.setIssuer(iss);
		builder.setAudience(aud);
		builder.setSubject(sub);
		builder.setIssuedAt(iat);
		builder.setNotBefore(nbf);
		builder.setExpires(exp);
		builder.setNonce(nonce);

		assertEquals(builder.build(), new Jwt(builder));
		assertEquals(builder.build(), new JwtBuilder(builder).build());
	}

	@Test
	public void testIsExpiredUnsupported() {
		final var builder = new JwtBuilder();
		final var error = assertThrows(UnsupportedOperationException.class, () -> builder.isExpired());
		assertEquals("Use build() to complete JWT construction before verifying token claims", error.getMessage());
	}

	@Test
	public void testValidateClaimsUnsupported() {
		final var builder = new JwtBuilder();
		final var error = assertThrows(UnsupportedOperationException.class, () -> builder.validateClaims(null, null));
		assertEquals("Use build() to complete JWT construction before verifying token claims", error.getMessage());
	}

}
