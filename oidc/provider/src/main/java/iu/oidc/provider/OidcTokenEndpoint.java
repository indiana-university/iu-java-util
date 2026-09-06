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

import static iu.oidc.provider.OidcProviderUtils.audience;
import static iu.oidc.provider.OidcProviderUtils.clientCredentialsScopes;
import static iu.oidc.provider.OidcProviderUtils.isRegisteredResource;
import static iu.oidc.provider.OidcProviderUtils.isValidResource;
import static iu.oidc.provider.OidcProviderUtils.resourcesGrantingScope;
import static iu.oidc.provider.OidcProviderUtils.scopes;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuDigest;
import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.IuAuthorizationDetails;
import edu.iu.jwt.WebToken;
import edu.iu.jwt.WebTokenBuilder;
import edu.iu.oidc.IuOidcClaims;
import edu.iu.oidc.config.IuOidcClientEndpoint;
import edu.iu.oidc.config.IuOidcClientRole;
import edu.iu.oidc.config.IuOidcProviderReference;

/**
 * Answers the OAuth 2.0 token request.
 *
 * <p>
 * Nothing here is a servlet. It takes a request, reports what came of it, and
 * leaves the transport to write the response.
 * </p>
 *
 * <h2>Client authentication</h2>
 *
 * <p>
 * Every request authenticates its client through {@link ClientAuthenticator}. A
 * registration's shape decides which method it may use, so a client cannot pick
 * a weaker one than it was registered for. Credentials belong to endpoints:
 * when a request supplies {@code redirect_uri}, only an endpoint registering
 * that exact URI is eligible; when it supplies {@code resource} &mdash; which
 * RFC 8707 permits naming more than once &mdash; only an endpoint registering
 * every named resource URI is eligible, an endpoint's own resource with no URI
 * counting as this provider's own issuer identifier for that comparison. If
 * both selectors are supplied they must match the same endpoint. A request
 * supplying neither tries each endpoint in turn, since not every token request
 * has either selector. Naming a resource no endpoint registers is refused as
 * {@code invalid_target}, distinct from {@code invalid_client}: the client may
 * be perfectly able to authenticate, it simply asked for a target this provider
 * doesn't recognize.
 * </p>
 *
 * <h2>Grant types</h2>
 *
 * <ul>
 * <li>{@code client_credentials} acts for the client itself. There is no end
 * user, so no ID token is issued and {@code openid} is not granted; the scope
 * comes from the resources the authenticating endpoint registers for the named
 * {@code resource}, narrowed to a requested {@code scope} when one is given.
 * Naming no {@code resource} doesn't restrict this to the endpoint's own entry:
 * every resource whose scope overlaps a requested {@code scope} is considered,
 * so a client need not name a resource it can identify by the scope it's asking
 * for.</li>
 * <li>{@code authorization_code} redeems a code
 * {@link OidcAuthorizeEndpoint} issued. The code resolves a grant through
 * {@link GrantStore}, and the request must repeat the {@code redirect_uri} it
 * was issued to and satisfy the PKCE challenge it recorded.</li>
 * <li>{@code refresh_token} redeems a refresh token this endpoint issued, which
 * is a {@link GrantStore} reference of its own kind carrying the same grant.
 * Each redemption spends the old reference and, so long as
 * {@code offline_access} is still granted and the original authentication
 * hasn't aged past the deployment's refresh token time to live, issues a new
 * one good for whatever of that lifetime remains &mdash; so a session can be
 * kept alive by refreshing, but never beyond the age its authentication was
 * good for in the first place.</li>
 * </ul>
 *
 * <p>
 * Whichever grant type answers, the access token's audience is derived from the
 * granted scope: every registered resource whose scope was granted. A resource
 * with no URI names this provider's own issuer identifier, so a scope such as
 * {@code openid} only addresses a token to this provider when a resource
 * registers that scope for it; nothing is added on the strength of a scope
 * alone, and a scope with no registered resource behind it contributes no
 * audience at all rather than falling back to the issuer implicitly. For
 * {@code authorization_code} and {@code refresh_token}, that derivation is
 * bounded by whatever {@code resource} the authorization endpoint recorded when
 * the code was first issued; a {@code resource} repeated at redemption may
 * narrow the audience to a subset of that, but naming one never authorized is
 * refused as {@code invalid_target} rather than silently widening it. This is
 * separate from a {@link GrantStore} reference's own {@code aud} claim, which
 * always names this provider &mdash; that token is never read by anything but
 * the endpoint that wrote it.
 * </p>
 *
 * <h2>Identity, roles, and impersonation</h2>
 *
 * <p>
 * A code or refresh grant answers for whoever the identity provider
 * authenticated, unless the authorization request named an
 * {@link OidcGrant#getImpersonatedPrincipalName() impersonated principal} the
 * authenticated principal holds a
 * {@link IuOidcClientEndpoint#getBackdoorRoles() backdoor role} for and
 * {@link IuOidcProviderReference#isProduction() this deployment isn't a
 * production one} &mdash; in which case the impersonated principal is who the
 * tokens answer for instead, and the real principal rides along as the
 * {@code act} claim. Either way, the effective principal must hold one of the
 * endpoint's {@link IuOidcClientEndpoint#getAccessRoles() access roles} to get
 * a token at all, and the {@link IuOidcClientRole application roles} it matches
 * are added as claims to both the access token and the ID token.
 * </p>
 *
 * <h2>Claims follow scope</h2>
 *
 * <p>
 * What an ID token says about its subject is decided the same way a UserInfo
 * response is: the granted scope admits a set of claims, deny-by-default, and
 * the {@link edu.iu.oidc.config.IuOidcClaimsSource claims source} is asked for
 * those and no others. A client that asks for {@code openid} alone gets an ID
 * token naming a subject and nothing else about them.
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">RFC 6749
 *      &sect;5.2</a>
 */
public class OidcTokenEndpoint {

	private static final Logger LOG = Logger.getLogger(OidcTokenEndpoint.class.getName());

	/** The scope that asks for an ID token. */
	private static final String OPENID = "openid";

	/** The scope that asks for a refresh token. */
	private static final String OFFLINE_ACCESS = "offline_access";

	/** {@code typ} of an issued access token. */
	private static final String ACCESS_TOKEN_TYPE = "at+jwt";

	/** {@code typ} of an issued ID token. */
	private static final String ID_TOKEN_TYPE = "JWT";

	/** The only {@code token_type} this provider issues. */
	private static final String BEARER = "Bearer";

	/** A role naming everyone, which needs no identity lookup. */
	private static final String ALL = "all";

	/** Status for a refusal the client could correct. */
	private static final int BAD_REQUEST = 400;

	/** Status for a client whose credential didn't verify. */
	private static final int UNAUTHORIZED = 401;

	/** Status for a principal the endpoint doesn't admit. */
	private static final int FORBIDDEN = 403;

	/**
	 * Names an OAuth 2.0 error to answer the client with.
	 */
	private static final class TokenError extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final String error;
		private final int status;

		private TokenError(String error, String description, int status) {
			this(error, description, status, null);
		}

		private TokenError(String error, String description, int status, Throwable cause) {
			super(description, cause);
			this.error = error;
			this.status = status;
		}
	}

	private final IuOidcProviderReference reference;
	private final OidcIssuer issuer;
	private final GrantStore grantStore;
	private final ClientAuthenticator clientAuthenticator;

	/**
	 * Creates a token endpoint.
	 *
	 * <p>
	 * The data store is read once, here, since an endpoint cannot usefully outlive
	 * it. Everything else is read per request, so a configuration change &mdash;
	 * including whether this is a production deployment &mdash; takes effect on the
	 * next one.
	 * </p>
	 *
	 * @param reference application resources this provider's endpoints read through
	 */
	public OidcTokenEndpoint(IuOidcProviderReference reference) {
		this.reference = Objects.requireNonNull(reference, "Missing provider reference");
		this.issuer = new OidcIssuer(reference::getConfiguration);
		this.grantStore = new GrantStore(reference.getDataStore());
		this.clientAuthenticator = new ClientAuthenticator(reference);
	}

	/**
	 * Answers a token request.
	 *
	 * @param request incoming request
	 * @return what the request came to; an {@link OidcTokenResult.Error} rather
	 *         than an exception, since a client parses the refusal
	 */
	public OidcTokenResult token(OidcTokenRequest request) {
		try {
			return issue(request);
		} catch (TokenError e) {
			LOG.log(Level.INFO, e, () -> "token-deny:" + e.error + ":" + request.getRemoteAddr());
			return new OidcTokenResult.Error(e.error, e.getMessage(), e.status,
					e.status == UNAUTHORIZED ? "Bearer" : null);
		}
	}

	/**
	 * Authenticates the client, redeems whatever the request presents, and builds
	 * the tokens it earns.
	 *
	 * @param request incoming request
	 * @return issued tokens
	 * @throws TokenError if the request is invalid or the client doesn't
	 *                    authenticate
	 */
	private OidcTokenResult.Issued issue(OidcTokenRequest request) {
		final var grantType = required(request.getGrantType(), "grant_type");
		final var credential = credential(request);
		final var clientId = clientId(request, credential);
		final var providerIssuer = issuer.issuer();
		final var resources = requestedResources(request);

		final var endpoint = authenticate(providerIssuer, resources, request.getRedirectUri(), clientId, credential);

		final Set<String> scopes;
		final OidcGrant grant;
		switch (grantType) {
		case "client_credentials":
			grant = null;
			final var requestedScope = request.getScope();

			// naming no resource doesn't restrict the request to just the endpoint's
			// self entry: every resource whose scope overlaps what was requested is
			// considered, the same as an authorization request naming none
			final var effectiveResources = resources.isEmpty()
					? resourcesGrantingScope(endpoint, providerIssuer, scopes(requestedScope))
					: resources;
			scopes = clientCredentialsScopes(endpoint, providerIssuer, requestedScope, effectiveResources);

			// only a resource the client actually named, or one its scope inferred, is
			// refused for granting nothing; an endpoint that simply has no resources
			// configured at all still answers, though with no audience of its own to
			// name
			if (!effectiveResources.isEmpty() //
					&& scopes.isEmpty())
				throw new TokenError("invalid_target", "No scope granted for the requested resource", BAD_REQUEST);
			break;

		case "authorization_code":
			grant = code(endpoint, clientId, request);
			scopes = scopes(grant.getScope());
			break;

		case "refresh_token":
			grant = refresh(endpoint, clientId, request);
			scopes = scopes(grant.getScope());
			break;

		default:
			throw new TokenError("unsupported_grant_type", "Unsupported grant_type " + grantType, BAD_REQUEST);
		}

		return respond(endpoint, clientId, grantType, grant, resources, scopes);
	}

	/**
	 * Reads and validates the request's {@code resource} parameter values.
	 *
	 * @param request incoming request
	 * @return requested resource URIs, in the order named, with duplicates removed;
	 *         empty if the request named none
	 * @throws TokenError if a named value is malformed
	 * @see <a href="https://www.rfc-editor.org/rfc/rfc8707#section-2">RFC 8707
	 *      &sect;2</a>
	 */
	private static Set<String> requestedResources(OidcTokenRequest request) {
		final Set<String> resources = new LinkedHashSet<>();

		final var values = request.getResource();
		if (values != null)
			for (final var value : values) {
				if (value == null //
						|| !isValidResource(value))
					throw new TokenError("invalid_target", "Malformed resource parameter", BAD_REQUEST);

				resources.add(value);
			}

		return resources;
	}

	/**
	 * Reads the credential a request presents.
	 *
	 * <p>
	 * An assertion is looked for first, since a client presenting one has proven
	 * more than a secret would and OpenID Connect forbids presenting both.
	 * </p>
	 *
	 * @param request incoming request
	 * @return credential presented, or {@code null} for a public client
	 * @throws TokenError if a credential is malformed or two are presented
	 */
	private static ClientAuthenticator.Credential credential(OidcTokenRequest request) {
		final var assertion = request.getClientAssertion();
		final var authorization = request.getAuthorization();
		final var secret = request.getClientSecret();

		if (assertion != null) {
			if (!ClientAuthenticator.JWT_BEARER.equals(request.getClientAssertionType()))
				throw new TokenError("invalid_request",
						"Unsupported client_assertion_type; expected " + ClientAuthenticator.JWT_BEARER, BAD_REQUEST);

			if (authorization != null //
					|| secret != null)
				throw new TokenError("invalid_request", "Present only one client credential", BAD_REQUEST);

			return ClientAuthenticator.Credential.assertion(assertion);
		}

		if (authorization != null) {
			if (secret != null)
				throw new TokenError("invalid_request", "Present only one client credential", BAD_REQUEST);

			if (!authorization.regionMatches(true, 0, "Basic ", 0, 6))
				throw new TokenError("invalid_client", "Unsupported Authorization scheme", UNAUTHORIZED);

			return ClientAuthenticator.Credential.basic(basic(authorization.substring(6))[1]);
		}

		if (secret != null)
			return ClientAuthenticator.Credential.post(secret);

		return null;
	}

	/**
	 * Splits an HTTP Basic credential into its username and secret.
	 *
	 * <p>
	 * Both halves are form-urlencoded, as RFC 6749 &sect;2.3.1 requires, and
	 * decoded here. That is not cosmetic: a client ID is often a URI, and its colon
	 * would otherwise be taken for the separator and leave {@code https} as the
	 * username.
	 * </p>
	 *
	 * @param credential base64 portion of the Basic credential
	 * @return two-element array of username and secret
	 * @throws TokenError if the credential isn't a base64, form-urlencoded
	 *                    {@code user:secret} pair
	 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-2.3.1">RFC 6749
	 *      &sect;2.3.1</a>
	 */
	private static String[] basic(String credential) {
		final String decoded;
		try {
			decoded = new String(IuText.base64(credential), StandardCharsets.UTF_8);
		} catch (RuntimeException e) {
			throw new TokenError("invalid_client", "Malformed Basic credential", UNAUTHORIZED, e);
		}

		final var i = decoded.indexOf(':');
		if (i < 0)
			throw new TokenError("invalid_client", "Malformed Basic credential", UNAUTHORIZED);

		try {
			return new String[] { //
					URLDecoder.decode(decoded.substring(0, i), StandardCharsets.UTF_8), //
					URLDecoder.decode(decoded.substring(i + 1), StandardCharsets.UTF_8) };
		} catch (RuntimeException e) {
			throw new TokenError("invalid_client", "Malformed Basic credential", UNAUTHORIZED, e);
		}
	}

	/**
	 * Determines which client a request is for.
	 *
	 * <p>
	 * A Basic credential names the client in its username, an assertion in its
	 * issuer, and everything else in {@code client_id}. Where both a Basic username
	 * and a {@code client_id} are given they must agree, since the credential is
	 * verified against one of them and the grant against the other.
	 * </p>
	 *
	 * @param request    incoming request
	 * @param credential credential presented, or {@code null}
	 * @return client ID
	 * @throws TokenError if no client is named, or two disagree
	 */
	private static String clientId(OidcTokenRequest request, ClientAuthenticator.Credential credential) {
		final var parameter = request.getClientId();

		if (credential != null //
				&& ClientAuthenticator.Method.CLIENT_SECRET_BASIC.equals(credential.method())) {
			final var username = basic(request.getAuthorization().substring(6))[0];
			if (parameter != null //
					&& !parameter.equals(username))
				throw new TokenError("invalid_request", "client_id does not match the Basic credential", BAD_REQUEST);

			return username;
		}

		if (credential != null //
				&& credential.assertion() != null) {
			final var assertionIssuer = request.getClientAssertionIssuer();
			if (assertionIssuer == null)
				throw new TokenError("invalid_client", "Malformed client_assertion", UNAUTHORIZED);

			if (parameter != null //
					&& !parameter.equals(assertionIssuer))
				throw new TokenError("invalid_request", "client_id does not match the client_assertion issuer",
						BAD_REQUEST);

			return assertionIssuer;
		}

		if (parameter == null)
			throw new TokenError("invalid_request", "Missing client_id", BAD_REQUEST);

		return parameter;
	}

	/**
	 * Authenticates a client against its eligible registered endpoints, answering
	 * the first whose credential verifies.
	 *
	 * <p>
	 * A supplied {@code redirect_uri} and {@code resource} are exact endpoint
	 * selectors, applied before a credential is evaluated. When both are supplied,
	 * both must match the same endpoint. Missing selectors impose no constraint, so
	 * a request naming neither may authenticate against any registered endpoint.
	 * When several {@code resource} values are named, an endpoint is eligible only
	 * if it registers every one of them, since a single token is meant to be usable
	 * at all of them.
	 * </p>
	 *
	 * <p>
	 * A client naming no endpoint that registers every requested resource is
	 * refused as {@code invalid_target} before any credential is even evaluated, so
	 * that outcome isn't reported as a credential failure.
	 * </p>
	 *
	 * @param providerIssuer this provider's issuer identifier
	 * @param resources      {@code resource} values; empty if the request named
	 *                       none
	 * @param redirectUri    {@code redirect_uri}, or {@code null}
	 * @param clientId       client ID the request named
	 * @param credential     credential presented, or {@code null}
	 * @return endpoint that authenticated
	 * @throws TokenError if the client is unregistered, disabled, or nothing
	 *                    verifies, or if no endpoint registers every requested
	 *                    resource
	 */
	private IuOidcClientEndpoint authenticate(URI providerIssuer, Set<String> resources, String redirectUri,
			String clientId, ClientAuthenticator.Credential credential) {
		final Iterable<IuOidcClientEndpoint> endpoints;
		try {
			final var client = Objects.requireNonNull(reference.getClientSource().client(clientId),
					"Unregistered client");

			// answered the same as an unregistered client, so a disabled
			// registration isn't distinguishable from one that never existed
			if (!client.isEnabled())
				throw new IllegalStateException("Client is disabled");

			endpoints = Objects.requireNonNull(client.getEndpoints(), "Client registers no endpoint");
		} catch (Exception e) {
			throw new TokenError("invalid_client", "Unregistered client_id", UNAUTHORIZED, e);
		}

		// checked independently of redirect_uri, so a resource this client cannot
		// serve at all is reported as such rather than as a credential failure once
		// redirect_uri happens to rule out the one endpoint that would have matched
		if (!resources.isEmpty()) {
			var anyRegistersAll = false;
			for (final var endpoint : endpoints)
				if (endpoint != null //
						&& registersAll(endpoint, providerIssuer, resources)) {
					anyRegistersAll = true;
					break;
				}

			if (!anyRegistersAll)
				throw new TokenError("invalid_target", "Unregistered resource", BAD_REQUEST);
		}

		SecurityException failure = null;
		for (final var endpoint : endpoints) {
			if (endpoint == null)
				continue;

			if (redirectUri != null) {
				final var uri = endpoint.getRedirectUri();
				if (uri == null //
						|| !uri.toString().equals(redirectUri))
					continue;
			}

			if (!resources.isEmpty() //
					&& !registersAll(endpoint, providerIssuer, resources))
				continue;

			try {
				final var method = clientAuthenticator.authenticate(endpoint, clientId, credential);
				LOG.info(() -> "token-authn:" + method.parameterValue + ":" + clientId);
				return endpoint;
			} catch (SecurityException e) {
				failure = (SecurityException) IuException.suppress(failure, e);
			}
			// any other RuntimeException propagates: a defective registration means the
			// credential could not be evaluated, which is not the caller's error
		}

		throw new TokenError("invalid_client", "Client authentication failed", UNAUTHORIZED, failure);
	}

	/**
	 * Determines whether an endpoint registers every named resource.
	 *
	 * @param endpoint       candidate endpoint
	 * @param providerIssuer this provider's issuer identifier
	 * @param resources      requested resource URIs
	 * @return true if the endpoint registers an entry for each; else false
	 */
	private static boolean registersAll(IuOidcClientEndpoint endpoint, URI providerIssuer, Set<String> resources) {
		for (final var resource : resources)
			if (!isRegisteredResource(endpoint, providerIssuer, resource))
				return false;

		return true;
	}

	/**
	 * Redeems an authorization code.
	 *
	 * @param endpoint endpoint that authenticated
	 * @param clientId authenticated client ID
	 * @param request  incoming request
	 * @return redeemed grant
	 * @throws TokenError if the code doesn't redeem, names a different client, or
	 *                    the PKCE challenge isn't satisfied
	 */
	private OidcGrant code(IuOidcClientEndpoint endpoint, String clientId, OidcTokenRequest request) {
		final var code = required(request.getCode(), "code");
		required(request.getRedirectUri(), "redirect_uri");

		final var grant = take(GrantStore.CODE, endpoint, code);

		if (!clientId.equals(grant.getClientId()))
			throw new TokenError("invalid_grant", "Authorization code was issued to a different client", BAD_REQUEST);

		verifyPkce(grant, request.getCodeVerifier());

		return grant;
	}

	/**
	 * Redeems a refresh token.
	 *
	 * @param endpoint endpoint that authenticated
	 * @param clientId authenticated client ID
	 * @param request  incoming request
	 * @return redeemed grant
	 * @throws TokenError if the token doesn't redeem or names a different client
	 */
	private OidcGrant refresh(IuOidcClientEndpoint endpoint, String clientId, OidcTokenRequest request) {
		// the wrapped token is addressed to this provider regardless of the endpoint,
		// so one registering no redirect URI redeems the reference the same as any
		// other
		final var grant = take(GrantStore.REFRESH, endpoint, required(request.getRefreshToken(), "refresh_token"));

		if (!clientId.equals(grant.getClientId()))
			throw new TokenError("invalid_grant", "Refresh token was issued to a different client", BAD_REQUEST);

		return grant;
	}

	/**
	 * Reads a grant out of the store, converting a rejection into an OAuth error.
	 *
	 * @param type      reference type
	 * @param endpoint  endpoint that authenticated
	 * @param reference reference presented
	 * @return redeemed grant
	 * @throws TokenError if the reference doesn't redeem
	 */
	private OidcGrant take(String type, IuOidcClientEndpoint endpoint, String reference) {
		try {
			return Objects.requireNonNull(
					grantStore.take(type, issuer.issuer(), issuer.issuerKey(endpoint.getAlg()), reference),
					"Empty grant");
		} catch (RuntimeException e) {
			throw new TokenError("invalid_grant", "Invalid or expired " + type + " reference", BAD_REQUEST, e);
		}
	}

	/**
	 * Verifies the PKCE challenge a grant recorded.
	 *
	 * @param grant    redeemed grant
	 * @param verifier {@code code_verifier} presented, or {@code null}
	 * @throws TokenError if a challenge was recorded and the verifier doesn't
	 *                    satisfy it, or a verifier is presented against no
	 *                    challenge
	 */
	private static void verifyPkce(OidcGrant grant, String verifier) {
		final var challenge = grant.getCodeChallenge();
		if (challenge == null) {
			if (verifier != null)
				throw new TokenError("invalid_grant", "No code_challenge was recorded for this code", BAD_REQUEST);
			return;
		}

		if (verifier == null)
			throw new TokenError("invalid_grant", "Missing code_verifier", BAD_REQUEST);

		final var computed = IuText.base64Url(IuDigest.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
		if (!challenge.equals(computed))
			throw new TokenError("invalid_grant", "code_verifier does not satisfy the recorded code_challenge",
					BAD_REQUEST);
	}

	/**
	 * Builds the tokens a redeemed request earns.
	 *
	 * <p>
	 * A refresh token is included only when {@code offline_access} was granted, and
	 * only when the grant's original authentication hasn't aged past the
	 * deployment's refresh token time to live &mdash; the absolute limit, measured
	 * from that authentication rather than from the most recent refresh, on how
	 * long a session may be kept alive without the user authenticating again. The
	 * one issued is good for whatever of that limit remains, so each redemption's
	 * refresh token is shorter-lived than the last, until what would remain is no
	 * longer worth even one more access token and none is issued at all.
	 * </p>
	 *
	 * @param endpoint  endpoint that authenticated
	 * @param clientId  authenticated client ID
	 * @param grantType grant type answered
	 * @param grant     redeemed grant, or {@code null} for
	 *                  {@code client_credentials}
	 * @param resources {@code resource} values from the token request; empty if it
	 *                  named none
	 * @param scopes    granted scopes
	 * @return issued tokens
	 * @throws TokenError if the request impersonates a principal without a backdoor
	 *                    role, the effective principal holds none of the endpoint's
	 *                    access roles, names a resource the grant being redeemed
	 *                    did not authorize, or the endpoint registers no resource
	 *                    for any of the granted scope
	 */
	private OidcTokenResult.Issued respond(IuOidcClientEndpoint endpoint, String clientId, String grantType,
			OidcGrant grant, Set<String> resources, Set<String> scopes) {
		final var configuration = issuer.configuration();
		final var ttl = Objects.requireNonNull(configuration.getAccessTokenTimeToLive(), "Missing access token TTL");

		final var expires = Instant.now().plus(ttl);
		final var scope = String.join(" ", scopes);
		final var providerIssuer = issuer.issuer();
		final var audience = audience(providerIssuer, endpoint, authorizedResources(grant, resources), scopes);

		// an access token addressed to nothing is a token nobody could ever accept,
		// and never what was actually configured; issuing one anyway would grant an
		// audience by omission rather than by an administrator naming it explicitly
		if (audience.isEmpty()) {
			LOG.fine(() -> "invalid_target; grant=" + grant + "; endpoint=" + endpoint);
			throw new TokenError("invalid_target", "No resource configured for the granted scope", BAD_REQUEST);
		}

		final var admitted = OidcClaimScopes.admitted(scopes);

		final String subject;
		IuOidcClaims claims = null;
		OidcActor actor = null;
		List<String> roles = List.of();
		Iterable<? extends IuAuthorizationDetails> released = null;

		if (grant != null) {
			// code or refresh grants only
			subject = principal(endpoint, clientId, grant);
			claims = claims(subject, admitted);
			if (!subject.equals(grant.getPrincipalName()))
				actor = actor(grant.getPrincipalName(), admitted);
			roles = roles(endpoint, subject);
			released = grant.getReleasedAuthorizationDetails();
		} else
			subject = clientId;

		final var accessTokenBuilder = WebToken.builder() //
				.jti() //
				.iss(providerIssuer) //
				.sub(subject) //
				.aud(audience.toArray(URI[]::new)) //
				.iat() //
				.exp(expires) //
				.claim("client_id", clientId, String.class) //
				.claim("scope", scope, String.class);

		// an access token names the actor and nothing else about them: a resource
		// server needs to know an action was delegated, not who the delegate is
		if (actor != null)
			accessTokenBuilder.claim("act", (OidcActor) new Actor(actor.getSub(), null, null), OidcActor.class);

		if (!roles.isEmpty())
			accessTokenBuilder.claim("roles", roles.toArray(String[]::new), String[].class);

		authorizationDetails(accessTokenBuilder, released);

		final var accessToken = sign(endpoint, ACCESS_TOKEN_TYPE, accessTokenBuilder);

		String idToken = null;
		String refreshToken = null;

		if (grant != null) {
			// code or refresh grants only
			if (scopes.contains(OPENID))
				idToken = idToken(accessToken, endpoint, clientId, subject, claims, actor, roles, released, grant);

			if (scopes.contains(OFFLINE_ACCESS)) {
				final var authAge = Duration.between(grant.getAuthnInstant(), Instant.now());
				final var maxAge = Objects.requireNonNull(configuration.getRefreshTokenTimeToLive(),
						"Missing refresh token TTL");
				final var remaining = maxAge.minus(authAge);

				// a refresh token that couldn't outlive even one more access token isn't
				// worth issuing; the client has to re-authenticate once its session gets
				// this close to the absolute limit rather than keep rotating a token that
				// bottoms out to nothing
				if (remaining.compareTo(ttl) > 0)
					refreshToken = grantStore.put(GrantStore.REFRESH, providerIssuer,
							issuer.issuerKey(endpoint.getAlg()), remaining, grant);
			}
		}

		final var subjectName = subject;
		LOG.info(() -> "token-issue:" + grantType + ":" + clientId + ":" + subjectName + " [" + scope + "] " + grant);

		return new OidcTokenResult.Issued(accessToken, BEARER, ttl.getSeconds(), scope, idToken, refreshToken,
				released);
	}

	/**
	 * Reconciles a token request's own {@code resource} parameter against what a
	 * code or refresh grant recorded when it was first authorized, to determine
	 * which resources an access token's audience may be derived from.
	 *
	 * <p>
	 * For {@code client_credentials} there is no prior grant to anchor to, so the
	 * request's own {@code resource} is authoritative outright &mdash; that's
	 * {@code grant} answering {@code null}. For a code or refresh grant, what the
	 * authorization endpoint recorded on {@link OidcGrant#getResource()} is
	 * authoritative: the redemption request may repeat a subset of it to narrow the
	 * audience further, but naming a resource the grant never authorized is refused
	 * rather than silently widening what the token &mdash; or, across however many
	 * times a refresh token descending from it is redeemed, what a long-lived
	 * session can go on minting tokens for &mdash; is addressed to. A grant
	 * recording no resource at all imposes no bound of its own, so the request's
	 * own {@code resource} governs exactly as it does for
	 * {@code client_credentials}.
	 * </p>
	 *
	 * @param grant     redeemed grant, or {@code null} for
	 *                  {@code client_credentials}
	 * @param requested {@code resource} values from the token request
	 * @return resources to derive the audience from
	 * @throws TokenError if the request names a resource the grant did not
	 *                    authorize
	 */
	private static Set<String> authorizedResources(OidcGrant grant, Set<String> requested) {
		if (grant == null)
			return requested;

		final var recorded = grant.getResource();
		if (recorded == null //
				|| recorded.length == 0)
			return requested;

		final Set<String> authorized = new LinkedHashSet<>(Arrays.asList(recorded));
		if (requested.isEmpty())
			return authorized;

		if (!authorized.containsAll(requested))
			throw new TokenError("invalid_target", "resource was not authorized when this grant was issued",
					BAD_REQUEST);

		return requested;
	}

	/**
	 * Settles the principal a code or refresh grant's tokens are issued for, and
	 * enforces the endpoint's access roles against it.
	 *
	 * <p>
	 * A backdoor request &mdash; one naming
	 * {@link OidcGrant#getImpersonatedPrincipalName()} &mdash; is honored only
	 * outside a production deployment, and only when the principal the identity
	 * provider actually authenticated holds one of the endpoint's
	 * {@link IuOidcClientEndpoint#getBackdoorRoles() backdoor roles}. A request
	 * naming one in production is answered as if it had named none, after logging a
	 * warning; the authenticated principal's own backdoor roles are not even
	 * checked in that case, since the outcome does not depend on them.
	 * </p>
	 *
	 * @param endpoint endpoint that authenticated
	 * @param clientId authenticated client ID, for the log record naming a refused
	 *                 impersonation attempt
	 * @param grant    redeemed grant
	 * @return effective principal name
	 * @throws TokenError if impersonation was requested but not honored, or the
	 *                    effective principal holds none of the endpoint's access
	 *                    roles
	 */
	private String principal(IuOidcClientEndpoint endpoint, String clientId, OidcGrant grant) {
		final var principalName = grant.getPrincipalName();
		final var impersonatedPrincipalName = grant.getImpersonatedPrincipalName();

		var effectivePrincipalName = principalName;
		if (impersonatedPrincipalName != null) {
			if (reference.isProduction())
				LOG.warning(() -> "token-impersonation-denied:production:" + clientId + ":" + principalName);
			else if (!hasAnyRole(endpoint.getBackdoorRoles(), principalName))
				throw new TokenError("access_denied", "Not authorized to impersonate another principal", FORBIDDEN);
			else
				effectivePrincipalName = impersonatedPrincipalName;
		}

		if (!hasAnyRole(endpoint.getAccessRoles(), effectivePrincipalName))
			throw new TokenError("access_denied", "Not authorized for this endpoint", FORBIDDEN);

		return effectivePrincipalName;
	}

	/**
	 * Determines whether a principal holds at least one of a set of identity roles.
	 *
	 * <p>
	 * A role naming everyone and a role naming this principal by name are settled
	 * here rather than asked about, so the identity source only ever sees roles
	 * that need a real lookup, and is not consulted at all when none do.
	 * </p>
	 *
	 * @param roles         identity roles to check, or {@code null} to admit no one
	 * @param principalName principal name to check
	 * @return true if {@code roles} names at least one role the principal holds
	 * @throws TokenError if the principal name is invalid
	 */
	private boolean hasAnyRole(Iterable<String> roles, String principalName) {
		if (roles == null)
			return false;

		final List<String> roleList = new ArrayList<>();
		for (final var role : roles)
			if (role != null)
				if (role.equalsIgnoreCase(ALL) //
						|| role.equalsIgnoreCase(principalName))
					return true;
				else
					roleList.add(role);

		if (roleList.isEmpty())
			return false;

		try {
			return reference.getIdentitySource().hasRole(principalName, roleList.toArray(String[]::new));
		} catch (RuntimeException e) {
			throw new TokenError("invalid_request", "Invalid principal name", BAD_REQUEST, e);
		}
	}

	/**
	 * Reads the claims a grant's scope admits about one principal.
	 *
	 * @param principalName principal name
	 * @param admitted      claim names the granted scope admits
	 * @return claims held for {@code principalName}
	 * @throws TokenError            if the principal name is invalid
	 * @throws IllegalStateException if the source answers for somebody else
	 */
	private IuOidcClaims claims(String principalName, Set<String> admitted) {
		final IuOidcClaims claims;
		try {
			// the rendered document is never published from here -- an ID token names
			// its own iss and aud -- so the source is told to render neither
			claims = Objects.requireNonNull(reference.getClaimsSource().claims(principalName, admitted, null, null),
					"Missing claims");
		} catch (RuntimeException e) {
			throw new TokenError("invalid_request", "Invalid principal name", BAD_REQUEST, e);
		}

		// as at the UserInfo endpoint: asserting somebody else's claims about this
		// subject would be worse than asserting none
		if (!principalName.equals(claims.getSub()))
			throw new IllegalStateException(
					"Claims source answered for " + claims.getSub() + " rather than " + principalName);

		return claims;
	}

	/**
	 * Reads the {@code act} claim for the principal really behind an impersonated
	 * request.
	 *
	 * <p>
	 * The actor's own display name and email ride along on the ID token so a
	 * relying party can show its user whose session they are looking through, and
	 * only when the granted scope admits those claims about anyone &mdash; the same
	 * gate the subject's claims pass.
	 * </p>
	 *
	 * @param principalName real principal's name
	 * @param admitted      claim names the granted scope admits
	 * @return actor claims
	 * @throws TokenError if the principal name is invalid
	 */
	private OidcActor actor(String principalName, Set<String> admitted) {
		final var claims = claims(principalName, admitted);
		return new Actor(principalName, claims.getName(), claims.getEmail());
	}

	/**
	 * Determines which of an endpoint's application roles a principal holds, by
	 * identity role.
	 *
	 * @param endpoint      endpoint that authenticated
	 * @param principalName effective principal name
	 * @return {@link IuOidcClientRole#getRole() application role names} the
	 *         principal is entitled to act in; empty if none match or the endpoint
	 *         declares none
	 * @throws TokenError if the principal name is invalid
	 */
	private List<String> roles(IuOidcClientEndpoint endpoint, String principalName) {
		final var declared = endpoint.getRoles();
		if (declared == null)
			return List.of();

		final List<String> matched = new ArrayList<>();
		for (final var role : declared)
			if (role != null //
					&& hasAnyRole(role.getIdRoles(), principalName))
				matched.add(role.getRole());

		return matched;
	}

	/**
	 * Builds an ID token for a grant.
	 *
	 * @param accessToken access token issued alongside this ID token
	 * @param endpoint    endpoint that authenticated
	 * @param clientId    authenticated client ID, which is the ID token's audience
	 * @param subject     effective principal name {@link #principal} settled on
	 * @param claims      effective principal's claims, limited to what the scope
	 *                    admits
	 * @param actor       real principal's claims, or {@code null} unless a backdoor
	 *                    request is being honored
	 * @param roles       application roles the effective principal is entitled to
	 *                    act in
	 * @param released    authorization details the grant released, or {@code null}
	 * @param grant       redeemed grant
	 * @return signed, and where the endpoint registers a key, encrypted ID token
	 */
	private String idToken(String accessToken, IuOidcClientEndpoint endpoint, String clientId, String subject,
			IuOidcClaims claims, OidcActor actor, List<String> roles,
			Iterable<? extends IuAuthorizationDetails> released, OidcGrant grant) {
		final var builder = WebToken.builder() //
				.jti() //
				.iss(issuer.issuer()) //
				.sub(subject) //
				.aud(URI.create(clientId)) //
				.iat() //
				.exp(Instant.now().plus(Objects.requireNonNull(issuer.configuration().getAccessTokenTimeToLive(),
						"Missing access token TTL")));

		// the source was told which claims the scope admits and answers those and no
		// others, so what comes back goes on the token as it is
		claim(builder, "name", claims.getName());
		claim(builder, "given_name", claims.getGivenName());
		claim(builder, "family_name", claims.getFamilyName());
		claim(builder, "middle_name", claims.getMiddleName());
		claim(builder, "email", claims.getEmail());

		final var nonce = grant.getNonce();
		if (nonce != null)
			builder.nonce(nonce);

		final var issuerKey = issuer.issuerKey(endpoint.getAlg());
		builder.claim("at_hash", atHash(issuerKey.getAlgorithm(), accessToken), String.class);

		final var authnInstant = grant.getAuthnInstant();
		if (authnInstant != null)
			builder.claim("auth_time", authnInstant.getEpochSecond(), Long.class);

		final var authority = grant.getAuthnAuthority();
		if (authority != null)
			builder.claim("idp", authority, String.class);

		if (actor != null)
			builder.claim("act", actor, OidcActor.class);

		if (!roles.isEmpty())
			builder.claim("roles", roles.toArray(String[]::new), String[].class);

		authorizationDetails(builder, released);

		return sign(endpoint, ID_TOKEN_TYPE, builder);
	}

	/**
	 * Adds the {@code authorization_details} a grant released.
	 *
	 * <p>
	 * Declared as {@link IuAuthorizationDetails} rather than as whatever concrete
	 * type the deployment built, and deliberately so. Nothing here knows what a
	 * detail says: an integration parses one into its own interface over a
	 * {@code JsonProxy}, and a serializer hands that proxy's document through
	 * whole, so what a relying party reads back off the token is the JSON its own
	 * component produced &mdash; unfiltered, unreordered, and unknown to this
	 * module. Naming the concrete type here would achieve nothing but a compile
	 * dependency on it.
	 * </p>
	 *
	 * <p>
	 * Set on both tokens issued: a resource server reads the access token's copy
	 * to decide what an action is authorized for, and a relying party reads the ID
	 * token's to know what it was granted.
	 * </p>
	 *
	 * @param builder  token being built
	 * @param released authorization details the grant released, or {@code null} for
	 *                 a grant that released none
	 * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-7">RFC 9396
	 *      &sect;7</a>
	 */
	private static void authorizationDetails(WebTokenBuilder builder,
			Iterable<? extends IuAuthorizationDetails> released) {
		if (released == null)
			return;

		for (final var detail : released)
			if (detail != null)
				builder.authorizationDetails(detail, IuAuthorizationDetails.class);
	}

	/**
	 * Adds a claim, unless the source held nothing for it.
	 *
	 * @param builder token being built
	 * @param name    claim name
	 * @param value   claim value, or {@code null} to add nothing
	 */
	private static void claim(WebTokenBuilder builder, String name, String value) {
		if (value != null)
			builder.claim(name, value, String.class);
	}

	/**
	 * Computes the {@code at_hash} claim OpenID Connect defines for validating an
	 * access token against the ID token issued alongside it.
	 *
	 * <p>
	 * The hash algorithm is the one that signs the ID token, not any algorithm of
	 * the access token's own, since a client only ever verifies {@code at_hash}
	 * after having already verified the ID token it came from.
	 * </p>
	 *
	 * @param idTokenAlg  algorithm the ID token is signed with
	 * @param accessToken access token issued alongside the ID token
	 * @return base64url-encoded left half of the access token's hash
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#CodeIDToken">OpenID
	 *      Connect Core 1.0 &sect;3.3.2.11</a>
	 */
	private static String atHash(Algorithm idTokenAlg, String accessToken) {
		final var encoded = accessToken.getBytes(StandardCharsets.US_ASCII);
		final var hash = IuException
				.unchecked(() -> MessageDigest.getInstance("SHA-" + idTokenAlg.size).digest(encoded));

		return IuText.base64Url(Arrays.copyOf(hash, hash.length / 2));
	}

	/**
	 * Signs a token, and encrypts it when the endpoint registers a key to encrypt
	 * to.
	 *
	 * @param endpoint endpoint the token is issued to
	 * @param type     {@code typ} header
	 * @param builder  token to sign
	 * @return compact serialization
	 */
	private String sign(IuOidcClientEndpoint endpoint, String type, WebTokenBuilder builder) {
		final var issuerKey = issuer.issuerKey(endpoint.getAlg());
		final var token = builder.build();

		LOG.fine(() -> "oidc-issue:" + type + ":" + issuerKey.getAlgorithm().alg + ":" + issuerKey.getKeyId() + " "
				+ token);

		final var encryptKey = endpoint.getEncryptJwk();
		final var encryption = endpoint.getEnc();
		if (encryptKey == null //
				|| encryption == null)
			return OidcJose.sign(token.toString(), type, issuerKey);

		return OidcJose.signAndEncrypt(token.toString(), type, issuerKey, encryptKey, encryption);
	}

	/**
	 * Reads a required request parameter.
	 *
	 * @param value parameter value
	 * @param name  parameter name
	 * @return {@code value}
	 * @throws TokenError if the parameter is absent
	 */
	private static String required(String value, String name) {
		if (value == null)
			throw new TokenError("invalid_request", "Missing " + name, BAD_REQUEST);

		return value;
	}

	/**
	 * The {@code act} claim as this endpoint builds it.
	 *
	 * @param sub   actor's principal name
	 * @param name  actor's display name, or {@code null}
	 * @param email actor's email address, or {@code null}
	 */
	private record Actor(String sub, String name, String email) implements OidcActor {

		@Override
		public String getSub() {
			return sub;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getEmail() {
			return email;
		}
	}

}
