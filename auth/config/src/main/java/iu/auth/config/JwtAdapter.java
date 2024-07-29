package iu.auth.config;

import java.net.URI;
import java.time.Instant;

import edu.iu.auth.jwt.IuWebToken;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Handles common JWT claims.
 */
class JwtAdapter implements IuJsonAdapter<IuWebToken> {

	/**
	 * Translates {@link Instant} values as seconds since epoch
	 */
	static final IuJsonAdapter<Instant> NUMERIC_DATE = IuJsonAdapter.from(
			v -> v == null ? null : Instant.ofEpochSecond(IuJsonAdapter.of(Long.class).fromJson(v).longValue()),
			v -> v == null ? null : IuJsonAdapter.of(Long.class).toJson(v.getEpochSecond()));

	/**
	 * Default constructor
	 */
	JwtAdapter() {
	}

	@Override
	public IuWebToken fromJson(JsonValue serializedJwt) {
		if (serializedJwt == null)
			return null;
		else
			return new Jwt(serializedJwt.asJsonObject());
	}

	@Override
	public JsonValue toJson(IuWebToken jwt) {
		if (jwt == null)
			return null;

		final var builder = IuJson.object();
		IuJson.add(builder, "jti", jwt.getTokenId());
		IuJson.add(builder, "iss", jwt::getIssuer, IuJsonAdapter.of(URI.class));
		IuJson.add(builder, "aud", jwt::getAudience, IuJsonAdapter.of(Iterable.class, IuJsonAdapter.of(URI.class)));
		IuJson.add(builder, "sub", jwt.getSubject());
		IuJson.add(builder, "iat", jwt::getIssuedAt, NUMERIC_DATE);
		IuJson.add(builder, "nbf", jwt::getNotBefore, NUMERIC_DATE);
		IuJson.add(builder, "exp", jwt::getExpires, NUMERIC_DATE);
		IuJson.add(builder, "nonce", jwt.getNonce());
		return builder.build();
	}

}
