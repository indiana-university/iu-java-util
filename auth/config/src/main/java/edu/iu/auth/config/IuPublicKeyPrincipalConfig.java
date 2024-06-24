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

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.crypt.WebKey;
import iu.auth.config.IuAuthConfig;

/**
 * Describes an authorization configuration that describes a public key or
 * public/private key pair, for use with digital signature creation and
 * validation, encryption, and decryption.
 */
public interface IuPublicKeyPrincipalConfig extends IuAuthConfig {

	/**
	 * Gets the identity key, including private and/or secret key data if
	 * authoritative.
	 * 
	 * @return authoritative identity key; null if not authoritative: only a public
	 *         key or X.509 certificate is configured
	 */
	IuPrincipalIdentity getIdentity();

	/**
	 * Gets the digital signature verification and/or creation key.
	 * 
	 * @return signature key
	 */
	WebKey getSignatureKey();

	/**
	 * Gets the encryption key.
	 * 
	 * @return encryption key
	 */
	WebKey getEncryptKey();

}
