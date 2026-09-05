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

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import edu.iu.IuBadRequestException;
import edu.iu.IuIterable;
import edu.iu.IuWebUtils;
import edu.iu.oidc.config.IuOidcClientConfiguration;
import edu.iu.oidc.config.IuOidcClientEndpoint;
import edu.iu.oidc.config.IuOidcClientResource;

/**
 * Request-shaping logic shared by this provider's endpoints.
 *
 * <p>
 * Every endpoint reads {@code scope} the same way, matches a
 * {@code redirect_uri} against a client's registrations the same way, and
 * resolves what {@code resource} a request may act on the same way. Holding
 * that here once, rather than duplicated or reached into across endpoints, is
 * what keeps them from drifting apart on it.
 * </p>
 *
 * <p>
 * Nothing here writes a response. Building an error redirect is here because
 * the shape of one is OAuth 2.0's; sending it is the caller's.
 * </p>
 */
final class OidcProviderUtils {

	/**
	 * Splits a space-delimited {@code scope} parameter.
	 *
	 * @param scope {@code scope} parameter value
	 * @return requested scopes; empty if the parameter is {@code null} or blank
	 */
	static Set<String> scopes(String scope) {
		final Set<String> scopes = new LinkedHashSet<>();
		if (scope != null)
			for (final var requested : scope.split(" "))
				if (!requested.isEmpty())
					scopes.add(requested);

		return scopes;
	}

	/**
	 * Appends parameters to a URI that may already carry a query string.
	 *
	 * @param uri    base URI
	 * @param params parameters to append
	 * @return URI carrying both the original and the appended parameters
	 */
	static URI appendQuery(URI uri, Map<String, Iterable<String>> params) {
		final var base = uri.toString();
		return URI.create(base + (uri.getRawQuery() == null ? '?' : '&') + IuWebUtils.createQueryString(params));
	}

	/**
	 * Builds an OAuth 2.0 error redirect.
	 *
	 * @param redirectUri verified redirect URI
	 * @param error       OAuth 2.0 error code
	 * @param description human-readable description
	 * @param state       {@code state} to echo, or {@code null}
	 * @return error redirect URI
	 */
	static URI errorUri(URI redirectUri, String error, String description, String state) {
		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		params.put("error", IuIterable.iter(error));
		params.put("error_description", IuIterable.iter(description));
		if (state != null)
			params.put("state", IuIterable.iter(state));

		return appendQuery(redirectUri, params);
	}

	/**
	 * Builds the refusal for a request that named no verified client and redirect
	 * URI.
	 *
	 * <p>
	 * Returned to be thrown at the call site rather than thrown from here, so both
	 * the compiler and a reader can see that control does not continue &mdash; and
	 * so the call is not left looking unreachable to coverage analysis.
	 * </p>
	 *
	 * @param error            error to report
	 * @param errorDescription details of error to report
	 * @return {@link IuBadRequestException} to throw
	 */
	static IuBadRequestException deny(String error, String errorDescription) {
		return new IuBadRequestException(error + "; " + errorDescription);
	}

	/**
	 * Determines whether a {@code resource} parameter value is well-formed.
	 *
	 * <p>
	 * RFC 8707 &sect;2 requires an absolute URI with no fragment component; a value
	 * that isn't one names no target this provider could ever issue a token for.
	 * </p>
	 *
	 * @param resource value to check
	 * @return {@code true} if {@code resource} is an absolute URI with no fragment;
	 *         else {@code false}
	 * @see <a href="https://www.rfc-editor.org/rfc/rfc8707#section-2">RFC 8707
	 *      &sect;2</a>
	 */
	static boolean isValidResource(String resource) {
		final URI uri;
		try {
			uri = URI.create(resource);
		} catch (IllegalArgumentException e) {
			return false;
		}

		return uri.isAbsolute() && uri.getFragment() == null;
	}

	/**
	 * Determines whether an endpoint registers a resource entry naming the given
	 * URI.
	 *
	 * <p>
	 * An entry with no {@link IuOidcClientResource#getUri() URI} names this
	 * provider's own issuer identifier rather than an external resource, so a
	 * request naming the issuer itself matches it the same as one naming an
	 * external resource matches an entry registered for that URI.
	 * </p>
	 *
	 * @param endpoint verified client endpoint
	 * @param issuer   this provider's issuer identifier
	 * @param resource resource URI, as a string
	 * @return {@code true} if the endpoint registers an entry naming that URI; else
	 *         {@code false}
	 */
	static boolean isRegisteredResource(IuOidcClientEndpoint endpoint, URI issuer, String resource) {
		final var resources = endpoint.getResources();
		if (resources == null)
			return false;

		for (final var clientResource : resources) {
			if (clientResource == null)
				continue;

			final var uri = clientResource.getUri();
			if ((uri == null ? issuer : uri).toString().equals(resource))
				return true;
		}

		return false;
	}

	/**
	 * Infers which resources a request naming no {@code resource} parameter should
	 * be understood to act on, from the scope it asks for.
	 *
	 * <p>
	 * Naming no resource doesn't restrict a request to just the entry registered
	 * without a URI: every resource whose scope overlaps what was requested is
	 * included. That is what lets a client ask for an external resource's scope
	 * without also having to name that resource explicitly.
	 * </p>
	 *
	 * @param endpoint verified client endpoint
	 * @param issuer   this provider's issuer identifier
	 * @param scopes   requested scopes
	 * @return resource URIs whose registered scope overlaps a requested one; empty
	 *         if none do
	 */
	static Set<String> resourcesGrantingScope(IuOidcClientEndpoint endpoint, URI issuer, Set<String> scopes) {
		final Set<String> resources = new LinkedHashSet<>();

		final var clientResources = endpoint.getResources();
		if (clientResources != null)
			for (final var resource : clientResources) {
				if (resource == null)
					continue;

				final var resourceScope = resource.getScope();
				if (resourceScope == null //
						|| resourceScope.stream().noneMatch(scopes::contains))
					continue;

				final var uri = resource.getUri();
				resources.add(uri == null ? issuer.toString() : uri.toString());
			}

		return resources;
	}

	/**
	 * Determines the resources a request through one endpoint may act on.
	 *
	 * <p>
	 * An endpoint's resources are filtered by the requested {@code resource}
	 * values, so a client registered for several resources only gets the scopes of
	 * the ones it named. A request naming none matches the resource registered
	 * without a URI, which is how a client that acts on only one resource registers
	 * it &mdash; that same entry also names this provider's own issuer identifier
	 * rather than an external resource, so a request that names the issuer
	 * explicitly matches it too. A matching entry that grants no scope contributes
	 * nothing and is left out, the same as one that doesn't match at all.
	 * </p>
	 *
	 * <p>
	 * {@code openid} is not a special case: a client that wants an ID token issued
	 * registers a resource whose scope includes {@code openid} the same as any
	 * other.
	 * </p>
	 *
	 * @param endpoint  verified client endpoint
	 * @param issuer    this provider's issuer identifier
	 * @param resources {@code resource} parameter values; empty if the request
	 *                  named none
	 * @return resources granting at least one scope the request may ask for; empty
	 *         if the endpoint registers none matching
	 */
	static Set<IuOidcClientResource> grantedResources(IuOidcClientEndpoint endpoint, URI issuer, Set<String> resources) {
		final Set<IuOidcClientResource> granted = new LinkedHashSet<>();

		final var clientResources = endpoint.getResources();
		if (clientResources == null)
			return granted;

		for (final var clientResource : clientResources) {
			if (clientResource == null)
				continue;

			final var uri = clientResource.getUri();
			final var matches = resources.isEmpty() //
					? uri == null //
					: resources.contains((uri == null ? issuer : uri).toString());

			if (matches) {
				final var scope = clientResource.getScope();
				if (scope != null //
						&& !scope.isEmpty())
					granted.add(clientResource);
			}
		}

		return granted;
	}

	/**
	 * Matches a requested redirect URI against the client's registered endpoints.
	 *
	 * <p>
	 * OpenID Connect requires an exact comparison, so a URI that merely starts with
	 * a registered value doesn't match.
	 * </p>
	 *
	 * @param client      client configuration
	 * @param redirectUri {@code redirect_uri} parameter value
	 * @return registered endpoint, or {@code null} if the parameter is missing or
	 *         names no registered endpoint
	 */
	static IuOidcClientEndpoint registeredEndpoint(IuOidcClientConfiguration client, String redirectUri) {
		if (redirectUri == null)
			return null;

		final var endpoints = client.getEndpoints();
		if (endpoints == null)
			return null;

		for (final var endpoint : endpoints) {
			if (endpoint == null)
				continue;

			final var candidate = endpoint.getRedirectUri();
			if (candidate != null //
					&& candidate.toString().equals(redirectUri))
				return endpoint;
		}

		return null;
	}

	private OidcProviderUtils() {
	}

}
