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
package iu.oidc.client;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.function.Function;

import edu.iu.client.IuJsonAdapter;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcPrincipal;
import jakarta.json.JsonObject;

/**
 * {@link IuOidcPrincipal} implementation class.
 */
public class OidcPrincipal implements IuOidcPrincipal {

	private final WebToken idToken;
	private final JsonObject userinfoClaims;
	private final String setCookie;
	private final Function<URI, String> accessTokenLookup;
	private final Function<Type, IuJsonAdapter<?>> adapterFactory;

	/**
	 * Constructor.
	 * 
	 * @param idToken           ID token
	 * @param userinfoClaims    Claims provided by the userinfo endpoint
	 * @param setCookie         set-cookie header value to pass back to the user
	 *                          agent if session state changed assembling the
	 *                          principal
	 * @param accessTokenLookup finds access tokens by URI
	 * @param adapterFactory    JSON type adapter factory
	 */
	public OidcPrincipal(WebToken idToken, JsonObject userinfoClaims, String setCookie,
			Function<URI, String> accessTokenLookup, Function<Type, IuJsonAdapter<?>> adapterFactory) {
		this.idToken = idToken;

		if (!userinfoClaims.containsKey("sub"))
			throw new IllegalArgumentException("userinfo missing sub claim");
		if (!userinfoClaims.getString("sub").equals(idToken.getSubject()))
			throw new IllegalArgumentException("userinfo sub claim doesn't match id token");
		this.userinfoClaims = userinfoClaims;

		this.setCookie = setCookie;

		this.accessTokenLookup = accessTokenLookup;
		this.adapterFactory = adapterFactory;
	}

	@Override
	public String getName() {
		return idToken.getSubject();
	}

	@Override
	public String getSetCookie() {
		return setCookie;
	}

	@Override
	public WebToken getIdToken() {
		return idToken;
	}

	@Override
	public <T> T getClaim(String name, Class<T> type) {
		final var userinfoClaimValue = userinfoClaims.get(name);
		if (userinfoClaimValue == null)
			return idToken.getClaim(name, type);
		else
			return type.cast(adapterFactory.apply(type).fromJson(userinfoClaimValue));
	}

	@Override
	public String getAccessToken(URI resourceUri) {
		return accessTokenLookup.apply(resourceUri);
	}

}
