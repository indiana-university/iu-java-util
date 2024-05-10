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

import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link BearerToken} instances.
 */
final class BearerTokenVerifier implements PrincipalVerifier<BearerToken> {

	private final ThreadLocal<Boolean> loop = new ThreadLocal<>();
	private final String realm;
	private final String scope;

	/**
	 * Constructor.
	 * 
	 * @param realm bearer authentication realm
	 * @param scope OAuth formatted scope string
	 */
	BearerTokenVerifier(String realm, String scope) {
		this.realm = realm;
		this.scope = scope;
	}

	@Override
	public Class<BearerToken> getType() {
		return BearerToken.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		for (final var realm : OAuthSpi.getClient(realm).getPrincipalRealms())
			if (realm.equals(this.realm))
				return true;
		return false;
	}

	@Override
	public void verify(BearerToken id, String realm) throws IuAuthenticationException {
		final var client = OAuthSpi.getClient(realm);

		if (!realm.equals(this.realm))
			throw challenge("invalid_token", "Invalid token for realm");

		if (id.expired())
			throw challenge("invalid_token", "Token is expired");

		for (final var idRealm : client.getPrincipalRealms())
			if (id.realm().equals(idRealm))
				if (id == id.getSubject().getPrincipals().iterator().next()) {
					if (realm.equals(idRealm))
						return; // authoritative bearer token
					else
						break;
				} else {
					if (Boolean.TRUE.equals(loop.get()))
						throw new IllegalStateException("illegal principal reference");
					try {
						loop.set(true);
						id.verifyPrincipal();
						return;
					} finally {
						loop.remove();
					}
				}

		throw challenge("invalid_token", "Invalid token for principal realm");
	}

	private IuAuthenticationException challenge(String error, String errorDescription) {
		final Map<String, String> params = new LinkedHashMap<>();
		params.put("realm", realm);
		if (scope != null)
			params.put("scope", scope);
		params.put("error", error);
		params.put("error_description", errorDescription);
		return new IuAuthenticationException(IuWebUtils.createChallenge("Bearer", params));
	}

}
