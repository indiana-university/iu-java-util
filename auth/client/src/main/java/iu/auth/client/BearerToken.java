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
package iu.auth.client;

import java.net.URI;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;
import java.time.Instant;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.client.IuBearerToken;
import edu.iu.auth.config.IuAuthorizationResource;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebSignedPayload;
import iu.auth.jwt.JwtAdapter;

/**
 * {@link IuBearerToken} implementation.
 */
final class BearerToken implements IuBearerToken {

	/** Principal authentication realm */
	private final String realm;

	/** Identifying principal */
	private final IuPrincipalIdentity id;

	/** Root resource URI */
	private final URI resourceUri;

	/** Parsed {@link WebSignedPayload} from the access token */
	private final WebSignedPayload jws;

	/** Access token claims */
	private final IuWebToken token;

	/** Access token */
	private final String accessToken;

	/**
	 * Constructor.
	 * 
	 * @param realm       Principal authentication realm
	 * @param id          Identifying principal
	 * @param resourceUri Root resource URI
	 * @param jws         Parsed {@link WebSignedPayload} from the access token
	 * @param accessToken Access token
	 * @param ttlPolicy   Maximum access token time to live from
	 *                    {@link IuAuthorizationResource#getTokenTtl()}
	 * @param expires     point in time the token expires
	 * @throws IuAuthenticationException If the subject's identifying principal
	 *                                   could not be verified
	 */
	BearerToken(String realm, IuPrincipalIdentity id, URI resourceUri, WebSignedPayload jws, String accessToken,
			Duration ttlPolicy) throws IuAuthenticationException {
		this.realm = realm;
		this.id = id;
		this.resourceUri = resourceUri;
		this.jws = jws;
		this.token = JwtAdapter.parseToken(jws);
		this.accessToken = accessToken;
	}

	@Override
	public String getName() {
		return id.getName();
	}

	@Override
	public Subject getSubject() {
		return id.getSubject();
	}

	@Override
	public IuWebToken getToken() {
		return token;
	}

	@Override
	public String getAccessToken() {
		return accessToken;
	}

	@Override
	public Instant getIssuedAt() {
		return token.getIssuedAt();
	}

	@Override
	public Instant getAuthTime() {
		return token.getIssuedAt();
	}

	@Override
	public Instant getExpires() {
		return token.getExpires();
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) throws IuAuthenticationException {
		token.validateClaims(resourceUri, null);
		IuPrincipalIdentity.verify(id, realm);

		httpRequestBuilder.header("Authorization", "Bearer " + accessToken);
	}

	@Override
	public String toString() {
		return "BearerToken [" + id + "] " + token;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(realm, id, token);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		BearerToken other = (BearerToken) obj;
		return IuObject.equals(realm, other.realm) //
				&& IuObject.equals(id, other.id) //
				&& IuObject.equals(token, other.token);
	}

	/**
	 * {@link WebSignedPayload#verify(edu.iu.crypt.WebKey) verifies} the signature,
	 * {@link IuWebToken#validateClaims(URI, Duration) validates} token claims and
	 * {@link IuPrincipalIdentity#verify(IuPrincipalIdentity, String) verifies} the
	 * ID principal associated with the token.
	 * 
	 * @param key 
	 * @throws IuAuthenticationException If principal verification fails
	 */
	void verify(WebKey key) throws IuAuthenticationException {
		jws.verify(key);
		token.validateClaims(resourceUri, null);
		IuPrincipalIdentity.verify(id, realm);
	}

	/**
	 * Gets {@code #realm}
	 * 
	 * @return {@code #realm}
	 */
	String realm() {
		return realm;
	}

}