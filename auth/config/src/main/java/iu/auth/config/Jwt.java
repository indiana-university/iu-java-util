package iu.auth.config;

import java.net.URI;
import java.time.Instant;

import edu.iu.IuObject;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonObject;

/**
 * Basic JWT claims {@link IuWebToken} implementation.
 */
final class Jwt implements IuWebToken {
	static {
		IuObject.assertNotOpen(Jwt.class);
	}

	private final JsonObject claims;

	/**
	 * Constructor
	 * 
	 * @param claims {@link JsonObject}
	 */
	Jwt(JsonObject claims) {
		this.claims = claims;
	}

	@Override
	public String getTokenId() {
		return IuJson.get(claims, "jti");
	}

	@Override
	public URI getIssuer() {
		return IuJson.get(claims, "iss", IuJsonAdapter.of(URI.class));
	}

	@Override
	public Iterable<URI> getAudience() {
		return IuJson.get(claims, "aud", IuJsonAdapter.of(Iterable.class, IuJsonAdapter.of(URI.class)));
	}

	@Override
	public String getSubject() {
		return IuJson.get(claims, "sub");
	}

	@Override
	public Instant getIssuedAt() {
		return IuJson.get(claims, "iat", JwtAdapter.NUMERIC_DATE);
	}

	@Override
	public Instant getNotBefore() {
		return IuJson.get(claims, "nbf", JwtAdapter.NUMERIC_DATE);
	}

	@Override
	public Instant getExpires() {
		return IuJson.get(claims, "exp", JwtAdapter.NUMERIC_DATE);
	}

	@Override
	public String getNonce() {
		return IuJson.get(claims, "nonce");
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(claims);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		Jwt other = (Jwt) obj;
		return IuObject.equals(claims, other.claims);
	}

}
