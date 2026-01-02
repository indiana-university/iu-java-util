/*
 * Copyright © 2026 Indiana University
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
import java.time.Duration;

import edu.iu.auth.nonce.IuOneTimeNumberConfig;

/**
 * Provides audience configuration.
 */
public interface IuAuthorizedAudience {

	/**
	 * Get the max token lifetime
	 * 
	 * @return {@link Duration}
	 */
	default Duration getTokenTtl() {
		return Duration.ofSeconds(15L);
	};

	/**
	 * Gets the external root resource URI for this audience.
	 * 
	 * @return resource URI
	 */
	URI getResourceUri();

	/**
	 * Gets the token issuer's root resource URI.
	 * 
	 * @return Issuer root resource URI
	 */
	URI getOidcMetadataUri();

	/**
	 * Gets the one-time number configuration for this audience.
	 * 
	 * @return {@link IuOneTimeNumberConfig}
	 */
	IuOneTimeNumberConfig getNonce();

	/**
	 * Gets the principal identity of the audience endpoint, and optionally
	 * additional trusted token issuer keys.
	 * 
	 * @return {@link IuPrivateKeyPrincipal} of the audience entity, optionally
	 *         followed by additional trusted token issuer keys
	 */
	Iterable<IuPrivateKeyPrincipal> getIdentity();

}
