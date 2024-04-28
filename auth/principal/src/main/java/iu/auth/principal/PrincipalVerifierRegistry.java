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

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.spi.IuPrincipalSpi;

/**
 * Provides identity verification support for internal implementation modules.
 */
public class PrincipalVerifierRegistry implements IuPrincipalSpi {

	private static Map<String, PrincipalVerifier<?>> VERIFIERS = new HashMap<>();

	/**
	 * Determines if a class is a final implementation class.
	 * 
	 * @param type type
	 * @return true if the class is not an interface and includes the final modifier
	 */
	static Class<?> requireFinalImpl(Class<?> type) {
		if (type.isInterface() //
				|| (type.getModifiers() & Modifier.FINAL) == 0)
			throw new IllegalArgumentException("must be a final implementation class: " + type);
		return type;
	}

	/**
	 * Determines if the indicated realm has been registered as authoritative for
	 * the local node.
	 * 
	 * @param realm authentication realm
	 * @return true of the local node registered as authoritative for the realm
	 */
	public static boolean isAuthoritative(String realm) {
		return VERIFIERS.containsKey(realm) //
				&& VERIFIERS.get(realm).isAuthoritative();
	}

	/**
	 * Registers a principal identity verifier for an authentication realm.
	 * 
	 * <p>
	 * Only one verifier may be registered per realm
	 * </p>
	 * 
	 * @param verifier principal identity verifier
	 */
	public static synchronized void registerVerifier(PrincipalVerifier<?> verifier) {
		requireFinalImpl(verifier.getClass());
		requireFinalImpl(verifier.getType());

		final var realm = verifier.getRealm();
		if (VERIFIERS.containsKey(realm))
			throw new IllegalStateException("verifier already registered for " + realm);

		VERIFIERS.put(realm, verifier);
	}

	/**
	 * Default constructor.
	 */
	public PrincipalVerifierRegistry() {
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public boolean verify(IuPrincipalIdentity id, String realm) throws IuAuthenticationException {
		final PrincipalVerifier verifier = Objects.requireNonNull(VERIFIERS.get(realm), "missing verifier for realm");
		verifier.verify(id, realm);
		return verifier.isAuthoritative();
	}

}
