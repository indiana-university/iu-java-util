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
package iu.auth.config;

import java.net.http.HttpRequest.Builder;
import java.time.Instant;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;

/**
 * Encapsulates a bearer token
 */
public class BearerToken implements IuApiCredentials {

	private final String issuer;
	private final Instant issuedAt;
	private final Instant authTime;
	private final Instant expires;
	private final String name;
	private final String token;

	/**
	 * Constructor.
	 * 
	 * @param issuer   token issuer
	 * @param issuedAt point in time the token response was issued
	 * @param authTime token authentication time, if known
	 * @param expires  token expiration time
	 * @param name     subject principal name
	 * @param token    token value
	 */
	public BearerToken(String issuer, Instant issuedAt, Instant authTime, Instant expires, String name, String token) {
		this.issuer = issuer;
		this.issuedAt = issuedAt;
		this.authTime = authTime;
		this.expires = expires;
		this.name = name;
		this.token = token;
	}

	@Override
	public String getIssuer() {
		return issuer;
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
		return new Subject(true, Set.of(this), Set.of(), Set.of());
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) throws IuAuthenticationException {
		httpRequestBuilder.header("Authorization", "Bearer " + token);
	}

}
