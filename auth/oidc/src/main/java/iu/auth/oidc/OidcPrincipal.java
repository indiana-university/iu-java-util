package iu.auth.oidc;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.security.auth.Subject;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuExpiredCredentialsException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oidc.IuAuthoritativeOpenIdClient;
import edu.iu.auth.oidc.IuOpenIdPrincipal;

/**
 * OpenID Connect principal identity implementation;
 */
class OidcPrincipal implements IuOpenIdPrincipal {
	private static final long serialVersionUID = 1L;

	static {
		IuObject.assertNotOpen(OidcPrincipal.class);
	}

	private static Map<String, ?> getClaims(String idToken, String accessToken, OpenIdProvider provider)
			throws IuAuthenticationException {

		final var client = provider.client();
		if (!(client instanceof IuAuthoritativeOpenIdClient))
			return provider.userinfo(accessToken);

		final var authClient = (IuAuthoritativeOpenIdClient) client;
		final var clientId = authClient.getCredentials().getName();
		if (idToken == null) {
			final var userinfo = provider.userinfo(accessToken);
			final var principal = (String) Objects.requireNonNull(userinfo.get("principal"),
					"Userinfo missing principal claim");
			final var sub = (String) Objects.requireNonNull(userinfo.get("sub"), "Userinfo missing sub claim");
			if (clientId.equals(principal) //
					&& sub.equals(principal))
				return userinfo;
			else
				throw new IllegalStateException("Missing ID token");
		}

		final var idTokenAlgorithm = authClient.getIdTokenAlgorithm();
		@SuppressWarnings("deprecation") // TODO: replace with iu-java-auth-jwt
		final var verifiedIdToken = provider.idTokenVerifier().verify(clientId, idToken);
		if (!idTokenAlgorithm.equals(verifiedIdToken.getAlgorithm()))
			throw new IllegalArgumentException(idTokenAlgorithm + " required");

		final var encodedHash = IuException
				.unchecked(() -> MessageDigest.getInstance("SHA-256").digest(IuText.utf8(accessToken)));
		final var halfOfEncodedHash = Arrays.copyOf(encodedHash, (encodedHash.length / 2));
		final var atHashGeneratedfromAccessToken = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(halfOfEncodedHash);

		final var atHash = Objects.requireNonNull(verifiedIdToken.getClaim("at_hash").asString(), "at_hash");
		if (!atHash.equals(atHashGeneratedfromAccessToken))
			throw new IllegalStateException("Invalid at_hash");

		final var nonce = verifiedIdToken.getClaim("nonce").asString();
		authClient.verifyNonce(nonce);

		final var now = Instant.now();
		final var authTime = verifiedIdToken.getClaim("auth_time").asInstant();
		final var authExpires = authTime.plus(authClient.getAuthenticatedSessionTimeout());
		if (now.isAfter(authExpires)) {
			final Map<String, String> challengeAttributes = new LinkedHashMap<>();
			challengeAttributes.put("realm", client.getRealm());
			challengeAttributes.put("scope", String.join(" ", authClient.getScope()));
			challengeAttributes.put("error", "invalid_token");
			challengeAttributes.put("error_description", "session timeout, must reauthenticate");
			throw new IuExpiredCredentialsException(IuWebUtils.createChallenge("Bearer", challengeAttributes));
		}

		// TODO: provide directly from ID Token JWT payload
		final Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("sub", verifiedIdToken.getSubject());
		claims.put("aud", verifiedIdToken.getAudience().iterator().next());
		claims.put("iat", verifiedIdToken.getIssuedAtAsInstant());
		claims.put("exp", verifiedIdToken.getExpiresAtAsInstant());
		claims.put("auth_time", authTime);

		for (final var userinfoClaimEntry : provider.userinfo(accessToken).entrySet())
			claims.compute(userinfoClaimEntry.getKey(),
					(name, value) -> IuObject.once(value, userinfoClaimEntry.getValue()));

		return Collections.unmodifiableMap(claims);
	}

	/**
	 * Verifies ID and access tokens.
	 * 
	 * @param id
	 */
	static void verify(IuPrincipalIdentity id) {

	}

	private final String idToken;
	private final String accessToken;
	private final String name;
	private final String realm;
	private transient Map<String, ?> claims;
	private transient Instant claimsVerified;

	/**
	 * Constructor.
	 * 
	 * @param idToken     id token
	 * @param accessToken access token
	 * @param provider    issuing provider
	 * @throws IuAuthenticationException if the ID or access token is invalid
	 */
	OidcPrincipal(String idToken, String accessToken, OpenIdProvider provider) throws IuAuthenticationException {
		this.idToken = idToken;
		this.accessToken = accessToken;
		this.realm = provider.client().getRealm();
		this.name = Objects.requireNonNull((String) getClaims(idToken, accessToken, provider).get("principal"));
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Map<String, ?> getClaims() {
		final var provider = OpenIdConnectSpi.getProvider(realm);
		if (Instant.now().isAfter(claimsVerified.plus(provider.client().getVerificationInterval())))
			claims = IuException.unchecked(() -> getClaims(idToken, accessToken, provider));
		return claims;
	}

	@Override
	public Subject getSubject() {
		final var subject = new Subject();
		subject.getPrincipals().add(this);
		subject.setReadOnly();
		return subject;
	}

	@Override
	public void revoke() {
		// TODO Auto-generated method stub

	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(name);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		OidcPrincipal other = (OidcPrincipal) obj;
		return IuObject.equals(name, other.name);
	}

	@Override
	public String toString() {
		return "OIDC Principal ID [name=" + name + "]";
	}

}
