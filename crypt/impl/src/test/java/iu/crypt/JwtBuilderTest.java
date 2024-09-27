package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;

@SuppressWarnings("javadoc")
public class JwtBuilderTest {

	@Test
	public void testProperties() {
		final var builder = new JwtBuilder();
		final var type = IdGenerator.generateId();
		final var tokenId = IdGenerator.generateId();
		final var issuer = URI.create(IdGenerator.generateId());
		final var subject = IdGenerator.generateId();
		final var audience = URI.create(IdGenerator.generateId());
		final var issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var notBefore = issuedAt.minusSeconds(30L);
		final var expires = issuedAt.plusSeconds(30L);
		final var nonce = IdGenerator.generateId();

		builder.setType(type);
		builder.setTokenId(tokenId);
		builder.setIssuer(issuer);
		builder.setSubject(subject);
		builder.setAudience(Set.of(audience));
		builder.setIssuedAt(issuedAt);
		builder.setNotBefore(notBefore);
		builder.setExpires(expires);
		builder.setNonce(nonce);

		assertEquals(type, builder.getType());
		assertEquals(tokenId, builder.getTokenId());
		assertEquals(issuer, builder.getIssuer());
		assertEquals(subject, builder.getSubject());
		assertTrue(
				IuIterable.remaindersAreEqual(IuIterable.iter(audience).iterator(), builder.getAudience().iterator()));
		assertEquals(issuedAt, builder.getIssuedAt());
		assertEquals(notBefore, builder.getNotBefore());
		assertEquals(expires, builder.getExpires());
		assertEquals(nonce, builder.getNonce());

		final var jwt = builder.build();
		assertEquals(tokenId, jwt.getTokenId());
		assertEquals(issuer, jwt.getIssuer());
		assertEquals(subject, jwt.getSubject());
		assertTrue(IuIterable.remaindersAreEqual(IuIterable.iter(audience).iterator(), jwt.getAudience().iterator()));
		assertEquals(issuedAt, jwt.getIssuedAt());
		assertEquals(notBefore, jwt.getNotBefore());
		assertEquals(expires, jwt.getExpires());
		assertEquals(nonce, jwt.getNonce());

		final var copy = new JwtBuilder(jwt);
		assertEquals("JWT", copy.getType());
		assertEquals(tokenId, copy.getTokenId());
		assertEquals(issuer, copy.getIssuer());
		assertEquals(subject, copy.getSubject());
		assertTrue(IuIterable.remaindersAreEqual(IuIterable.iter(audience).iterator(), copy.getAudience().iterator()));
		assertEquals(issuedAt, copy.getIssuedAt());
		assertEquals(notBefore, copy.getNotBefore());
		assertEquals(expires, copy.getExpires());
		assertEquals(nonce, copy.getNonce());
	}

}
