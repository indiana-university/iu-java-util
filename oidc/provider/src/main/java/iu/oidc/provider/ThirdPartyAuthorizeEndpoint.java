package iu.esas.thirdparty.auth.oidc.client;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuBadRequestException;
import edu.iu.IuDigest;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuRequestAttributes;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuAuthorizationDetails;
import edu.iu.auth.oidc.IuOpenIdProviderMetadata;
import edu.iu.auth.oidc.OidcIdToken;
import edu.iu.auth.session.IuSessionConfiguration;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.oidc.ThirdPartyClient;
import edu.iu.oidc.IuOidcClientEndpoint;
import edu.iu.thirdparty.auth.oidc.ThirdPartyAuthorizationDetails;
import edu.iu.thirdparty.auth.oidc.ThirdPartyAuthorizationDetailsResource;
import edu.iu.thirdparty.auth.oidc.api.ThirdPartyAccess;
import edu.iu.thirdparty.auth.oidc.api.ThirdPartyAuthnRedirect;
import edu.iu.thirdparty.auth.oidc.api.ThirdPartyAuthorize;
import edu.iu.thirdparty.auth.oidc.api.ThirdPartyAuthorizeRequest;
import edu.iu.thirdparty.auth.oidc.api.ThirdPartyAuthorizedPrincipals;
import edu.iu.thirdparty.auth.oidc.api.ThirdPartyTokenRequest;
import iu.auth.config.AuthConfig;
import iu.auth.config.OAuthTokenResponse;
import iu.auth.config.RemoteAccessToken;
import iu.auth.config.SelfIssuedAccessToken;
import iu.auth.pki.PkiPrincipal;
import iu.auth.pki.PkiVerifier;
import iu.crypt.Jwt;
import iu.oidc.provider.config.IuClientResource;
import jakarta.annotation.Resource;

/**
 * Implementation of the {@link ThirdPartyAuthorize} interface for handling
 * third-party authorization flows.
 */
@Resource
public class ThirdPartyAuthorizeEndpoint implements ThirdPartyAuthorize {

	private static final Logger LOG = Logger.getLogger(ThirdPartyAuthorizeEndpoint.class.getName());

	@Resource
	private String application;
	@Resource
	private URI resourceUri;
	@Resource
	private Duration tokenTtl = Duration.ofMinutes(15L);

	@Resource
	private ThirdPartyAuth thirdPartyAuth;
	@Resource
	private ThirdPartyAccess thirdPartyAccess;
	@Resource
	private ThirdPartyAuthorizationDetailsResource thirdPartyAuthorizationDetailsResource;

	/**
	 * Constructs a new {@code ThirdPartyAuthorizeEndpoint}.
	 */
	ThirdPartyAuthorizeEndpoint() {
	}

	@Override
	public String metadata() {
		return AuthConfig.adaptJson(IuOpenIdProviderMetadata.class).toJson(thirdPartyAuth.getOidcMetadata()).toString();
	}

	@Override
	public String jwks() {
		final Queue<WebKey> jwks = new ArrayDeque<>();
		for (final var jwk : AuthConfig.load(ThirdPartyIssuer.class, application).getKeys())
			jwks.offer(jwk.wellKnown());

		return WebKey.asJwks(jwks);
	}

	/**
	 * Traces back from a resolved resource to the endpoint and client that refer to
	 * it.
	 * 
	 * @param client   client
	 * @param endpoint endpoint
	 * @param resource resource
	 */
	record ResolvedResource(ThirdPartyClient client, IuOidcClientEndpoint endpoint,
			IuClientResource resource) {
	}

	/**
	 * Resolves a resource code from an auth request relative from Vault
	 * configuration.
	 * 
	 * @param authRequest auth request
	 * @return resource code
	 */
	ResolvedResource resolveResource(ThirdPartyAuthorizeRequest authRequest) {
		final var scope = authRequest.getScope();
		if (scope == null)
			throw new IuBadRequestException("missing scope");
		if (!IuIterable.filter(scope, "openid"::equals).iterator().hasNext())
			throw new IuBadRequestException("missing openid scope");

		final var state = authRequest.getState();
		if (state == null)
			throw new IuBadRequestException("missing state");
		if (!state.matches("[\\w-]{16,}"))
			throw new IuBadRequestException("invalid state");

		final var clientId = authRequest.getClientId();
		if (clientId == null)
			throw new IuBadRequestException("missing client_id");
		if (!clientId.matches("[\\w-]{3,}"))
			throw new IuBadRequestException("invalid client_id");

		final var redirectUri = authRequest.getRedirectUri();
		if (redirectUri == null)
			throw new IuBadRequestException("missing redirect_uri");

		final var resource = authRequest.getResource();

		try {
			final var client = AuthConfig.load(ThirdPartyClient.class, clientId);

			final var endpoint = IuIterable.select(client.getEndpoints(), e -> redirectUri.equals(e.getRedirectUri()));

			IuClientResource clientResource = null;
			for (final var endpointResource : endpoint.getResources())
				if (resource == null //
						|| resource.equals(endpointResource.getUri()))
					clientResource = IuObject.once(clientResource, endpointResource, "duplicate client resource");

			return new ResolvedResource(client, endpoint,
					Objects.requireNonNull(clientResource, "client resource mismatch"));

		} catch (Throwable e) {
			throw new IuBadRequestException("Invalid or incomplete resource designation in auth request", e);
		}
	}

	@Override
	public ThirdPartyAuthnRedirect init(IuRequestAttributes requestAttributes, ThirdPartyAuthorizeRequest authRequest) {
		final var resolved = resolveResource(authRequest);

		IuException.unchecked(() -> thirdPartyAuth.call(() -> {
			if (!thirdPartyAccess.validateResourceCode(resolved.resource.getCode()))
				throw new IuBadRequestException("invalid resource code");
			return null;
		}, requestAttributes, null, null, null, null));

		final var sessionHandler = thirdPartyAuth.sessionHandler();
		final var session = sessionHandler.create();
		session.setStrict(false);

		final var authorizeRequest = session.getDetail(ThirdPartySession.class);
		authorizeRequest.setAuthorizeRequest(authRequest);

		final var samlRequest = thirdPartyAuth.samlSessionVerifier().initRequest(session,
				requestAttributes.getRequestUri());

		final var setCookie = sessionHandler.store(session);
		return new ThirdPartyAuthnRedirect() {
			@Override
			public String getSetCookie() {
				return setCookie;
			}

			@Override
			public URI getLocation() {
				return samlRequest;
			}
		};
	}

	@Override
	public ThirdPartyAuthnRedirect authenticate(IuRequestAttributes requestAttributes, String samlResponse,
			String relayState) {
		final var sessionHandler = thirdPartyAuth.sessionHandler();
		final var session = Objects.requireNonNull(sessionHandler.activate(requestAttributes.getCookies()),
				"invalid or expired session");
		session.setStrict(false);

		URI returnUri;
		try {
			returnUri = thirdPartyAuth.samlSessionVerifier().verifyResponse(session, requestAttributes.getRemoteAddr(),
					samlResponse, relayState, relayState == null);
		} catch (IuAuthenticationException e) {
			LOG.log(Level.INFO, "Invalid SAML Response", e);
			returnUri = e.getLocation();
		}

		sessionHandler.remove(requestAttributes.getCookies());

		final var location = returnUri;
		final var setCookie = sessionHandler.store(session);
		return new ThirdPartyAuthnRedirect() {
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
	public String authorize(IuRequestAttributes requestAttributes) {
		final var sessionHandler = thirdPartyAuth.sessionHandler();
		final var session = Objects.requireNonNull(sessionHandler.activate(requestAttributes.getCookies()),
				"invalid or expired session");

		final var thirdPartySession = session.getDetail(ThirdPartySession.class);
		final var authRequest = Objects.requireNonNull(thirdPartySession.getAuthorizeRequest(),
				"missing auth request in session");

		final var verifier = thirdPartyAuth.samlSessionVerifier();
		final IuPrincipalIdentity samlIdentity;
		try {
			samlIdentity = verifier.getPrincipalIdentity(null, session);
		} catch (IuAuthenticationException e) {
			throw new IuBadRequestException("SAML authentication failure", e);
		}

		final var resolved = resolveResource(thirdPartySession.getAuthorizeRequest());
		final var call = new Callable<Void>() {
			private ThirdPartyAuthorizedPrincipals authorizedPrincipals;
			private IuAuthorizationDetails authorizationDetails;

			@Override
			public Void call() throws Exception {

				// 5.3: look up authorization attributes and details URI
				authorizedPrincipals = thirdPartyAccess.getAuthorizedPrincipals(authRequest.getDelegatingPrincipal(),
						resolved.resource.getCode());

				final var authorizationDetails = authorizedPrincipals.getAuthorizationDetails();
				final var principal = Optional.ofNullable(authorizationDetails.getDelegatingPrincipal())
						.orElse(authorizationDetails.getPrincipal());

				// 5.5: call details URI to get application attributes
				this.authorizationDetails = thirdPartyAuthorizationDetailsResource.getAuthorizationDetails(principal);

				return null;
			}
		};
		IuException.unchecked(() -> thirdPartyAuth.call(call, requestAttributes, resolved.endpoint,
				resolved.resource.getCode(), samlIdentity, authRequest.getImpersonatedPrincipal()));

		final var oidcMetadata = thirdPartyAuth.getOidcMetadata();
		final var clientId = authRequest.getClientId();
		final var client = AuthConfig.load(ThirdPartyClient.class, clientId);
		IuObject.once(clientId, client.getClientId(), "client ID mismatch");

		final var endpoint = IuIterable.select(client.getEndpoints(),
				e -> authRequest.getRedirectUri().equals(e.getRedirectUri()));
		final var identity = endpoint.getJwk();
		final var alg = Objects.requireNonNull(identity.getAlgorithm(), "alg required");

		final var issuer = AuthConfig.load(ThirdPartyIssuer.class, application);
		WebKey issuerKey = null;
		for (final var key : issuer.getKeys())
			if (Use.SIGN.equals(key.getUse()) //
					&& (Arrays.asList(alg.type).contains(key.getType()))) {
				issuerKey = key;
				break;
			}
		Objects.requireNonNull(issuerKey, "No issuer key available for " + alg);

		final var key = issuerKey;
		IuException.unchecked(() -> new PkiVerifier(key).verify(new PkiPrincipal(key)));

		final var exp = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(tokenTtl);
		final var accessToken = RemoteAccessToken.builder() //
				.iss(oidcMetadata.getIssuer()) //
				.aud(oidcMetadata.getIssuer()) //
				.sub(samlIdentity.getName()) //
				.iat() //
				.nonce(authRequest.getNonce()) //
				.exp(exp) //
				.build().sign("JWT", alg, issuerKey);

		final var authTime = samlIdentity.getAuthTime();
		final var idTokenBuilder = OidcIdToken.builder(alg, authRequest.getClientId(), Duration.ofHours(12L)) //
				.jti() //
				.iss(oidcMetadata.getIssuer()) //
				.aud(URI.create(clientId)) //
				.sub(samlIdentity.getName()) //
				.authTime(authTime) //
				.iat() //
				.exp(exp) //
				.nonce(authRequest.getNonce()) //
				.accessToken(accessToken) //
				.authorizationDetails(ThirdPartyAuthorizationDetails.class,
						call.authorizedPrincipals.getAuthorizationDetails());

		final var roles = call.authorizedPrincipals.getRoles();
		if (roles != null)
			idTokenBuilder.roles(IuIterable.stream(roles).toArray(String[]::new));

		if (call.authorizationDetails != null)
			idTokenBuilder.authorizationDetails(IuAuthorizationDetails.class, call.authorizationDetails);

		final var idClaims = idTokenBuilder.build();
		final var encryptJwk = endpoint.getEncryptJwk();
		final String idToken;
		if (encryptJwk != null)
			idToken = idClaims.signAndEncrypt("JWT", alg, issuerKey, endpoint.getEncryptJwk().getAlgorithm(),
					endpoint.getEnc(), encryptJwk);
		else
			idToken = idClaims.sign("JWT", alg, issuerKey);

		LOG.info(() -> "authorize:" + clientId + ":" + samlIdentity.getName());
		LOG.fine(() -> "authorize claims " + idClaims + "; SAML identity " + samlIdentity);

		final var codeKey = EphemeralKeys.contentEncryptionKey(256);
		final var code = IuText.base64Url(codeKey);
		final var codeHash = Arrays.copyOfRange(IuDigest.sha512(codeKey), 0, 32);
		final var tokenResponse = new OAuthTokenResponse() {
			@Override
			public String getTokenType() {
				return null;
			}

			@Override
			public String getRefreshToken() {
				return IdGenerator.generateId();
			}

			@Override
			public String getIdToken() {
				return idToken;
			}

			@Override
			public int getExpiresIn() {
				return (int) tokenTtl.toSeconds();
			}

			@Override
			public URI getErrorUri() {
				return null;
			}

			@Override
			public String getErrorDescription() {
				return null;
			}

			@Override
			public String getError() {
				return null;
			}

			@Override
			public String getAccessToken() {
				return accessToken;
			}
		};

		final var sessionIdentity = AuthConfig.load(IuSessionConfiguration.class, application).getJwk();
		final var storedTokens = new TokenResponseJwtBuilder().iss(oidcMetadata.getIssuer()) //
				.aud(oidcMetadata.getIssuer()) //
				.sub(samlIdentity.getName()) //
				.iat() //
				.nonce(authRequest.getNonce()) //
				.exp(exp) //
				.clientId(clientId) //
				.redirectUri(authRequest.getRedirectUri()) //
				.tokenResponse(tokenResponse) //
				.build().signAndEncrypt("jwt+token_response", sessionIdentity.getAlgorithm(), sessionIdentity,
						Algorithm.DIRECT, Encryption.A256GCM, WebKey.builder(Algorithm.DIRECT).key(codeKey).build());

		thirdPartyAuth.sessionStore().put(codeHash, IuText.utf8(storedTokens));

		sessionHandler.remove(requestAttributes.getCookies());

		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		params.put("code", IuIterable.iter(code));
		params.put("state", IuIterable.iter(authRequest.getState()));
		return authRequest.getRedirectUri() + "?" + IuWebUtils.createQueryString(params);
	}

	@Override
	public String token(IuRequestAttributes requestAttributes, ThirdPartyTokenRequest tokenRequest) {
		if (!"urn:ietf:params:oauth:client-assertion-type:jwt-bearer".equals(tokenRequest.getClientAssertionType()))
			throw new IuBadRequestException("Missing or invalid client_assertion_type");

		final var clientAssertion = Objects.requireNonNull(tokenRequest.getClientAssertion(),
				"missing client_assertion");

		final var store = thirdPartyAuth.sessionStore();
		final var oidc = thirdPartyAuth.getOidcMetadata();
		final var issuer = oidc.getIssuer();

		final TokenResponseJwt storedTokens;
		if ("authorization_code".equals(tokenRequest.getGrantType())) {
			final var codeKey = IuText.base64Url(Objects.requireNonNull(tokenRequest.getCode(), "missing code"));
			final var nonce = Objects.requireNonNull(tokenRequest.getNonce(), "missing nonce");
			final var redirectUri = Objects.requireNonNull(tokenRequest.getRedirectUri(), "missing redirect_uri");

			final var codeHash = Arrays.copyOfRange(IuDigest.sha512(codeKey), 0, 32);
			final var storedTokenJwt = IuText.utf8(thirdPartyAuth.sessionStore().get(codeHash));
			if (storedTokenJwt == null)
				throw new IuBadRequestException("Invalid or expired authorization code");
			else
				store.put(codeHash, null);

			final var sessionIdentity = AuthConfig.load(IuSessionConfiguration.class, application).getJwk();
			storedTokens = new TokenResponseJwt(Jwt.decryptAndVerify(storedTokenJwt, sessionIdentity,
					WebKey.builder(Algorithm.DIRECT).key(codeKey).build()));
			storedTokens.validateClaims(issuer, tokenTtl);

			if (!issuer.equals(storedTokens.getIssuer()))
				throw new IuBadRequestException("issuer mismatch");
			if (!nonce.equals(storedTokens.getNonce()))
				throw new IuBadRequestException("nonce mismatch");
			if (!redirectUri.equals(storedTokens.getRedirectUri()))
				throw new IuBadRequestException("redirect_uri mismatch");

		} else // TODO: refresh token
			throw new IuBadRequestException("Invalid grant type");

		final var clientId = storedTokens.getClientId();
		final var client = AuthConfig.load(ThirdPartyClient.class, clientId);
		final var endpoint = IuIterable.select(client.getEndpoints(),
				e -> storedTokens.getRedirectUri().equals(e.getRedirectUri()));

		final var assertion = new SelfIssuedAccessToken(endpoint.getJwk(), URI.create(clientId), oidc.getTokenEndpoint(), tokenTtl,
				clientAssertion);
		IuObject.once(clientId, assertion.getName());
		final var assertionKey = IuText.utf8(assertion.getAccessToken().getTokenId());
		if (store.get(assertionKey) != null)
			throw new IuBadRequestException("assertion jti replay detected");
		else
			store.put(assertionKey, new byte[0]);

		LOG.info("token:" + clientId + ":" + storedTokens.getSubject());

		return storedTokens.getTokenResponse().toString();
	}

}