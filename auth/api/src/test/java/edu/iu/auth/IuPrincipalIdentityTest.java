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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import javax.security.auth.Subject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.auth.spi.IuPrincipalSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuPrincipalIdentityTest {

	private MockedStatic<IuAuthSpiFactory> mockSpiFactory;
	private IuPrincipalSpi spi;

	@BeforeEach
	public void setup() {
		spi = mock(IuPrincipalSpi.class);
		mockSpiFactory = mockStatic(IuAuthSpiFactory.class);
		mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuPrincipalSpi.class)).thenReturn(spi);
	}

	@AfterEach
	public void tearDown() {
		mockSpiFactory.close();
		mockSpiFactory = null;
		spi = null;
	}

	@Test
	public void testVerify() throws IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var principal = mock(IuPrincipalIdentity.class);
		IuPrincipalIdentity.verify(principal, realm);
		verify(spi).verify(principal, realm);
	}

	@Test
	public void testImpliesSubject() {
		final var principal = mock(IuPrincipalIdentity.class, CALLS_REAL_METHODS);
		final var subject = mock(Subject.class);
		assertFalse(principal.implies(subject));

		when(subject.getPrincipals()).thenReturn(Set.of(principal));
		assertTrue(principal.implies(subject));

		when(principal.getSubject()).thenReturn(subject);
		assertTrue(principal.implies(subject));
	}

}
