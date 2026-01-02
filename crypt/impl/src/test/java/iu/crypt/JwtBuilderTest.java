/*
 * Copyright © 2026 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import jakarta.json.JsonString;

@SuppressWarnings("javadoc")
public class JwtBuilderTest {

	@Test
	public void testProperties() {
		final var builder = new JwtBuilder<>();
		final var tokenId = IdGenerator.generateId();
		final var issuer = URI.create(IdGenerator.generateId());
		final var subject = IdGenerator.generateId();
		final var audience = URI.create(IdGenerator.generateId());
		final var notBefore = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(30L);
		final var expires = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(30L);
		final var nonce = IdGenerator.generateId();

		builder.jti(tokenId);
		builder.iss(issuer);
		builder.sub(subject);
		builder.aud(audience);
		builder.iat();
		builder.nbf(notBefore);
		builder.exp(expires);
		builder.nonce(nonce);

		final var issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var jwt = builder.build();
		assertEquals(tokenId, jwt.getTokenId());
		assertEquals(issuer, jwt.getIssuer());
		assertEquals(subject, jwt.getSubject());
		assertEquals(audience.toString(),
				assertInstanceOf(JsonString.class, IuJson.parse(jwt.toString()).asJsonObject().get("aud")).getString());
		assertTrue(IuIterable.remaindersAreEqual(IuIterable.iter(audience).iterator(), jwt.getAudience().iterator()));
		assertEquals(issuedAt, jwt.getIssuedAt());
		assertEquals(notBefore, jwt.getNotBefore());
		assertEquals(expires, jwt.getExpires());
		assertEquals(nonce, jwt.getNonce());
	}

	@Test
	public void testJti() {
		final var tokenId = new JwtBuilder<>().jti().build().getTokenId();
		assertNotNull(tokenId);
		assertDoesNotThrow(() -> IdGenerator.verifyId(tokenId, 1000L));
	}

	@Test
	public void testAudNull() {
		final var error = assertThrows(IllegalArgumentException.class, () -> new JwtBuilder<>().aud());
		assertEquals("At least one audience URI is required", error.getMessage());
	}

	@Test
	public void testMultipleAud() {
		final var audience = new URI[] { URI.create(IdGenerator.generateId()), URI.create(IdGenerator.generateId()) };
		final var jwt = new JwtBuilder<>().aud(audience).build();
		assertTrue(IuIterable.remaindersAreEqual(IuIterable.iter(audience).iterator(), jwt.getAudience().iterator()));
	}
}
