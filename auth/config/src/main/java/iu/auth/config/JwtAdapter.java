/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.auth.config;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import edu.iu.IuObject;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * Handles common JWT claims.
 * 
 * <p>
 * <em>Should</em> be extended to implement token-specific claim values.
 * </p>
 * 
 * @param <T> token type
 */
public class JwtAdapter<T extends IuWebToken> implements IuJsonAdapter<T> {
	static {
		IuObject.assertNotOpen(JwtAdapter.class);
	}

	/**
	 * Translates {@link Instant} values as seconds since epoch
	 */
	public static final IuJsonAdapter<Instant> NUMERIC_DATE = IuJsonAdapter.from(
			v -> v == null ? null : Instant.ofEpochSecond(IuJsonAdapter.of(Long.class).fromJson(v).longValue()),
			v -> v == null ? null : IuJsonAdapter.of(Long.class).toJson(v.getEpochSecond()));

	private class Claim<C> {
		private final Function<T, C> get;
		private final IuJsonAdapter<C> adapter;

		private Claim(Function<T, C> get, IuJsonAdapter<C> adapter) {
			this.get = get;
			this.adapter = adapter;
		}

		private void addTo(JsonObjectBuilder builder, String name, T jwt) {
			IuJson.add(builder, name, () -> get.apply(jwt), adapter);
		}
	}

	private final Map<String, Claim<?>> registeredClaims = new LinkedHashMap<>();
	private boolean sealed;

	/**
	 * Default constructor
	 */
	public JwtAdapter() {
		registerClaim("jti", IuWebToken::getTokenId, IuJsonAdapter.of(String.class));
		registerClaim("iss", IuWebToken::getIssuer, IuJsonAdapter.of(URI.class));
		registerClaim("aud", IuWebToken::getAudience, IuJsonAdapter.of(Iterable.class, IuJsonAdapter.of(URI.class)));
		registerClaim("sub", IuWebToken::getSubject, IuJsonAdapter.of(String.class));
		registerClaim("iat", IuWebToken::getIssuedAt, NUMERIC_DATE);
		registerClaim("nbf", IuWebToken::getNotBefore, NUMERIC_DATE);
		registerClaim("exp", IuWebToken::getExpires, NUMERIC_DATE);
		registerClaim("nonce", IuWebToken::getNonce, IuJsonAdapter.of(String.class));
		registerClaims();
		sealed = true;
	}

	/**
	 * Override to register extended claims before the registry is sealed.
	 */
	protected void registerClaims() {
	}

	/**
	 * Registers a claim based on an access method of the {@link IuWebToken token
	 * interface}.
	 * 
	 * @param <C>     claim type
	 * @param name    <a href=
	 *                "https://www.iana.org/assignments/jwt/jwt.xhtml">Registered
	 *                JSON property name</a> for the claim
	 * @param get     Function from token interface to claim value, typically a
	 *                handle to a simple getter method
	 * @param adapter claim JSON type adapter
	 */
	protected final <C> void registerClaim(String name, Function<T, C> get, IuJsonAdapter<C> adapter) {
		if (sealed)
			throw new IllegalStateException("sealed");

		if (registeredClaims.containsKey(name))
			throw new IllegalArgumentException("already registered");

		registeredClaims.put(name, new Claim<>(get, adapter));
	}

	/**
	 * Gets a claim value from a parsed {@link JsonObject}
	 * 
	 * @param <C>    claim type
	 * @param claims parsed {@link JsonObject}
	 * @param name   <a href=
	 *               "https://www.iana.org/assignments/jwt/jwt.xhtml">Registered
	 *               JSON property name</a> for the claim
	 * @return claim value
	 */
	@SuppressWarnings("unchecked")
	public <C> C getClaim(JsonObject claims, String name) {
		if (!sealed)
			throw new IllegalStateException("not sealed");

		return (C) Objects.requireNonNull(registeredClaims.get(name), "claim " + name + " not registered").adapter
				.fromJson(claims.get(name));
	}

	@SuppressWarnings("unchecked")
	@Override
	public final T fromJson(JsonValue serializedJwt) {
		if (serializedJwt == null)
			return null;
		else
			return (T) new Jwt(this, serializedJwt.asJsonObject());
	}

	@Override
	public JsonValue toJson(T jwt) {
		if (jwt == null)
			return null;

		final var builder = IuJson.object();
		registeredClaims.forEach((name, claim) -> claim.addTo(builder, name, jwt));
		return builder.build();
	}

}
