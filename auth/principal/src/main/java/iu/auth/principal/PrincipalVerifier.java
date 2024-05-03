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
package iu.auth.principal;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;

/**
 * Verifies a principal as valid for a realm.
 * 
 * @param <I> principal identity type
 */
public interface PrincipalVerifier<I extends IuPrincipalIdentity> {

	/**
	 * Gets the identity type.
	 * 
	 * @return identity type; must be a final implementation class
	 */
	Class<I> getType();

	/**
	 * Gets the authentication realm.
	 * 
	 * @return authentication realm
	 */
	String getRealm();

	/**
	 * Determines if this verifier is authoritative for the realm.
	 * 
	 * @return true if the identity principal is managed by the authorization
	 *         module, or verifiable through an established trust relationship with
	 *         a remote authentication provider; false verification is based solely
	 *         on an implicit trust relationship based on well-known information
	 *         about the authentication provider.
	 */
	boolean isAuthoritative();

	/**
	 * Verifies a principal identity.
	 * 
	 * @param id    principal identity
	 * @param realm authentication realm
	 * @throws IuAuthenticationException If the principal could not be verified
	 */
	void verify(I id, String realm) throws IuAuthenticationException;

}
