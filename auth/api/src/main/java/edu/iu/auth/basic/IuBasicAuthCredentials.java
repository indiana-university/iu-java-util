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
package edu.iu.auth.basic;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.spi.IuBasicAuthSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Represents credentials for use with
 * <a href="https://datatracker.ietf.org/doc/html/rfc7617">HTTP Basic
 * Authentication</a>.
 * 
 * <p>
 * <strong>Basic authentication is not secure</strong> and <em>should not</em>
 * be used when secure options are available for establishing a principal
 * identity. When using {@link IuBasicAuthCredentials} to authenticate remote
 * clients, an expiration policy of no more than 45 days <em>should</em> be
 * used.
 * </p>
 * 
 * <p>
 * Basic authentication <em>must not</em> be used to verify a <strong>user
 * principal</strong>.
 * </p>
 */
public interface IuBasicAuthCredentials extends IuApiCredentials, IuPrincipalIdentity {

	/**
	 * Gets credentials for use with
	 * <a href="https://datatracker.ietf.org/doc/html/rfc7617">HTTP Basic
	 * Authentication</a>.
	 * 
	 * @param username username
	 * @param password password
	 * @return credentials for use with HTTP basic auth
	 */
	static IuBasicAuthCredentials of(String username, String password) {
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
	 * Gets the password.
	 * 
	 * @return password
	 */
	String getPassword();

	/**
	 * Gets the encoding to use with
	 * {@link #applyTo(java.net.http.HttpRequest.Builder)}.
	 * 
	 * @return encoding
	 */
	default String getCharset() {
		return "US-ASCII";
	}

}
