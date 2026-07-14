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
package iu.oidc.client.config;

import java.net.URI;
import java.time.Duration;

import edu.iu.crypt.WebKey;

/**
 * Client view of an OIDC registration.
 */
public interface IuOidcClient {
	
	/**
	 * Gets the resource URI.
	 * 
	 * @return resource URI
	 */
	URI getResourceUri();

	/**
	 * Gets the client ID.
	 * 
	 * @return client ID
	 */
	String getClientId();

	/**
	 * Gets the client secret.
	 * 
	 * @return client secret
	 */
	String getClientSecret();

	/**
	 * Gets the resource owner username.
	 * 
	 * @return resource owner username
	 */
	String getUsername();

	/**
	 * Gets the resource owner password.
	 * 
	 * @return resource owner password
	 */
	String getPassword();

	/**
	 * Determines if client secret should sent via the Authorization Basic header.
	 * 
	 * @return true to send client secret as Authorization Basic
	 *         (client_secret_basic); false (default) to send as POST param
	 *         (client_secret_post). Ignored if {@link #getAssertionJwk()} is
	 *         non-null.
	 */
	default boolean isUseBasicAuth() {
		return false;
	}

	/**
	 * Gets {@link WebKey} to use for signing assertions issued on behalf of this
	 * client.
	 * 
	 * @return {@link WebKey}
	 */
	WebKey getAssertionJwk();

	/**
	 * Gets the length of time to issue client assertions for.
	 * 
	 * @return {@link Duration}; default is PT2M
	 */
	default Duration getAssertionTtl() {
		return Duration.ofMinutes(2L);
	}

	/**
	 * Gets the available keys for decrypting tokens for this client.
	 * 
	 * @return {@link WebKey}s for decryption
	 */
	Iterable<WebKey> getDecryptJwk();

	/**
	 * Gets the maximum length of time to allow an ID token issued to this client to
	 * be valid.
	 * 
	 * @return {@link Duration}; default is PT15M
	 */
	default Duration getTokenTtl() {
		return Duration.ofMinutes(15L);
	}

	/**
	 * Gets the maximum length of time to allow since user authentication.
	 * 
	 * @return {@link Duration}; default is PT12H
	 */
	default Duration getMaxAge() {
		return Duration.ofHours(12L);
	}

}
