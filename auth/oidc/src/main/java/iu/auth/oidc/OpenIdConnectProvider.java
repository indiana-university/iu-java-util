package iu.auth.oidc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.UnsafeSupplier;
import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.auth.config.IuOpenIdProviderEndpoint;
import edu.iu.auth.oidc.IuAuthorizationRequest;
import iu.auth.client.IuTokenResponse;
import iu.auth.config.AuthConfig;

/**
 * OpenID Connect Provider implementation.
 */
public final class OpenIdConnectProvider {
	static {
		IuObject.assertNotOpen(OpenIdConnectProvider.class);
	}

	private final static Logger LOG = Logger.getLogger(OpenIdConnectProvider.class.getName());

	/** Matches 2 to 4096 visible ASCII characters */
	static final Pattern VSCHAR2 = Pattern.compile("[\\x20-\\x7e]{2,4096}");

	/** Matches 16 to 4096 visible ASCII characters */
	static final Pattern VSCHAR16 = Pattern.compile("[\\x20-\\x7e]{16,4096}");

	private final IuOpenIdProviderEndpoint config;

	/**
	 * Validates and converts the scope parameter.
	 * TODO: regex
	 * @param scope configured scope
	 * @return scope attribute
	 */
	static String validateScope(Iterable<String> scope) {
		if (scope == null)
			return null;

		final var sb = new StringBuilder();
		scope.forEach(scopeToken -> {
			// scope = scope-token *( SP scope-token )
			if (sb.length() > 0)
				sb.append(' ');
			// scope-token = 1*( %x21 / %x23-5B / %x5D-7E )
			for (var i = 0; i < scopeToken.length(); i++) {
				final var c = (int) scopeToken.charAt(i);
				if (c < 0x21 || c == 0x22 || c == 0x5c || c > 0x7e)
					throw new IllegalArgumentException();
			}
			sb.append(scopeToken);
		});

		if (sb.length() == 0)
			return null;
		else
			return sb.toString();
	}

	/**
	 * Constructor.
	 * 
	 * @param config OpenID Connect provider endpoint configuration
	 */
	public OpenIdConnectProvider(IuOpenIdProviderEndpoint config) {
		this.config = config;
	}

	/**
	 * Gets {@link #config}
	 * 
	 * @return {@link #config}
	 */
	IuOpenIdProviderEndpoint config() {
		return config;
	}

	/**
	 * Reads an incoming request parameter.
	 * 
	 * @param params request parameters, received from
	 *               HttpServletRequest.getParameterMap() after confirming HTTP POST
	 *               w/ Content-Type: application/x-form-url-encoded;charset=UTF-8
	 * @param name   parameter name
	 * @return parameter value if present and not polluted (VSCHAR2 mismatch or more
	 *         than one item in the array); never null
	 */
	static String param(Map<String, String[]> params, String name) {
		final var values = Objects.requireNonNull(params.get(name), () -> "Missing " + name);
		if (values.length != 1 //
				|| !VSCHAR2.matcher(values[0]).matches())
			throw new IllegalArgumentException("Invalid " + name);
		return values[0];
	}

	/**
	 * Executes {@link UnsafeSupplier#get()}, inserting a PT0.5S delay before
	 * throwing exceptions.
	 * 
	 * @param <T>      result type
	 * @param supplier supplier
	 * @return result
	 * @throws Throwable from {@link UnsafeSupplier#get()}
	 */
	static <T> T delayOnFailure(UnsafeSupplier<T> supplier) throws Throwable {
		try {
			return supplier.get();
		} catch (Throwable e) {
			IuException.suppress(e, () -> Thread.sleep(500L));
			throw e;
		}
	}

	/**
	 * Validates that a token time to live configuration parameter is at least 15
	 * seconds, and no more than 15 minutes.
	 * 
	 * @param ttl token time to live
	 * @throws IllegalArgumentException if the ttl argument is invalid
	 */
	static void validateTtl(Duration ttl) throws IllegalArgumentException {
		if (ttl.compareTo(Duration.ofSeconds(15L)) <= 0 //
				|| ttl.compareTo(Duration.ofMinutes(15L)) > 0)
			throw new IllegalArgumentException("Invalid ttl");
	}

	/**
	 * Validates that an expiration date is no more than 15 seconds in the past, and
	 * no more than 15 minutes in the future.
	 * 
	 * @param exp       expiration date
	 * @param ttlPolicy maximum length of time to allow for the expiration date to
	 *                  be in the future
	 * @throws IllegalArgumentException if the exp argument is invalid
	 */
	static void validateExpiration(Instant exp, Duration ttlPolicy) {
		final var now = Instant.now();
		if (exp.isBefore(now.minusSeconds(15L)) //
				|| exp.isAfter(now.plus(ttlPolicy)))
			throw new IllegalArgumentException("expired");
	}

	/**
	 * Validates that a remote address is in the client's allow list.
	 * 
	 * @param ipAllowList allowed IP ranges
	 * @param remoteAddr  remote address
	 */
	static void verifyIpAllowed(Iterable<String> ipAllowList, String remoteAddr) {
		final var clientIp = IuWebUtils.getInetAddress(remoteAddr);
		for (final var ipAllow : ipAllowList)
			if (IuWebUtils.isInetAddressInRange(clientIp, ipAllow))
				return;
		throw new IllegalArgumentException("Remote address not in allow list " + ipAllowList);
	}

	/**
	 * Authenticates an incoming request.
	 * 
	 * @param request request
	 * @return {@link AuthenticatedClient}
	 */
	@SuppressWarnings("deprecation")
	AuthenticatedClient authenticateClient(IuAuthorizationRequest request) {
		final var authorization = request.getAuthorizaton();
		final var params = request.getParams();
		final var grantType = GrantType.from(param(params, "grant_type"));

		if (authorization != null)
			if (authorization.startsWith("Basic "))
				return authenticateClientBasic(authorization.substring(6), param(params, "nonce"));
			else if (authorization.startsWith("Bearer "))
				return authenticateClientAssertion(authorization.substring(7));
			else
				throw new UnsupportedOperationException("Unsupported authorization method");
		else if (grantType.equals(GrantType.JWT_BEARER))
			return authenticateClientAssertion(param(params, "assertion"));
		else if (params.containsKey("client_assertion_type"))
			if (!param(params, "client_assertion_type")
					.equals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"))
				throw new UnsupportedOperationException("Unsupported client assertion type");
			else
				return authenticateClientAssertion(param(params, "client_assertion"));
		else
			return authenticateClientSecret(AuthMethod.CLIENT_SECRET_POST, param(params, "client_id"),
					param(params, "client_secret"), param(params, "nonce"));
	}

	/**
	 * Authenticates a confidential client using HTTP Basic authentication.
	 * 
	 * @param encodedCredentials encoded basic authentication credentials
	 * @param nonce              one-time number
	 * @return {@link AuthenticatedClient}
	 */
	@SuppressWarnings("deprecation")
	AuthenticatedClient authenticateClientBasic(String encodedCredentials, String nonce) {
		final var decodedCredentials = IuText.utf8(IuText.base64(encodedCredentials));
		final var indexOfColon = decodedCredentials.indexOf(':');
		if (indexOfColon == -1)
			throw new IllegalArgumentException("Invalid Basic auth credentials");
		else
			return authenticateClientSecret(AuthMethod.CLIENT_SECRET_BASIC,
					decodedCredentials.substring(0, indexOfColon), decodedCredentials.substring(indexOfColon + 1),
					nonce);
	}

	/**
	 * Authenticates a confidential client using the client secret as a password.
	 * 
	 * @param authMethod   {@link AuthMethod}
	 * @param clientId     Client ID
	 * @param clientSecret Client Secret
	 * @param nonce        One-time number
	 * @return {@link AuthenticatedClient}
	 */
	AuthenticatedClient authenticateClientSecret(AuthMethod authMethod, String clientId, String clientSecret,
			String nonce) {
		if (!VSCHAR2.matcher(clientId).matches())
			throw new IllegalArgumentException("Illegal client_id");

		if (!VSCHAR16.matcher(clientSecret).matches())
			throw new IllegalArgumentException("Illegal client_secret");
		IdGenerator.verifyId(clientSecret, authMethod.ttlPolicy.toMillis());

		final var client = AuthConfig.load(IuAuthorizationClient.class, clientId);
		return new AuthenticatedClient(clientId, client, ClientSecretVerifier.verify(client, authMethod, clientSecret),
				IdGenerator.generateId(), nonce);
	}

	/**
	 * Authenticates a confidential client from a JWT bearer assertion.
	 * 
	 * @param assertion JWT bearer assertion
	 * @return authorized client record
	 */
	AuthenticatedClient authenticateClientAssertion(String assertion) {
		return ClientAssertionVerifier.verify(config().getTokenEndpoint(), assertion,
				config().getAssertionIssuerRealm());
	}

	/**
	 * Handles an incoming token endpoint request.
	 * 
	 * @param request {@link IuAuthorizationRequest}
	 * @return {@link IuTokenResponse}
	 */
	public IuTokenResponse handleTokenEndpointRequest(IuAuthorizationRequest request) {
		final AuthenticatedClient authenticatedClient = IuException
				.unchecked(() -> delayOnFailure(() -> authenticateClient(request)));

		if (authenticatedClient.credentials().getTokenEndpointAuthMethod().requiresIpAllow)
			verifyIpAllowed(authenticatedClient.client().getIpAllow(), request.getRemoteAddr());

		final var params = request.getParams();
		final var grantType = GrantType.from(param(params, "grant_type"));
		// TODO: verify grant type is allowed for credentials

		switch (grantType) {
		case CLIENT_CREDENTIALS:
		case JWT_BEARER:
			// TODO: resolve client attributes from authenticated metadata
			break;

		case AUTHORIZATION_CODE:
			// TODO: resolve user principal and attributes from authorization code

		case REFRESH_TOKEN:
			// TODO: resolve user principal and attributes from refresh token

		case PASSWORD:
		default:
			throw new UnsupportedOperationException("Unsupported grant type " + grantType);
		}

		// TODO: verify all requested scopes are satisfied
		// TODO: issue access, id, and/or refresh tokens

		throw new UnsupportedOperationException("TODO");
	}

}
