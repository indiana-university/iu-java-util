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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Type;
import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oauth.IuCallerAttributes;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;

@SuppressWarnings("javadoc")
public class RemoteAccessTokenBuilderTest {

	@Test
	public void testAdaptNonClass() {
		try (final var mockJsonAdapter = mockStatic(IuJsonAdapter.class)) {
			final var mockType = mock(Type.class);
			final var mockAdapter = mock(IuJsonAdapter.class);
			mockJsonAdapter.when(() -> IuJsonAdapter.of(mockType)).thenReturn(mockAdapter);
			assertSame(mockAdapter, RemoteAccessTokenBuilder.adaptAuthorizationDetails(mockType));
		}
	}

	@Test
	public void testAdaptNonInterface() {
		try (final var mockJsonAdapter = mockStatic(IuJsonAdapter.class)) {
			class A {
			}
			final var mockAdapter = mock(IuJsonAdapter.class);
			mockJsonAdapter.when(() -> IuJsonAdapter.of((Type) A.class)).thenReturn(mockAdapter);
			assertSame(mockAdapter, RemoteAccessTokenBuilder.adaptAuthorizationDetails(A.class));
		}
	}

	@Test
	public void testScope() {
		final var scope = IdGenerator.generateId();
		final var t = new RemoteAccessTokenBuilder<>().scope(scope).build();
		assertEquals(scope, t.getScope());
	}

	@Test
	public void testCallerAttrbutes() {
		final var requestUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var userAgent = IdGenerator.generateId();
		final var authnPrincipal = IdGenerator.generateId();

		final var t = new RemoteAccessTokenBuilder<>() //
				.caller(new IuCallerAttributes() {
					@Override
					public URI getRequestUri() {
						return requestUri;
					}

					@Override
					public String getRemoteAddr() {
						return remoteAddr;
					}

					@Override
					public String getUserAgent() {
						return userAgent;
					}

					@Override
					public String getAuthnPrincipal() {
						return authnPrincipal;
					}
				}) //
				.build();

		final var callerAttributes = t.getCallerAttributes();
		assertEquals(requestUri, callerAttributes.getRequestUri());
		assertEquals(remoteAddr, callerAttributes.getRemoteAddr());
		assertEquals(userAgent, callerAttributes.getUserAgent());
		assertEquals(authnPrincipal, callerAttributes.getAuthnPrincipal());
	}

}
