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
package edu.iu.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Type;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.auth.jwt.IuWebTokenIssuer;
import edu.iu.auth.jwt.IuWebTokenIssuer.ClaimDefinition;
import edu.iu.auth.spi.IuJwtSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuWebTokenIssuerTest {

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
	public void testRegister() {
		final var issuer = mock(IuWebTokenIssuer.class);
		IuWebTokenIssuer.register(issuer);
		verify(spi).register(issuer);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testStringOrUri() {
		final var verifier = mock(Consumer.class);
		IuWebTokenIssuer.stringOrUri(verifier);
		verify(spi).stringOrUri(verifier);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNumericDate() {
		final var verifier = mock(Consumer.class);
		IuWebTokenIssuer.numericDate(verifier);
		verify(spi).numericDate(verifier);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExtend() {
		final var verifier = mock(BiConsumer.class);
		IuWebTokenIssuer.extend(getClass(), verifier);
		verify(spi).claimDefinition(getClass(), null, verifier);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExtendGeneric() {
		final var type = mock(Type.class);
		final var definition = mock(ClaimDefinition.class);
		final var verifier = mock(BiConsumer.class);
		IuWebTokenIssuer.extend(type, definition, verifier);
		verify(spi).claimDefinition(type, definition, verifier);
	}

}
