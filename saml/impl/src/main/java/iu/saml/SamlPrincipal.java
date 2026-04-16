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
package iu.saml;

import java.time.Instant;
import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.saml.IuSamlAssertion;
import edu.iu.saml.IuSamlPrincipal;

/**
 * SAML {@link IuPrincipalIdentity} implementation.
 */
public final class SamlPrincipal implements IuSamlPrincipal {
	static {
		IuObject.assertNotOpen(SamlPrincipal.class);
	}

	private final String name;
	private final String authnAuthority;
	private final Instant authnInstant;
	private final Instant expires;
	private final Iterable<IuSamlAssertion> assertions;

	/**
	 * Constructor.
	 * 
	 * @param name           principal name
	 * @param authnAuthority authentication authority entity ID
	 * @param authnInstant   authentication instant
	 * @param expires        expiration instant
	 * @param assertions     verified SAML assertion
	 */
	SamlPrincipal(String name, String authnAuthority, Instant authnInstant, Instant expires,
			Iterable<IuSamlAssertion> assertions) {
		this.name = Objects.requireNonNull(name, "missing principal name");
		this.authnAuthority = Objects.requireNonNull(authnAuthority, "missing authnAuthority");
		this.authnInstant = Objects.requireNonNull(authnInstant, "missing authnInstant");
		this.expires = Objects.requireNonNull(expires, "missing expires");
		this.assertions = Objects.requireNonNull(assertions, "missing assertions");
	}

	/**
	 * Creates a principal from the bound session details.
	 * 
	 * @param details session details
	 * @return principal
	 */
	static SamlPrincipal from(SamlPostAuthentication details) {
		if (details.isInvalid())
			throw new IllegalStateException("invalid session");

		return new SamlPrincipal(details.getName(), details.getAuthnAuthority(), details.getAuthnInstant(),
				details.getExpires(), details.getAssertions());
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getAuthnAuthority() {
		return authnAuthority;
	}

	@Override
	public Instant getAuthnInstant() {
		return authnInstant;
	}

	@Override
	public Instant getExpires() {
		return expires;
	}

	@Override
	public Iterable<IuSamlAssertion> getAssertions() {
		return assertions;
	}

	@Override
	public String toString() {
		return "SamlPrincipal [name=" + name + ", authnAuthority=" + authnAuthority + ", authnInstant=" + authnInstant
				+ ", expires=" + expires + ", assertions=" + assertions + "]";
	}
}
