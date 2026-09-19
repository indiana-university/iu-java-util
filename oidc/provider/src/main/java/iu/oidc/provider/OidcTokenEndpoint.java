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
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.IuAuthorizationDetails;
import edu.iu.jwt.WebToken;
import edu.iu.jwt.WebTokenBuilder;
import edu.iu.oidc.IuOidcActor;
import edu.iu.oidc.config.IuOidcClaimsSource;
import edu.iu.oidc.config.IuOidcClaimsSource.Usage;
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
 * <li>{@code urn:ietf:params:oauth:grant-type:token-exchange} answers for one
 * principal on the strength of another's token &mdash; see below. It is the one
 * grant type with no stored grant behind it: what it answers for is named in
 * the request and settled there.</li>
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
 * authenticated. A <strong>token exchange</strong> is how a request answers for
 * somebody else: it presents an access token this provider already issued as
 * {@code actor_token}, names the principal it wants to answer for as
 * {@code subject_token} under the
 * {@link #PRINCIPAL_NAME_TOKEN_TYPE principal name token type}, and is honored
 * only when the principal that token was issued to holds one of the endpoint's
 * {@link IuOidcClientEndpoint#getBackdoorRoles() backdoor roles} and
 * {@link IuOidcProviderReference#isProduction() this deployment isn't a
 * production one}. The named principal is then who the tokens answer for, and
 * the one that authenticated rides along as the {@link IuOidcActor act} claim.
 * </p>
 *
 * <p>
 * The exchanged tokens are deliberately narrower than what they descend from.
 * The granted scope cannot exceed what the presented token already carries, so
 * exchanging never gains authority; {@code offline_access} is dropped, so no
 * refresh token descends from an exchange and an impersonated session cannot
 * outlive the token that bought it; and the ID token carries no top-level
 * {@code auth_time}, because its subject never authenticated &mdash; the
 * actor's own authentication time rides inside {@code act} instead, where it is
 * true.
 * </p>
 *
 * <p>
 * Whichever way the principal is settled, the effective principal must hold one
 * of the endpoint's {@link IuOidcClientEndpoint#getAccessRoles() access roles}
 * to get a token at all &mdash; impersonating somebody doesn't inherit the
 * impersonator's access &mdash; and the {@link IuOidcClientRole application
 * roles} it matches are added as claims to both the access token and the ID
 * token.
 * </p>
 *
 * <h2>Claims follow scope</h2>
 *
 * <p>
 * What an ID token says about its subject is decided the same way a UserInfo
 * response is: the granted scope admits a set of claims, deny-by-default, and
 * the {@link IuOidcClaimsSource claims source} is asked for those and no others.
 * A client that asks for {@code openid} alone gets an ID token naming a subject
 * and nothing else about them.
 * </p>
 *
 * <p>
 * Nothing here reads a claim. The source is told which names the scope admits
 * and writes them onto the token itself, typed as it states they serialize, so
 * how a claim prints stays with whatever holds it. It writes before the claims
 * this provider derives &mdash; {@code at_hash}, {@code act}, {@code roles} and
 * the rest &mdash; which are not a deployment's to set.
 * </p>
 *
 * <p>
 * An access token is asked for a different set than an ID token. RFC 9068
 * defines no claim describing the end user, so none of the &sect;5.4 sets goes
 * on one; what a deployment releases for a scope of its own does, since a
 * resource server has nowhere else to read it. Which of the two is being built
 * reaches the source as {@link Usage}, so a claim published from UserInfo can be
 * withheld from a token the client keeps.
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">RFC 6749
 *      &sect;5.2</a>
 */
public class OidcTokenEndpoint {

	private static final Logger LOG = Logger.getLogger(OidcTokenEndpoint.class.getName());

	/** {@code typ} of an issued access token. */
	private static final String ACCESS_TOKEN_TYPE = "at+jwt";

	/** {@code typ} of an issued ID token. */
	private static final String ID_TOKEN_TYPE = "JWT";

	/** The only {@code token_type} this provider issues. */
	private static final String BEARER = "Bearer";

	/** A role naming everyone, which needs no identity lookup. */
	private static final String ALL = "all";

	/**
	 * RFC 8693 {@code grant_type}.
	 */
	static final String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";

	/**
	 * RFC 8693 &sect;3 token type identifier for an OAuth 2.0 access token, which
	 * is what an {@code actor_token} must be and the only thing an exchange here
	 * issues.
	 */
	static final String ACCESS_TOKEN_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

	/**
	 * Token type identifier for a bare principal name, naming whoever an exchange
	 * is asking to answer for.
	 *
	 * <p>
	 * Provider-defined, which is what RFC 8693 &sect;3 leaves room for: the
	 * registry holds the identifiers a token format has earned, and nothing in it
	 * describes a principal a caller simply names. That is exactly what this is
	 * &mdash; the caller proves it holds the {@code actor_token} and then
	 * <em>asserts</em> the subject, so this is impersonation authorized by the
	 * actor's own role rather than delegation authorized by the subject. RFC 8693
	 * &sect;4.4's {@code may_act} is the mechanism this deliberately doesn't use,
	 * and why the whole thing is refused in production.
	 * </p>
	 */
	static final String PRINCIPAL_NAME_TOKEN_TYPE = "https://iu.edu/oauth/token-type/principal-name";

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
		this.issuer = new OidcIssuer(reference::getConfiguration, reference::getClaimsSource);
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

		final var authenticated = authenticate(providerIssuer, resources, request.getRedirectUri(), clientId,
				credential);
		final var endpoint = authenticated.endpoint();

		final Set<String> scopes;
		final Redeemed redeemed;
		switch (grantType) {
		case "client_credentials":
			// RFC 6749 §4.4 has no unauthenticated form of this grant: the client is the
			// resource owner, so the credential is the whole authorization. A public
			// registration has none, which would make client_id alone enough
			if (authenticated.isPublic()) {
				LOG.info(() -> "token-deny:public-client-credentials:" + clientId);
				throw new TokenError("invalid_client",
						"client_credentials requires an authenticated client", UNAUTHORIZED);
			}

			redeemed = null;
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

		case "authorization_code": {
			final var grant = code(authenticated, clientId, request);
			redeemed = Redeemed.of(grant);
			scopes = scopes(grant.getScope());
			break;
		}

		case "refresh_token": {
			final var grant = refresh(endpoint, clientId, request);
			redeemed = Redeemed.of(grant);
			scopes = scopes(grant.getScope());
			break;
		}

		case TOKEN_EXCHANGE: {
			final var actor = exchangeActor(endpoint, clientId, request);
			// principalName here is the actor, so the authentication time and authority
			// read off their token are theirs -- which is what Redeemed means by both.
			// acr(...) keeps them out of the top level, where they would describe a
			// subject who never authenticated, and act(...) is where they belong instead
			redeemed = new Redeemed(actor.getSubject(), required(request.getSubjectToken(), "subject_token"),
					actor.getToken().getClaim("auth_time", Instant.class),
					actor.getToken().getClaim("acr", String.class), null, Set.of(), null, null);
			scopes = exchangeScopes(endpoint, providerIssuer, request, resources, actor.getScope());
			break;
		}

		default:
			throw new TokenError("unsupported_grant_type", "Unsupported grant_type " + grantType, BAD_REQUEST);
		}

		return respond(endpoint, clientId, grantType, redeemed, resources, scopes);
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
	 * @return endpoint that authenticated, and how
	 * @throws TokenError if the client is unregistered, disabled, or nothing
	 *                    verifies, or if no endpoint registers every requested
	 *                    resource
	 */
	private Authenticated authenticate(URI providerIssuer, Set<String> resources, String redirectUri,
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
				return new Authenticated(endpoint, method);
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
	 * @param authenticated endpoint that authenticated, and how
	 * @param clientId      authenticated client ID
	 * @param request       incoming request
	 * @return redeemed grant
	 * @throws TokenError if the code doesn't redeem, names a different client or
	 *                    redirect URI, or the PKCE challenge isn't satisfied
	 */
	private OidcGrant code(Authenticated authenticated, String clientId, OidcTokenRequest request) {
		final var code = required(request.getCode(), "code");
		final var redirectUri = required(request.getRedirectUri(), "redirect_uri");

		final var grant = take(GrantStore.CODE, authenticated.endpoint(), code);

		if (!clientId.equals(grant.getClientId()))
			throw new TokenError("invalid_grant", "Authorization code was issued to a different client", BAD_REQUEST);

		// RFC 6749 §4.1.3: the value must be identical to the one the code was issued
		// to, not merely one this client registered. Endpoint eligibility already
		// matched it against a registration, but a client registering several -- the
		// per-environment pattern IuOidcClientEndpoint documents -- would otherwise
		// redeem through one a code obtained through another, across different keys
		// and a different resource set
		final var granted = grant.getRedirectUri();
		if (granted == null //
				|| !granted.toString().equals(redirectUri))
			throw new TokenError("invalid_grant", "redirect_uri does not match the authorization request",
					BAD_REQUEST);

		verifyPkce(authenticated, grant, request.getCodeVerifier());

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
		// A spent reference is remembered for as long as one could still be presented,
		// which is the longest any reference lives -- a refresh token's ceiling, since
		// an authorization code's is far shorter. Read here rather than held, like
		// every other configuration value this endpoint reads, so a deployment that
		// lengthens it does not leave a shorter replay window behind
		final var tombstoneTtl = Objects.requireNonNull(issuer.configuration().getRefreshTokenTimeToLive(),
				"Missing refresh token TTL");

		try {
			return Objects.requireNonNull(
					grantStore.take(type, issuer.issuer(), issuer.issuerKey(endpoint.getAlg()), reference,
							tombstoneTtl),
					"Empty grant");
		} catch (RuntimeException e) {
			throw new TokenError("invalid_grant", "Invalid or expired " + type + " reference", BAD_REQUEST, e);
		}
	}

	/**
	 * Verifies the {@code actor_token} a token exchange presents, and everything
	 * about the exchange that doesn't depend on what it is asking for.
	 *
	 * <p>
	 * The token must be one this provider issued <em>to itself</em> &mdash; its
	 * audience must name this issuer, the same thing
	 * {@link OidcUserinfoEndpoint} requires, since the token endpoint is acting as
	 * a resource server for it here. A token addressed only to some external API
	 * resource was never addressed to this provider and is refused rather than
	 * honored on the strength of a signature alone; a client that wants to use
	 * token exchange has to register a resource for this provider's own issuer
	 * identifier.
	 * </p>
	 *
	 * <p>
	 * An actor token already carrying an {@code act} claim is refused outright.
	 * RFC 8693 &sect;4.1 wants a further exchange to nest the previous actor
	 * inside the new one, and {@link IuOidcActor} has nowhere to put it &mdash; so
	 * rather than issue a token that silently drops who was really behind the one
	 * before it, chaining is not allowed at all.
	 * </p>
	 *
	 * @param endpoint endpoint that authenticated
	 * @param clientId authenticated client ID
	 * @param request  incoming request
	 * @return verified {@code actor_token}
	 * @throws TokenError if the exchange names the wrong token types, wants a token
	 *                    type this provider doesn't issue, presents a token that
	 *                    doesn't verify or belongs to another client or already
	 *                    names an actor, or asks to answer for the actor itself
	 */
	private OidcTokenAuthorization exchangeActor(IuOidcClientEndpoint endpoint, String clientId,
			OidcTokenRequest request) {
		final var subjectToken = required(request.getSubjectToken(), "subject_token");
		final var subjectTokenType = required(request.getSubjectTokenType(), "subject_token_type");
		if (!PRINCIPAL_NAME_TOKEN_TYPE.equals(subjectTokenType))
			throw new TokenError("invalid_request", "Unsupported subject_token_type " + subjectTokenType, BAD_REQUEST);

		final var actorToken = required(request.getActorToken(), "actor_token");
		final var actorTokenType = required(request.getActorTokenType(), "actor_token_type");
		if (!ACCESS_TOKEN_TOKEN_TYPE.equals(actorTokenType))
			throw new TokenError("invalid_request", "Unsupported actor_token_type " + actorTokenType, BAD_REQUEST);

		// optional, and answered with an access token either way; naming something
		// else is refused rather than quietly answered with what wasn't asked for
		final var requestedTokenType = request.getRequestedTokenType();
		if (requestedTokenType != null //
				&& !ACCESS_TOKEN_TOKEN_TYPE.equals(requestedTokenType))
			throw new TokenError("invalid_request", "Unsupported requested_token_type " + requestedTokenType,
					BAD_REQUEST);

		final OidcTokenAuthorization actor;
		try {
			actor = OidcTokenAuthorization.verify(actorToken, issuer.configuration(), issuer.issuer());
		} catch (SecurityException e) {
			throw new TokenError("invalid_grant",
					"actor_token is not a valid access token addressed to this provider", BAD_REQUEST, e);
		}

		if (!clientId.equals(actor.getClientId()))
			throw new TokenError("invalid_grant", "actor_token was issued to a different client", BAD_REQUEST);

		if (actor.getToken().getClaim("act", IuOidcActor.class) != null)
			throw new TokenError("invalid_grant", "actor_token already names an actor", BAD_REQUEST);

		// answering for yourself grants nothing you don't already hold, and would
		// make an exchange a way to renew a token past the age its authentication
		// was good for
		if (subjectToken.equals(actor.getSubject()))
			throw new TokenError("invalid_grant", "actor_token already answers for this subject", BAD_REQUEST);

		return actor;
	}

	/**
	 * Settles what a token exchange is granted.
	 *
	 * <p>
	 * Shaped like {@code client_credentials} rather than like a redemption, since
	 * there is no recorded grant to take a scope from: the request and the
	 * endpoint's registration decide, and naming no {@code resource} considers
	 * every resource whose scope overlaps what was asked for. Two things then
	 * narrow it. It is intersected with what the presented token already carries,
	 * so exchanging never gains authority the caller didn't already have; and
	 * {@code offline_access} is dropped, so no refresh token descends from an
	 * exchange and an impersonated session cannot outlive the token that bought it.
	 * </p>
	 *
	 * @param endpoint       endpoint that authenticated
	 * @param providerIssuer this provider's issuer identifier
	 * @param request        incoming request
	 * @param resources      {@code resource} values from the request
	 * @param actorScope     scope the presented {@code actor_token} carries
	 * @return granted scopes
	 * @throws TokenError if nothing is left to grant
	 */
	private static Set<String> exchangeScopes(IuOidcClientEndpoint endpoint, URI providerIssuer,
			OidcTokenRequest request, Set<String> resources, Set<String> actorScope) {
		final var requestedScope = request.getScope();
		final var effectiveScope = requestedScope == null ? String.join(" ", actorScope) : requestedScope;

		final var effectiveResources = resources.isEmpty()
				? resourcesGrantingScope(endpoint, providerIssuer, scopes(effectiveScope))
				: resources;

		final var granted = clientCredentialsScopes(endpoint, providerIssuer, effectiveScope, effectiveResources);
		granted.retainAll(actorScope);
		granted.remove(OidcClaimScopes.OFFLINE_ACCESS);

		if (granted.isEmpty())
			throw new TokenError("invalid_scope", "No scope granted for this exchange", BAD_REQUEST);

		return granted;
	}

	/**
	 * Verifies the PKCE challenge a grant recorded.
	 *
	 * <p>
	 * PKCE is optional for a client that authenticated and <strong>required</strong>
	 * for one that did not. RFC 9700 &sect;2.1.1 makes it a MUST for a public
	 * client, which is the whole basis on which such a registration is allowed to
	 * redeem anything: the code alone proves only that the caller received the
	 * authorization response, while the verifier proves it is the same party that
	 * began the request.
	 * </p>
	 *
	 * @param authenticated endpoint that authenticated, and how
	 * @param grant         redeemed grant
	 * @param verifier      {@code code_verifier} presented, or {@code null}
	 * @throws TokenError if a challenge was recorded and the verifier doesn't
	 *                    satisfy it, a verifier is presented against no challenge,
	 *                    or a public client recorded no challenge at all
	 */
	private static void verifyPkce(Authenticated authenticated, OidcGrant grant, String verifier) {
		final var challenge = grant.getCodeChallenge();
		if (challenge == null) {
			if (verifier != null)
				throw new TokenError("invalid_grant", "No code_challenge was recorded for this code", BAD_REQUEST);

			if (authenticated.isPublic())
				throw new TokenError("invalid_grant",
						"A public client must redeem an authorization code with PKCE", BAD_REQUEST);

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
			Redeemed redeemed, Set<String> resources, Set<String> scopes) {
		final var configuration = issuer.configuration();
		final var ttl = Objects.requireNonNull(configuration.getAccessTokenTimeToLive(), "Missing access token TTL");

		final var expires = Instant.now().plus(ttl);
		final var scope = String.join(" ", scopes);
		final var providerIssuer = issuer.issuer();
		final var audience = audience(providerIssuer, endpoint, authorizedResources(redeemed, resources), scopes);

		// an access token addressed to nothing is a token nobody could ever accept,
		// and never what was actually configured; issuing one anyway would grant an
		// audience by omission rather than by an administrator naming it explicitly
		if (audience.isEmpty()) {
			LOG.fine(() -> "invalid_target; grant=" + redeemed + "; endpoint=" + endpoint);
			throw new TokenError("invalid_target", "No resource configured for the granted scope", BAD_REQUEST);
		}

		final String subject;
		Set<String> idTokenClaims = Set.of();
		Set<String> accessTokenClaims = Set.of();
		IuOidcActor actor = null;
		List<String> roles = List.of();
		Iterable<? extends IuAuthorizationDetails> released = null;

		if (redeemed != null) {
			// every grant that answers for an end user rather than for the client itself
			// -- and the only case with a principal to ask a claims source about, so a
			// deployment issuing client credentials alone never needs one bound
			subject = principal(endpoint, clientId, redeemed);
			idTokenClaims = admitted(scopes, Usage.ID_TOKEN);
			accessTokenClaims = admitted(scopes, Usage.ACCESS_TOKEN);
			if (!subject.equals(redeemed.principalName()))
				actor = actor(redeemed.principalName(), redeemed.authnInstant(), redeemed.authnAuthority(),
						idTokenClaims);
			roles = roles(endpoint, subject);
			released = redeemed.released();
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

		// RFC 9068 defines no claim describing the end user, so nothing OpenID Connect
		// binds to a scope goes on an access token. What a deployment releases for a
		// scope of its own does, since a resource server has nowhere else to read it
		// and no UserInfo request of its own to make
		if (!accessTokenClaims.isEmpty())
			claims(subject, accessTokenClaims, accessTokenBuilder);

		// an access token names the actor and nothing else about them: a resource
		// server needs to know an action was delegated, not who the delegate is.
		// auth_time rides along because it is the actor's own, and an exchange has
		// nowhere else to read it back from
		if (actor != null)
			accessTokenBuilder.claim("act",
					(IuOidcActor) new Actor(actor.getSub(), null, null, actor.getAuthTime(), actor.getAcr()),
					IuOidcActor.class);

		// when this token answers for the end user who authenticated, RFC 9068 §2.2.1
		// allows it to say when; a token exchange reads that back off the token it is
		// presented, which is the only record of the actor's own authentication it
		// has, and answers it inside act rather than here -- its own subject never
		// authenticated, so nothing at this level may claim they did
		if (redeemed != null //
				&& redeemed.impersonated() == null //
				&& redeemed.authnInstant() != null)
			accessTokenBuilder.claim("auth_time", redeemed.authnInstant().getEpochSecond(), Long.class);

		// RFC 9068 §2.2.1 admits acr on an access token, and this is the one that
		// matters most: a resource server reads this token, not the ID token, so it is
		// here that sub has to be legible as an end user rather than a client
		if (redeemed != null)
			acr(accessTokenBuilder, redeemed);

		if (!roles.isEmpty())
			accessTokenBuilder.claim("roles", roles.toArray(String[]::new), String[].class);

		authorizationDetails(accessTokenBuilder, released);

		final var accessToken = sign(endpoint, ACCESS_TOKEN_TYPE, accessTokenBuilder);

		String idToken = null;
		String refreshToken = null;

		if (redeemed != null) {
			// every grant that answers for an end user rather than for the client itself
			if (scopes.contains(OidcClaimScopes.OPENID))
				idToken = idToken(accessToken, endpoint, clientId, subject, idTokenClaims, actor, roles, released,
						redeemed);

			// an exchange never grants offline_access, so it never reaches this
			if (scopes.contains(OidcClaimScopes.OFFLINE_ACCESS)) {
				final var authAge = Duration.between(redeemed.authnInstant(), Instant.now());
				final var maxAge = Objects.requireNonNull(configuration.getRefreshTokenTimeToLive(),
						"Missing refresh token TTL");
				final var remaining = maxAge.minus(authAge);

				// a refresh token that couldn't outlive even one more access token isn't
				// worth issuing; the client has to re-authenticate once its session gets
				// this close to the absolute limit rather than keep rotating a token that
				// bottoms out to nothing
				if (remaining.compareTo(ttl) > 0)
					refreshToken = grantStore.put(GrantStore.REFRESH, providerIssuer,
							issuer.issuerKey(endpoint.getAlg()), remaining, redeemed.grant());
			}
		}

		final var subjectName = subject;
		LOG.info(() -> "token-issue:" + grantType + ":" + clientId + ":" + subjectName + " [" + scope + "] " + redeemed);

		// RFC 8693 §2.2.1 requires an exchange to name what it issued; every other
		// grant type answers a response shape that has no such member, so it is left
		// out rather than written for the sake of being written
		final var issuedTokenType = TOKEN_EXCHANGE.equals(grantType) ? ACCESS_TOKEN_TOKEN_TYPE : null;

		return new OidcTokenResult.Issued(accessToken, BEARER, ttl.getSeconds(), scope, idToken, refreshToken, released,
				issuedTokenType);
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
	private static Set<String> authorizedResources(Redeemed redeemed, Set<String> requested) {
		if (redeemed == null)
			return requested;

		final var authorized = redeemed.resource();
		if (authorized.isEmpty())
			return requested;

		if (requested.isEmpty())
			return authorized;

		if (!authorized.containsAll(requested))
			throw new TokenError("invalid_target", "resource was not authorized when this grant was issued",
					BAD_REQUEST);

		return requested;
	}

	/**
	 * Settles the principal a grant's tokens are issued for, and enforces the
	 * endpoint's access roles against it.
	 *
	 * <p>
	 * A {@link Redeemed#impersonated() token exchange naming another principal} is
	 * honored only outside a production deployment, and only when the principal
	 * that actually authenticated holds one of the endpoint's
	 * {@link IuOidcClientEndpoint#getBackdoorRoles() backdoor roles}. Production
	 * refuses rather than quietly answering for the caller instead: a client that
	 * asked for somebody else's token must not be handed its own without being told.
	 * The refusal is the same either way, so which principals hold a backdoor role
	 * cannot be learned by probing a production deployment.
	 * </p>
	 *
	 * <p>
	 * The access roles are then checked against the <em>effective</em> principal, so
	 * impersonating somebody does not inherit the impersonator's access.
	 * </p>
	 *
	 * @param endpoint endpoint that authenticated
	 * @param clientId authenticated client ID, for the log record naming a refused
	 *                 impersonation attempt
	 * @param redeemed what is being redeemed
	 * @return effective principal name
	 * @throws TokenError if impersonation was requested but not honored, or the
	 *                    effective principal holds none of the endpoint's access
	 *                    roles
	 */
	private String principal(IuOidcClientEndpoint endpoint, String clientId, Redeemed redeemed) {
		final var principalName = redeemed.principalName();
		final var impersonated = redeemed.impersonated();

		var effectivePrincipalName = principalName;
		if (impersonated != null) {
			if (reference.isProduction()) {
				LOG.warning(() -> "token-impersonation-denied:production:" + clientId + ":" + principalName);
				throw new TokenError("access_denied", "Token exchange is not available in this deployment", FORBIDDEN);
			} else if (!hasAnyRole(endpoint.getBackdoorRoles(), principalName, false)) {
				LOG.warning(() -> "token-impersonation-denied:norole:" + clientId + ":" + principalName);
				throw new TokenError("access_denied", "Not authorized to impersonate another principal", FORBIDDEN);
			} else
				effectivePrincipalName = impersonated;
		}

		if (!hasAnyRole(endpoint.getAccessRoles(), effectivePrincipalName, true))
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
	 * <p>
	 * {@code wildcard} is what distinguishes the two uses. Admitting everyone is a
	 * reasonable thing for an <em>access</em> role to say &mdash; a resource open
	 * to anyone the provider authenticated. It is not a reasonable thing for a
	 * {@link IuOidcClientEndpoint#getBackdoorRoles() backdoor role} to say, because
	 * that gate decides who may answer as somebody else: a wildcard there lets
	 * every principal impersonate every other, which is never the intent of naming
	 * a role at all. Naming a principal outright still works there, since that is
	 * how a backdoor allowlist names the people who may use it.
	 * </p>
	 *
	 * @param roles         identity roles to check, or {@code null} to admit no one
	 * @param principalName principal name to check
	 * @param wildcard      whether {@value #ALL} admits everyone
	 * @return true if {@code roles} names at least one role the principal holds
	 * @throws TokenError if the principal name is invalid
	 */
	private boolean hasAnyRole(Iterable<String> roles, String principalName, boolean wildcard) {
		if (roles == null)
			return false;

		final List<String> roleList = new ArrayList<>();
		for (final var role : roles)
			if (role != null)
				if (wildcard //
						&& role.equalsIgnoreCase(ALL))
					return true;
				else if (role.equalsIgnoreCase(principalName))
					return true;
				else if (!role.equalsIgnoreCase(ALL))
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
	 * Names the claims a grant's scope admits of an end user, both halves of it.
	 *
	 * <p>
	 * The sets OpenID Connect &sect;5.4 defines are mapped here; every other scope
	 * is the deployment's, and the claims source names what those release. It is
	 * asked under {@link Usage#ID_TOKEN}, which is the narrower of the two
	 * destinations an end user's claims reach &mdash; an ID token is kept by the
	 * relying party and may outlive the access token issued beside it, where a
	 * UserInfo response is fetched on demand. A source that draws no distinction
	 * answers the same set either way.
	 * </p>
	 *
	 * <p>
	 * An {@link Usage#ACCESS_TOKEN} set carries none of the &sect;5.4 claims, since
	 * RFC 9068 defines no claim describing the end user and a resource server is
	 * not who OpenID Connect releases those to. Only what a deployment names for a
	 * scope of its own reaches one.
	 * </p>
	 *
	 * @param scopes granted scopes
	 * @param usage  token the claims are being admitted to
	 * @return claim names admitted
	 */
	private Set<String> admitted(Set<String> scopes, Usage usage) {
		final Set<String> admitted = new LinkedHashSet<>();
		if (!Usage.ACCESS_TOKEN.equals(usage))
			admitted.addAll(OidcClaimScopes.admitted(scopes));

		admitted.addAll(reference.getClaimsSource().admitted(OidcClaimScopes.additional(scopes), usage));
		return admitted;
	}

	/**
	 * Has the claims source write the claims a grant's scope admits onto a token.
	 *
	 * @param principalName principal name
	 * @param admitted      claim names the granted scope admits
	 * @param builder       token being issued
	 * @throws TokenError if the principal name is invalid, or the source refuses
	 */
	private void claims(String principalName, Set<String> admitted, WebTokenBuilder builder) {
		try {
			reference.getClaimsSource().claims(principalName, admitted, builder);
		} catch (RuntimeException e) {
			throw new TokenError("invalid_request", "Invalid principal name", BAD_REQUEST, e);
		}
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
	private IuOidcActor actor(String principalName, Instant authTime, String acr, Set<String> admitted) {
		// written to a token of its own and read back, rather than off a claims bean:
		// how a claim is typed is the source's to state, and act carries only these two
		final var held = WebToken.builder();
		claims(principalName, admitted, held);

		final var actorClaims = held.build();
		return new Actor(principalName, actorClaims.getClaim("name", String.class),
				actorClaims.getClaim("email", String.class), authTime == null ? null : authTime.getEpochSecond(), acr);
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
					&& hasAnyRole(role.getIdRoles(), principalName, true))
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
	 * @param admitted    claim names the scope admits about the effective
	 *                    principal, for the source to write
	 * @param actor       real principal's claims, or {@code null} unless a backdoor
	 *                    request is being honored
	 * @param roles       application roles the effective principal is entitled to
	 *                    act in
	 * @param released    authorization details the grant released, or {@code null}
	 * @param grant       redeemed grant
	 * @return signed, and where the endpoint registers a key, encrypted ID token
	 */
	private String idToken(String accessToken, IuOidcClientEndpoint endpoint, String clientId, String subject,
			Set<String> admitted, IuOidcActor actor, List<String> roles,
			Iterable<? extends IuAuthorizationDetails> released, Redeemed redeemed) {
		final var builder = WebToken.builder() //
				.jti() //
				.iss(issuer.issuer()) //
				.sub(subject) //
				.aud(URI.create(clientId)) //
				.iat() //
				.exp(Instant.now().plus(Objects.requireNonNull(issuer.configuration().getAccessTokenTimeToLive(),
						"Missing access token TTL")));

		// the source was told which claims the scope admits and writes those and no
		// others, typed as it states they serialize. It writes before the claims this
		// provider derives for itself, which are not a deployment's to set
		claims(subject, admitted, builder);

		final var nonce = redeemed.nonce();
		if (nonce != null)
			builder.nonce(nonce);

		final var issuerKey = issuer.issuerKey(endpoint.getAlg());
		builder.claim("at_hash", atHash(issuerKey.getAlgorithm(), accessToken), String.class);

		// only when this token's own subject is who authenticated: an exchanged token
		// names somebody a caller asked to answer for, and they never did, so the
		// actor's authentication time rides inside act instead of standing here as
		// though it were theirs
		final var authnInstant = redeemed.authnInstant();
		if (authnInstant != null //
				&& redeemed.impersonated() == null)
			builder.claim("auth_time", authnInstant.getEpochSecond(), Long.class);

		acr(builder, redeemed);

		if (actor != null)
			builder.claim("act", actor, IuOidcActor.class);

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
	 * Names the authority that authenticated a token's subject, as {@code acr}.
	 *
	 * <p>
	 * OpenID Connect &sect;2 defines {@code acr} as an authentication context class
	 * whose values the parties using it agree on, so what it carries here is the
	 * authenticating authority's unique identifier &mdash; a federated SAML
	 * identity provider's entity ID, for a deployment authenticating that way. It
	 * is the same value {@link OidcGrant#getAuthnAuthority()} recorded when the
	 * authorization endpoint issued the code.
	 * </p>
	 *
	 * <h4>Its absence is the claim</h4>
	 *
	 * <p>
	 * Nothing constrains a {@code client_id} from reading like a principal name, so
	 * a resource server presented with a {@code sub} cannot tell an end user from a
	 * client by looking at it &mdash; RFC 9700 &sect;4.15. This is what settles it:
	 * a token that answers for somebody who authenticated names who authenticated
	 * them, and a token that answers for the client itself has no such authority to
	 * name and carries no {@code acr} at all. A resource server reads the
	 * <em>presence</em> of the claim, not only its value.
	 * </p>
	 *
	 * <p>
	 * Three cases follow, and together they cover every token this endpoint issues:
	 * </p>
	 * <ul>
	 * <li><strong>{@code acr}</strong> &mdash; an end user, authenticated by the
	 * authority it names.</li>
	 * <li><strong>{@code act} and no {@code acr}</strong> &mdash; an exchange. The
	 * subject never authenticated, so there is no authority of theirs to name, the
	 * same reason {@code auth_time} does not stand at this level either; what
	 * {@code act} names is the actor who did.</li>
	 * <li><strong>neither</strong> &mdash; {@code client_credentials}, whose
	 * {@code sub} is the client and where no end user is involved at all.</li>
	 * </ul>
	 *
	 * <p>
	 * A grant that recorded no authority writes nothing rather than writing empty,
	 * since an {@code acr} naming nobody would assert the claim while saying
	 * nothing, which is worse than leaving it out.
	 * </p>
	 *
	 * @param builder  token being built
	 * @param redeemed what is being redeemed
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID
	 *      Connect Core 1.0 &sect;2</a>
	 */
	private static void acr(WebTokenBuilder builder, Redeemed redeemed) {
		if (redeemed.impersonated() != null)
			return;

		final var authority = redeemed.authnAuthority();
		if (authority != null)
			builder.claim("acr", authority, String.class);
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

		// identifies the token without reproducing it: WebToken#toString renders every
		// claim, which at FINE would write each caller's released claims -- names,
		// email addresses, roles, authorization details -- into the log. Which token
		// was issued, to whom, and for what is what a diagnostic here needs
		LOG.fine(() -> "oidc-issue:" + type + ":" + issuerKey.getAlgorithm().alg + ":" + issuerKey.getKeyId() //
				+ " jti=" + token.getTokenId() //
				+ " sub=" + token.getSubject() //
				+ " aud=" + IuIterable.print(token.getAudience()));

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
	 * Which endpoint answered for a client, and what verified it.
	 *
	 * <p>
	 * The method travels with the endpoint rather than being logged and dropped,
	 * because what a grant may do depends on whether anything was verified at all.
	 * {@link ClientAuthenticator.Method#NONE} is a registration that presents no
	 * credential by design, so the grant itself has to prove possession: an
	 * authorization code with PKCE does, and {@code client_credentials} has nothing
	 * to prove it with.
	 * </p>
	 *
	 * @param endpoint endpoint that authenticated
	 * @param method   method that verified the credential
	 */
	private record Authenticated(IuOidcClientEndpoint endpoint, ClientAuthenticator.Method method) {

		/**
		 * Determines whether this client authenticated with no credential at all.
		 *
		 * @return true if the registration is public; else false
		 */
		boolean isPublic() {
			return ClientAuthenticator.Method.NONE.equals(method);
		}
	}

	/**
	 * The {@code act} claim as this endpoint builds it.
	 *
	 * @param sub      actor's principal name
	 * @param name     actor's display name, or {@code null}
	 * @param email    actor's email address, or {@code null}
	 * @param authTime when the actor authenticated as a NumericDate, or {@code null}
	 */
	private record Actor(String sub, String name, String email, Long authTime, String acr) implements IuOidcActor {

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

		@Override
		public Long getAuthTime() {
			return authTime;
		}

		@Override
		public String getAcr() {
			return acr;
		}
	}

	/**
	 * What a grant contributes to the tokens built from it.
	 *
	 * <p>
	 * Every grant type that answers for an end user reaches
	 * {@link #respond(IuOidcClientEndpoint, String, String, Redeemed, Set, Set)
	 * respond} through one of these, which is what lets a token exchange &mdash;
	 * which has no stored grant at all &mdash; take the same path as a code or
	 * refresh redemption. {@code null} in place of one means the tokens answer for
	 * the client itself, as {@code client_credentials} does.
	 * </p>
	 *
	 * <p>
	 * {@link #grant()} is the only member the tokens themselves don't read. It is
	 * here because issuing a refresh token means filing the original grant again,
	 * and an exchange has none to file &mdash; which is consistent, since an
	 * exchange never grants {@code offline_access} in the first place.
	 * </p>
	 *
	 * @param principalName who authenticated
	 * @param impersonated  principal an honored token exchange answers for instead,
	 *                      or {@code null} when the tokens answer for
	 *                      {@code principalName}
	 * @param authnInstant  when {@code principalName} authenticated, or
	 *                      {@code null} if unrecorded
	 * @param authnAuthority identity provider that authenticated them, or
	 *                      {@code null} if unrecorded
	 * @param nonce         {@code nonce} to echo on an ID token, or {@code null}
	 * @param resource      resource URIs the grant authorized, bounding the
	 *                      audience of every token derived from it; empty if it
	 *                      recorded none
	 * @param released      authorization details the grant released, or
	 *                      {@code null} if it released none
	 * @param grant         stored grant to re-file when issuing a refresh token, or
	 *                      {@code null} when there is none
	 */
	private record Redeemed(String principalName, String impersonated, Instant authnInstant, String authnAuthority,
			String nonce, Set<String> resource, Iterable<? extends IuAuthorizationDetails> released, OidcGrant grant) {

		/**
		 * Reads what a stored grant contributes.
		 *
		 * @param grant redeemed grant
		 * @return {@link Redeemed}
		 */
		static Redeemed of(OidcGrant grant) {
			final var resource = grant.getResource();
			return new Redeemed(grant.getPrincipalName(), null, grant.getAuthnInstant(), grant.getAuthnAuthority(),
					grant.getNonce(),
					resource == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(resource)),
					grant.getReleasedAuthorizationDetails(), grant);
		}
	}

}
