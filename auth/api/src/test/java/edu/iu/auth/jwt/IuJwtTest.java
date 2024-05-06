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
package edu.iu.auth.jwt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.spi.IuJwtSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuJwtTest {

	private MockedStatic<IuAuthSpiFactory> mockSpiFactory;
	private IuJwtSpi spi;

	@BeforeEach
	public void setup() {
		spi = mock(IuJwtSpi.class);
		mockSpiFactory = mockStatic(IuAuthSpiFactory.class);
		mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuJwtSpi.class)).thenReturn(spi);
	}

	@AfterEach
	public void tearDown() {
		mockSpiFactory.close();
		mockSpiFactory = null;
		spi = null;
	}

	@Test
	public void testTrust() {
		final var jwksUri = URI.create("test:" + IdGenerator.generateId());
		final var keyId = IdGenerator.generateId();
		IuWebKey.from(jwksUri, keyId);
		verify(spi).getWebKey(jwksUri, keyId);
	}

	@Test
	public void testParse() {
		final var token = IdGenerator.generateId();
		IuWebToken.from(token);
		verify(spi).parse(token);
	}

	@Test
	public void testIssue() {
		final var iss = IdGenerator.generateId();
		IuWebToken.issue(iss);
		verify(spi).issue(iss);
	}

	@Test
	public void testRegisterIssuer() {
		final var issuer = mock(IuPrincipalIdentity.class);
		IuWebToken.register(issuer);
		verify(spi).register(issuer);
	}

	@Test
	public void testRegisterAudience() {
		final var jwtRealm = IdGenerator.generateId();
		final var realm = IdGenerator.generateId();
		final var audience = mock(IuPrincipalIdentity.class);
		IuWebToken.register(jwtRealm, audience, realm);
		verify(spi).register(jwtRealm, audience, realm);
	}
	
	@Test
	public void testSeal() {
		IuWebToken.seal();
		verify(spi).seal();
	}

}
