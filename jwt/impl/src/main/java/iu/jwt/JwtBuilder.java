/*
 * Copyright © 2026 Indiana University
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
package iu.jwt;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Instant;

import edu.iu.IdGenerator;
import edu.iu.client.IuJsonBuilder;
import edu.iu.jwt.WebToken;
import edu.iu.jwt.WebTokenBuilder;

/**
 * Mutable builder implementation for programmatically constructing new
 * {@link Jwt} instances.
 * 
 * <p>
 * Modules that provide a subclass of {@link Jwt} SHOULD also provide a subclass
 * of this class that overrides {@link #build()}.
 * </p>
 * 
 * @param <B> Concrete builder type
 */
public class JwtBuilder<B extends JwtBuilder<B>> extends IuJsonBuilder<B> implements WebTokenBuilder {

	private boolean setIssuedAt;

	/**
	 * Default constructor
	 */
	public JwtBuilder() {
	}

	@Override
	public B jti() {
		return jti(IdGenerator.generateId());
	}

	@Override
	public B jti(String tokenId) {
		return claim("jti", tokenId, String.class);
	}

	@Override
	public B iss(URI issuer) {
		return claim("iss", issuer, URI.class);
	}

	@Override
	public B aud(URI... audience) {
		if (audience.length == 0)
			throw new IllegalArgumentException("At least one audience URI is required");
		if (audience.length == 1)
			return claim("aud", audience[0], URI.class);
		else
			return claim("aud", audience, URI[].class);
	}

	@Override
	public B sub(String subject) {
		return claim("sub", subject, String.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	public B iat() {
		setIssuedAt = true;
		return (B) this;
	}

	@Override
	public B nbf(Instant notBefore) {
		return claim("nbf", notBefore, Instant.class);
	}

	@Override
	public B exp(Instant expires) {
		return claim("exp", expires, Instant.class);
	}

	@Override
	public B nonce(String nonce) {
		return claim("nonce", nonce, String.class);
	}

	@Override
	public B claim(String name, Object value, Type type) {
		return param(name, value, Jwt.adapt(type));
	}

	/**
	 * Applies state just prior to building the token.
	 * 
	 * <p>
	 * Call this method when overriding {@link #build()} to apply any final state,
	 * e.g., setting the iat claim value.
	 * </p>
	 */
	protected void prepare() {
		if (setIssuedAt)
			claim("iat", Instant.now(), Instant.class);
	}

	@Override
	public WebToken build() {
		prepare();
		return new Jwt(toJson());
	}

}