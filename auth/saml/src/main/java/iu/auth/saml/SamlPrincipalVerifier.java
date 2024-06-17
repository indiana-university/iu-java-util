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
package iu.auth.saml;

import java.net.URI;

import edu.iu.auth.IuAuthenticationException;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link SamlPrincipal SAML principals}.
 */
final class SamlPrincipalVerifier implements PrincipalVerifier<SamlPrincipal> {

	private final String realm;
	private final URI authenticationEndpoint;

	/**
	 * Constructor.
	 * 
	 * @param realm                  authentication realm the service provider id
	 * @param authenticationEndpoint authentication endpoint, to redirect the user
	 *                               to if authentication expires
	 */
	SamlPrincipalVerifier(String realm, URI authenticationEndpoint) {
		this.realm = realm;
		this.authenticationEndpoint = authenticationEndpoint;
	}

	@Override
	public Class<SamlPrincipal> getType() {
		return SamlPrincipal.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		return true;
	}

	@Override
	public String getAuthScheme() {
		return null;
	}

	@Override
	public URI getAuthenticationEndpoint() {
		return authenticationEndpoint;
	}

	@Override
	public void verify(SamlPrincipal id) throws IuAuthenticationException {
		try {
			id.verify(realm);
		} catch (Throwable e) {
			final var authException = new IuAuthenticationException(null, e);
			authException.setLocation(authenticationEndpoint);
			throw authException;
		}
	}

}
