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

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuBearerAuthCredentials;
import iu.auth.util.PrincipalVerifierRegistry;

/**
 * {@link IuBearerAuthCredentials} implementation.
 */
public class BearerAuthCredentials implements IuBearerAuthCredentials {
	private static final long serialVersionUID = 1L;

	static {
		PrincipalVerifierRegistry.registerDelegate(BearerAuthCredentials.class, bearer -> bearer.id);
	}

	/**
	 * Identifying principal.
	 */
	private final IuPrincipalIdentity id;

	/**
	 * Authorized subject.
	 */
	private final Subject subject;

	/**
	 * Access token.
	 */
	private final String accessToken;

	/**
	 * Constructor.
	 * 
	 * @param realm       authentication realm
	 * @param subject     verified subject
	 * @param accessToken access token
	 */
	protected BearerAuthCredentials(String realm, Subject subject, String accessToken) {
		this.id = IuPrincipalIdentity.from(subject, realm);
		subject.getPrincipals(IuPrincipalIdentity.class).iterator().next();
		this.subject = subject;
		this.accessToken = accessToken;
	}

	@Override
	public String getName() {
		return id.getName();
	}

	@Override
	public Subject getSubject() {
		return subject;
	}

	@Override
	public String getAccessToken() {
		return accessToken;
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) {
		httpRequestBuilder.header("Authorization", "Bearer " + accessToken);
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(subject);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		BearerAuthCredentials other = (BearerAuthCredentials) obj;
		return IuObject.equals(subject, other.subject);
	}

	@Override
	public String toString() {
		return "BearerAuthCredentials [" + subject + "]";
	}

}
