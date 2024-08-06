package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.jwt.IuWebToken;

@SuppressWarnings("javadoc")
public class JwtBuilderTest {

	private JwtBuilder builder = new JwtBuilder();

	@Test
	public void testTokenId() {
		final var tokenId = IdGenerator.generateId();
		builder.setTokenId(tokenId);
		assertSame(tokenId, builder.getTokenId());
	}

	@Test
	public void testIssuer() {
		final var issuer = URI.create(IdGenerator.generateId());
		builder.setIssuer(issuer);
		assertSame(issuer, builder.getIssuer());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAudience() {
		final var audience = mock(Iterable.class);
		builder.setAudience(audience);
		assertSame(audience, builder.getAudience());
	}

	@Test
	public void testSubject() {
		final var subject = IdGenerator.generateId();
		builder.setSubject(subject);
		assertSame(subject, builder.getSubject());
	}

	@Test
	public void testIssuedAt() {
		final var issuedAt = mock(Instant.class);
		builder.setIssuedAt(issuedAt);
		assertSame(issuedAt, builder.getIssuedAt());
	}

	@Test
	public void testNotBefore() {
		final var notBefore = mock(Instant.class);
		builder.setNotBefore(notBefore);
		assertSame(notBefore, builder.getNotBefore());
	}

	@Test
	public void testExpires() {
		final var expires = mock(Instant.class);
		builder.setExpires(expires);
		assertSame(expires, builder.getExpires());
	}

	@Test
	public void testNonce() {
		final var nonce = IdGenerator.generateId();
		builder.setNonce(nonce);
		assertSame(nonce, builder.getNonce());
	}

	@Test
	public void testCopy() {
		final var tokenId = IdGenerator.generateId();
		final var issuer = URI.create(IdGenerator.generateId());
		final var audience = mock(Iterable.class);
		final var subject = IdGenerator.generateId();
		final var issuedAt = mock(Instant.class);
		final var notBefore = mock(Instant.class);
		final var expires = mock(Instant.class);
		final var nonce = IdGenerator.generateId();

		final var builder = new JwtBuilder(new IuWebToken() {
			@Override
			public String getTokenId() {
				return tokenId;
			}

			@Override
			public URI getIssuer() {
				return issuer;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Iterable<URI> getAudience() {
				return audience;
			}

			@Override
			public String getSubject() {
				return subject;
			}

			@Override
			public Instant getIssuedAt() {
				return issuedAt;
			}

			@Override
			public Instant getNotBefore() {
				return notBefore;
			}

			@Override
			public Instant getExpires() {
				return expires;
			}

			@Override
			public String getNonce() {
				return nonce;
			}
		});

		assertSame(tokenId, builder.getTokenId());
		assertSame(issuer, builder.getIssuer());
		assertSame(audience, builder.getAudience());
		assertSame(subject, builder.getSubject());
		assertSame(issuedAt, builder.getIssuedAt());
		assertSame(notBefore, builder.getNotBefore());
		assertSame(expires, builder.getExpires());
		assertSame(nonce, builder.getNonce());
	}

}
