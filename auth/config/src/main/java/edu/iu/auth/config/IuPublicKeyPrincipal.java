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

import java.net.URI;

import edu.iu.crypt.WebKey;

/**
 * Configures a {@link URI} reference to a well-known public key, or other key
 * configured via environment URI.
 */
public interface IuPublicKeyPrincipal extends IuWebKeyPrincipal {

	/**
	 * Gets the key ID relative to the JWKS key at {@link #getJku()} of the
	 * signature verification or encryption key.
	 * 
	 * @return Key ID
	 */
	String getKid();

	/**
	 * Gets the reference URI for looking up the signature or verification key.
	 *
	 * <p>
	 * If {@link #getKid() kid} is also set, this {@link URI} MUST refer to a
	 * {@link WebKey#readJwks(URI)} JWKS key set; else it refers to a single
	 * {@link WebKey#parse(String) JWK}.
	 * </p>
	 * 
	 * <p>
	 * Note: http/https URIs SHOULD only be used for looking up well-known public
	 * key data.
	 * </p>
	 * 
	 * @return JWK or JWKS {@link URI}
	 */
	URI getJku();

}
