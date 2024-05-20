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
import edu.iu.auth.config.AuthConfig;
import edu.iu.auth.spi.IuPrincipalSpi;

/**
 * Provides identity verification support for internal implementation modules.
 */
public class PrincipalVerifierRegistry implements IuPrincipalSpi {

	/**
	 * Determines if the indicated realm has been registered as authoritative for
	 * the local node.
	 * 
	 * @param realm authentication realm
	 * @return true of the local node registered as authoritative for the realm
	 */
	public static boolean isAuthoritative(String realm) {
		try {
			return AuthConfig.<PrincipalVerifier<?>>get(realm).isAuthoritative();
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Default constructor.
	 */
	public PrincipalVerifierRegistry() {
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public boolean verify(IuPrincipalIdentity id, String realm) throws IuAuthenticationException {
		final PrincipalVerifier verifier = AuthConfig.get(realm);
		verifier.verify(id);
		return verifier.isAuthoritative();
	}

}
