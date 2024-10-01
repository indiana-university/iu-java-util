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
package iu.auth.session;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.auth.session.IuSession;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import iu.crypt.Jwt;

/**
 * {@link IuSession} implementation
 */
class Session implements IuSession {
	static {
		IuObject.assertNotOpen(Session.class);
	}

	/** root protected resource URI */
	private URI resourceUri;

	/** Session expiration time */
	private Instant expires;

	/** change flag to determine when session attributes change */
	private boolean changed;

	/** Session details */
	private Map<String, Map<String, Object>> details;

	/**
	 * New session constructor.
	 * 
	 * @param resourceUri root protected resource URI
	 * @param expires     expiration time
	 */
	Session(URI resourceUri, Duration expires) {
		this.resourceUri = resourceUri;
		this.expires = Instant.now().plus(expires).truncatedTo(ChronoUnit.SECONDS);
		details = new LinkedHashMap<String, Map<String, Object>>();
	}

	/**
	 * Session token constructor.
	 * 
	 * @param token         tokenized session
	 * @param secretKey     Secret key to use for detokenizing the session.
	 * @param issuerKey     issuer key
	 * @param maxSessionTtl maximum session time to live
	 */
	Session(String token, byte[] secretKey, WebKey issuerKey, Duration maxSessionTtl) {
		final var jose = WebCryptoHeader.getProtectedHeader(token);
		if (!Algorithm.DIRECT.equals(jose.getAlgorithm()))
			throw new IllegalArgumentException("Invalid token key protection algorithm");
		if (!Encryption.A256GCM.equals(WebCryptoHeader.Param.ENCRYPTION.get(jose)))
			throw new IllegalArgumentException("Invalid token content encryption algorithm");
		if (!"session+jwt".equals(jose.getContentType()))
			throw new IllegalArgumentException("Invalid token type");

		final var jwt = new SessionJwt(
				Jwt.decryptAndVerify(token, issuerKey, WebKey.builder(Type.RAW).key(secretKey).build()));

		resourceUri = Objects.requireNonNull(jwt.getIssuer(), "Missing token issuer");
		jwt.validateClaims(resourceUri, maxSessionTtl);
		IuObject.require(jwt.getSubject(), resourceUri.toString()::equals);
		expires = Objects.requireNonNull(jwt.getExpires());
		details = new LinkedHashMap<>(Objects.requireNonNull(jwt.getDetails()));
	}

	/**
	 * Token constructor
	 * 
	 * @param secretKey secret key
	 * @param issuerKey issuer key
	 * @param algorithm algorithm
	 * @return tokenized session
	 */
	String tokenize(byte[] secretKey, WebKey issuerKey, Algorithm algorithm) {
		return new SessionJwtBuilder() //
				.iss(resourceUri) //
				.sub(resourceUri.toString()) //
				.aud(resourceUri) //
				.iat() //
				.exp(expires) //
				.details(details) //
				.build().signAndEncrypt("session+jwt", algorithm, issuerKey, Algorithm.DIRECT, Encryption.A256GCM,
						WebKey.builder(Type.RAW).key(secretKey).build());
	}

	@Override
	public <T> T getDetail(Class<T> type) {
		Map<String, Object> attributes = null;
		if (details.containsKey(type.getName())) {
			attributes = details.get(type.getName());
		} else {
			attributes = new HashMap<String, Object>();
			details.put(type.getName(), attributes);
		}
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				new SessionDetail(attributes, this)));

	}

	@Override
	public boolean isChanged() {
		return changed;
	}

	/**
	 * Sets the change flag
	 * 
	 * @param changed set to true when session attributes change, otherwise false
	 */
	void setChanged(boolean changed) {
		this.changed = changed;
	}

	/**
	 * Gets session expire time
	 * 
	 * @return {@link Instant} session expire time
	 */
	Instant getExpires() {
		return expires;
	}

	@Override
	public String toString() {
		return "Session [resourceUri=" + resourceUri + ", expires=" + expires + ", changed=" + changed + ", details="
				+ details + "]";
	}

}
