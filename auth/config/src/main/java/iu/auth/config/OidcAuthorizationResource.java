package iu.auth.config;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuBadRequestException;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.IuRequestAttributes;
import edu.iu.auth.config.IuOidcClient;
import edu.iu.auth.config.IuOpenIdProviderMetadata;
import edu.iu.auth.oauth.IuCallerAttributes;
import edu.iu.auth.oidc.IuAuthorizationRedirect;
import edu.iu.auth.oidc.IuAuthorizedPrincipal;
import edu.iu.auth.oidc.IuOidcAuthorization;
import edu.iu.auth.session.IuSession;
import edu.iu.auth.session.IuSessionHandler;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebKey;

/**
 * {@link IuOidcAuthorization} implementation resource.
 */
public abstract class OidcAuthorizationResource implements IuOidcAuthorization {

	private static final Logger LOG = Logger.getLogger(OidcAuthorizationResource.class.getName());

	private IuOpenIdProviderMetadata oidcProviderMetadata;
	private Instant lastOidcProviderMetadata;

	/**
	 * Default constructor.
	 */
	protected OidcAuthorizationResource() {
	}

	/**
	 * Get OIDC client configuration.
	 * 
	 * @return {@link IuOidcClient}
	 */
	protected abstract IuOidcClient getOidcClient();

	/**
	 * Provides the session handler to use for authorization endpoint interactions.
	 * 
	 * @return {@link IuSessionHandler}
	 */
	protected abstract IuSessionHandler getSessionHandler();

	/**
	 * Handles post-authorization activity before issuing a new session cookie and
	 * final redirect.
	 * 
	 * @param postAuth populated {@link OidcPostAuthSession}
	 */
	protected abstract void handlePostAuth(OidcPostAuthSession postAuth);

	/**
	 * Reads the OIDC provider metadata, or returns the last known good version if
	 * cached and not expired or temporarily unavailable.
	 * 
	 * @return {@link IuOpenIdProviderMetadata}
	 */
	IuOpenIdProviderMetadata oidcProviderMetadata() {
		final var oidcClient = getOidcClient();
		if (lastOidcProviderMetadata == null //
				|| Duration.between(lastOidcProviderMetadata, Instant.now())
						.compareTo(oidcClient.getMetadataRefreshInterval()) > 0)
			try {
				oidcProviderMetadata = AuthConfig.adaptJson(IuOpenIdProviderMetadata.class)
						.fromJson(IuHttp.get(oidcClient.getMetadataUri(), IuHttp.READ_JSON_OBJECT));

				lastOidcProviderMetadata = Instant.now();
			} catch (Throwable e) {
				if (oidcProviderMetadata == null)
					throw IuException.unchecked(e);
				else
					LOG.log(Level.INFO, e, () -> "OIDC provider metadata lookup failure " + oidcClient.getMetadataUri()
							+ "; using last good version");
			}

		return oidcProviderMetadata;
	}

	@Override
	public IuAuthorizationRedirect init(String delegatingPrincipal, String backdoorId) {
		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();

		final var oidcClient = getOidcClient();

		final var sessionHandler = getSessionHandler();
		final var session = sessionHandler.create();
		final var preAuth = session.getDetail(OidcPreAuthSession.class);
		preAuth.setState(state);
		preAuth.setNonce(nonce);
		session.setStrict(false);
		final var setCookie = sessionHandler.store(session);

		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		params.put("response_type", IuIterable.iter("code"));
		params.put("client_id", IuIterable.iter(oidcClient.getClientId()));
		params.put("scope", IuIterable.iter("openid"));
		params.put("nonce", IuIterable.iter(nonce));
		params.put("resource", IuIterable.iter(oidcClient.getResourceUri().toString()));
		params.put("redirect_uri", IuIterable.iter(oidcClient.getRedirectUri().toString()));
		params.put("state", IuIterable.iter(state));
		if (delegatingPrincipal != null)
			params.put("delegating_principal", IuIterable.iter(delegatingPrincipal));
		if (backdoorId != null)
			params.put("impersonated_principal", IuIterable.iter(backdoorId));

		final var location = URI.create(
				Objects.requireNonNull(oidcProviderMetadata().getAuthorizationEndpoint(), "authorization_endpoint")
						+ "?" + IuWebUtils.createQueryString(params));

		return new IuAuthorizationRedirect() {
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

	/**
	 * Adds authorization attributes to a token endpoint code request.
	 * 
	 * @param caller         caller attributes for adding as authorization details
	 *                       to the client assertion
	 * @param code           authorization code received at the redirect URI
	 * @param nonce          nonce value from the original authorization request
	 * @param requestBuilder {@link HttpRequest.Builder}
	 */
	protected void codeTokenAuth(IuCallerAttributes caller, String code, String nonce,
			HttpRequest.Builder requestBuilder) {
		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		params.put("grant_type", IuIterable.iter("authorization_code"));
		params.put("code", IuIterable.iter(code));
		params.put("nonce", IuIterable.iter(nonce));

		final var oidcClient = getOidcClient();

		final var assertion = new SelfIssuedAccessToken(oidcClient.getAssertionJwk(),
				URI.create(oidcClient.getClientId()), oidcProviderMetadata().getTokenEndpoint(),
				oidcClient.getAssertionTtl(), caller);
		params.put("client_assertion_type", IuIterable.iter("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
		params.put("client_assertion", IuIterable.iter(assertion.getBearerToken()));
		requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
		requestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(params)));
	}

	/**
	 * Handles a token response.
	 * 
	 * @param postAuth      post-auth session detail for storing verified response
	 *                      tokens
	 * @param nonce         nonce value from the original authorization request
	 * @param tokenResponse parsed response from the OP token endpoint
	 */
	void handleTokenResponse(OidcPostAuthSession postAuth, String nonce, OAuthTokenResponse tokenResponse) {
		final var oidcClient = getOidcClient();
		final var issuer = Objects.requireNonNull(oidcProviderMetadata.getIssuer(), "missing issuer");
		final var issuerKey = IuObject.convert(oidcProviderMetadata.getJwksUri(), WebKey::readJwks).iterator().next();

		final String accessToken = tokenResponse.getAccessToken();

		final String encryptedIdToken = tokenResponse.getIdToken();

		final String idToken;
		final var decryptKeys = oidcClient.getDecryptJwk();
		if (decryptKeys != null) {
			final var jose = WebCryptoHeader.getProtectedHeader(encryptedIdToken);
			final var kid = Objects.requireNonNull(jose.getKeyId(), "ID token header missing decryption key ID");
			final var decryptJwk = IuIterable.select(decryptKeys, k -> kid.equals(k.getKeyId()),
					"decryption key not found using kid " + kid);
			idToken = WebEncryption.parse(encryptedIdToken).decryptText(decryptJwk);
		} else
			idToken = encryptedIdToken; // not encrypted

		final var verifiedIdToken = OidcIdToken.verify(idToken, issuerKey, oidcClient.getClientId(), nonce, accessToken,
				oidcClient.getMaxAge());
		verifiedIdToken.validateClaims(URI.create(oidcClient.getClientId()), oidcClient.getTokenTtl());
		if (!issuer.equals(verifiedIdToken.getIssuer()))
			throw new IuBadRequestException("iss mismatch in id token");

		postAuth.setIdToken(idToken);
		postAuth.setAccessToken(accessToken);
		postAuth.setRefreshToken(tokenResponse.getRefreshToken());

		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		postAuth.setNotAfter(IuObject.require(now.plusSeconds(tokenResponse.getExpiresIn()), now::isBefore,
				"non-positive expires_in"));

		handlePostAuth(postAuth);
	}

	@Override
	public IuAuthorizationRedirect authorize(IuRequestAttributes requestAttributes, String code, String state) {
		final var sessionHandler = getSessionHandler();
		final var session = sessionHandler.activate(requestAttributes.getCookies());
		if (session == null)
			throw new IllegalStateException("missing or expired preAuth session");

		final var preAuth = session.getDetail(OidcPreAuthSession.class);
		if (!IuObject.equals(preAuth.getState(), state))
			throw new IllegalStateException("state mismatch " + state + " preAuth=" + preAuth);

		final var oidcClient = getOidcClient();
		final var nonce = preAuth.getNonce();
		final var oidcProviderMetadata = oidcProviderMetadata();

		final var caller = new RequestCallerAttributes(requestAttributes, oidcClient.getClientId(), null);

		handleTokenResponse(session.getDetail(OidcPostAuthSession.class), nonce,
				OAuthTokenResponse.from(IuException.unchecked(() -> IuHttp.send(oidcProviderMetadata.getTokenEndpoint(),
						rb -> codeTokenAuth(caller, code, nonce, rb), IuHttp.READ_JSON_OBJECT))));

		return new IuAuthorizationRedirect() {
			@Override
			public String getSetCookie() {
				return sessionHandler.store(session);
			}

			@Override
			public URI getLocation() {
				return oidcClient.getResourceUri();
			}
		};
	}

	/**
	 * Adds authorization attributes to a token endpoint refresh request.
	 * 
	 * @param caller         caller attributes for adding as authorization details
	 *                       to the client assertion
	 * @param refreshToken   refresh token from the most recent token response
	 * @param nonce          nonce value from the original authorization request
	 * @param requestBuilder {@link HttpRequest.Builder}
	 */
	protected void refreshTokenAuth(IuCallerAttributes caller, String refreshToken, String nonce,
			HttpRequest.Builder requestBuilder) {
		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		params.put("grant_type", IuIterable.iter("refresh_token"));
		params.put("refresh_token", IuIterable.iter(refreshToken));
		params.put("nonce", IuIterable.iter(nonce));

		final var oidcClient = getOidcClient();

		final var assertion = new SelfIssuedAccessToken(oidcClient.getAssertionJwk(),
				URI.create(oidcClient.getClientId()), oidcProviderMetadata().getTokenEndpoint(),
				oidcClient.getAssertionTtl(), caller);
		params.put("client_assertion_type", IuIterable.iter("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
		params.put("client_assertion", IuIterable.iter(assertion.getBearerToken()));
		requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
		requestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(params)));
	}

	/**
	 * Refreshes the tokens for an authorization session with expired id and/or
	 * access tokens.
	 * 
	 * @param requestAttributes incoming request attributes
	 * @param refreshToken      refresh token from most recent token endpoint
	 *                          response
	 * @param session           session with expired tokens
	 * @return set-cookie header value for the updated session
	 */
	protected String refresh(IuRequestAttributes requestAttributes, String refreshToken, IuSession session) {
		final var preAuth = session.getDetail(OidcPreAuthSession.class);
		final var nonce = preAuth.getNonce();
		final var postAuth = session.getDetail(OidcPostAuthSession.class);

		final var oidcClient = getOidcClient();
		final var oidcProviderMetadata = oidcProviderMetadata();

		final var caller = new RequestCallerAttributes(requestAttributes, oidcClient.getClientId(), null);
		handleTokenResponse(postAuth, nonce,
				OAuthTokenResponse.from(IuException.unchecked(() -> IuHttp.send(oidcProviderMetadata.getTokenEndpoint(),
						rb -> refreshTokenAuth(caller, refreshToken, nonce, rb), IuHttp.READ_JSON_OBJECT))));

		return getSessionHandler().store(session);
	}

	@Override
	public IuAuthorizedPrincipal getAuthorizedPrincipal(IuRequestAttributes requestAttributes) {
		final var sessionHandler = getSessionHandler();
		final var session = sessionHandler.activate(requestAttributes.getCookies());
		if (session == null)
			throw new IllegalStateException("missing or expired authorization session");

		final var preAuth = session.getDetail(OidcPreAuthSession.class);
		final var nonce = preAuth.getNonce();
		if (nonce == null)
			throw new IllegalStateException("missing pre-auth nonce");

		final var postAuth = session.getDetail(OidcPostAuthSession.class);

		final var notAfter = postAuth.getNotAfter();
		if (notAfter == null)
			throw new IllegalStateException("missing post-auth not-after date");

		final String setCookie;
		if (Instant.now().isAfter(notAfter)) {
			final var refreshToken = postAuth.getRefreshToken();
			if (refreshToken == null)
				throw new IllegalStateException("Session expired with no refresh token");
			else
				setCookie = refresh(requestAttributes, refreshToken, session);
		} else
			setCookie = null;

		final var accessToken = postAuth.getAccessToken();
		if (accessToken == null)
			throw new IllegalStateException("missing post-auth access token");

		final var idToken = postAuth.getIdToken();
		if (idToken == null)
			throw new IllegalStateException("missing post-auth ID token");

		final var oidcProviderMetadata = oidcProviderMetadata();
		final var issuer = IuObject.convert(oidcProviderMetadata.getJwksUri(), WebKey::readJwks).iterator().next();
		final var oidcClient = getOidcClient();

		final var verifiedIdToken = OidcIdToken.verify(idToken, issuer, oidcClient.getClientId(), preAuth.getNonce(),
				postAuth.getAccessToken(), oidcClient.getMaxAge());
		verifiedIdToken.validateClaims(URI.create(oidcClient.getClientId()), oidcClient.getTokenTtl());

		final var encryptedUserinfoResponse = IuException
				.unchecked(() -> IuHttp.send(oidcProviderMetadata.getUserinfoEndpoint(),
						rb -> rb.header("Authorization", "Bearer " + accessToken), IuHttp.READ_UTF8));
		final String userinfoResponse;
		final var decryptKeys = oidcClient.getDecryptJwk();
		if (decryptKeys != null) {
			final var jose = WebCryptoHeader.getProtectedHeader(encryptedUserinfoResponse);
			final var kid = Objects.requireNonNull(jose.getKeyId(), "ID token header missing decryption key ID");
			final var decryptJwk = IuIterable.select(decryptKeys, k -> kid.equals(k.getKeyId()),
					"decryption key not found using kid " + kid);
			userinfoResponse = WebEncryption.parse(encryptedUserinfoResponse).decryptText(decryptJwk);
		} else
			userinfoResponse = encryptedUserinfoResponse; // not encrypted

		final var principal = new OidcIdTokenPrincipal(verifiedIdToken, IuJson.parse(userinfoResponse).asJsonObject());
		return new IuAuthorizedPrincipal() {
			@Override
			public String getSetCookie() {
				return setCookie;
			}

			@Override
			public IuPrincipalIdentity getPrincipal() {
				return principal;
			}
		};
	}

}
