/*
 * Copyright © 2025 Indiana University
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

import java.security.Principal;
import java.time.Instant;

import javax.security.auth.Subject;

import edu.iu.auth.spi.IuPrincipalSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Designates an authenticated principal identity.
 */
public interface IuPrincipalIdentity extends Principal {

	/**
	 * Verifies that a principal identity was issued by a registered identity
	 * provider for an authentication realm.
	 * 
	 * @param id    principal identity
	 * @param realm authentication realm
	 * @return true if verification was successful <em>and</em> the authorization
	 *         module is considered authoritative for the authentication realm;
	 *         false if verification was successful <em>but</em> based solely on
	 *         well-known information about the authentication provider.
	 * @throws IuAuthenticationException If credentials could not be verified
	 */
	static boolean verify(IuPrincipalIdentity id, String realm) throws IuAuthenticationException {
		return IuAuthSpiFactory.get(IuPrincipalSpi.class).verify(id, realm);
	}

	/**
	 * Gets the point in time proof of principal identity was issued by an
	 * authentication provider.
	 * 
	 * @return {@link Instant}
	 */
	Instant getIssuedAt();

	/**
	 * Gets the point in time the principal's credentials were verified by the
	 * authentication provider.
	 * 
	 * @return {@link Instant}
	 */
	Instant getAuthTime();

	/**
	 * Gets the point in time after which the principal's authenticated session is
	 * expired.
	 * 
	 * @return {@link Instant}
	 */
	Instant getExpires();

	/**
	 * Gets a subject including this principal, related principals, and implied
	 * credentials.
	 * 
	 * @return {@link Subject}
	 */
	Subject getSubject();

	@Override
	default boolean implies(Subject subject) {
		return subject == getSubject() || Principal.super.implies(subject);
	}

}
