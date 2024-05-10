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
package edu.iu.auth.jwt;

import java.net.URI;

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.pki.IuPkiPrincipal;
import edu.iu.auth.spi.IuJwtSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Represents a JSON Web Key (JWK) principal.
 * 
 * <p>
 * {@link IuPkiPrincipal} is preferred over this principal type when available.
 * </p>
 * 
 * <p>
 * The {@link #toString() JWK serialized form} may be passed to
 * {@link IuPkiPrincipal#from(String)} if the key contains a trusted
 * certificate, or directly to {@link IuWebToken#register(IuPrincipalIdentity)}
 * or {@link IuWebToken#register(String, IuPrincipalIdentity, String)} if the
 * key doesn't include a trusted PKI certificate.
 * </p>
 */
public interface IuWebKey extends IuPrincipalIdentity {

	/**
	 * Reads a public key from a well-known JSON Web Key Set (JWKS).
	 * 
	 * <p>
	 * Public JWK principals are not authoritative, but are available for cases
	 * where a trusted issuer provides a JWKS URI and key ID, but does not include a
	 * valid PKI certificate in the key set.
	 * </p>
	 * 
	 * @param jwksUri Public JWKS {@link URI}
	 * @param keyId   Key identifier (kid JOSE parameter)
	 * @return {@link IuWebKey}
	 */
	static IuWebKey from(URI jwksUri, String keyId) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).getWebKey(jwksUri, keyId);
	}

	/**
	 * Creates a secret key principal.
	 * 
	 * @param name Unique principal name
	 * @param key  Secret key data; <em>must</em> contain at least 128 bits (length
	 *             16) of securely generated psuedo-random data appropriate for the
	 *             encryption and/or signature algorithm.
	 * @return {@link IuWebKey}
	 */
	static IuWebKey from(String name, byte[] key) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).getSecretKey(name, key);
	}

}
