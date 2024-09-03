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
package edu.iu.auth.config;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import edu.iu.auth.client.IuAuthorizationAttributeResponse;

/**
 * Provides endpoint-facing authorization client configuration metadata.
 */
public interface IuAuthorizationClient {

	/**
	 * Gets the authentication realm to use with
	 * {@link GrantType#AUTHORIZATION_CODE}.
	 * 
	 * @return redirect URIs
	 */
	String getRealm();

	/**
	 * Gets redirect URIs allowed for this client to use with
	 * {@link GrantType#AUTHORIZATION_CODE}.
	 * 
	 * @return redirect URIs
	 */
	Set<URI> getRedirectUri();

	/**
	 * Gets scopes permitted for use with this client.
	 * 
	 * @return permitted scopes
	 */
	Set<String> getScope();

	/**
	 * Gets attribute release {@link URI}s.
	 * 
	 * @return attribute release {@link URI}s
	 * @see IuAuthorizationAttributeResponse
	 */
	Iterable<URI> getAttributeUris();

	/**
	 * Gets the allowed IP address ranges.
	 * 
	 * @return Set of allowed IP address ranges
	 */
	Set<String> getIpAllow();

	/**
	 * Defines the maximum time to live for assertions issued by this client.
	 * 
	 * @return {@link Duration}
	 */
	Duration getAssertionTtl();

	/**
	 * Gets credentials issued to this client.
	 * 
	 * @return {@link IuAuthorizationCredentials}
	 */
	Iterable<? extends IuAuthorizationCredentials> getCredentials();

}
