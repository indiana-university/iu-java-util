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
package iu.auth.jwt;

import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import edu.iu.IuIterable;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuBearerToken;

/**
 * {@link IuBearerToken} view of a JWT.
 */
final class JwtAuthorizationGrant extends JwtAssertion {
	private static final long serialVersionUID = 1L;

	/**
	 * Requested scope.
	 */
	private final Set<String> scope;

	/**
	 * Constructor.
	 * 
	 * @param jwt   JWT
	 * @param scope requested scope
	 */
	JwtAuthorizationGrant(Jwt jwt, Set<String> scope) {
		super(jwt);
		this.scope = scope;
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) throws IuAuthenticationException {
		IuPrincipalIdentity.verify(jwt, jwt.realm());
		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		params.put("grant_type", IuIterable.iter("urn:ietf:params:oauth:grant-type:jwt-bearer"));
		params.put("assertion", IuIterable.iter(jwt.token()));
		if (scope != null)
			params.put("scope", IuIterable.iter(String.join(" ", scope)));
		httpRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
		httpRequestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(params)));
	}

}
