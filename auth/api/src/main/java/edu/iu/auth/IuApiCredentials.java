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
package edu.iu.auth;

import java.net.http.HttpRequest;
import java.security.Principal;

import edu.iu.auth.basic.IuBasicAuthCredentials;
import edu.iu.auth.spi.IuBasicAuthSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * {@link Principal} implementation for an HTTP-authenticated user or client
 * application.
 */
public interface IuApiCredentials extends IuPrincipalIdentity {

	/**
	 * Gets credentials for use with
	 * <a href="https://datatracker.ietf.org/doc/html/rfc7617">HTTP Basic
	 * Authentication</a>.
	 * 
	 * @param username username
	 * @param password password
	 * @return credentials for use with HTTP basic auth
	 */
	static IuBasicAuthCredentials basic(String username, String password) {
		return basic(username, password, "US-ASCII");
	}

	/**
	 * Gets credentials for use with
	 * <a href="https://datatracker.ietf.org/doc/html/rfc7617">HTTP Basic
	 * Authentication</a>.
	 * 
	 * @param username username
	 * @param password password
	 * @param charset  charset to use with
	 *                 {@link #applyTo(java.net.http.HttpRequest.Builder)}
	 * @return credentials for use with HTTP basic auth
	 */
	static IuBasicAuthCredentials basic(String username, String password, String charset) {
		return IuAuthSpiFactory.get(IuBasicAuthSpi.class).createCredentials(username, password, charset);
	}

	/**
	 * Applies the client's API credentials to an HTTP request.
	 * 
	 * @param httpRequestBuilder {@link HttpRequest.Builder}
	 * @throws IuAuthenticationException If authenticated credentials have expired
	 *                                   or been revoked since initial authorization
	 */
	void applyTo(HttpRequest.Builder httpRequestBuilder) throws IuAuthenticationException;

}
