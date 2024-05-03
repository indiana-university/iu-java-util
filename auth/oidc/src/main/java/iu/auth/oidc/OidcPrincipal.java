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
package iu.auth.oidc;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.oidc.IuOpenIdPrincipal;

/**
 * OpenID Connect principal identity implementation;
 */
final class OidcPrincipal implements IuOpenIdPrincipal {
	private static final long serialVersionUID = 1L;

	private final String idToken;
	private final String accessToken;
	private final String name;
	private final String realm;

	private transient Map<String, ?> claims;
	private transient Instant claimsVerified;

	/**
	 * Constructor.
	 * 
	 * @param idToken     id token
	 * @param accessToken access token
	 * @param provider    issuing provider
	 */
	OidcPrincipal(String idToken, String accessToken, OpenIdProvider provider) {
		this.idToken = idToken;
		this.accessToken = accessToken;
		this.realm = provider.client().getRealm();
		this.claims = provider.getClaims(idToken, accessToken);
		this.name = Objects.requireNonNull((String) claims.get("principal"));
		this.claimsVerified = Instant.now();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Map<String, ?> getClaims() {
		final var provider = OpenIdConnectSpi.getProvider(realm);
		final var now = Instant.now();
		if (now.isAfter(claimsVerified.plus(provider.client().getVerificationInterval()))) {
			claims = provider.getClaims(idToken, accessToken);
			IuObject.once(name, claims.get("principal"));
			claimsVerified = now;
		}
		return claims;
	}

	@Override
	public Subject getSubject() {
		final var subject = new Subject();
		subject.getPrincipals().add(this);
		subject.setReadOnly();
		return subject;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(name, realm);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		OidcPrincipal other = (OidcPrincipal) obj;
		return IuObject.equals(name, other.name) //
				&& IuObject.equals(realm, other.realm);
	}

	@Override
	public String toString() {
		return "OIDC Principal ID [" + name + "; " + realm + "] " + claims;
	}

	/**
	 * Gets the authentication realm.
	 * 
	 * @return authentication realm
	 */
	String realm() {
		return realm;
	}
}
