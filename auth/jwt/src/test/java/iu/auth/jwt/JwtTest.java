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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.auth.config.AuthConfig;
import edu.iu.auth.config.IuPublicKeyPrincipalConfig;
import edu.iu.client.IuJson;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class JwtTest {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testClaims() throws Exception {
		final var realm = IdGenerator.generateId();
		final var alg = IuTest.rand(Algorithm.class);
		final var iss = IdGenerator.generateId();
		final var sub = IdGenerator.generateId();
		final var aud = IdGenerator.generateId();
		final var jti = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var jku = URI.create("test:" + IdGenerator.generateId());
		final var iat = Instant.now().minusSeconds(2L).truncatedTo(ChronoUnit.SECONDS);
		final var nbf = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var exp = Instant.now().plusSeconds(2L).truncatedTo(ChronoUnit.SECONDS);
		final var token = IdGenerator.generateId();
		try (final var mockJws = mockStatic(WebSignedPayload.class)) {
			final var jws = mock(WebSignedPayload.class);
			when(jws.getPayload()).thenReturn(IuText.utf8(IuJson.object() //
					.add("iss", iss) //
					.add("sub", sub) //
					.add("aud", aud) //
					.add("jti", jti) //
					.add("iat", iat.getEpochSecond()) //
					.add("nbf", nbf.getEpochSecond()) //
					.add("exp", exp.getEpochSecond()) //
					.add("nonce", nonce) //
					.add("jku", jku.toString()) //
					.build().toString()));

			final var sig = mock(WebSignature.class);
			final var jose = mock(WebCryptoHeader.class);
			when(jose.getAlgorithm()).thenReturn(alg);
			when(sig.getHeader()).thenReturn(jose);
			when(jws.getSignatures()).thenReturn((List) List.of(sig));

			mockJws.when(() -> WebSignedPayload.parse(token)).thenReturn(jws);
			final var jwt = new Jwt(realm, token);
			assertEquals(alg.name(), jwt.getAlgorithm());
			assertEquals(iss, jwt.getIssuer());
			assertEquals(sub, jwt.getName());
			assertEquals(aud, jwt.getAudience().iterator().next());
			assertEquals(jti, jwt.getTokenId());
			assertEquals(iat, jwt.getIssuedAt());
			assertEquals(nbf, jwt.getNotBefore());
			assertEquals(exp, jwt.getExpires());
			assertEquals(Set.of(jwt), jwt.getSubject().getPrincipals());
			assertEquals(nonce, jwt.getClaim("nonce"));
			assertEquals(jku, jwt.getClaim("jku", URI.class));
			assertEquals("Jwt [alg=" + alg + ", iss=" + iss + ", sub=" + sub + ", jti=" + jti + ", aud=[" + aud
					+ "], iat=" + iat + ", nbf=" + nbf + ", exp=" + exp + "]", jwt.toString());

			final Jwt serialCopy;
			final var b = new ByteArrayOutputStream();
			try (final var o = new ObjectOutputStream(b)) {
				o.writeObject(jwt);
			}
			try (final var i = new ObjectInputStream(new ByteArrayInputStream(b.toByteArray()))) {
				serialCopy = (Jwt) i.readObject();
			}
			assertEquals(realm, serialCopy.realm());
			assertEquals(token, serialCopy.token());
			assertEquals(alg.name(), serialCopy.getAlgorithm());
			assertEquals(iss, serialCopy.getIssuer());
			assertEquals(sub, serialCopy.getName());
			assertEquals(aud, serialCopy.getAudience().iterator().next());
			assertEquals(jti, serialCopy.getTokenId());
			assertEquals(iat, serialCopy.getIssuedAt());
			assertEquals(nbf, serialCopy.getNotBefore());
			assertEquals(exp, serialCopy.getExpires());
			assertEquals(Set.of(serialCopy), serialCopy.getSubject().getPrincipals());
			assertEquals(nonce, serialCopy.getClaim("nonce"));
			assertEquals(jku, serialCopy.getClaim("jku", URI.class));
			assertArrayEquals(jwt.payload(), serialCopy.payload());
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testNotCompact() {
		final var token = IdGenerator.generateId();
		try (final var mockJws = mockStatic(WebSignedPayload.class)) {
			final var jws = mock(WebSignedPayload.class);
			final var sig = mock(WebSignature.class);
			final var sig2 = mock(WebSignature.class);
			when(jws.getSignatures()).thenReturn((List) List.of(sig, sig2));
			mockJws.when(() -> WebSignedPayload.parse(token)).thenReturn(jws);
			assertThrows(IllegalArgumentException.class, () -> new Jwt(IdGenerator.generateId(), token));
		}
	}

	@Test
	public void testStringOrUri() {
		final var s = IdGenerator.generateId();
		assertEquals(s, Jwt.STRING_OR_URI.fromJson(IuJson.string(s)));
		assertEquals("test:" + s, Jwt.STRING_OR_URI.fromJson(IuJson.string("test:" + s)));
	}

	@Test
	public void testNumericDate() {
		final var i = Instant.now();
		assertEquals(i.truncatedTo(ChronoUnit.SECONDS), Jwt.NUMERIC_DATE.fromJson(Jwt.NUMERIC_DATE.toJson(i)));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testBearer() {
		final var token = IdGenerator.generateId();
		try (final var mockJws = mockStatic(WebSignedPayload.class);
				final var mockBearer = mockConstruction(JwtBearer.class)) {
			final var jws = mock(WebSignedPayload.class);
			when(jws.getPayload()).thenReturn(IuText.utf8(IuJson.object() //
					.build().toString()));
			final var sig = mock(WebSignature.class);
			when(jws.getSignatures()).thenReturn((List) List.of(sig));
			mockJws.when(() -> WebSignedPayload.parse(token)).thenReturn(jws);
			final var jwt = new Jwt(IdGenerator.generateId(), token);
			final var scope = mock(Set.class);
			final var bearer = jwt.asBearerToken(scope);
			assertSame(mockBearer.constructed().get(0), bearer);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testAuthorizationGrant() {
		final var iss = IdGenerator.generateId();
		final var key = EphemeralKeys.secret(Algorithm.HS256.algorithm, Algorithm.HS256.size);
		final var aud = IdGenerator.generateId();
		final var sub = IdGenerator.generateId();
		AuthConfig.register(new JwkSecretVerifier(new JwkSecret(iss, key)));
		AuthConfig.seal();

		final var claims = IuJson.object();
		claims.add("iss", iss);
		claims.add("aud", aud);
		claims.add("sub", sub);
		claims.add("exp", Instant.now().getEpochSecond() + 5);
		final var token = WebSignature.builder(Algorithm.HS256).compact().type("JWT") //
				.key(AuthConfig.<IuPublicKeyPrincipalConfig>get(iss).getIdentity().getSubject()
						.getPrivateCredentials(WebKey.class).stream().filter(a -> a.getUse().equals()).next())
				.sign(claims.build().toString()).compact();

		final var jwt = new Jwt(IdGenerator.generateId(), token);
		assertEquals("HS256", jwt.getAlgorithm());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testClientAssertion() {
		final var token = IdGenerator.generateId();
		try (final var mockJws = mockStatic(WebSignedPayload.class);
				final var mockClientAssertion = mockConstruction(JwtClientAssertion.class)) {
			final var jws = mock(WebSignedPayload.class);
			when(jws.getPayload()).thenReturn(IuText.utf8(IuJson.object() //
					.build().toString()));
			final var sig = mock(WebSignature.class);
			when(jws.getSignatures()).thenReturn((List) List.of(sig));
			mockJws.when(() -> WebSignedPayload.parse(token)).thenReturn(jws);
			final var jwt = new Jwt(IdGenerator.generateId(), token);
			final var params = mock(Map.class);
			final var clientAssertion = jwt.asClientAssertion(params);
			assertSame(mockClientAssertion.constructed().get(0), clientAssertion);
		}
	}

}
