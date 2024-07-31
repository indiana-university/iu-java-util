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
