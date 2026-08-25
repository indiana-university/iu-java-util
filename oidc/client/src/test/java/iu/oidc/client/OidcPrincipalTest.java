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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcTokenResponse;
import iu.oidc.client.config.IuOidcClientReference;

@SuppressWarnings("javadoc")
public class OidcPrincipalTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	/** Creates a client configuration reference with a generated resource URI. */
	private static IuOidcClientReference config() {
		return config(URI.create("https://" + IdGenerator.generateId() + "/app"));
	}

	/**
	 * Creates a client configuration reference that reports {@code resourceUri} as
	 * the client's own resource and resolves JSON adapters as the real interface
	 * does.
	 */
	private static IuOidcClientReference config(URI resourceUri) {
		final var config = mock(IuOidcClientReference.class);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.adaptJson(String.class))
				.thenReturn(IuJsonAdapter.adapt(String.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));
		return config;
	}

	/** Creates a principal with matching ID token and userinfo sub claims. */
	private static OidcPrincipal principal(IuOidcClientReference config, String accessToken,
			WebToken verifiedAccessToken) {
		final var sub = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).build();
		final var userinfoClaims = IuJson.object().add("sub", sub).build();
		return new OidcPrincipal(idToken, userinfoClaims, null, config, accessToken, verifiedAccessToken, null);
	}

	/** Creates a token response reporting {@code accessToken}. */
	private static IuOidcTokenResponse tokenResponse(String accessToken) {
		final var response = mock(IuOidcTokenResponse.class);
		when(response.getAccessToken()).thenReturn(accessToken);
		return response;
	}

	@Test
	void testProperties() throws Throwable {
		final var sub = IdGenerator.generateId();
		final var foo = IdGenerator.generateId();
		final var bar = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).claim("foo", foo, String.class).build();
		final var userinfoClaims = IuJson.object().add("sub", sub).add("bar", bar).build();
		final var setCookie = IdGenerator.generateId();

		final var resourceUri = URI.create("https://" + IdGenerator.generateId() + "/app");
		final var accessToken = IdGenerator.generateId();

		final var principal = new OidcPrincipal(idToken, userinfoClaims, setCookie, config(resourceUri), accessToken,
				null, null);

		assertEquals(sub, principal.getName());
		assertEquals(idToken, principal.getIdToken());
		assertEquals(setCookie, principal.getSetCookie());
		assertEquals(foo, principal.getClaim("foo", String.class));
		assertEquals(bar, principal.getClaim("bar", String.class));
		assertNull(principal.getClaim("baz", String.class));
		assertDoesNotThrow(principal::toString);
	}

	@Test
	void testSubVerification() {
		final var sub = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).build();
		final var config = mock(IuOidcClientReference.class);

		assertEquals("userinfo missing sub claim",
				assertThrows(IllegalArgumentException.class,
						() -> new OidcPrincipal(idToken, IuJson.object().build(), null, config, null, null, null))
						.getMessage());

		assertEquals("userinfo sub claim doesn't match id token",
				assertThrows(IllegalArgumentException.class,
						() -> new OidcPrincipal(idToken, IuJson.object().add("sub", IdGenerator.generateId()).build(),
								null, config, null, null, null)).getMessage());
	}

	@Test
	void testAlternativePrincipalNameFromUserinfo() {
		final var sub = IdGenerator.generateId();
		final var principalName = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).build();
		final var userinfoClaims = IuJson.object().add("sub", sub).add("preferred_username", principalName).build();

		final var principal = new OidcPrincipal(idToken, userinfoClaims, null, config(), null, null,
				"preferred_username");

		assertEquals(principalName, principal.getName());
		assertEquals(sub, principal.getIdToken().getSubject());
	}

	@Test
	void testAlternativePrincipalNameFromIdToken() {
		final var sub = IdGenerator.generateId();
		final var principalName = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).claim("preferred_username", principalName, String.class).build();
		final var userinfoClaims = IuJson.object().add("sub", sub).build();

		final var principal = new OidcPrincipal(idToken, userinfoClaims, null, config(), null, null,
				"preferred_username");

		assertEquals(principalName, principal.getName());
		assertEquals(sub, principal.getIdToken().getSubject());
	}

	@Test
	void testAlternativePrincipalNameFallbackToSub() {
		final var sub = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).build();
		final var userinfoClaims = IuJson.object().add("sub", sub).build();

		final var principal = new OidcPrincipal(idToken, userinfoClaims, null, config(), null, null,
				"preferred_username");

		assertEquals(sub, principal.getName());
	}

	@Test
	void testAlternativePrincipalNameIdTokenPriority() {
		final var sub = IdGenerator.generateId();
		final var userinfoUsername = IdGenerator.generateId();
		final var idTokenUsername = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).claim("preferred_username", idTokenUsername, String.class)
				.build();
		final var userinfoClaims = IuJson.object().add("sub", sub).add("preferred_username", userinfoUsername).build();

		final var principal = new OidcPrincipal(idToken, userinfoClaims, null, config(), null, null,
				"preferred_username");

		assertEquals(idTokenUsername, principal.getName());
	}

	@Test
	void testAccessTokenUsedDirectlyForClientResource() throws Exception {
		final var resourceUri = URI.create("https://" + IdGenerator.generateId() + "/app");
		final var accessToken = IdGenerator.generateId();
		final var principal = principal(config(resourceUri), accessToken, null);

		try (final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class,
				(a, ctx) -> fail("must not exchange the access token for the client's own resource"))) {
			assertEquals(accessToken, principal.getAccessToken(resourceUri));
			assertEquals(accessToken, principal.getAccessToken(URI.create(resourceUri + "/api")));
			assertEquals(0, mockOboGrant.constructed().size());
		}
	}

	@Test
	void testAccessTokenUsedDirectlyForVerifiedAudience() throws Exception {
		final var config = config();

		// the first audience doesn't cover the resource, the second does
		final var otherAudience = URI.create("api://" + IdGenerator.generateId());
		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		final var verifiedAccessToken = mock(WebToken.class);
		when(verifiedAccessToken.getAudience()).thenReturn(IuIterable.iter(otherAudience, apiResource));

		final var accessToken = IdGenerator.generateId();
		final var principal = principal(config, accessToken, verifiedAccessToken);

		try (final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class,
				(a, ctx) -> fail("must not exchange when the verified audience already covers the resource"))) {
			assertEquals(accessToken, principal.getAccessToken(URI.create(apiResource + "/v1")));
			assertEquals(0, mockOboGrant.constructed().size());
		}
	}

	@Test
	void testAccessTokenExchangedWhenVerifiedAudienceDoesntCoverResource() throws Exception {
		final var config = config();

		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		when(config.getApiResources()).thenReturn(IuIterable.iter(apiResource));

		final var verifiedAccessToken = mock(WebToken.class);
		when(verifiedAccessToken.getAudience())
				.thenReturn(IuIterable.iter(URI.create("api://" + IdGenerator.generateId())));

		final var accessToken = IdGenerator.generateId();
		final var apiAccessToken = IdGenerator.generateId();
		final var principal = principal(config, accessToken, verifiedAccessToken);

		try (final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class, (a, ctx) -> {
			assertSame(config, ctx.arguments().get(0));
			assertEquals(apiResource, ctx.arguments().get(1));
			assertEquals(accessToken, ctx.arguments().get(2));
			final var response = tokenResponse(apiAccessToken);
			when(a.getTokenResponse()).thenReturn(response);
		})) {
			assertEquals(apiAccessToken, principal.getAccessToken(apiResource));
			assertEquals(1, mockOboGrant.constructed().size());
		}
	}

	@Test
	void testAccessTokenExchangedWhenVerifiedTokenHasNoAudience() throws Exception {
		final var config = config();

		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		when(config.getApiResources()).thenReturn(IuIterable.iter(apiResource));

		final var verifiedAccessToken = mock(WebToken.class);
		when(verifiedAccessToken.getAudience()).thenReturn(null);

		final var apiAccessToken = IdGenerator.generateId();
		final var principal = principal(config, IdGenerator.generateId(), verifiedAccessToken);

		try (final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class, (a, ctx) -> {
			final var response = tokenResponse(apiAccessToken);
			when(a.getTokenResponse()).thenReturn(response);
		})) {
			assertEquals(apiAccessToken, principal.getAccessToken(apiResource));
			assertEquals(1, mockOboGrant.constructed().size());
		}
	}

	@Test
	void testAccessTokenExchangeSelectsMostSpecificApiResource() throws Exception {
		final var config = config();

		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		final var apiResourcev1 = URI.create(apiResource + "/v1");
		final var apiResourcev2 = URI.create(apiResource + "/v2");

		// deliberately unordered, so selection can't depend on iteration order
		when(config.getApiResources()).thenReturn(IuIterable.iter(apiResourcev2, apiResource, apiResourcev1));

		final var principal = principal(config, IdGenerator.generateId(), null);

		try (final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class, (a, ctx) -> {
			// echo the API resource the grant was created for
			final var response = tokenResponse(ctx.arguments().get(1).toString());
			when(a.getTokenResponse()).thenReturn(response);
		})) {

			// the versioned resources are more specific than their shared root
			assertEquals(apiResourcev1.toString(), principal.getAccessToken(URI.create(apiResourcev1 + "/get")));
			assertEquals(apiResourcev2.toString(), principal.getAccessToken(apiResourcev2));

			// nothing more specific than the root covers this one
			assertEquals(apiResource.toString(), principal.getAccessToken(URI.create(apiResource + "/v3")));

			assertEquals(3, mockOboGrant.constructed().size());
		}
	}

	@Test
	void testAccessTokenExchangeCachesGrantPerApiResource() throws Exception {
		final var config = config();

		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		final var apiResourcev1 = URI.create(apiResource + "/v1");
		final var apiResourcev2 = URI.create(apiResource + "/v2");
		when(config.getApiResources()).thenReturn(IuIterable.iter(apiResourcev1, apiResourcev2));

		final var principal = principal(config, IdGenerator.generateId(), null);

		try (final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class, (a, ctx) -> {
			// echo the API resource the grant was created for
			final var response = tokenResponse(ctx.arguments().get(1).toString());
			when(a.getTokenResponse()).thenReturn(response);
		})) {

			assertEquals(apiResourcev1.toString(), principal.getAccessToken(apiResourcev1));
			assertEquals(apiResourcev1.toString(), principal.getAccessToken(URI.create(apiResourcev1 + "/get")));
			assertEquals(1, mockOboGrant.constructed().size());

			assertEquals(apiResourcev2.toString(), principal.getAccessToken(apiResourcev2));
			assertEquals(2, mockOboGrant.constructed().size());
		}
	}

	@Test
	void testAccessTokenRequiresApiResources() {
		final var config = config();
		final var principal = principal(config, IdGenerator.generateId(), null);

		final var resourceUri = URI.create("api://" + IdGenerator.generateId());
		assertEquals("invalid resource URI " + resourceUri + "; access token not verified",
				assertThrows(NullPointerException.class, () -> principal.getAccessToken(resourceUri)).getMessage());
	}

	@Test
	void testAccessTokenRequiresMatchingApiResource() {
		final var config = config();
		when(config.getApiResources()).thenReturn(IuIterable.iter(URI.create("api://" + IdGenerator.generateId())));

		final var audience = IuIterable.iter(URI.create("api://" + IdGenerator.generateId()));
		final var verifiedAccessToken = mock(WebToken.class);
		when(verifiedAccessToken.getAudience()).thenReturn(audience);

		final var principal = principal(config, IdGenerator.generateId(), verifiedAccessToken);

		final var resourceUri = URI.create("api://" + IdGenerator.generateId());
		assertEquals(
				"invalid resource URI " + resourceUri + "; access token doesn't include resource URI as audience "
						+ audience,
				assertThrows(NullPointerException.class, () -> principal.getAccessToken(resourceUri)).getMessage());
	}

	@Test
	void testAccessTokenExchangeFailureReportsResourceMismatch() {
		final var config = config();

		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		when(config.getApiResources()).thenReturn(IuIterable.iter(apiResource));

		final var principal = principal(config, IdGenerator.generateId(), null);
		final var oboFailure = new IOException(IdGenerator.generateId());

		try (final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class,
				(a, ctx) -> when(a.getTokenResponse()).thenThrow(oboFailure))) {
			assertSame(oboFailure, assertThrows(IOException.class, () -> principal.getAccessToken(apiResource)));
			assertEquals(1, oboFailure.getSuppressed().length);

			final var suppressed = oboFailure.getSuppressed()[0];
			assertEquals(IllegalArgumentException.class, suppressed.getClass());
			assertEquals("invalid resource URI " + apiResource + "; access token not verified",
					suppressed.getMessage());
		}
	}

}
