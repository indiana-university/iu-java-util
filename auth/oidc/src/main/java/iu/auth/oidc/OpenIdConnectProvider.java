package iu.auth.oidc;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.UnsafeSupplier;
import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.auth.config.IuOpenIdProviderEndpoint;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.oidc.IuAuthorizationRequest;
import edu.iu.auth.oidc.IuTokenResponse;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebSignedPayload;
import iu.auth.config.AuthConfig;
import iu.auth.config.JwtAdapter;

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

	private static final IuJsonAdapter<? extends IuWebToken> JWT_JSON = new JwtAdapter<>();

	private final IuOpenIdProviderEndpoint config;

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
	 * Parses JWT claims.
	 * 
	 * @param jws {@link WebSignedPayload}
	 * @return {@link IuWebToken}
	 */
	static IuWebToken parseTokenClaims(WebSignedPayload jws) {
		return JWT_JSON.fromJson(IuJson.parse(IuText.utf8(jws.getPayload())));
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
	 * Verify JWT registered claims are well-formed and within the allowed time
	 * window.
	 * 
	 * @param audience Expected audience {@link URI}
	 * @param claims   Parsed JWT claims
	 * @param ttl      Maximum assertion time to live allowed by client
	 *                 configuration
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1">RFC-7519 JWT
	 *      Section 4.1</a>
	 */
	static void validateClaims(URI audience, IuWebToken claims, Duration ttl) {
		validateTtl(ttl);

		Objects.requireNonNull(claims.getIssuer(), "Missing iss claim");
		Objects.requireNonNull(claims.getSubject(), "Missing sub claim");

		boolean found = false;
		for (final var aud : Objects.requireNonNull(claims.getAudience(), "Missing aud claim"))
			if (aud.equals(audience)) {
				found = true;
				break;
			}
		if (!found)
			throw new IllegalArgumentException("Token aud claim doesn't include " + audience);

		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

		final var iat = Objects.requireNonNull(claims.getIssuedAt(), "Missing iat claim");
		if (iat.isAfter(now.plusSeconds(15L)))
			throw new IllegalArgumentException("Token iat claim must be no more than PT15S in the future");

		final var nbf = claims.getNotBefore();
		if (nbf != null && nbf.isAfter(now.plusSeconds(15L)))
			throw new IllegalArgumentException("Token nbf claim must be no more than PT15S in the future");

		final var exp = Objects.requireNonNull(claims.getExpires(), "Missing exp claim");
		if (ttl.compareTo(Duration.between(iat, exp)) < 0)
			throw new IllegalArgumentException("Token exp claim must be no more than " + ttl + " in the future");
		if (exp.isBefore(now.minusSeconds(15L)))
			throw new IllegalArgumentException("Token is expired");
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
		return ClientAssertionVerifier.verify(config.getTokenEndpoint(), assertion, config.getAssertionIssuerRealm());
	}

	/**
	 * Handles an incoming token endpoint request.
	 * 
	 * @param request {@link IuAuthorizationRequest}
	 * @return {@link IuTokenResponse}
	 */
	public IuTokenResponse handleTokenEndpointRequest(IuAuthorizationRequest request) {
		final AuthenticatedClient authorizedClient = IuException
				.unchecked(() -> delayOnFailure(() -> authenticateClient(request)));

		throw new UnsupportedOperationException("TODO");
	}

}
