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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

import edu.iu.IdGenerator;
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
 * principal<strong>.
 * <p>
 */
public interface IuBasicAuthCredentials extends IuApiCredentials, IuPrincipalIdentity {

	/**
	 * Registers Basic authentication principals for verifying external OAuth 2
	 * client credentials.
	 * 
	 * <p>
	 * Client secret values provided via {@link #getPassword()} <em>must</em> be
	 * printable ASCII, at least 12 characters in length. Implementations
	 * <em>should</em> use {@link IdGenerator#generateId()} to create passwords.
	 * <p>
	 * 
	 * <p>
	 * {@link IuBasicAuthCredentials#getNotBefore()} and {@link #getExpires()}
	 * <em>must</em> be non-null for all entries. Entries <em>may</em> be expired;
	 * expired entries <em>may</em> be changed. <em>May</em> include multiple
	 * entries with the same name but different passwords and expiration times.
	 * </p>
	 * 
	 * <p>
	 * This method <em>may</em> be called no more than once per realm.
	 * </p>
	 * 
	 * <p>
	 * <em>Implementation Note:</em> The {@link Iterable} provided to this method is
	 * controlled externally. {@link Iterable#iterator()} is invoked each time an
	 * {@link IuBasicAuthCredentials} principal is verified to discover externally
	 * controlled metadata
	 * </p>
	 * 
	 * @param clientCredentials Basic authentication client credential principals
	 * @param realm             Authentication realm
	 * @param expirationPolicy  Maximum length of time to allow passwords to remain
	 *                          valid
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1">OAuth 2.0
	 *      Client Password</a>
	 * @see <a href=
	 *      "https://github.com/OWASP/ASVS/raw/v4.0.3/4.0/OWASP%20Application%20Security%20Verification%20Standard%204.0.3-en.pdf">ASVS
	 *      4.0: 2.1 and 2.4</a>
	 */
	static void registerClientCredentials(Iterable<? extends IuBasicAuthCredentials> clientCredentials, String realm,
			TemporalAmount expirationPolicy) {
		IuAuthSpiFactory.get(IuBasicAuthSpi.class).register(clientCredentials, realm, expirationPolicy);
	}

	/**
	 * <em>Optional</em> time before which the password <em>should</em> be
	 * considered invalid.
	 * 
	 * @return expiration time
	 */
	Instant getNotBefore();

	/**
	 * <em>Optional</em> time after which the password <em>should</em> be considered
	 * invalid.
	 * 
	 * @return expiration time
	 */
	Instant getExpires();

	/**
	 * Gets the password.
	 * 
	 * @return password
	 */
	String getPassword();

}
