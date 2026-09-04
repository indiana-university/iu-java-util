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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuBadRequestException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuRequestAttributes;
import edu.iu.IuStatefulRedirect;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcAuthorization;
import edu.iu.oidc.IuOidcPrincipal;
import edu.iu.oidc.IuOidcTokenResponse;
import edu.iu.session.IuSession;
import iu.oidc.client.config.IuOidcClient;
import iu.oidc.client.config.IuOidcClientReference;
import jakarta.json.JsonObject;

/**
 * {@link IuOidcAuthorization} implementation resource.
 *
 * <p>
 * Successful authorization and token refreshes retrieve userinfo claims and
 * store them in the managed {@link OidcPostAuthSession}. Principal lookup then
 * reuses those claims without another userinfo request while the token response
 * is unchanged. Sessions created before the claims were stored are repaired on
 * their next principal lookup.
 * </p>
 */
public class OidcAuthorization implements IuOidcAuthorization {

	private static final Logger LOG = Logger.getLogger(OidcAuthorization.class.getName());

	private final IuOidcClientReference config;

	/**
	 * Constructor.
	 * 
	 * @param config OIDC client configuration reference
	 */
	public OidcAuthorization(IuOidcClientReference config) {
		this.config = config;
	}

	@Override
	public IuStatefulRedirect init(String delegatingPrincipal, String backdoorId, Consumer<IuSession> preAuthDetail)
			throws IOException {
		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var oidcClient = config.getClient();

		final var sessionHandler = config.getSessionHandler();
		final var session = sessionHandler.create();
		final var preAuth = session.getDetail(OidcPreAuthSession.class);
		preAuth.setState(state);
		preAuth.setNonce(nonce);

		// after this flow's own detail and before the store, so one write carries both
		if (preAuthDetail != null)
			preAuthDetail.accept(session);

		session.setStrict(false);
		final var setCookie = sessionHandler.store(session);

		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		params.put("response_type", IuIterable.iter("code"));
		params.put("client_id", IuIterable.iter(oidcClient.getClientId()));
		params.put("redirect_uri", IuIterable.iter(config.getRedirectUri().toString()));
		params.put("nonce", IuIterable.iter(nonce));
		params.put("state", IuIterable.iter(state));

		final var scope = config.getScope();
		if (scope != null)
			params.put("scope", IuIterable.iter(scope));

		final var resource = oidcClient.getResourceUri();
		if (resource != null)
			params.put("resource", IuIterable.iter(resource.toString()));

		if (delegatingPrincipal != null)
			params.put("delegating_principal", IuIterable.iter(delegatingPrincipal));

		if (backdoorId != null)
			params.put("impersonated_principal", IuIterable.iter(backdoorId));

		final var metadata = OidcProviders.getMetadata(config.getProvider());
		final var location = URI
				.create(Objects.requireNonNull(metadata.getAuthorizationEndpoint(), "authorization_endpoint") + "?"
						+ IuWebUtils.createQueryString(params));

		return new IuStatefulRedirect() {
			@Override
			public String getSetCookie() {
				return setCookie;
			}

			@Override
			public URI getLocation() {
				return location;
			}
		};
	}

	@Override
	public IuStatefulRedirect authorize(IuRequestAttributes requestAttributes, String code, String state)
			throws IOException {
		if (!requestAttributes.getRequestUri().equals(config.getRedirectUri()))
			throw new IuBadRequestException("redirect_uri mismatch, expected " + config.getRedirectUri());

		final var sessionHandler = config.getSessionHandler();
		final var session = sessionHandler.activate(requestAttributes.getCookies());
		if (session == null)
			throw new IllegalStateException("missing or expired preAuth session");
		final var preAuth = session.getDetail(OidcPreAuthSession.class);

		final var preAuthState = preAuth.getState();
		if (preAuthState == null)
			throw new IllegalStateException("invalid pre-auth session; missing state");
		if (state == null)
			throw new IuBadRequestException("missing state parameter");
		if (!IuObject.equals(preAuth.getState(), state))
			throw new IllegalStateException("state mismatch " + state + " preAuth=" + preAuth);
		preAuth.setState(null);

		final var grant = new AuthorizationGrant(config, code, config.getRedirectUri());
		final var response = grant.getTokenResponse();
		final var idToken = Objects.requireNonNull(grant.getIdToken(), "missing verified ID token");

		final var nonce = preAuth.getNonce();
		final var vnonce = idToken.getNonce();
		if (nonce == null) {
			if (vnonce != null)
				throw new IllegalArgumentException("Unexpected nonce claim");
		} else if (vnonce == null)
			throw new IllegalArgumentException("Expected nonce claim");
		else
			IuObject.once(nonce, vnonce, "nonce mismatch");
		preAuth.setNonce(null);

		final var userinfoClaims = getUserinfoClaims(config.getClient(), response.getAccessToken());

		final var postAuth = session.getDetail(OidcPostAuthSession.class);
		postAuth.setTokenResponse(response);
		postAuth.setUserinfoClaims(userinfoClaims);

		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		postAuth.setNotAfter(
				IuObject.require(now.plusSeconds(response.getExpiresIn()), now::isBefore, "non-positive expires_in"));

		session.setStrict(false);
		final var setCookie = sessionHandler.store(session);
		return new IuStatefulRedirect() {
			@Override
			public String getSetCookie() {
				return setCookie;
			}

			@Override
			public URI getLocation() {
				return config.getResourceUri();
			}
		};
	}

	/**
	 * Verifies an access token as a JWT signed by the OpenID Provider.
	 *
	 * <p>
	 * Returns null when the access token can't be identified as one issued by the
	 * provider, so callers can fall back to another means of authorizing access,
	 * e.g., token exchange. This includes tokens that are not signed JWTs, whose
	 * issuer claim does not match provider metadata, or whose key ID the provider
	 * does not publish. Once the issuer and key ID match, a signature verification
	 * failure is not a fallback signal and is allowed to propagate.
	 * </p>
	 *
	 * @param accessToken access token
	 * @return verified {@link WebToken}; null if the access token isn't a signed
	 *         JWT, its issuer doesn't match provider metadata, or its key isn't
	 *         published by the issuer
	 * @throws IOException if OP metadata or JWKS interactions fail
	 */
	private WebToken verifyAccessToken(String accessToken) throws IOException {
		final WebCryptoHeader jose;
		try {
			jose = WebCryptoHeader.getProtectedHeader(accessToken);
		} catch (RuntimeException e) {
			return null; // not a JWT
		}

		final var kid = jose.getKeyId();
		if (kid == null)
			return null;

		final var metadata = OidcProviders.getMetadata(config.getProvider());

		// Parse claims and verify the access token was issued by the provider
		// before attempting to validate the signature or inspect audience claim.
		// Failure isn't strictly an issue since the access token probably isn't for us.
		// FINE messages simply indicate we're going to ignore the audience claim
		// when determining whether or not to use the access token with a remote call
		final JsonObject claims;
		try {
			claims = IuJson.parse(new ByteArrayInputStream(WebSignedPayload.parse(accessToken).getPayload()))
					.asJsonObject();

			final var expectedIssuer = metadata.getIssuer().toString();
			final var iss = claims.getString("iss");
			if (!expectedIssuer.equals(iss)) {
				LOG.fine(() -> "access token iss claim mismatch " + iss + "; expected " + expectedIssuer);
				return null;
			}

		} catch (Exception e) {
			LOG.log(Level.FINE, e, () -> "couldn't verify access token iss claim");
			return null;
		}

		final WebKey issuerKey;
		try {
			issuerKey = IuIterable.select(WebKey.readJwks(metadata.getJwksUri()), k -> kid.equals(k.getKeyId()));
		} catch (NoSuchElementException e) {
			return null; // signing key not published by issuer
		}

		// fail if the access token can't be verified as a valid JWT; this indicates
		// an invalid response from the token endpoint
		return WebToken.verify(accessToken, issuerKey);
	}

	/** Segment count of a JWE compact serialization; a JWS has three. */
	private static final int JWE_SEGMENTS = 5;

	/**
	 * Retrieves and parses the userinfo claims for an access token, unwrapping
	 * whatever form the provider answered in.
	 *
	 * @param client      client configuration, including optional decryption keys
	 * @param accessToken token used to authorize the userinfo request
	 * @return parsed userinfo claims
	 * @throws IOException if the userinfo request fails, or provider metadata or
	 *                     JWKS interactions fail
	 */
	private JsonObject getUserinfoClaims(IuOidcClient client, String accessToken) throws IOException {
		final var response = IuHttp.send(OidcProviders.getMetadata(config.getProvider()).getUserinfoEndpoint(),
				rb -> rb.header("Authorization", "Bearer " + accessToken), IuHttp.READ_UTF8);

		// OpenID Connect nests at most one level -- a signature inside an encryption --
		// so two passes unwrap every form a provider may answer in: a plain claims
		// document, a signature, an encryption, and an encrypted signature
		return IuJson.parse(unwrapUserinfo(client, unwrapUserinfo(client, response))).asJsonObject();
	}

	/**
	 * Unwraps one layer of a userinfo response.
	 *
	 * <p>
	 * What the <em>response</em> is decides how it is read, rather than what the
	 * client configures. A provider signs and encrypts what it answers according to
	 * the client's registration <em>there</em>, so a client holding a decryption
	 * key its provider doesn't use would otherwise refuse a plain document it could
	 * read perfectly well, and one registered for signing would fail to read a
	 * signature it never expected. A document beginning with <code>{</code> is the
	 * claims; anything else is a JOSE compact serialization, and its segment count
	 * says which.
	 * </p>
	 *
	 * @param client   client configuration, including optional decryption keys
	 * @param response response, or the plaintext of one already decrypted
	 * @return the claims document, or the layer beneath the one unwrapped
	 * @throws IOException if provider metadata or JWKS interactions fail
	 */
	private String unwrapUserinfo(IuOidcClient client, String response) throws IOException {
		final var document = response.strip();
		if (document.startsWith("{"))
			return document;

		if (segments(document) == JWE_SEGMENTS)
			return decryptUserinfo(client, document);

		return verifyUserinfo(document);
	}

	/**
	 * Counts the dot-delimited segments of a JOSE compact serialization, which is
	 * what distinguishes a signature's three from an encryption's five.
	 *
	 * @param compact compact serialization
	 * @return segment count
	 */
	private static int segments(String compact) {
		return compact.split("\\.", -1).length;
	}

	/**
	 * Decrypts an encrypted userinfo response.
	 *
	 * @param client client configuration, including optional decryption keys
	 * @param jwe    JWE compact serialization
	 * @return decrypted plaintext, which is either the claims or a signature over
	 *         them
	 * @throws NullPointerException   if the client configures no decryption key, or
	 *                                the response names none
	 * @throws NoSuchElementException if the response names a key the client doesn't
	 *                                hold
	 */
	private static String decryptUserinfo(IuOidcClient client, String jwe) {
		final var decryptKeys = Objects.requireNonNull(client.getDecryptJwk(),
				"userinfo response is encrypted but no decryption key is configured");

		final var kid = Objects.requireNonNull(WebCryptoHeader.getProtectedHeader(jwe).getKeyId(),
				"userinfo response header missing decryption key ID");

		final var decryptJwk = IuIterable.select(decryptKeys, k -> kid.equals(k.getKeyId()),
				"decryption key not found using kid " + kid);

		return WebEncryption.parse(jwe).decryptText(decryptJwk);
	}

	/**
	 * Verifies a signed userinfo response against the keys its issuer publishes.
	 *
	 * <p>
	 * Resolved by the {@code kid} the signature names, from the JWK Set the
	 * provider's discovery document points at &mdash; the same way an access token
	 * is. Unlike an access token, a failure here is not a fallback signal: a
	 * provider that signed the response is the only thing that could have, so a
	 * signature that doesn't verify is a response to refuse rather than one to read
	 * anyway.
	 * </p>
	 *
	 * @param jws JWS compact serialization
	 * @return verified payload, which is the claims document
	 * @throws IOException            if OP metadata or JWKS interactions fail
	 * @throws NullPointerException   if the response names no signing key
	 * @throws NoSuchElementException if the response names a key the issuer doesn't
	 *                                publish
	 */
	private String verifyUserinfo(String jws) throws IOException {
		final var kid = Objects.requireNonNull(WebCryptoHeader.getProtectedHeader(jws).getKeyId(),
				"userinfo response header missing signature key ID");

		final var metadata = OidcProviders.getMetadata(config.getProvider());
		final var issuerKey = IuIterable.select(WebKey.readJwks(metadata.getJwksUri()), k -> kid.equals(k.getKeyId()),
				"userinfo response signature key not published by the issuer using kid " + kid);

		final var signed = WebSignedPayload.parse(jws);
		signed.verify(issuerKey);

		return IuText.utf8(signed.getPayload());
	}

	@Override
	public IuOidcPrincipal getAuthorizedPrincipal(IuRequestAttributes requestAttributes) throws IOException {
		final var sessionHandler = config.getSessionHandler();
		final var session = sessionHandler.activate(requestAttributes.getCookies());
		if (session == null)
			return null;

		final var postAuth = session.getDetail(OidcPostAuthSession.class);
		final var notAfter = postAuth.getNotAfter();
		if (notAfter == null)
			return null; // init w/o authorize; incomplete login session

		final var grant = new RefreshTokenGrant(config, postAuth.getTokenResponse(), postAuth.getNotAfter());

		final String setCookie;
		final IuOidcTokenResponse response;
		try {
			response = grant.getTokenResponse();
		} catch (RuntimeException e) {
			LOG.log(Level.INFO, "refresh token failed after ID token expired", e);
			return null;
		}

		final var client = config.getClient();

		JsonObject userinfoClaims = postAuth.getUserinfoClaims();
		if (!response.equals(postAuth.getTokenResponse())) {
			userinfoClaims = getUserinfoClaims(client, response.getAccessToken());
			postAuth.setTokenResponse(response);
			postAuth.setNotAfter(grant.getNotAfter());
			postAuth.setUserinfoClaims(userinfoClaims);
			setCookie = sessionHandler.store(session);
		} else if (userinfoClaims == null) {
			userinfoClaims = getUserinfoClaims(client, response.getAccessToken());
			postAuth.setUserinfoClaims(userinfoClaims);
			setCookie = sessionHandler.store(session);
		} else if (!postAuth.isStrict()) {
			postAuth.setStrict(true);
			session.setStrict(true);
			setCookie = sessionHandler.store(session);
		} else
			setCookie = null;

		final var accessToken = response.getAccessToken();
		final var verifiedAccessToken = verifyAccessToken(accessToken);

		return new OidcPrincipal(grant.getIdToken(), userinfoClaims, setCookie, config, accessToken,
				verifiedAccessToken, client.getPrincipalNameClaimName());
	}

}
