package iu.auth.oidc;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import edu.iu.IuException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.Credentials;
import edu.iu.auth.config.IuOpenIdProviderEndpoint;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import iu.auth.config.AuthConfig;
import iu.auth.pki.PkiPrincipal;

/**
 * Verifies a JWT client assertion for authenticating an
 * {@link IuAuthorizationClient}.
 */
final class ClientAssertionVerifier {

	private record IssuedToken(URI iss, Instant iat) {
	}

	private static final Map<String, IssuedToken> USED_ASSERTIONS = new ConcurrentHashMap<>();
	private static final Set<AuthMethod> AUTH_METHODS = //
			Set.of(AuthMethod.CLIENT_SECRET_JWT, AuthMethod.PRIVATE_KEY_JWT);

	static {
		final var timer = new Timer("iu-oidc-jti-purge", true);
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				purgeUsedAssertions(Duration.ofMinutes(15L));
			}
		}, 1000L, 1000L);
	}

	private ClientAssertionVerifier() {
	}

	/**
	 * Purges used assertion jti claim values.
	 * 
	 * @param ttl used assertion time to live
	 */
	static void purgeUsedAssertions(Duration ttl) {
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final Iterator<IssuedToken> usedAssertions = USED_ASSERTIONS.values().iterator();
		while (usedAssertions.hasNext()) {
			final var usedAssertion = usedAssertions.next();
			if (usedAssertion.iat.isBefore(now.minus(ttl)))
				usedAssertions.remove();
		}
	}

	/**
	 * Requires and validates a JTI claim against replay attacks.
	 * 
	 * @param jti jti claim value
	 * @param iss iss claim value
	 */
	static void validateAssertionJti(String jti, URI iss) {
		if (!OpenIdConnectProvider.VSCHAR16.matcher(jti).matches())
			throw new IllegalArgumentException(
					"Invalid assertion jti claim, must be at least 16 printable ASCII characters");

		final var usedAt = USED_ASSERTIONS.get(jti);
		if (usedAt != null)
			throw new IllegalArgumentException("Replayed assertion jti claim");
		else
			USED_ASSERTIONS.put(jti, new IssuedToken(iss, Instant.now().truncatedTo(ChronoUnit.SECONDS)));
	}

	/**
	 * Verifies the first certificate from the JOSE {@code x5c} property as a
	 * trusted issuer, then verifies the signature using its public key.
	 * 
	 * @param jws         parsed assertion {@link WebSignature}
	 * @param token       parsed assertion {@link IuWebToken} claims
	 * @param issuerRealm issuer authentication realm
	 * @return {@link Credentials} derived from the JOSE header after passing a
	 *         verification steps
	 */
	static Credentials verifyAssertionIssuerTrust(WebSignedPayload jws, IuWebToken token, String issuerRealm) {
		final var credentials = new TrustedIssuerCredentials(jws.getSignatures().iterator().next().getHeader());
		final var principal = new PkiPrincipal(credentials);

		if (!principal.getName().equals(token.getIssuer().toString()))
			throw new IllegalArgumentException("Token issuer doesn't match subject principal");

		IuException.unchecked(() -> IuPrincipalIdentity.verify(principal,
				Objects.requireNonNull(issuerRealm, "Missing issuerRealm")));
		jws.verify(credentials.getJwk());
		return credentials;
	}

	/**
	 * Verifies a client assertion.
	 * 
	 * <p>
	 * The {@code sub} claim on the client assertion MUST be a valid
	 * {@link client_id} value {@link AuthConfig#load(Class, String) loadable} as an
	 * {@link IuAuthorizationClient}.
	 * </p>
	 * 
	 * @param tokenEndpoint Token endpoint {@link URI}
	 * @param assertion     JWT client assertion
	 * @param issuerRealm   Authentication realm for verifying the JWT issuer. When
	 *                      the token issued by a private key holder other than the
	 *                      {@link IuAuthorizedClient client}, a certificate with a
	 *                      common name matching the token issuer MUST be included
	 *                      in the {@code x5c} JWS header property, and
	 *                      {@link IuPrincipalIdentity#verify(IuPrincipalIdentity, String)
	 *                      verifiable} as trusted by this realm. MAY be null to
	 *                      REQUIRE the token to be issued by the private or MAC key
	 *                      holder associated with a public key registered via
	 *                      {@link IuAuthorizationClient#getCredentials()}
	 * @return {@link AuthenticatedClient}
	 * @see IuOpenIdProviderEndpoint#getAssertionIssuerRealm
	 */
	static AuthenticatedClient verify(URI tokenEndpoint, String assertion, String issuerRealm) {
		final var jws = WebSignedPayload.parse(assertion);
		final var token = OpenIdConnectProvider.parseTokenClaims(jws);

		final var clientId = Objects.requireNonNull(token.getSubject(), "Missing sub claim");
		if (!OpenIdConnectProvider.VSCHAR2.matcher(clientId).matches())
			throw new IllegalArgumentException("Invalid client_id in sub claim");
		
		final var client = AuthConfig.load(IuAuthorizationClient.class, clientId);
		OpenIdConnectProvider.validateClaims(tokenEndpoint, token, client.getAssertionTtl());

		final var iss = token.getIssuer();
		final var jti = token.getTokenId();
		validateAssertionJti(jti, iss);

		final Credentials credentials;
		if (!clientId.equals(iss.toString()))
			credentials = verifyAssertionIssuerTrust(jws, token, issuerRealm);
		else {
			Credentials verifiedCredentials = null;
			final Queue<Throwable> errors = new ArrayDeque<>();
			for (final var credentialsToTry : client.getCredentials())
				if (AUTH_METHODS.contains(credentialsToTry.getTokenEndpointAuthMethod()))
					try {
						jws.verify(credentialsToTry.getJwk());
						verifiedCredentials = credentialsToTry;
						break;
					} catch (Throwable e) {
						errors.offer(e);
					}

			if (verifiedCredentials == null) {
				final var error = new IllegalArgumentException("Invalid client assertion signature");
				errors.forEach(error::addSuppressed);
				throw error;
			}

			credentials = verifiedCredentials;
		}

		return new AuthenticatedClient(clientId, client, credentials, token.getTokenId(), token.getNonce());
	}

}
