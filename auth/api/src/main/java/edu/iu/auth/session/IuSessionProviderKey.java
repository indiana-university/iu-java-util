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
package edu.iu.auth.session;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Describes a trusted session provider key.
 */
public interface IuSessionProviderKey {

	/**
	 * Designates key type.
	 */
	enum Type {

		/**
		 * RSA key.
		 */
		RSA,

		/**
		 * NIST P-256 Elliptical Curve.
		 */
		EC_P256,

		/**
		 * NIST P-384 Elliptical Curve.
		 */
		EC_P384,

		/**
		 * NIST P-521 Elliptical Curve.
		 */
		EC_P521;
	}

	/**
	 * Designates key usage.
	 */
	enum Usage {

		/**
		 * Used for signing.
		 */
		SIGN,

		/**
		 * Use for encryption.
		 */
		ENCRYPT;
	}

	/**
	 * Gets the key ID.
	 * 
	 * @return key ID
	 */
	String getId();

	/**
	 * Gets the key type.
	 * 
	 * @return key type
	 */
	Type getType();

	/**
	 * Gets the key usage.
	 * 
	 * @return key usage
	 */
	Usage getUsage();

	/**
	 * Gets the public key.
	 * 
	 * @return public key
	 */
	PublicKey getPublic();

	/**
	 * Gets the private key.
	 * 
	 * @return private key
	 */
	PrivateKey getPrivate();

}
