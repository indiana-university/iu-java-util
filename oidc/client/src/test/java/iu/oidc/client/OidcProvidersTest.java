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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.test.IuTestLogger;
import iu.oidc.client.config.IuOidcProvider;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class OidcProvidersTest {

	@Test
	void testDirect() {
		final var metadata = mock(IuOidcProviderMetadata.class);
		final var provider = mock(IuOidcProvider.class);
		when(provider.getMetadata()).thenReturn(metadata);
		assertEquals(metadata, OidcProviders.getMetadata(provider));
	}

	@Test
	void testReadsFromUri() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var metadataUri = URI.create(IdGenerator.generateId());
		final var metadataTtl = Duration.ofSeconds(1L);
		final var provider = mock(IuOidcProvider.class);
		when(provider.getIssuer()).thenReturn(issuer);
		when(provider.getMetadataUri()).thenReturn(metadataUri);
		when(provider.getMetadataTtl()).thenReturn(metadataTtl);

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT))
				.thenThrow(IllegalStateException.class);
		assertThrows(IllegalStateException.class, () -> OidcProviders.getMetadata(provider));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)) //
				.thenReturn(IuJson.object() //
						.add("issuer", issuer.toString()) //
						.build());
		assertEquals(issuer, OidcProviders.getMetadata(provider).getIssuer());
		assertEquals(issuer, OidcProviders.getMetadata(provider).getIssuer());

		assertDoesNotThrow(() -> Thread.sleep(1000L));
		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT))
				.thenThrow(IllegalStateException.class);

		IuTestLogger.expect(OidcProviders.class.getName(), Level.INFO,
				"OIDC provider metadata lookup failure " + metadataUri + "; using last good version",
				IllegalStateException.class);
		assertEquals(issuer, OidcProviders.getMetadata(provider).getIssuer());
	}

}
