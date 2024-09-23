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
package iu.crypt;

import java.net.URI;
import java.time.Instant;

import edu.iu.crypt.WebToken;
import edu.iu.crypt.WebTokenBuilder;

/**
 * Mutable builder implementation for programmatically constructing new
 * {@link Jwt} instances.
 * 
 * <p>
 * Modules that provide a subclass of {@link Jwt} SHOULD also provide a subclass
 * of this class that overrides {@link #build()}.
 * </p>
 */
public class JwtBuilder implements WebTokenBuilder {

	private String type = "JWT";
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
	public JwtBuilder(WebToken token) {
		tokenId = token.getTokenId();
		issuer = token.getIssuer();
		audience = token.getAudience();
		subject = token.getSubject();
		issuedAt = token.getIssuedAt();
		notBefore = token.getNotBefore();
		expires = token.getExpires();
		nonce = token.getNonce();
	}

	/**
	 * Gets the token type
	 * 
	 * @return token type
	 */
	public String getType() {
		return type;
	}

	/**
	 * Sets the token type
	 * 
	 * @param type token type
	 */
	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String getTokenId() {
		return tokenId;
	}

	/**
	 * Sets {@link #tokenId}
	 * 
	 * @param tokenId {@link #tokenId}
	 */
	public void setTokenId(String tokenId) {
		this.tokenId = tokenId;
	}

	@Override
	public URI getIssuer() {
		return issuer;
	}

	/**
	 * Sets {@link #issuer}
	 * 
	 * @param issuer {@link #issuer}
	 */
	public void setIssuer(URI issuer) {
		this.issuer = issuer;
	}

	@Override
	public Iterable<URI> getAudience() {
		return audience;
	}

	/**
	 * Sets {@link #audience}
	 * 
	 * @param audience {@link #audience}
	 */
	public void setAudience(Iterable<URI> audience) {
		this.audience = audience;
	}

	@Override
	public String getSubject() {
		return subject;
	}

	/**
	 * Sets {@link #subject}
	 * 
	 * @param subject {@link #subject}
	 */
	public void setSubject(String subject) {
		this.subject = subject;
	}

	@Override
	public Instant getIssuedAt() {
		return issuedAt;
	}

	/**
	 * Sets {@link #issuedAt}
	 * 
	 * @param issuedAt {@link #issuedAt}
	 */
	public void setIssuedAt(Instant issuedAt) {
		this.issuedAt = issuedAt;
	}

	@Override
	public Instant getNotBefore() {
		return notBefore;
	}

	/**
	 * Sets {@link #notBefore}
	 * 
	 * @param notBefore {@link #notBefore}
	 */
	public void setNotBefore(Instant notBefore) {
		this.notBefore = notBefore;
	}

	@Override
	public Instant getExpires() {
		return expires;
	}

	/**
	 * Sets {@link #expires}
	 * 
	 * @param expires {@link #expires}
	 */
	public void setExpires(Instant expires) {
		this.expires = expires;
	}

	@Override
	public String getNonce() {
		return nonce;
	}

	/**
	 * Sets {@link #nonce}
	 * 
	 * @param nonce {@link #nonce}
	 */
	public void setNonce(String nonce) {
		this.nonce = nonce;
	}

	/**
	 * Completes {@link Jwt} construction.
	 * 
	 * <p>
	 * Subclasses SHOULD override this method to refine return type and support
	 * extended claims.
	 * </p>
	 * 
	 * @return {@link Jwt}
	 */
	public Jwt build() {
		return new Jwt(type, this);
	}

}