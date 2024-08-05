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
package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

@SuppressWarnings({ "javadoc", "rawtypes", "unchecked" })
public class JwtAdapterTest {

	private final JwtAdapter jwt = new JwtAdapter<>() {
		@Override
		protected void registerClaims() {
			super.registerClaims(); // for coverage
			assertEquals("not sealed",
					assertThrows(IllegalStateException.class, () -> getClaim(null, null)).getMessage());
			assertEquals("already registered",
					assertThrows(IllegalArgumentException.class, () -> registerClaim("jti", null, null)).getMessage());
		}
	};

	@Test
	public void testSealed() {
		assertEquals("sealed",
				assertThrows(IllegalStateException.class, () -> jwt.registerClaim(null, null, null)).getMessage());
	}

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
		assertEquals(claims, IuJson.parse(jwt.toString()));

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
