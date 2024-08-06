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
package edu.iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.spi.IuSamlSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuSamlSessionTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testCreate() {
		final var postUri = mock(URI.class);
		final var entryPointUri = mock(URI.class);
		final var secretKey = mock(Supplier.class);
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			final var mockSpi = mock(IuSamlSpi.class);
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuSamlSpi.class)).thenReturn(mockSpi);
			final var mockSession = mock(IuSamlSession.class);
			when(mockSpi.createSession(entryPointUri, postUri, secretKey)).thenReturn(mockSession);
			assertSame(mockSession, IuSamlSession.create(entryPointUri, postUri, secretKey));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testActivate() {
		final var tokenizedSession = IdGenerator.generateId();
		final var secretKey = mock(Supplier.class);
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			final var mockSpi = mock(IuSamlSpi.class);
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuSamlSpi.class)).thenReturn(mockSpi);
			final var mockSession = mock(IuSamlSession.class);
			when(mockSpi.activateSession(tokenizedSession, secretKey)).thenReturn(mockSession);
			assertSame(mockSession, IuSamlSession.activate(tokenizedSession, secretKey));
		}
	}

}
