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
package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import edu.iu.IdGenerator;
import edu.iu.auth.oauth.IuCallerAttributes;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class SelfIssuedAccessTokenTest {

	@BeforeEach
	void setup() {
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
	}

	@Test
	public void testIssueAndVerify() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).ephemeral().keyId(kid).build();
		final var audience = URI.create(IdGenerator.generateId());
		final var tokenTtl = Duration.ofSeconds(15L);
		final var caller = mock(IuCallerAttributes.class, CALLS_REAL_METHODS);
		final var authnPrincipal = IdGenerator.generateId();
		final var remoteAddr = IdGenerator.generateId();
		final var requestUri = URI.create(IdGenerator.generateId());
		final var userAgent = IdGenerator.generateId();
		when(caller.getAuthnPrincipal()).thenReturn(authnPrincipal);
		when(caller.getRemoteAddr()).thenReturn(remoteAddr);
		when(caller.getRequestUri()).thenReturn(requestUri);
		when(caller.getUserAgent()).thenReturn(userAgent);

		final var accessToken = new SelfIssuedAccessToken(jwk, issuer, audience, tokenTtl, caller);
		assertDoesNotThrow(accessToken::toString);
		assertEquals(authnPrincipal, accessToken.getName());
		assertEquals(issuer.toString(), accessToken.getIssuer());
		final var iat = accessToken.getIssuedAt();
		assertNotNull(iat);
		assertEquals(tokenTtl, Duration.between(iat, accessToken.getExpires()));
		assertEquals(iat, accessToken.getAuthTime());
		assertNotNull(accessToken.getAccessToken().getTokenId());

		final var sub = accessToken.getSubject();
		assertEquals(Set.of(accessToken), sub.getPrincipals());
		final var vcaller = sub.getPublicCredentials(IuCallerAttributes.class).iterator().next();
		assertEquals(authnPrincipal, vcaller.getAuthnPrincipal());
		assertEquals(remoteAddr, vcaller.getRemoteAddr());
		assertEquals(requestUri, vcaller.getRequestUri());
		assertEquals(userAgent, vcaller.getUserAgent());

		final var authMatcher = new ArgumentMatcher<String>() {
			String token;

			@Override
			public boolean matches(String argument) {
				final var rv = argument.startsWith("Bearer ");
				if (rv)
					token = argument.substring(7);
				return rv;
			}
		};
		final var rb = mock(HttpRequest.Builder.class);
		assertDoesNotThrow(() -> accessToken.applyTo(rb));
		verify(rb).header(eq("Authorization"), argThat(authMatcher));

		final var verifiedToken = new SelfIssuedAccessToken(jwk, issuer, audience, tokenTtl, authMatcher.token);
		assertEquals(authnPrincipal, verifiedToken.getName());
		assertEquals(issuer.toString(), verifiedToken.getIssuer());
		final var verifiedIat = verifiedToken.getIssuedAt();
		assertNotNull(verifiedIat);
		assertEquals(tokenTtl, Duration.between(verifiedIat, verifiedToken.getExpires()));
		assertEquals(iat, verifiedToken.getAuthTime());

		final var verifiedSub = verifiedToken.getSubject();
		assertEquals(Set.of(verifiedToken), verifiedSub.getPrincipals());
		final var verifiedVcaller = verifiedSub.getPublicCredentials(IuCallerAttributes.class).iterator().next();
		assertEquals(authnPrincipal, verifiedVcaller.getAuthnPrincipal());
		assertEquals(remoteAddr, verifiedVcaller.getRemoteAddr());
		assertEquals(requestUri, verifiedVcaller.getRequestUri());
		assertEquals(userAgent, verifiedVcaller.getUserAgent());
	}

	@Test
	void testImpersonated() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).ephemeral().keyId(kid).build();
		final var audience = URI.create(IdGenerator.generateId());
		final var tokenTtl = Duration.ofSeconds(15L);
		final var caller = mock(IuCallerAttributes.class, CALLS_REAL_METHODS);
		final var authnPrincipal = IdGenerator.generateId();
		final var impersonatedPrincipal = IdGenerator.generateId();
		final var remoteAddr = IdGenerator.generateId();
		final var requestUri = URI.create(IdGenerator.generateId());
		final var userAgent = IdGenerator.generateId();
		when(caller.getAuthnPrincipal()).thenReturn(authnPrincipal);
		when(caller.getImpersonatedPrincipal()).thenReturn(impersonatedPrincipal);
		when(caller.getRemoteAddr()).thenReturn(remoteAddr);
		when(caller.getRequestUri()).thenReturn(requestUri);
		when(caller.getUserAgent()).thenReturn(userAgent);

		final var accessToken = new SelfIssuedAccessToken(jwk, issuer, audience, tokenTtl, caller);
		assertEquals(impersonatedPrincipal, accessToken.getName());

		final var authMatcher = new ArgumentMatcher<String>() {
			String token;

			@Override
			public boolean matches(String argument) {
				final var rv = argument.startsWith("Bearer ");
				if (rv)
					token = argument.substring(7);
				return rv;
			}
		};
		final var rb = mock(HttpRequest.Builder.class);
		assertDoesNotThrow(() -> accessToken.applyTo(rb));
		verify(rb).header(eq("Authorization"), argThat(authMatcher));
		final var verifiedToken = new SelfIssuedAccessToken(jwk, issuer, audience, tokenTtl, authMatcher.token);

		assertEquals(impersonatedPrincipal, verifiedToken.getName());
		assertEquals(authMatcher.token, verifiedToken.getBearerToken());
	}

	@Test
	void testSubMismatch() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var jwk = WebKey.builder(Algorithm.EDDSA).ephemeral().keyId(issuer.toString()).build();
		final var audience = URI.create(IdGenerator.generateId());
		final var tokenTtl = Duration.ofSeconds(15L);
		final var caller = mock(IuCallerAttributes.class, CALLS_REAL_METHODS);
		final var authnPrincipal = IdGenerator.generateId();
		final var impersonatedPrincipal = IdGenerator.generateId();
		final var remoteAddr = IdGenerator.generateId();
		final var requestUri = URI.create(IdGenerator.generateId());
		final var userAgent = IdGenerator.generateId();
		when(caller.getAuthnPrincipal()).thenReturn(authnPrincipal);
		when(caller.getImpersonatedPrincipal()).thenReturn(impersonatedPrincipal);
		when(caller.getRemoteAddr()).thenReturn(remoteAddr);
		when(caller.getRequestUri()).thenReturn(requestUri);
		when(caller.getUserAgent()).thenReturn(userAgent);

		final var accessToken = RemoteAccessToken.builder().jti() //
				.iss(issuer).aud(audience).sub(IdGenerator.generateId()) //
				.iat().exp(Instant.now().plus(tokenTtl)) //
				.caller(caller).build();
		final var bearerToken = accessToken.sign("JWT", jwk.getAlgorithm(), jwk);

		assertThrows(IllegalArgumentException.class,
				() -> new SelfIssuedAccessToken(jwk, issuer, audience, tokenTtl, bearerToken));
	}

}
