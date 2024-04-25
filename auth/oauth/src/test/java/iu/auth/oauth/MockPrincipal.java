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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IdGenerator;
import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import iu.auth.principal.PrincipalVerifierRegistry;

@SuppressWarnings("javadoc")
class MockPrincipal implements IuPrincipalIdentity {
	private static final long serialVersionUID = 1L;

	static void registerVerifier(String realm) {
		PrincipalVerifierRegistry.registerVerifier(realm, id -> {
			if (assertInstanceOf(MockPrincipal.class, id).revoked)
				throw new IuAuthenticationException("Bearer realm=\"" + realm + "\"");
		}, false);
	}

	private final String realm;
	private final String name = IdGenerator.generateId();
	private final Set<Principal> additionalPrincipals = new LinkedHashSet<>();
	boolean revoked;

	public MockPrincipal(String realm) {
		this.realm = realm;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(name);
	}

	@Override
	public void revoke() {
		revoked = true;
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		MockPrincipal other = (MockPrincipal) obj;
		return IuObject.equals(name, other.name);
	}

	@Override
	public String toString() {
		return "MockPrincipal [realm=" + realm + ", name=" + name + "]";
	}

	@Override
	public Subject getSubject() {
		final var subject = new Subject();
		subject.getPrincipals().add(this);
		subject.getPrincipals().addAll(additionalPrincipals);
		subject.setReadOnly();
		return subject;
	}

	String getRealm() {
		return realm;
	}

	void addPrincipal(Principal p) {
		additionalPrincipals.add(p);
	}

}