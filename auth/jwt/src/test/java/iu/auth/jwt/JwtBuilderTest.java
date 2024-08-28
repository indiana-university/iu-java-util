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
package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;
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

			@Override
			public void validateClaims(URI audience, Duration ttl) {
				fail();
			}

			@Override
			public boolean isExpired() {
				fail();
				return true;
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
