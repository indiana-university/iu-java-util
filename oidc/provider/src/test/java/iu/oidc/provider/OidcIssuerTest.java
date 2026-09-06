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
package iu.oidc.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.IuOidcProviderConfiguration;

@SuppressWarnings("javadoc")
public class OidcIssuerTest {

	static {
		edu.iu.crypt.Init.init();
	}

	private static final URI ISSUER = URI.create("https://example.iu.edu/oidc");

	/** Answers a configuration over one issuer and key set. */
	private static IuOidcProviderConfiguration configuration(URI issuer, Iterable<WebKey> jwks) {
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getIssuer()).thenReturn(issuer);

		return new IuOidcProviderConfiguration() {

			@Override
			public IuOidcProviderMetadata getMetadata() {
				return metadata;
			}

			@Override
			public Iterable<WebKey> getJwks() {
				return jwks;
			}
		};
	}

	private static OidcIssuer issuer(Iterable<WebKey> jwks) {
		return new OidcIssuer(() -> configuration(ISSUER, jwks));
	}

	/** Answers a key declaring one algorithm. */
	private static WebKey key(Algorithm algorithm) {
		final var jwk = mock(WebKey.class);
		when(jwk.getAlgorithm()).thenReturn(algorithm);
		return jwk;
	}

	@Test
	void testAConfigurationIsRequired() {
		assertEquals("Missing provider configuration",
				assertThrows(NullPointerException.class, () -> new OidcIssuer(null)).getMessage());
	}

	@Test
	void testASupplierThatAnswersNothingIsAConfigurationFault() {
		final var issuer = new OidcIssuer(() -> null);
		assertEquals("Missing provider configuration",
				assertThrows(NullPointerException.class, issuer::configuration).getMessage());
	}

	@Test
	void testTheConfigurationIsReadAfresh() {
		// a change takes effect on the next request rather than the next deployment
		final var reads = new int[1];
		final var configuration = configuration(ISSUER, null);
		final var issuer = new OidcIssuer(() -> {
			reads[0]++;
			return configuration;
		});

		assertSame(configuration, issuer.configuration());
		assertSame(configuration, issuer.configuration());
		assertEquals(2, reads[0]);
	}

	@Test
	void testItNamesItsOwnIssuer() {
		assertEquals(ISSUER, issuer(null).issuer());
	}

	@Test
	void testAnIssuerIsRequired() {
		final var issuer = new OidcIssuer(() -> configuration(null, null));
		assertEquals("Missing issuer", assertThrows(NullPointerException.class, issuer::issuer).getMessage());
	}

	@Test
	void testItNamesItsOwnEndpoints() {
		final var issuer = issuer(null);
		assertEquals(URI.create("https://example.iu.edu/oidc/authorize"),
				issuer.endpointUri(OidcProviderMetadata.AUTHORIZE_PATH));
		assertEquals(ISSUER, issuer.endpointUri(""));
	}

	@Test
	void testItsMetadataDerivesFromItsConfiguration() {
		assertEquals(URI.create("https://example.iu.edu/oidc/token"), issuer(null).metadata().getTokenEndpoint());
	}

	@Test
	void testTheDefaultKeyIsTheFirstThatCanSign() {
		// a null entry, a key with no algorithm, and an encryption algorithm are all
		// passed over
		final var signing = key(Algorithm.ES256);
		final var issuer = issuer(
				Arrays.asList(null, key(null), key(Algorithm.RSA_OAEP), signing, key(Algorithm.ES384)));

		assertSame(signing, issuer.issuerKey());
	}

	@Test
	void testAnAlgorithmSelectsTheKey() {
		final var es384 = key(Algorithm.ES384);
		final var issuer = issuer(List.of(key(Algorithm.ES256), es384));

		assertSame(es384, issuer.issuerKey(Algorithm.ES384));
	}

	@Test
	void testNoKeysMeansNothingCanSign() {
		assertEquals("No issuer signing key",
				assertThrows(IllegalStateException.class, () -> issuer(null).issuerKey()).getMessage());
	}

	@Test
	void testNoKeyForAnAlgorithmNamesIt() {
		final var issuer = issuer(List.of(key(Algorithm.ES256)));
		assertEquals("No issuer signing key for ES384",
				assertThrows(IllegalStateException.class, () -> issuer.issuerKey(Algorithm.ES384)).getMessage());
	}

	@Test
	void testTheKeySetPublishesOnlyWhatHasAPublicHalf() {
		final var signing = WebKey.builder(Algorithm.ES256).keyId("sign").ephemeral().build();
		final var secret = WebKey.builder(Algorithm.HS256).keyId("secret").key(IuText.utf8("hunter2")).build();

		// a null entry, and a shared secret with no public half to publish, are both
		// skipped rather than emitted as empty entries
		final var published = WebKey.parseJwks(issuer(Arrays.asList(null, signing, secret)).publishedJwks());
		assertIterableEquals(List.of("sign"), IuIterable.map(published, WebKey::getKeyId));

		// and what is published carries no private material, however it was configured
		assertNull(published.iterator().next().getPrivateKey());
	}

	@Test
	void testADeploymentWithNoKeysPublishesAnEmptySet() {
		assertIterableEquals(List.of(), WebKey.parseJwks(issuer(null).publishedJwks()));
	}

}
