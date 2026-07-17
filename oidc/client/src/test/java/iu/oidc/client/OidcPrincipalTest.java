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
package iu.oidc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.jwt.WebToken;

@SuppressWarnings("javadoc")
public class OidcPrincipalTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	@SuppressWarnings("unchecked")
	@Test
	void testProperties() {
		final var sub = IdGenerator.generateId();
		final var foo = IdGenerator.generateId();
		final var bar = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).claim("foo", foo, String.class).build();
		final var userinfoClaims = IuJson.object().add("sub", sub).add("bar", bar).build();
		final var setCookie = IdGenerator.generateId();

		final var uri = URI.create(IdGenerator.generateId());
		final var accessToken = IdGenerator.generateId();
		final var accessTokenLookup = mock(Function.class);
		when(accessTokenLookup.apply(uri)).thenReturn(accessToken);

		final var principal = new OidcPrincipal(idToken, userinfoClaims, setCookie, accessTokenLookup,
				t -> IuJsonAdapter.adapt(t, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		assertEquals(sub, principal.getName());
		assertEquals(idToken, principal.getIdToken());
		assertEquals(setCookie, principal.getSetCookie());
		assertEquals(accessToken, principal.getAccessToken(uri));
		assertEquals(foo, principal.getClaim("foo", String.class));
		assertEquals(bar, principal.getClaim("bar", String.class));
	}

	@Test
	void testSubVerification() {
		final var sub = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).build();
		assertEquals("userinfo missing sub claim",
				assertThrows(IllegalArgumentException.class,
						() -> new OidcPrincipal(idToken, IuJson.object().build(), null, a -> null,
								t -> IuJsonAdapter.adapt(t, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES)))
						.getMessage());
		assertEquals("userinfo sub claim doesn't match id token", assertThrows(IllegalArgumentException.class,
				() -> new OidcPrincipal(idToken, IuJson.object().add("sub", IdGenerator.generateId()).build(), null,
						a -> null, t -> IuJsonAdapter.adapt(t, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES)))
				.getMessage());

	}

}
