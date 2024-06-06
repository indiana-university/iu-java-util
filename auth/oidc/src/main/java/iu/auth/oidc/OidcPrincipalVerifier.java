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

import edu.iu.auth.IuAuthenticationException;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link OidcPrincipal OpenID Connection principals}.
 */
final class OidcPrincipalVerifier implements PrincipalVerifier<OidcPrincipal> {

	private final boolean authoritative;
	private final String realm;

	/**
	 * Constructor.
	 * 
	 * @param authoritative authoritative flag: requires ID token or client ID to
	 *                      match principal claim when true; relies on userinfo
	 *                      access token for verification
	 * @param realm         authentication realm
	 */
	OidcPrincipalVerifier(boolean authoritative, String realm) {
		this.authoritative = authoritative;
		this.realm = realm;
	}

	@Override
	public Class<OidcPrincipal> getType() {
		return OidcPrincipal.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		return authoritative;
	}

	@Override
	public void verify(OidcPrincipal id, String realm) throws IuAuthenticationException {
		if (!id.realm().equals(realm))
			throw new IllegalArgumentException("Principal is invalid for the authenticaiton realm");

		id.getClaims();
	}

}
