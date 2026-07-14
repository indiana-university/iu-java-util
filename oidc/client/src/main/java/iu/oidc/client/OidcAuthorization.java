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

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuRequestAttributes;
import edu.iu.IuStatefulRedirect;
import edu.iu.IuWebUtils;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.oidc.IuOidcAuthorization;
import edu.iu.oidc.IuOidcPrincipal;
import iu.oidc.client.config.IuOidcClientReference;

/**
 * {@link IuOidcAuthorization} implementation resource.
 */
public class OidcAuthorization implements IuOidcAuthorization {

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
	public IuStatefulRedirect init(String delegatingPrincipal, String backdoorId) {
		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var oidcClient = config.getClient();

		final var sessionHandler = config.getSessionHandler();
		final var session = sessionHandler.create();
		final var preAuth = session.getDetail(OidcPreAuthSession.class);
		preAuth.setState(state);
		preAuth.setNonce(nonce);
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

		final var resource = config.getResourceUri();
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
	public IuStatefulRedirect authorize(IuRequestAttributes requestAttributes, String code, String state) {
		final var sessionHandler = config.getSessionHandler();
		final var session = sessionHandler.activate(requestAttributes.getCookies());
		if (session == null)
			throw new IllegalStateException("missing or expired preAuth session");

		final var preAuth = session.getDetail(OidcPreAuthSession.class);
		if (!IuObject.equals(preAuth.getState(), state))
			throw new IllegalStateException("state mismatch " + state + " preAuth=" + preAuth);

		final var grant = new AuthorizationGrant(config, code);
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

		final var postAuth = session.getDetail(OidcPostAuthSession.class);
		postAuth.setTokenResponse(response);

		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		postAuth.setNotAfter(
				IuObject.require(now.plusSeconds(response.getExpiresIn()), now::isBefore, "non-positive expires_in"));

		return new IuStatefulRedirect() {
			@Override
			public String getSetCookie() {
				return sessionHandler.store(session);
			}

			@Override
			public URI getLocation() {
				return config.getResourceUri();
			}
		};
	}

	@Override
	public IuOidcPrincipal getAuthorizedPrincipal(IuRequestAttributes requestAttributes) {
		final var sessionHandler = config.getSessionHandler();
		final var session = sessionHandler.activate(requestAttributes.getCookies());
		if (session == null)
			throw new IllegalStateException("missing or expired authorization session");

		final var postAuth = session.getDetail(OidcPostAuthSession.class);
		final var notAfter = postAuth.getNotAfter();
		if (notAfter == null)
			throw new IllegalStateException("missing post-auth not-after date");

		final var grant = new RefreshTokenGrant(config, postAuth.getTokenResponse(), postAuth.getNotAfter());

		final String setCookie;
		final var response = grant.getTokenResponse();
		if (!response.equals(postAuth.getTokenResponse())) {
			postAuth.setTokenResponse(response);
			postAuth.setNotAfter(grant.getNotAfter());
			setCookie = sessionHandler.store(session);
		} else
			setCookie = null;

		final var accessToken = response.getAccessToken();
		final var idToken = grant.getIdToken();

		final var metadata = OidcProviders.getMetadata(config.getProvider());
		final var client = config.getClient();

		final var encryptedUserinfoResponse = IuException.unchecked(() -> IuHttp.send(metadata.getUserinfoEndpoint(),
				rb -> rb.header("Authorization", "Bearer " + accessToken), IuHttp.READ_UTF8));
		final String userinfoResponse;
		final var decryptKeys = client.getDecryptJwk();
		if (decryptKeys != null) {
			final var jose = WebCryptoHeader.getProtectedHeader(encryptedUserinfoResponse);
			final var kid = Objects.requireNonNull(jose.getKeyId(), "ID token header missing decryption key ID");
			final var decryptJwk = IuIterable.select(decryptKeys, k -> kid.equals(k.getKeyId()),
					"decryption key not found using kid " + kid);
			userinfoResponse = WebEncryption.parse(encryptedUserinfoResponse).decryptText(decryptJwk);
		} else
			userinfoResponse = encryptedUserinfoResponse; // not encrypted

		final var accessTokenLookup = new Function<URI, String>() {
			private Map<URI, OidcTokenGrant> grants = new HashMap<>();

			@Override
			public String apply(URI uri) {
				final var accessToken = grant.getTokenResponse().getAccessToken();
				if (IuWebUtils.isRootOf(config.getResourceUri(), uri))
					return accessToken;

				URI apiResource = null;
				for (final var u : config.getApiResources())
					if (IuWebUtils.isRootOf(u, uri) //
							&& (apiResource == null //
									|| IuWebUtils.isRootOf(apiResource, u)))
						apiResource = u;
				Objects.requireNonNull(apiResource, "invalid resource URI " + uri);

				var obo = grants.get(apiResource);
				if (obo == null) {
					obo = new OnBehalfOfGrant(config, apiResource, accessToken);
					synchronized (grants) {
						grants.put(apiResource, obo);
					}
				}

				return obo.getTokenResponse().getAccessToken();
			}
		};

		return new OidcPrincipal(idToken, IuJson.parse(userinfoResponse).asJsonObject(), setCookie, accessTokenLookup,
				config::adaptJson);
	}

}
