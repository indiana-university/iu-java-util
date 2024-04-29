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
package iu.auth.oauth;

import java.net.http.HttpRequest.Builder;
import java.time.Instant;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuBearerToken;

/**
 * {@link IuBearerToken} implementation.
 */
final class BearerToken implements IuBearerToken {

	private static final long serialVersionUID = 1L;

	/**
	 * Principal authentication realm.
	 */
	private final String realm;

	/**
	 * Identifying principal.
	 */
	private final IuPrincipalIdentity id;

	/**
	 * Authorized scope.
	 */
	private final Set<String> scope;

	/**
	 * Access token.
	 */
	private final String accessToken;

	/**
	 * Expiration time.
	 */
	private final Instant expires;

	/**
	 * Constructor.
	 * 
	 * @param realm       principal authentication realm
	 * @param id          verified principal identity
	 * @param scope       authorized scope
	 * @param accessToken access token
	 * @param expires     point in time the token expires
	 * @throws IuAuthenticationException If the subject's identifying principal
	 *                                   could not be verified
	 */
	BearerToken(String realm, IuPrincipalIdentity id, Set<String> scope, String accessToken, Instant expires)
			throws IuAuthenticationException {
		this.realm = realm;
		this.id = id;
		this.scope = scope;
		this.accessToken = accessToken;
		this.expires = expires;
	}

	@Override
	public String getName() {
		if (id == null)
			return OAuthSpi.getClient(realm).getCredentials().getName();
		else
			return id.getName();
	}

	@Override
	public Subject getSubject() {
		if (id == null)
			return new Subject(true, Set.of(this), Set.of(), Set.of());
		else
			return id.getSubject();
	}

	@Override
	public Set<String> getScope() {
		return scope;
	}

	@Override
	public String getAccessToken() {
		return accessToken;
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) throws IuAuthenticationException {
		IuPrincipalIdentity.verify(id, realm);
		httpRequestBuilder.header("Authorization", "Bearer " + accessToken);
	}

	@Override
	public String toString() {
		return "BearerToken [" + id + "]; scope " + scope;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(accessToken, id, scope);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		BearerToken other = (BearerToken) obj;
		return IuObject.equals(accessToken, other.accessToken) //
				&& IuObject.equals(id, other.id) //
				&& IuObject.equals(scope, other.scope);
	}

	/**
	 * Gets the principal authentication realm.
	 * 
	 * @return principal authentication realm
	 */
	String realm() {
		return realm;
	}

	/**
	 * Determines if this bearer token has expired.
	 * 
	 * @return true if expired; else false
	 */
	boolean expired() {
		return !Instant.now().isBefore(expires);
	}

	/**
	 * Verifies the principal identify this bearer token refers to.
	 * 
	 * @throws IuAuthenticationException If principal verification fails
	 */
	void verifyPrincipal() throws IuAuthenticationException {
		IuPrincipalIdentity.verify(id, realm);
	}

}
