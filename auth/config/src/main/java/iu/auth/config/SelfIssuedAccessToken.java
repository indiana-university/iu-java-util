package iu.auth.config;

import java.net.URI;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.oauth.IuCallerAttributes;
import iu.crypt.Jwt;

/**
 * Encapsulates a self-issued access token suitable for server-to-server
 * invocation between nodes associated by a web of trust relationship.
 */
public class SelfIssuedAccessToken implements IuApiCredentials {

	private final String bearerToken;
	private final RemoteAccessToken accessToken;

	/**
	 * Constructor, for use by the self-issuing client endpoint.
	 * 
	 * @param pkp      Issuer identity metadata
	 * @param audience Remote audience URI
	 * @param tokenTtl Duration between token issue and expiration times
	 * @param caller   Caller attributes
	 */
	public SelfIssuedAccessToken(IuPrivateKeyPrincipal pkp, URI audience, Duration tokenTtl,
			IuCallerAttributes caller) {

		Objects.requireNonNull(caller, "missing caller attributes");
		final var authnPrincipal = Objects.requireNonNull(caller.getAuthnPrincipal(), "missing authn_principal");
		Objects.requireNonNull(caller.getRemoteAddr(), "missing remote_addr");
		Objects.requireNonNull(caller.getRequestUri(), "missing request_uri");
		Objects.requireNonNull(caller.getUserAgent(), "missing user_agent");

		final var impersonatedPrincipal = caller.getImpersonatedPrincipal();
		final var sub = impersonatedPrincipal == null ? authnPrincipal : impersonatedPrincipal;

		final var jwk = pkp.getJwk();
		final var iss = URI.create(jwk.getKeyId());
		accessToken = RemoteAccessToken.builder().jti() //
				.iss(iss).aud(audience).sub(sub) //
				.iat().exp(Instant.now().plus(tokenTtl)) //
				.caller(caller).build();
		bearerToken = accessToken.sign("JWT", pkp.getAlg(), jwk);
	}

	/**
	 * Constructor, for use by the verifying server endpoint.
	 * 
	 * @param pkp         Issuer identity metadata
	 * @param audience    Remote audience URI
	 * @param tokenTtl    Duration between token issue and expiration times
	 * @param bearerToken Bearer token
	 */
	public SelfIssuedAccessToken(IuPrivateKeyPrincipal pkp, URI audience, Duration tokenTtl, String bearerToken) {
		this.bearerToken = bearerToken;
		final var jwk = pkp.getJwk();
		final var iss = URI.create(jwk.getKeyId());
		accessToken = new RemoteAccessToken(Jwt.verify(bearerToken, jwk));
		accessToken.validateClaims(audience, tokenTtl);
		IuObject.once(iss, accessToken.getIssuer(), "issuer mismatch");

		final var caller = Objects.requireNonNull(accessToken.getCallerAttributes(), "missing caller attributes");
		final var authnPrincipal = Objects.requireNonNull(caller.getAuthnPrincipal(), "missing authn_principal");
		Objects.requireNonNull(caller.getRemoteAddr(), "missing remote_addr");
		Objects.requireNonNull(caller.getRequestUri(), "missing request_uri");
		Objects.requireNonNull(caller.getUserAgent(), "missing user_agent");

		final var sub = accessToken.getSubject();
		if (!authnPrincipal.equals(sub) //
				&& !sub.equals(caller.getImpersonatedPrincipal()))
			throw new IllegalArgumentException("sub mismatch");
	}

	@Override
	public String getName() {
		return accessToken.getSubject();
	}

	@Override
	public String getIssuer() {
		return accessToken.getIssuer().toString();
	}

	@Override
	public Instant getIssuedAt() {
		return accessToken.getIssuedAt();
	}

	@Override
	public Instant getAuthTime() {
		return accessToken.getIssuedAt();
	}

	@Override
	public Instant getExpires() {
		return accessToken.getExpires();
	}

	@Override
	public Subject getSubject() {
		return new Subject(true, Set.of(this), Set.of(accessToken.getCallerAttributes()), Set.of());
	}

	@Override
	public void applyTo(Builder requestBuilder) throws IuAuthenticationException {
		requestBuilder.header("Authorization", //
				"Bearer " + bearerToken);
	}

	@Override
	public String toString() {
		return "SelfIssuedAccessToken [" + accessToken + "]";
	}

}
