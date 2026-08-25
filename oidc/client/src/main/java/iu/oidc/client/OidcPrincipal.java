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

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import edu.iu.IuWebUtils;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcPrincipal;
import iu.oidc.client.config.IuOidcClientReference;
import jakarta.json.JsonObject;

/**
 * {@link IuOidcPrincipal} implementation class.
 *
 * <p>
 * Presents ID token and userinfo claims as a single claim set, and brokers
 * access tokens for remote resources. The access token issued alongside the ID
 * token is returned directly for resources covered by the client's own resource
 * URI, and for resources named as an audience by a verified access token. Any
 * other resource covered by a configured API resource URI is reached by an
 * on-behalf-of exchange with the OpenID Provider's token endpoint; those grants
 * are cached per API resource for the life of the principal.
 * </p>
 */
public class OidcPrincipal implements IuOidcPrincipal {

	private final WebToken idToken;
	private final JsonObject userinfoClaims;
	private final String setCookie;
	private final IuOidcClientReference config;
	private final String accessToken;
	private final WebToken verifiedAccessToken;
	private final String principalNameClaimName;

	/** On-behalf-of grants by API root resource URI; synchronized on itself. */
	private final Map<URI, OidcTokenGrant> grants = new HashMap<>();

	/**
	 * Constructor.
	 *
	 * @param idToken                verified ID token
	 * @param userinfoClaims         claims provided by the userinfo endpoint; MUST
	 *                               include a sub claim matching the ID token
	 * @param setCookie              set-cookie header value to pass back to the
	 *                               user agent if session state changed assembling
	 *                               the principal
	 * @param config                 OIDC client configuration reference; supplies
	 *                               the client's own resource URI, downstream API
	 *                               resource URIs, and JSON type adapters
	 * @param accessToken            access token issued with the ID token
	 * @param verifiedAccessToken    {@code accessToken} parsed and verified as a
	 *                               JWT issued by the OpenID Provider; null if it
	 *                               couldn't be verified as such, in which case its
	 *                               audience is not considered
	 * @param principalNameClaimName claim name for principal name; null to use
	 *                               "sub"
	 */
	public OidcPrincipal(WebToken idToken, JsonObject userinfoClaims, String setCookie, IuOidcClientReference config,
			String accessToken, WebToken verifiedAccessToken, String principalNameClaimName) {
		this.idToken = idToken;

		if (!userinfoClaims.containsKey("sub"))
			throw new IllegalArgumentException("userinfo missing sub claim");
		if (!userinfoClaims.getString("sub").equals(idToken.getSubject()))
			throw new IllegalArgumentException("userinfo sub claim doesn't match id token");
		this.userinfoClaims = userinfoClaims;

		this.setCookie = setCookie;

		this.config = config;
		this.accessToken = accessToken;
		this.verifiedAccessToken = verifiedAccessToken;

		this.principalNameClaimName = principalNameClaimName;
	}

	@Override
	public String getName() {
		if (principalNameClaimName != null) {
			final var idTokenValue = idToken.getClaim(principalNameClaimName, String.class);
			if (idTokenValue != null)
				return idTokenValue;
			final var userinfoValue = userinfoClaims.get(principalNameClaimName);
			if (userinfoValue != null)
				return config.adaptJson(String.class).fromJson(userinfoValue);
		}
		return idToken.getSubject();
	}

	@Override
	public String getSetCookie() {
		return setCookie;
	}

	@Override
	public WebToken getIdToken() {
		return idToken;
	}

	@Override
	public <T> T getClaim(String name, Class<T> type) {
		final var idTokenClaimValue = idToken.getClaim(name, type);
		if (idTokenClaimValue != null)
			return idTokenClaimValue;

		final var userinfoClaimValue = userinfoClaims.get(name);
		if (userinfoClaimValue == null)
			return null;

		return type.cast(config.adaptJson(type).fromJson(userinfoClaimValue));
	}

	@Override
	public String getAccessToken(URI resourceUri) throws IOException {
		if (isDirectlyAuthorized(resourceUri))
			return accessToken;

		final Supplier<String> error = () -> "invalid resource URI " + resourceUri + (verifiedAccessToken == null
				? "; access token not verified"
				: "; access token doesn't include resource URI as audience " + verifiedAccessToken.getAudience());

		final var apiResource = Objects.requireNonNull(selectApiResource(resourceUri, error), error);

		try {
			return oboGrant(apiResource).getTokenResponse().getAccessToken();
		} catch (Throwable e) {
			// the on-behalf-of exchange doesn't know why it was needed; attach that
			// context so a failed exchange reports the resource and audience mismatch
			e.addSuppressed(new IllegalArgumentException(error.get()));
			throw e;
		}
	}

	/**
	 * Determines whether the access token issued with the ID token may be sent to a
	 * resource as-is, without an on-behalf-of exchange.
	 *
	 * @param resourceUri resource URI
	 * @return true if the resource is covered by the client's own resource URI, or
	 *         named as an audience by the verified access token; else false
	 */
	private boolean isDirectlyAuthorized(URI resourceUri) {
		if (IuWebUtils.isRootOf(config.getResourceUri(), resourceUri))
			return true;

		if (verifiedAccessToken == null)
			return false;

		final var audience = verifiedAccessToken.getAudience();
		if (audience == null)
			return false;

		for (final var aud : audience)
			if (IuWebUtils.isRootOf(aud, resourceUri))
				return true;

		return false;
	}

	/**
	 * Selects the most specific configured API root resource URI that covers a
	 * resource URI.
	 *
	 * @param resourceUri resource URI
	 * @param error       describes the resource URI mismatch that made the lookup
	 *                    necessary; reported if no API resources are configured
	 * @return API root resource URI; null if none cover {@code resourceUri}
	 * @throws NullPointerException if no API resources are configured
	 */
	private URI selectApiResource(URI resourceUri, Supplier<String> error) {
		URI apiResource = null;
		for (final var configured : Objects.requireNonNull(config.getApiResources(), error))
			if (IuWebUtils.isRootOf(configured, resourceUri) //
					&& (apiResource == null //
							|| IuWebUtils.isRootOf(apiResource, configured)))
				apiResource = configured;

		return apiResource;
	}

	/**
	 * Gets the on-behalf-of grant for an API root resource URI, creating and
	 * caching one on first use.
	 *
	 * @param apiResource API root resource URI
	 * @return {@link OidcTokenGrant}
	 */
	private OidcTokenGrant oboGrant(URI apiResource) {
		synchronized (grants) {
			return grants.computeIfAbsent(apiResource, a -> new OnBehalfOfGrant(config, a, accessToken));
		}
	}

	@Override
	public String toString() {
		return "OidcPrincipal [idToken=" + idToken + ", userinfoClaims=" + userinfoClaims + "]";
	}

}
