/*
 * Copyright © 2025 Indiana University
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
package iu.auth.saml;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.security.auth.Subject;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;

/**
 * SAML {@link IuPrincipalIdentity} implementation.
 */
public final class SamlPrincipal implements IuPrincipalIdentity {
	static {
		IuObject.assertNotOpen(SamlPrincipal.class);
	}

	/** authentication realm */
	private final String realm;

	/** name */
	private final String name;

	/** issuer */
	private final String issuer;

	/** time authentication statement was issued by the IDP */
	private final Instant issueTime;

	/** authority */
	private final String authority;

	/** authentication time */
	private final Instant authTime;

	/** expires */
	private final Instant expires;

	/** attributes */
	private final StoredSamlAssertion[] assertions;

	/**
	 * Constructor.
	 * 
	 * @param realm          authentication realm
	 * @param name           principal name
	 * @param issuer         issuer
	 * @param issueTime      time authentication statement was issued by the IDP
	 * @param authority      authenticating authority
	 * @param authTime       authentication time
	 * @param expires        expire
	 * @param samlAssertions verified SAML assertions
	 */
	SamlPrincipal(String realm, String name, String issuer, Instant issueTime, String authority, Instant authTime,
			Instant expires, Iterable<StoredSamlAssertion> samlAssertions) {
		this.realm = Objects.requireNonNull(realm);
		this.name = Objects.requireNonNull(name);
		this.issuer = Objects.requireNonNull(issuer);
		this.issueTime = Objects.requireNonNull(issueTime);
		this.authority = authority;
		this.authTime = Objects.requireNonNull(authTime);
		this.expires = Objects.requireNonNull(expires);
		this.assertions = IuIterable.stream(samlAssertions).toArray(StoredSamlAssertion[]::new);
	}

	@Override
	public String getName() {
		return name;
	}

	/**
	 * Gets the IDP entity ID that issue the principal.
	 * 
	 * @return issuer IDP entity ID
	 */
	public String getIssuer() {
		return issuer;
	}

	@Override
	public Instant getIssuedAt() {
		return issueTime;
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
		final var subject = new Subject();
		subject.getPrincipals().add(this);
		subject.getPublicCredentials().addAll(List.of(assertions));
		subject.setReadOnly();
		return subject;
	}

	@Override
	public String toString() {
		return "SamlPrincipal [realm=" + realm + ", name=" + name + ", issuer=" + issuer + ", issueTime=" + issueTime
				+ ", authority=" + authority + ", authTime=" + authTime + ", expires=" + expires + ", assertions="
				+ Arrays.toString(assertions) + "]";
	}

	/**
	 * Determines if an authenticated principal is bound to a session.
	 * 
	 * @param details session details
	 * @return true if bound
	 */
	static boolean isBound(SamlPostAuthentication details) {
		return details.getRealm() != null //
				&& details.getName() != null //
				&& details.getIssueTime() != null //
				&& details.getAuthTime() != null //
				&& details.getExpires() != null //
				&& details.getAssertions() != null;
	}

	/**
	 * Creates a principal from the bound session details.
	 * 
	 * @param details session details
	 * @return principal
	 */
	static SamlPrincipal from(SamlPostAuthentication details) {
		IuObject.require(details, SamlPrincipal::isBound);
		return new SamlPrincipal(details.getRealm(), details.getName(), details.getIssuer(), details.getIssueTime(),
				details.getAuthnAuthority(), details.getAuthTime(), details.getExpires(), details.getAssertions());
	}

	/**
	 * Binds the authenticated principal data to a session.
	 * 
	 * @param details session details
	 */
	void bind(SamlPostAuthentication details) {
		details.setRealm(realm);
		details.setName(name);
		details.setIssuer(issuer);
		details.setIssueTime(issueTime);
		details.setAuthnAuthority(authority);
		details.setAuthTime(authTime);
		details.setExpires(expires);
		details.setAssertions(IuIterable.iter(assertions));
	}

	/**
	 * Verifies that the principal was issued by the indicated realm and has not
	 * expired.
	 * 
	 * @param realm authentication realm
	 * @throws IuAuthenticationException if the session is expired
	 */
	void verify(String realm) throws IuAuthenticationException {
		if (!this.realm.equals(realm))
			throw new IllegalArgumentException("invalid realm");

		if (Instant.now().isAfter(expires))
			throw new IuAuthenticationException(null, new IllegalStateException("expired"));
	}

}
