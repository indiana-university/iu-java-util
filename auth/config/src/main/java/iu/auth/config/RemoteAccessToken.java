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
package iu.auth.config;

import java.net.URI;

import edu.iu.auth.oauth.IuAuthorizationDetails;
import edu.iu.auth.oauth.IuCallerAttributes;
import edu.iu.client.IuJson;
import iu.crypt.Jwt;
import jakarta.json.JsonObject;

/**
 * Exposes claims specific to authorizing EJB invocation.
 */
public class RemoteAccessToken extends Jwt {

	/**
	 * Constructor.
	 * 
	 * @param claims Parsed JSON claims
	 */
	public RemoteAccessToken(JsonObject claims) {
		super(claims);
	}

	/**
	 * Gets a builder.
	 * 
	 * @return {@link RemoteAccessTokenBuilder}
	 */
	public static RemoteAccessTokenBuilder<?> builder() {
		return new RemoteAccessTokenBuilder<>();
	}

	/**
	 * Gets the called URL.
	 *
	 * @param <T>             details interface type
	 * @param type            authorization details type
	 * @param detailInterface authorization details interface
	 * @return authorization details
	 */
	protected <T extends IuAuthorizationDetails> T getAuthorizationDetails(String type, Class<T> detailInterface) {
		return detailInterface.cast(RemoteAccessTokenBuilder.adaptAuthorizationDetails(detailInterface)
				.fromJson(claims.getJsonArray("authorization_details").stream()
						.filter(a -> type.equals(IuJson.get(a.asJsonObject(), "type"))).findFirst().get()));
	}

	/**
	 * Gets the authorized scope.
	 * 
	 * @return scope
	 */
	public String getScope() {
		return IuJson.get(claims, "scope");
	}

	/**
	 * Gets the called URL.
	 * 
	 * @return {@link URI}
	 */
	public IuCallerAttributes getCallerAttributes() {
		return getAuthorizationDetails(IuCallerAttributes.TYPE, IuCallerAttributes.class);
	}

}
