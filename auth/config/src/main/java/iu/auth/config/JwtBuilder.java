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

import edu.iu.auth.config.IuWebTokenBuilder;
import edu.iu.auth.jwt.IuWebToken;

/**
 * {@link IuWebTokenBuilder} implementation.
 */
public class JwtBuilder implements IuWebTokenBuilder {

	private String tokenId;
	private URI issuer;
	private Iterable<URI> audience;
	private String subject;
	private Instant issuedAt;
	private Instant notBefore;
	private Instant expires;
	private String nonce;

	/**
	 * Default constructor
	 */
	public JwtBuilder() {
	}

	/**
	 * Copy constructor
	 * 
	 * @param token {@link IuWebToken} to copy from
	 */
	public JwtBuilder(IuWebToken token) {
		tokenId = token.getTokenId();
		issuer = token.getIssuer();
		audience = token.getAudience();
		subject = token.getSubject();
		issuedAt = token.getIssuedAt();
		notBefore = token.getNotBefore();
		expires = token.getExpires();
		nonce = token.getNonce();
	}

	@Override
	public String getTokenId() {
		return tokenId;
	}

	@Override
	public void setTokenId(String tokenId) {
		this.tokenId = tokenId;
	}

	@Override
	public URI getIssuer() {
		return issuer;
	}

	@Override
	public void setIssuer(URI issuer) {
		this.issuer = issuer;
	}

	@Override
	public Iterable<URI> getAudience() {
		return audience;
	}

	@Override
	public void setAudience(Iterable<URI> audience) {
		this.audience = audience;
	}

	@Override
	public String getSubject() {
		return subject;
	}

	@Override
	public void setSubject(String subject) {
		this.subject = subject;
	}

	@Override
	public Instant getIssuedAt() {
		return issuedAt;
	}

	@Override
	public void setIssuedAt(Instant issuedAt) {
		this.issuedAt = issuedAt;
	}

	@Override
	public Instant getNotBefore() {
		return notBefore;
	}

	@Override
	public void setNotBefore(Instant notBefore) {
		this.notBefore = notBefore;
	}

	@Override
	public Instant getExpires() {
		return expires;
	}

	@Override
	public void setExpires(Instant expires) {
		this.expires = expires;
	}

	@Override
	public String getNonce() {
		return nonce;
	}

	@Override
	public void setNonce(String nonce) {
		this.nonce = nonce;
	}

}
