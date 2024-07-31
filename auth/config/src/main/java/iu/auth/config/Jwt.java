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
