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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.IuOidcProviderConfiguration;

@SuppressWarnings("javadoc")
public class OidcProviderMetadataTest {

	private static final URI ISSUER = URI.create("https://example.iu.edu/oidc");

	/**
	 * Properties the wrapper answers for itself, so the delegation sweep below has
	 * to skip them: the five derived endpoints, the two signing algorithm lists,
	 * and the one default the interface already supplies.
	 */
	private static final Set<String> NOT_DELEGATED = Set.of( //
			"getIssuer", "getAuthorizationEndpoint", "getTokenEndpoint", "getUserinfoEndpoint", "getJwksUri",
			"getIdTokenSigningAlgValuesSupported", "getUserinfoSigningAlgValuesSupported",
			"isRequestUriParameterSupported");

	/** Answers a distinct, comparable value for one metadata property. */
	private static Object stub(Method property) {
		final var returnType = property.getReturnType();
		if (URI.class.equals(returnType))
			return URI.create("test:" + property.getName());
		if (boolean.class.equals(returnType))
			return true;
		return List.of(property.getName());
	}

	/** Answers a configuration over one metadata document and key set. */
	private static IuOidcProviderConfiguration provider(IuOidcProviderMetadata metadata, Iterable<WebKey> jwks) {
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

	/** Answers metadata declaring only an issuer, which every endpoint needs. */
	private static IuOidcProviderMetadata issuedBy(URI issuer) {
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getIssuer()).thenReturn(issuer);
		return metadata;
	}

	/** Answers a key configured with one algorithm. */
	private static WebKey key(Algorithm algorithm) {
		final var jwk = mock(WebKey.class);
		when(jwk.getAlgorithm()).thenReturn(algorithm);
		return jwk;
	}

	/** Wraps metadata declaring only an issuer, with no keys configured. */
	private static OidcProviderMetadata metadata(URI issuer) {
		return new OidcProviderMetadata(provider(issuedBy(issuer), null));
	}

	@Test
	void testMetadataIsRequired() {
		final var provider = provider(null, null);
		assertEquals("Missing provider metadata", assertThrows(NullPointerException.class, //
				() -> new OidcProviderMetadata(provider)).getMessage());
	}

	@Test
	void testTheIssuerIsReadFromConfiguration() {
		// only the deployment knows the URI it is reachable at
		assertEquals(ISSUER, metadata(ISSUER).getIssuer());
	}

	@Test
	void testEveryEndpointIsBuiltFromTheIssuer() {
		final var metadata = metadata(ISSUER);
		assertEquals(URI.create("https://example.iu.edu/oidc/authorize"), metadata.getAuthorizationEndpoint());
		assertEquals(URI.create("https://example.iu.edu/oidc/token"), metadata.getTokenEndpoint());
		assertEquals(URI.create("https://example.iu.edu/oidc/id"), metadata.getUserinfoEndpoint());
		assertEquals(URI.create("https://example.iu.edu/oidc/.well-known/jwks"), metadata.getJwksUri());
	}

	@Test
	void testATrailingSlashOnTheIssuerDoesntBecomeAnEmptySegment() {
		assertEquals(URI.create("https://example.iu.edu/oidc/authorize"),
				metadata(URI.create("https://example.iu.edu/oidc/")).getAuthorizationEndpoint());
	}

	@Test
	void testAnEmptyPathNamesTheIssuerItself() {
		assertEquals(ISSUER, OidcProviderMetadata.endpointUri(issuedBy(ISSUER), ""));
	}

	@Test
	void testAnEndpointCantBeNamedWithoutAnIssuer() {
		final var metadata = metadata(null);
		assertEquals("Missing issuer",
				assertThrows(NullPointerException.class, metadata::getTokenEndpoint).getMessage());
	}

	@Test
	void testOnlySigningKeysNameASigningAlgorithm() {
		// a null entry, a key with no algorithm, and an encryption algorithm are all
		// skipped; the rest are named once each, in the order the keys are configured
		final Iterable<WebKey> jwks = Arrays.asList(null, key(null), key(Algorithm.RSA_OAEP), key(Algorithm.ES384),
				key(Algorithm.ES256), key(Algorithm.ES384));

		final var metadata = new OidcProviderMetadata(provider(issuedBy(ISSUER), jwks));
		assertIterableEquals(List.of("ES384", "ES256"), metadata.getIdTokenSigningAlgValuesSupported());

		// one set of issuer keys signs both the ID token and the UserInfo response
		assertIterableEquals(List.of("ES384", "ES256"), metadata.getUserinfoSigningAlgValuesSupported());
	}

	@Test
	void testNoKeysMeansNoSigningAlgorithms() {
		assertIterableEquals(List.of(), metadata(ISSUER).getIdTokenSigningAlgValuesSupported());
	}

	/**
	 * Names the metadata properties, skipping the synthetic members instrumentation
	 * adds to the interface at run time.
	 */
	private static Iterable<Method> properties() {
		final List<Method> properties = new ArrayList<>();
		for (final var method : IuOidcProviderMetadata.class.getDeclaredMethods())
			if (Modifier.isPublic(method.getModifiers()) //
					&& !Modifier.isStatic(method.getModifiers()) //
					&& !method.isSynthetic() //
					&& method.getParameterCount() == 0)
				properties.add(method);
		return properties;
	}

	@Test
	void testDelegatesEveryPropertyItDoesntDerive() throws Exception {
		final var configured = mock(IuOidcProviderMetadata.class,
				withSettings().defaultAnswer(a -> stub(a.getMethod())));
		final var metadata = new OidcProviderMetadata(provider(configured, null));

		final Set<String> delegated = new LinkedHashSet<>();
		for (final var property : properties())
			if (!NOT_DELEGATED.contains(property.getName())) {
				assertEquals(stub(property), property.invoke(metadata), property.getName());
				delegated.add(property.getName());
			}

		// a name misspelled in NOT_DELEGATED would skip a property the sweep is here to
		// check, and pass for having checked nothing
		final Set<String> names = new LinkedHashSet<>();
		for (final var property : properties())
			names.add(property.getName());
		assertTrue(names.containsAll(NOT_DELEGATED), NOT_DELEGATED::toString);
		assertFalse(delegated.isEmpty());
	}

	@Test
	void testWhatItDerivesOutranksWhatWasConfigured() {
		final var configured = issuedBy(ISSUER);
		// a configured endpoint and algorithm the deployment doesn't get to choose
		when(configured.getTokenEndpoint()).thenReturn(URI.create("https://elsewhere.iu.edu/token"));
		when(configured.getIdTokenSigningAlgValuesSupported()).thenReturn(List.of("HS256"));
		when(configured.getScopesSupported()).thenReturn(List.of("openid", "profile"));

		final var metadata = new OidcProviderMetadata(provider(configured, List.of(key(Algorithm.ES256))));
		assertEquals(URI.create("https://example.iu.edu/oidc/token"), metadata.getTokenEndpoint());
		assertIterableEquals(List.of("ES256"), metadata.getIdTokenSigningAlgValuesSupported());

		// everything else passes through, so a property added to the configuration
		// reaches the discovery document without a code change
		assertIterableEquals(List.of("openid", "profile"), metadata.getScopesSupported());
	}

}
