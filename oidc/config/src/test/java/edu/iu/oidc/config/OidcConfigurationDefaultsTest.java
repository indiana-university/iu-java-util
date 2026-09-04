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
package edu.iu.oidc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import edu.iu.crypt.WebKey;
import edu.iu.oidc.IuOidcProviderMetadata;

/**
 * Covers the defaults a configuration source may leave out, using an
 * implementation that declares only the required properties &mdash; the same
 * shape a proxy over a record without the optional ones produces.
 */
@SuppressWarnings("javadoc")
public class OidcConfigurationDefaultsTest {

	/** Declares only the required properties, leaving every default in place. */
	private static OidcProviderConfiguration provider() {
		final var metadata = mock(IuOidcProviderMetadata.class);
		return new OidcProviderConfiguration() {

			@Override
			public IuOidcProviderMetadata getMetadata() {
				return metadata;
			}

			@Override
			public Iterable<WebKey> getJwks() {
				return null;
			}
		};
	}

	@Test
	void testAnAuthorizationCodeLivesOneMinuteByDefault() {
		assertEquals(Duration.ofMinutes(1L), provider().getAuthorizationCodeTimeToLive());
	}

	@Test
	void testAnAccessTokenLivesFifteenMinutesByDefault() {
		// so a token this provider issues isn't refused by a resource server for being
		// too long-lived
		assertEquals(Duration.ofMinutes(15L), provider().getAccessTokenTimeToLive());
	}

	@Test
	void testARefreshTokenLivesTwelveHoursByDefault() {
		assertEquals(Duration.ofHours(12L), provider().getRefreshTokenTimeToLive());
	}

	/** Declares only the required properties, leaving every default in place. */
	private static OidcClientConfiguration client() {
		return new OidcClientConfiguration() {

			@Override
			public String getClientId() {
				return "some-client";
			}

			@Override
			public boolean isEnabled() {
				return true;
			}

			@Override
			public Iterable<String> getAdminRoles() {
				return null;
			}

			@Override
			public Iterable<OidcClientEndpoint> getEndpoints() {
				return null;
			}

			@Override
			public Iterable<OidcClientRole> getRoles() {
				return null;
			}
		};
	}

	@Test
	void testAUserinfoResponseIsAPlainDocumentByDefault() {
		// unlike an ID token, which is always signed: registering no algorithm is what
		// OpenID Connect means by an unsigned UserInfo response
		final var client = client();
		assertNull(client.getUserinfoAlg());
		assertNull(client.getUserinfoEnc());
		assertNull(client.getUserinfoJwk());
	}

}
