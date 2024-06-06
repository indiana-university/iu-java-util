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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.security.PrivateKey;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.auth.config.AuthConfig;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebSignature;

@SuppressWarnings("javadoc")
public class JwtVerifierTest {

	@Test
	public void testValid() {
		final var key = mock(WebKey.class);
		final var audience = new SimpleId(key);
		AuthConfig.register(new SimpleVerifier(audience));
		final var issuer = new SimpleId(key);
		AuthConfig.register(new SimpleVerifier(issuer));

		final var sub = IdGenerator.generateId();
		final var jwtRealm = IdGenerator.generateId();
		final var jwt = mock(Jwt.class);
		when(jwt.getAudience()).thenReturn(IuIterable.iter(audience.getName()));
		when(jwt.getIssuer()).thenReturn(issuer.getName());
		when(jwt.getName()).thenReturn(sub);
		when(jwt.getExpires()).thenReturn(Instant.now()); // ok w/ PT15S leeway

		final var jws = mock(WebSignature.class);
		when(jwt.signature()).thenReturn(jws);

//		try (final var mockSpi = mockStatic(JwtSpi.class)) {
//			mockSpi.when(() -> JwtSpi.getIssuer(issuer.getName())).thenReturn(issuer);

		final var verifier = new JwtVerifier(jwtRealm, audience, audience.getName());
		assertEquals(Jwt.class, verifier.getType());
		assertEquals(audience.getName(), verifier.realm());
		assertFalse(verifier.isAuthoritative());
		assertDoesNotThrow(() -> verifier.verify(jwt, jwtRealm));
//		}
	}

	@Test
	public void testAuth() {
		final var key = mock(WebKey.class);
		final var pk = mock(PrivateKey.class);
		when(key.getPrivateKey()).thenReturn(pk);

		final var audience = new SimpleId(key);
		AuthConfig.register(new SimpleVerifier(audience));
		final var issuer = new SimpleId(key);
		AuthConfig.register(new SimpleVerifier(issuer));

		final var sub = IdGenerator.generateId();
		final var jwtRealm = IdGenerator.generateId();
		final var jwt = mock(Jwt.class);
		when(jwt.getAudience()).thenReturn(IuIterable.iter(audience.getName()));
		when(jwt.getIssuer()).thenReturn(issuer.getName());
		when(jwt.getName()).thenReturn(sub);
		when(jwt.getTokenId()).thenReturn(IdGenerator.generateId());

		// PT15s leeway, so min token lifetime is 15 seconds
		when(jwt.getIssuedAt()).thenReturn(Instant.now());
		when(jwt.getNotBefore()).thenReturn(Instant.now());
		when(jwt.getExpires()).thenReturn(Instant.now());

		final var jws = mock(WebSignature.class);
		when(jwt.signature()).thenReturn(jws);

//		try (final var mockSpi = mockStatic(JwtSpi.class)) {
//			mockSpi.when(() -> JwtSpi.getIssuer(issuer.getName())).thenReturn(issuer);

		final var verifier = new JwtVerifier(jwtRealm, audience, audience.getName());
		assertEquals(Jwt.class, verifier.getType());
		assertEquals(audience.getName(), verifier.realm());
		assertTrue(verifier.isAuthoritative());
		assertDoesNotThrow(() -> verifier.verify(jwt, jwtRealm));
//		}
	}

	@Test
	public void testAudValidation() {
		final var jwtRealm = IdGenerator.generateId();
		final var jwt = mock(Jwt.class);
		final var key = mock(WebKey.class);
		final var audience = new SimpleId(key);
		AuthConfig.register(new SimpleVerifier(audience));
		try (final var mockSpi = mockStatic(JwtSpi.class)) {
			final var verifier = new JwtVerifier(jwtRealm, audience, audience.getName());
			assertEquals("audience verification failed",
					assertThrows(IllegalArgumentException.class, () -> verifier.verify(jwt, jwtRealm)).getMessage());
		}
	}

	@Test
	public void testSubValidation() {
		final var key = mock(WebKey.class);
		final var audience = new SimpleId(key);
		AuthConfig.register(new SimpleVerifier(audience));
		final var issuer = new SimpleId(key);
		AuthConfig.register(new SimpleVerifier(issuer));

		final var jwtRealm = IdGenerator.generateId();
		final var jwt = mock(Jwt.class);
		when(jwt.getAudience()).thenReturn(IuIterable.iter(audience.getName()));
		when(jwt.getIssuer()).thenReturn(issuer.getName());

		final var jws = mock(WebSignature.class);
		when(jwt.signature()).thenReturn(jws);

//		try (final var mockSpi = mockStatic(JwtSpi.class)) {
//			mockSpi.when(() -> JwtSpi.getIssuer(issuer.getName())).thenReturn(issuer);

		final var verifier = new JwtVerifier(jwtRealm, audience, audience.getName());
		assertEquals("missing subject principal",
				assertThrows(NullPointerException.class, () -> verifier.verify(jwt, jwtRealm)).getMessage());
//		}
	}

	@Test
	public void testExpMissing() {
		final var key = mock(WebKey.class);
		final var audience = new SimpleId(key);
		AuthConfig.register(new SimpleVerifier(audience));
		final var issuer = new SimpleId(key);
		AuthConfig.register(new SimpleVerifier(issuer));

		final var sub = IdGenerator.generateId();
		final var jwtRealm = IdGenerator.generateId();
		final var jwt = mock(Jwt.class);
		when(jwt.getAudience()).thenReturn(IuIterable.iter(audience.getName()));
		when(jwt.getIssuer()).thenReturn(issuer.getName());
		when(jwt.getName()).thenReturn(sub);

		final var jws = mock(WebSignature.class);
		when(jwt.signature()).thenReturn(jws);

//		try (final var mockSpi = mockStatic(JwtSpi.class)) {
//			mockSpi.when(() -> JwtSpi.getIssuer(issuer.getName())).thenReturn(issuer);

		final var verifier = new JwtVerifier(jwtRealm, audience, audience.getName());
		assertEquals("missing expiration time",
				assertThrows(NullPointerException.class, () -> verifier.verify(jwt, jwtRealm)).getMessage());
//		}
	}

}
