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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import edu.iu.oidc.config.IuOidcClientConfiguration;
import edu.iu.oidc.config.IuOidcClientEndpoint;
import edu.iu.oidc.config.IuOidcClientResource;

@SuppressWarnings("javadoc")
public class OidcProviderUtilsTest {

	private static final URI ISSUER = URI.create("https://example.iu.edu/oidc");
	private static final URI EXTERNAL = URI.create("https://api.example.iu.edu");

	/** Answers a resource entry; a null URI names this provider's own issuer. */
	private static IuOidcClientResource resource(URI uri, Set<String> scope) {
		final var resource = mock(IuOidcClientResource.class);
		when(resource.getUri()).thenReturn(uri);
		when(resource.getScope()).thenReturn(scope);
		return resource;
	}

	private static IuOidcClientEndpoint endpoint(Iterable<IuOidcClientResource> resources) {
		final var endpoint = mock(IuOidcClientEndpoint.class);
		when(endpoint.getResources()).thenReturn(resources);
		return endpoint;
	}

	private static IuOidcClientEndpoint endpointAt(URI redirectUri) {
		final var endpoint = mock(IuOidcClientEndpoint.class);
		when(endpoint.getRedirectUri()).thenReturn(redirectUri);
		return endpoint;
	}

	private static IuOidcClientConfiguration client(Iterable<IuOidcClientEndpoint> endpoints) {
		final var client = mock(IuOidcClientConfiguration.class);
		when(client.getEndpoints()).thenReturn(endpoints);
		return client;
	}

	@Test
	void testScopeSplitsOnSpaces() {
		assertIterableEquals(List.of(), OidcProviderUtils.scopes(null));
		assertIterableEquals(List.of(), OidcProviderUtils.scopes(""));
		assertIterableEquals(List.of("openid", "email"), OidcProviderUtils.scopes("openid email"));
		// repeated spaces don't become a scope, and a repeat is named once
		assertIterableEquals(List.of("openid", "email"), OidcProviderUtils.scopes(" openid  email openid "));
	}

	@Test
	void testQueryAppendsToWhateverIsAlreadyThere() {
		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		params.put("code", IuIterable.iter("abc"));

		assertEquals(URI.create("https://client.example.iu.edu/cb?code=abc"),
				OidcProviderUtils.appendQuery(URI.create("https://client.example.iu.edu/cb"), params));
		assertEquals(URI.create("https://client.example.iu.edu/cb?x=1&code=abc"),
				OidcProviderUtils.appendQuery(URI.create("https://client.example.iu.edu/cb?x=1"), params));
	}

	@Test
	void testAnErrorRedirectEchoesStateOnlyWhenThereIsOne() {
		final var redirectUri = URI.create("https://client.example.iu.edu/cb");
		assertEquals(URI.create("https://client.example.iu.edu/cb?error=invalid_scope&error_description=nope"),
				OidcProviderUtils.errorUri(redirectUri, "invalid_scope", "nope", null));
		assertEquals(URI.create("https://client.example.iu.edu/cb?error=invalid_scope&error_description=nope&state=s1"),
				OidcProviderUtils.errorUri(redirectUri, "invalid_scope", "nope", "s1"));
	}

	@Test
	void testARefusalNamesTheErrorAndWhy() {
		assertEquals("invalid_client; Unregistered client_id",
				OidcProviderUtils.deny("invalid_client", "Unregistered client_id").getMessage());
	}

	@Test
	void testAResourceMustBeAnAbsoluteUriWithNoFragment() {
		assertTrue(OidcProviderUtils.isValidResource("https://api.example.iu.edu"));
		assertFalse(OidcProviderUtils.isValidResource("/relative"));
		assertFalse(OidcProviderUtils.isValidResource("https://api.example.iu.edu#part"));
		assertFalse(OidcProviderUtils.isValidResource("not a uri"));
	}

	@Test
	void testAnEntryWithNoUriNamesTheIssuer() {
		final var endpoint = endpoint(Arrays.asList(null, resource(null, Set.of("openid"))));
		assertTrue(OidcProviderUtils.isRegisteredResource(endpoint, ISSUER, ISSUER.toString()));
		assertFalse(OidcProviderUtils.isRegisteredResource(endpoint, ISSUER, EXTERNAL.toString()));
	}

	@Test
	void testAnEntryWithAUriIsNamedByIt() {
		final var endpoint = endpoint(List.of(resource(EXTERNAL, Set.of("read"))));
		assertTrue(OidcProviderUtils.isRegisteredResource(endpoint, ISSUER, EXTERNAL.toString()));
	}

	@Test
	void testAnEndpointRegisteringNothingRegistersNoResource() {
		assertFalse(OidcProviderUtils.isRegisteredResource(endpoint(null), ISSUER, ISSUER.toString()));
	}

	@Test
	void testScopeInfersTheResourcesItIsRegisteredFor() {
		final var endpoint = endpoint(Arrays.asList( //
				null, //
				resource(null, null), // no scope at all
				resource(URI.create("https://other.example.iu.edu"), Set.of("write")), // no overlap
				resource(null, Set.of("openid")), // overlaps, names the issuer
				resource(EXTERNAL, Set.of("read")))); // overlaps, names itself

		assertIterableEquals(List.of(ISSUER.toString(), EXTERNAL.toString()),
				OidcProviderUtils.resourcesGrantingScope(endpoint, ISSUER, Set.of("openid", "read")));
	}

	@Test
	void testAnEndpointRegisteringNothingGrantsNoScope() {
		assertIterableEquals(List.of(), OidcProviderUtils.resourcesGrantingScope(endpoint(null), ISSUER, Set.of("a")));
	}

	@Test
	void testNamingNoResourceMatchesTheEntryWithNoUri() {
		final var self = resource(null, Set.of("openid"));
		final var endpoint = endpoint(Arrays.asList(null, resource(EXTERNAL, Set.of("read")), self));

		final var granted = OidcProviderUtils.grantedResources(endpoint, ISSUER, Set.of());
		assertEquals(1, granted.size());
		assertSame(self, granted.iterator().next());
	}

	@Test
	void testNamingAResourceSelectsIt() {
		final var external = resource(EXTERNAL, Set.of("read"));
		final var self = resource(null, Set.of("openid"));
		final var endpoint = endpoint(List.of(external, self));

		assertIterableEquals(List.of(external),
				OidcProviderUtils.grantedResources(endpoint, ISSUER, Set.of(EXTERNAL.toString())));

		// the entry with no URI is selected by naming the issuer explicitly
		assertIterableEquals(List.of(self),
				OidcProviderUtils.grantedResources(endpoint, ISSUER, Set.of(ISSUER.toString())));
	}

	@Test
	void testAMatchingEntryGrantingNoScopeContributesNothing() {
		assertIterableEquals(List.of(),
				OidcProviderUtils.grantedResources(endpoint(List.of(resource(null, null))), ISSUER, Set.of()));
		assertIterableEquals(List.of(),
				OidcProviderUtils.grantedResources(endpoint(List.of(resource(null, Set.of()))), ISSUER, Set.of()));
	}

	@Test
	void testAnEndpointRegisteringNothingGrantsNoResource() {
		assertIterableEquals(List.of(), OidcProviderUtils.grantedResources(endpoint(null), ISSUER, Set.of()));
	}

	@Test
	void testTheRedirectUriMustMatchExactly() {
		final var registered = URI.create("https://client.example.iu.edu/cb");
		final var endpoint = endpointAt(registered);
		final var client = client(Arrays.asList(null, endpointAt(null), endpoint));

		assertSame(endpoint, OidcProviderUtils.registeredEndpoint(client, registered.toString()));
		// a URI that merely starts with a registered value doesn't match
		assertNull(OidcProviderUtils.registeredEndpoint(client, registered + "/more"));
	}

	@Test
	void testNoRedirectUriMatchesNoEndpoint() {
		assertNull(OidcProviderUtils.registeredEndpoint(client(List.of()), null));
	}

	@Test
	void testAClientRegisteringNoEndpointMatchesNone() {
		assertNull(OidcProviderUtils.registeredEndpoint(client(null), "https://client.example.iu.edu/cb"));
	}

	@Test
	void testTheAudienceIsWhatRegistersAGrantedScope() {
		final var endpoint = endpoint(
				Arrays.asList(null, resource(EXTERNAL, Set.of("read")), resource(null, Set.of("openid"))));

		// the entry with no URI names this provider, so granting openid addresses the
		// token to the issuer without the registration naming it
		assertIterableEquals(List.of(ISSUER), OidcProviderUtils.audience(ISSUER, endpoint, Set.of(), Set.of("openid")));
		assertIterableEquals(List.of(EXTERNAL), OidcProviderUtils.audience(ISSUER, endpoint, Set.of(), Set.of("read")));
		assertIterableEquals(List.of(EXTERNAL, ISSUER),
				OidcProviderUtils.audience(ISSUER, endpoint, Set.of(), Set.of("read", "openid")));
	}

	@Test
	void testNamingAResourceNarrowsTheAudienceToIt() {
		final var endpoint = endpoint(List.of(resource(EXTERNAL, Set.of("read")), resource(null, Set.of("read"))));

		assertIterableEquals(List.of(EXTERNAL),
				OidcProviderUtils.audience(ISSUER, endpoint, Set.of(EXTERNAL.toString()), Set.of("read")));
		assertIterableEquals(List.of(ISSUER),
				OidcProviderUtils.audience(ISSUER, endpoint, Set.of(ISSUER.toString()), Set.of("read")));
	}

	@Test
	void testAScopeNoResourceRegistersAddressesNothing() {
		// answering with the issuer anyway would grant an audience nobody configured
		final var endpoint = endpoint(List.of(resource(EXTERNAL, Set.of("read")), resource(null, null)));
		assertIterableEquals(List.of(), OidcProviderUtils.audience(ISSUER, endpoint, Set.of(), Set.of("openid")));
		assertIterableEquals(List.of(), OidcProviderUtils.audience(ISSUER, endpoint(null), Set.of(), Set.of("read")));
	}

	@Test
	void testClientCredentialsGrantsEveryScopeTheResourceRegisters() {
		final var endpoint = endpoint(List.of(resource(null, Set.of("read")), resource(EXTERNAL, Set.of("write"))));

		// naming no resource selects the entry with no URI, as grantedResources does
		assertIterableEquals(List.of("read"),
				OidcProviderUtils.clientCredentialsScopes(endpoint, ISSUER, null, Set.of()));
		assertIterableEquals(List.of("write"),
				OidcProviderUtils.clientCredentialsScopes(endpoint, ISSUER, null, Set.of(EXTERNAL.toString())));
	}

	@Test
	void testARequestedScopeNarrowsWhatIsGranted() {
		final var endpoint = endpoint(List.of(resource(null, new LinkedHashSet<>(List.of("read", "write")))));

		assertIterableEquals(List.of("read"),
				OidcProviderUtils.clientCredentialsScopes(endpoint, ISSUER, "read", Set.of()));
		// asking for something no resource registers narrows it to nothing rather
		// than refusing
		assertIterableEquals(List.of(),
				OidcProviderUtils.clientCredentialsScopes(endpoint, ISSUER, "admin", Set.of()));
	}

}
