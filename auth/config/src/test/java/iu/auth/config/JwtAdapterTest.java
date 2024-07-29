package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;

@SuppressWarnings("javadoc")
public class JwtAdapterTest {

	private final JwtAdapter jwt = new JwtAdapter();

	@Test
	public void testNullFromJson() {
		assertNull(jwt.fromJson(jwt.toJson(null)));
		assertNull(JwtAdapter.NUMERIC_DATE.fromJson(JwtAdapter.NUMERIC_DATE.toJson(null)));
	}

	@Test
	public void testAllClaimsToJson() {
		final var tokenId = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var subject = IdGenerator.generateId();
		final var issuer = URI.create(IdGenerator.generateId());
		final var aud = IuIterable.iter(URI.create(IdGenerator.generateId()), URI.create(IdGenerator.generateId()));
		final var now = Instant.now();
		final var nbf = now.minusSeconds(15L);
		final var exp = now.plusSeconds(15L);

		final var serializedJwt = this.jwt.toJson(new IuWebToken() {
			@Override
			public String getTokenId() {
				return tokenId;
			}

			@Override
			public String getSubject() {
				return subject;
			}

			@Override
			public Instant getNotBefore() {
				return nbf;
			}

			@Override
			public String getNonce() {
				return nonce;
			}

			@Override
			public URI getIssuer() {
				return issuer;
			}

			@Override
			public Instant getIssuedAt() {
				return now;
			}

			@Override
			public Instant getExpires() {
				return exp;
			}

			@Override
			public Iterable<URI> getAudience() {
				return aud;
			}
		}).asJsonObject();

		assertEquals(tokenId, serializedJwt.getString("jti"));
		assertEquals(nonce, serializedJwt.getString("nonce"));
		assertEquals(subject, serializedJwt.getString("sub"));
		assertEquals(issuer, URI.create(serializedJwt.getString("iss")));
		assertTrue(
				IuIterable.remaindersAreEqual(aud.iterator(),
						IuJson.get(serializedJwt, "aud",
								IuJsonAdapter.of(Iterator.class, IuJsonAdapter.of(URI.class)))),
				() -> aud + " != " + serializedJwt.getInt("aud"));
		assertEquals(nbf.getEpochSecond(), serializedJwt.getJsonNumber("nbf").longValue());
		assertEquals(now.getEpochSecond(), serializedJwt.getJsonNumber("iat").longValue());
		assertEquals(exp.getEpochSecond(), serializedJwt.getJsonNumber("exp").longValue());
	}

	@Test
	public void testAllClaimsFromJson() {
		final var tokenId = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var subject = IdGenerator.generateId();
		final var issuer = URI.create(IdGenerator.generateId());
		final var aud = IuIterable.iter(URI.create(IdGenerator.generateId()), URI.create(IdGenerator.generateId()));
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var nbf = now.minusSeconds(15L);
		final var exp = now.plusSeconds(15L);

		final var builder = IuJson.object();
		builder.add("jti", tokenId);
		builder.add("nonce", nonce);
		builder.add("sub", subject);
		builder.add("iss", issuer.toString());
		final var audBuilder = IuJson.array();
		aud.forEach(a -> audBuilder.add(a.toString()));
		builder.add("aud", audBuilder);
		builder.add("nbf", nbf.getEpochSecond());
		builder.add("iat", now.getEpochSecond());
		builder.add("exp", exp.getEpochSecond());
		final var claims = builder.build();

		final var jwt = this.jwt.fromJson(claims);
		assertEquals(tokenId, jwt.getTokenId());
		assertEquals(nonce, jwt.getNonce());
		assertEquals(subject, jwt.getSubject());
		assertEquals(issuer, jwt.getIssuer());
		assertTrue(IuIterable.remaindersAreEqual(aud.iterator(), jwt.getAudience().iterator()),
				() -> aud + " != " + jwt.getAudience());
		assertEquals(nbf, jwt.getNotBefore());
		assertEquals(now, jwt.getIssuedAt());
		assertEquals(exp, jwt.getExpires());

		assertNotEquals(jwt, new Object());
		final var jwt2 = this.jwt.fromJson(claims);
		assertEquals(jwt.hashCode(), jwt2.hashCode());
		assertEquals(jwt, jwt2);
	}

}
