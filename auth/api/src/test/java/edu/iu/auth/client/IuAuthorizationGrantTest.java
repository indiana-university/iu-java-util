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
package edu.iu.auth.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.auth.spi.IuAuthClientSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuAuthorizationGrantTest {

	private MockedStatic<IuAuthSpiFactory> mockSpiFactory;
	private IuAuthClientSpi spi;

	@BeforeEach
	public void setup() {
		spi = mock(IuAuthClientSpi.class);
		mockSpiFactory = mockStatic(IuAuthSpiFactory.class);
		mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuAuthClientSpi.class)).thenReturn(spi);
	}

	@AfterEach
	public void tearDown() {
		mockSpiFactory.close();
		mockSpiFactory = null;
		spi = null;
	}

	@Test
	public void testCreate() {
		final var request = mock(IuAuthorizationRequest.class);
		IuAuthorizationGrant.create(request);
		verify(spi).createClientCredentialsGrant(request);
	}

	@Test
	public void testInitiate() {
		final var request = mock(IuAuthorizationRequest.class);
		final var redirectUri = URI.create(IdGenerator.generateId());
		IuAuthorizationGrant.initiate(request, redirectUri);
		verify(spi).initiateAuthorizationCodeGrant(request, redirectUri);
	}

	@Test
	public void testCreateGrant() {
		final var redirectUri = URI.create(IdGenerator.generateId());
		final var code = IdGenerator.generateId();
		final var state = IdGenerator.generateId();
		IuAuthorizationGrant.complete(redirectUri, code, state);
		verify(spi).completeAuthorizationCodeGrant(redirectUri, code, state);
	}

}
