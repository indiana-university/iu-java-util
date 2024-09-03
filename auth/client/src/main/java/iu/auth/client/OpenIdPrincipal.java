package iu.auth.client;

import java.net.URI;
import java.time.Instant;

import javax.security.auth.Subject;

import edu.iu.IuIterable;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuAuthorizationResource;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.crypt.WebKey;
import iu.auth.config.AuthConfig;
import iu.auth.jwt.Jwt;

/**
 * Encapsulates an OpenID Connect ID Token received via {@link IuTokenResponse}
 */
class OpenIdPrincipal implements IuPrincipalIdentity {

	private final String clientId;
	private final URI issuer;
	private final String name;
	private final Instant issuedAt;
	private final Instant authTime;
	private final Instant expires;

	/**
	 * Constructor
	 * 
	 * @param clientId Client ID
	 * @param idToken  ID token
	 */
	OpenIdPrincipal(String clientId, String idToken) {
		final var resource = AuthConfig.load(IuAuthorizationResource.class, clientId);
		this.clientId = clientId;

		final var credentials = resource.getCredentials();
		final var audienceKey = credentials.getJwk();

		final var metadata = OpenIdProviderMetadataCache.get(resource.getProviderMetadataUri());
		issuer = metadata.getIssuer();

		final var issuerKey = IuIterable.select(WebKey.readJwks(metadata.getJwksUri()),
				k -> resource.getIdTokenKeyId().equals(k.getKeyId()));

		IuWebToken token = Jwt.from(idToken, issuerKey, audienceKey);
		

//		final Set<String> scope = new LinkedHashSet<>();
//		tokenResponse.getScope().forEach(scope::add);
//
//		if (!(client instanceof IuAuthoritativeOpenIdClient))
//			return userinfo(accessToken);
//
//		final var authClient = (IuAuthoritativeOpenIdClient) client;
//		final var clientId = authClient.getCredentials().getName();
//		if (idToken == null) {
//			final var userinfo = userinfo(accessToken);
//			final var principal = (String) Objects.requireNonNull(userinfo.get("principal"),
//					"Userinfo missing principal claim");
//			final var sub = (String) Objects.requireNonNull(userinfo.get("sub"), "Userinfo missing sub claim");
//			if (clientId.equals(principal) //
//					&& sub.equals(principal))
//				return userinfo;
//			else
//				throw new IllegalArgumentException("Missing ID token");
//		}
//
//		final var idTokenAlgorithm = authClient.getIdTokenAlgorithm();
//		final var verifiedIdToken = IuWebToken.from(idToken);
//		if (!idTokenAlgorithm.equals(verifiedIdToken.getAlgorithm()))
//			throw new IllegalArgumentException(idTokenAlgorithm + " required");
//
//		final var encodedHash = IuException
//				.unchecked(() -> MessageDigest.getInstance("SHA-256").digest(IuText.utf8(accessToken)));
//		final var halfOfEncodedHash = Arrays.copyOf(encodedHash, (encodedHash.length / 2));
//		final var atHashGeneratedfromAccessToken = Base64.getUrlEncoder().withoutPadding()
//				.encodeToString(halfOfEncodedHash);
//
//		final var atHash = Objects.requireNonNull(verifiedIdToken.getClaim("at_hash"), "at_hash");
//		if (!atHash.equals(atHashGeneratedfromAccessToken))
//			throw new IllegalArgumentException("Invalid at_hash");
//
//		final String nonce = verifiedIdToken.getClaim("nonce");
//		authClient.verifyNonce(nonce);
//
//		final var now = Instant.now();
//		final Instant authTime = verifiedIdToken.getClaim("auth_time");
//		final var authExpires = authTime.plus(authClient.getAuthenticatedSessionTimeout());
//		if (now.isAfter(authExpires))
//			throw new IllegalArgumentException("OIDC authenticated session is expired");
//
//		// TODO: provide directly from ID Token JWT payload
//		final Map<String, Object> claims = new LinkedHashMap<>();
//		claims.put("sub", verifiedIdToken.getSubject());
//		claims.put("aud", verifiedIdToken.getAudience().iterator().next());
//		claims.put("iat", verifiedIdToken.getIssuedAt());
//		claims.put("exp", verifiedIdToken.getExpires());
//		claims.put("auth_time", authTime);
//
//		for (final var userinfoClaimEntry : userinfo(accessToken).entrySet())
//			claims.compute(userinfoClaimEntry.getKey(),
//					(name, value) -> IuObject.once(value, userinfoClaimEntry.getValue()));
//
//		return Collections.unmodifiableMap(claims);

	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Instant getIssuedAt() {
		return issuedAt;
	}

	@Override
	public Instant getAuthTime() {
		return authTime;
	}

	@Override
	public Instant getExpires() {
		return expires;
	}

	@Override
	public Subject getSubject() {
		return subject;
	}

}
