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

import static iu.oidc.provider.OidcProviderUtils.appendQuery;
import static iu.oidc.provider.OidcProviderUtils.deny;
import static iu.oidc.provider.OidcProviderUtils.errorUri;
import static iu.oidc.provider.OidcProviderUtils.grantedResources;
import static iu.oidc.provider.OidcProviderUtils.isRegisteredResource;
import static iu.oidc.provider.OidcProviderUtils.isValidResource;
import static iu.oidc.provider.OidcProviderUtils.registeredEndpoint;
import static iu.oidc.provider.OidcProviderUtils.resourcesGrantingScope;
import static iu.oidc.provider.OidcProviderUtils.scopes;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuBadRequestException;
import edu.iu.IuIterable;
import edu.iu.oidc.config.OidcAuthenticatedPrincipal;
import edu.iu.oidc.config.OidcClientConfiguration;
import edu.iu.oidc.config.OidcClientEndpoint;
import edu.iu.oidc.config.OidcClientResource;
import edu.iu.oidc.config.OidcClientSource;
import edu.iu.session.IuSessionHandler;

/**
 * Answers the OpenID Connect authorization request.
 *
 * <p>
 * Only the authorization code flow. Nothing here is a servlet, and nothing here
 * knows how the end user authenticates: it takes a request, reports what came
 * of it, and leaves both the transport and the identity provider to whatever
 * called it.
 * </p>
 *
 * <h2>Two passes</h2>
 *
 * <p>
 * A request arrives carrying {@code client_id}. The endpoint validates it,
 * records the validated request as an {@link OidcGrant} in a session of its
 * own, and &mdash; when no principal is established yet &mdash; answers
 * {@link OidcAuthorizeResult.AuthenticationRequired}, naming the bare
 * authorization endpoint as where the end user comes back to.
 * </p>
 *
 * <p>
 * That second pass carries no {@code client_id}, which is what marks it as a
 * resumption: the request is read back out of the session rather than off the
 * query string, so {@code state}, {@code nonce}, and the PKCE challenge never
 * travel through the identity provider or turn up in its logs. A request that
 * already has a principal skips the round trip and issues a code on the first
 * pass.
 * </p>
 *
 * <p>
 * The principal arrives as a {@link Supplier} rather than a value, so it is
 * looked up only once the request is worth looking one up for &mdash; a request
 * naming an unregistered client is refused without ever asking who the end user
 * is.
 * </p>
 *
 * <h2>Granted scope</h2>
 *
 * <p>
 * A client registers endpoints, each keyed by its redirect URI, and each
 * endpoint declares the resources it may act on. The requested
 * {@code redirect_uri} selects the endpoint; the requested {@code resource}
 * &mdash; which RFC 8707 permits naming more than once, for a token meant to be
 * usable at several resources at once &mdash; selects among that endpoint's
 * resources, or, when the request names none, every resource whose scope
 * overlaps what was asked for. An entry registered without a URI names this
 * provider's own issuer identifier, so a request naming the issuer itself
 * matches it too; that is what lets a deployment grant a token addressed to
 * itself &mdash; {@code openid}, say &mdash; without the registration embedding
 * its own issuer URI, which would tie it to one deployment. The scopes those
 * resources declare are everything the request may ask for; {@code openid} is
 * not a special case beyond that.
 * </p>
 *
 * <p>
 * What is named here is recorded on the grant, not merely checked and
 * discarded: a token endpoint bounds the audience of a token redeemed against
 * this authorization &mdash; directly or through a refresh token descending
 * from it &mdash; to at most these resources, however the redemption request's
 * own {@code resource} parameter reads.
 * </p>
 *
 * <h2>Errors</h2>
 *
 * <p>
 * Follows OAuth 2.0: once {@code client_id} and {@code redirect_uri} check out,
 * an invalid request answers a {@link OidcAuthorizeResult.Redirect} to the
 * client carrying {@code error} and {@code error_description}. Before that
 * point there is no verified URI to redirect to, so the endpoint raises
 * {@link IuBadRequestException} and the caller answers the user agent where it
 * stands &mdash; redirecting an error to an unverified URI would make this an
 * open redirector.
 * </p>
 */
public class OidcAuthorizeEndpoint {

	private static final Logger LOG = Logger.getLogger(OidcAuthorizeEndpoint.class.getName());

	/** The only {@code response_type} this provider answers. */
	private static final String CODE = "code";

	/** The only {@code code_challenge_method} this provider accepts. */
	private static final String S256 = "S256";

	/**
	 * Names an OAuth 2.0 error to relay to the client's redirect URI.
	 *
	 * <p>
	 * Raised only after {@code client_id} and {@code redirect_uri} have been
	 * verified, since the redirect is what carries the error.
	 * </p>
	 */
	private static final class AuthorizationError extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final String error;

		private AuthorizationError(String error, String description) {
			super(description);
			this.error = error;
		}
	}

	private final OidcIssuer issuer;
	private final OidcClientSource clients;
	private final IuSessionHandler sessionHandler;
	private final GrantStore grantStore;

	/**
	 * Creates an authorization endpoint.
	 *
	 * @param issuer         this provider's own identity and signing keys
	 * @param clients        reads a relying party's registration
	 * @param sessionHandler holds the validated request across the round trip to
	 *                       the identity provider; <em>must</em> be a handler of
	 *                       its own, since a handler shared with the
	 *                       authentication mechanism would have each overwrite the
	 *                       other's cookie
	 * @param grantStore     hands the completed grant to the token endpoint
	 */
	public OidcAuthorizeEndpoint(OidcIssuer issuer, OidcClientSource clients, IuSessionHandler sessionHandler,
			GrantStore grantStore) {
		this.issuer = Objects.requireNonNull(issuer, "Missing issuer");
		this.clients = Objects.requireNonNull(clients, "Missing client source");
		this.sessionHandler = Objects.requireNonNull(sessionHandler, "Missing session handler");
		this.grantStore = Objects.requireNonNull(grantStore, "Missing grant store");
	}

	/**
	 * Answers an authorization request, or resumes one the identity provider has
	 * returned from.
	 *
	 * @param request   incoming request
	 * @param principal supplies the principal the identity provider has
	 *                  established, answering {@code null} when it has established
	 *                  none. The supplier itself is required; it is
	 *                  <em>the value it supplies</em> that may be absent, which is
	 *                  the ordinary first pass rather than a failure. Consulted at
	 *                  most once, and only once a request is worth consulting it
	 *                  for
	 * @return what the request came to
	 * @throws IuBadRequestException if the request names no verified client and
	 *                               redirect URI to relay an error to
	 * @throws NullPointerException  if {@code principal} is null
	 */
	public OidcAuthorizeResult authorize(OidcAuthorizeRequest request, Supplier<OidcAuthenticatedPrincipal> principal) {
		Objects.requireNonNull(principal, "Missing principal supplier");

		// The return target carries no parameters, so their absence is what marks a
		// request as the second pass of one already validated.
		final var clientId = request.getClientId();
		if (clientId == null)
			return resume(request, principal);

		// A bad client_id or redirect_uri can't be relayed to the client: there is no
		// URI yet that this provider has agreed to send a user agent to.
		final var client = client(clientId);

		// answered the same as an unregistered client, so a disabled registration
		// isn't distinguishable from one that never existed
		if (!client.isEnabled()) {
			LOG.info(() -> "authorize-deny:disabled-client:" + clientId);
			throw deny("invalid_client", "Unregistered client_id");
		}

		final var endpoint = registeredEndpoint(client, request.getRedirectUri());
		if (endpoint == null) {
			LOG.info(() -> "authorize-deny:unregistered-redirect-uri:" + clientId);
			throw deny("invalid_request", "Unregistered redirect_uri");
		}

		final var state = request.getState();
		try {
			return grant(request, principal, clientId, endpoint, state);
		} catch (AuthorizationError e) {
			LOG.log(Level.INFO, e, () -> "authorize-error:" + e.error + ":" + clientId);
			return new OidcAuthorizeResult.Redirect(
					errorUri(endpoint.getRedirectUri(), e.error, e.getMessage(), state));
		}
	}

	/**
	 * Reads one relying party's registration, treating a source that refuses and
	 * one that answers nothing the same way.
	 *
	 * @param clientId requested {@code client_id}
	 * @return registration
	 * @throws IuBadRequestException if the client is not registered
	 */
	private OidcClientConfiguration client(String clientId) {
		final OidcClientConfiguration client;
		try {
			client = clients.client(clientId);
		} catch (Exception e) {
			LOG.log(Level.INFO, e, () -> "authorize-deny:unregistered-client:" + clientId);
			throw deny("invalid_client", "Unregistered client_id");
		}

		if (client == null) {
			LOG.info(() -> "authorize-deny:unregistered-client:" + clientId);
			throw deny("invalid_client", "Unregistered client_id");
		}

		return client;
	}

	/**
	 * Validates the remaining request parameters and records them, then either
	 * defers to the identity provider or, when a principal is already established,
	 * issues an authorization code straight away.
	 *
	 * @param request   incoming request
	 * @param principal supplies the established principal
	 * @param clientId  verified client ID
	 * @param endpoint  verified client endpoint
	 * @param state     {@code state} to echo, or {@code null}
	 * @return what the request came to
	 * @throws AuthorizationError if the request is invalid
	 */
	private OidcAuthorizeResult grant(OidcAuthorizeRequest request, Supplier<OidcAuthenticatedPrincipal> principal,
			String clientId, OidcClientEndpoint endpoint, String state) {

		final var responseType = request.getResponseType();
		if (responseType == null)
			throw new AuthorizationError("invalid_request", "Missing response_type");
		if (!CODE.equals(responseType))
			throw new AuthorizationError("unsupported_response_type", "Only the code response_type is supported");

		// openid carries no special requirement here: a client that wants an ID token
		// registers a resource for it, the same as any other scope; one that never asks
		// for openid is simply answered without one
		final var scope = request.getScope();
		final var scopes = scopes(scope);
		final var issuerUri = issuer.issuer();
		final var resources = requestedResources(request, endpoint, issuerUri, scopes);

		// no resource selected by either parameter is no different from a resource
		// registering no scope: nothing here would ever resolve to an audience, so no
		// grant is authorized rather than issuing a code doomed to fail at redemption
		if (resources.isEmpty())
			throw new AuthorizationError("invalid_scope", "No resource is registered for the requested scope");

		final var granted = grantedResources(endpoint, issuerUri, resources);
		for (final var requested : scopes)
			if (granted.stream().map(OidcClientResource::getScope).flatMap(Set::stream).noneMatch(requested::equals))
				throw new AuthorizationError("invalid_scope", "Scope " + requested + " is not granted to this client");

		// PKCE is optional, but a challenge this provider can't verify is not.
		final var codeChallenge = request.getCodeChallenge();
		if (codeChallenge != null) {
			if (!S256.equals(request.getCodeChallengeMethod()))
				throw new AuthorizationError("invalid_request", "Only the S256 code_challenge_method is supported");
		} else if (request.getCodeChallengeMethod() != null)
			throw new AuthorizationError("invalid_request", "Missing code_challenge");

		final var session = sessionHandler.create();
		final var pending = session.getDetail(OidcGrant.class);
		pending.setImpersonatedPrincipalName(request.getImpersonatedPrincipal());
		pending.setClientId(clientId);
		pending.setRedirectUri(endpoint.getRedirectUri());
		pending.setScope(scope);
		pending.setResource(resources.toArray(String[]::new));
		pending.setState(state);
		pending.setNonce(request.getNonce());
		pending.setCodeChallenge(codeChallenge);

		final var authenticated = authenticated(principal);
		if (authenticated != null) {
			final var expires = authenticated.getExpires();
			if (expires == null || expires.isBefore(Instant.now()))
				LOG.info(() -> "authn-expired:" + clientId + ":" + authenticated.getName() + " " + authenticated);
			else {
				LOG.info(() -> "authn:" + clientId + ":" + authenticated.getName() + " " + authenticated);
				return issue(pending, authenticated);
			}
		}

		// First pass: the end user goes off to authenticate and comes back to the bare
		// authorization endpoint. The validated request rides in the session, not in a
		// return URI the identity provider would see.
		final var returnUri = issuer.endpointUri(OidcProviderMetadata.AUTHORIZE_PATH);
		LOG.info(() -> "authn-pending:" + clientId + " " + returnUri + " " + pending);

		// the end user leaves and comes back on a request this provider never issued,
		// so the session has to survive being activated somewhere other than where it
		// was stored
		session.setStrict(false);

		return new OidcAuthorizeResult.AuthenticationRequired(sessionHandler.store(session), returnUri);
	}

	/**
	 * Reads and validates the request's {@code resource} parameter values, or, when
	 * it names none, infers them from the requested scope.
	 *
	 * <p>
	 * RFC 8707 allows the parameter to repeat, one value per resource the client
	 * wants the eventual token usable at. Each named value must be well-formed and
	 * one the endpoint actually registers; naming anything else is refused
	 * outright, as {@code invalid_target}, rather than left to surface later as an
	 * {@code invalid_scope} that says nothing about why.
	 * </p>
	 *
	 * <p>
	 * A request naming no {@code resource} at all doesn't default to just the entry
	 * registered without a URI: every resource whose scope overlaps what was
	 * requested is included, the same set a token's audience is derived from. That
	 * is what lets a client ask for an external resource's scope without also
	 * having to name that resource explicitly.
	 * </p>
	 *
	 * @param request  incoming request
	 * @param endpoint verified client endpoint
	 * @param issuer   this provider's issuer identifier
	 * @param scopes   requested scopes
	 * @return requested resource URIs, in the order named, with duplicates removed;
	 *         empty if the request named none and none of the endpoint's resources
	 *         grant any requested scope
	 * @throws AuthorizationError if a named value is malformed or not one the
	 *                            endpoint registers
	 * @see <a href="https://www.rfc-editor.org/rfc/rfc8707#section-2">RFC 8707
	 *      &sect;2</a>
	 */
	private static Set<String> requestedResources(OidcAuthorizeRequest request, OidcClientEndpoint endpoint, URI issuer,
			Set<String> scopes) {
		final Set<String> resources = new LinkedHashSet<>();

		final var values = request.getResource();
		if (values != null)
			for (final var value : values) {
				if (value == null //
						|| !isValidResource(value))
					throw new AuthorizationError("invalid_target", "Malformed resource parameter");

				if (!isRegisteredResource(endpoint, issuer, value))
					throw new AuthorizationError("invalid_target", "Unregistered resource " + value);

				resources.add(value);
			}
		else
			resources.addAll(resourcesGrantingScope(endpoint, issuer, scopes));

		return resources;
	}

	/**
	 * Resumes an authorization request the identity provider has returned from.
	 *
	 * <p>
	 * Everything needed was validated on the first pass and recorded in the
	 * session, so nothing is read from the request but its cookies.
	 * </p>
	 *
	 * <p>
	 * The session is removed as soon as a request is found in it, before a
	 * principal is even looked for, so it is good for exactly one return from the
	 * identity provider. A replay finds nothing to resume rather than minting a
	 * second code against one authentication; the cost is that a return the
	 * provider declines can't be retried, which is the right way round.
	 * </p>
	 *
	 * @param request   incoming request
	 * @param principal supplies the established principal
	 * @return what the request came to
	 * @throws IuBadRequestException if no request was recorded, or the identity
	 *                               provider established no principal
	 */
	private OidcAuthorizeResult resume(OidcAuthorizeRequest request, Supplier<OidcAuthenticatedPrincipal> principal) {
		final var cookies = request.getCookies();
		final var session = sessionHandler.activate(cookies);
		final var pending = session == null ? null : session.getDetail(OidcGrant.class);
		if (pending == null //
				|| pending.getClientId() == null) {
			LOG.info(() -> "authorize-deny:no-pending-request:" + request.getRemoteAddr());
			throw deny("invalid_request", "Missing client_id request parameter");
		}

		sessionHandler.remove(cookies);

		final var authenticated = authenticated(principal);
		if (authenticated == null) {
			LOG.info(() -> "authorize-deny:unauthenticated:" + pending.getClientId());
			throw deny("login_required", "User is not authenticated");
		}

		LOG.info(() -> "authn-resume:" + pending.getClientId() + ":" + authenticated.getName() + " " + authenticated);
		return issue(pending, authenticated);
	}

	/**
	 * Issues an authorization code against a validated request.
	 *
	 * <p>
	 * No session outlives this. The grant travels to the token endpoint through the
	 * store, so the authorization response carries a code and nothing else &mdash;
	 * no cookie, and no session left behind for a replayed request to resume.
	 * </p>
	 *
	 * @param grant     validated request, completed here
	 * @param principal authenticated principal
	 * @return redirect to the client
	 */
	private OidcAuthorizeResult issue(OidcGrant grant, OidcAuthenticatedPrincipal principal) {
		final var clientId = grant.getClientId();
		final var principalName = principal.getName();
		final var redirectUri = grant.getRedirectUri();

		grant.setPrincipalName(principalName);
		grant.setAuthnAuthority(principal.getAuthnAuthority());
		grant.setAuthnInstant(principal.getAuthnInstant());

		final var code = grantStore.put(GrantStore.CODE, issuer.issuer(), issuer.issuerKey(),
				issuer.configuration().getAuthorizationCodeTimeToLive(), grant);

		LOG.info(() -> "authorize-grant:" + clientId + ":" + principalName + " " + grant);

		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		params.put(CODE, IuIterable.iter(code));

		final var state = grant.getState();
		if (state != null)
			params.put("state", IuIterable.iter(state));

		return new OidcAuthorizeResult.Redirect(appendQuery(redirectUri, params));
	}

	/**
	 * Gets the principal the identity provider has already authenticated, if there
	 * is one.
	 *
	 * <p>
	 * An unauthenticated request is the normal first pass, not a failure, and a
	 * caller is free to report it either by answering {@code null} or by refusing
	 * the lookup &mdash; a service provider asked for a principal it hasn't
	 * established may well throw. Both mean the same thing here, so both are read
	 * as nobody being signed in yet.
	 * </p>
	 *
	 * <p>
	 * What this deliberately does <em>not</em> absorb is a missing supplier, which
	 * {@link #authorize} rejects outright: a caller that passes none has made a
	 * programming error, and swallowing its {@link NullPointerException} here would
	 * report that as an ordinary unauthenticated request and send every end user to
	 * authenticate forever.
	 * </p>
	 *
	 * @param principal supplies the established principal
	 * @return {@link OidcAuthenticatedPrincipal}, or {@code null} if not
	 *         authenticated
	 */
	private static OidcAuthenticatedPrincipal authenticated(Supplier<OidcAuthenticatedPrincipal> principal) {
		try {
			return principal.get();
		} catch (Exception e) {
			LOG.log(Level.FINE, e, () -> "no established principal");
			return null;
		}
	}

}
