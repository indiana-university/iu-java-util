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
package edu.iu.auth.oauth;

import java.util.Map;
import java.util.Set;

import edu.iu.auth.IuApiCredentials;

/**
 * Represents a successful response to an {@link IuAuthorizationGrant}.
 */
public interface IuAuthorizationResponse {

	/**
	 * Gets the client ID.
	 * 
	 * @return client ID
	 */
	String getClientId();

	/**
	 * Gets the token type.
	 * 
	 * @return token type
	 */
	String getTokenType();

	/**
	 * Gets the authorized scopes.
	 * 
	 * @return authorized scopes
	 */
	Set<String> getScope();

	/**
	 * Gets additional attributes sent with the initial authorization request.
	 * 
	 * @return attributes request attributes
	 */
	Map<String, ?> getRequestAttributes();

	/**
	 * Gets additional attributes received with the token response.
	 * 
	 * @return attributes token attributes
	 */
	Map<String, ?> getTokenAttributes();

	/**
	 * Gets API credentials established through authorization.
	 * 
	 * <p>
	 * Although the token response providing the credentials <em>must</em> be
	 * verified before this response is created, the client <em>must</em>
	 * independently verify the credentials provided as valid for the authentication
	 * realm.
	 * </p>
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getCredentials();

}
