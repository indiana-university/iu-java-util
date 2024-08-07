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
package iu.auth.config;

import java.security.cert.X509Certificate;

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPrivateKeyPrincipal;

/**
 * Represents a configured private key principal trusted as a token issuer.
 */
public interface IuTrustedIssuer extends IuAuthConfig {

	/**
	 * Gets a verifiable {@link IuPrincipalIdentity} that corresponds to a
	 * configured private key principal, if the private key was registered as
	 * trusted.
	 * 
	 * <p>
	 * If the private key is held locally by the incoming config, the principal
	 * returned by this method will verify as authoritative. If a
	 * {@link X509Certificate certificate} in the private key's well-known
	 * certificate chain is held, but not the private key itself, the principal
	 * returned will verify as non-authoritative.
	 * </p>
	 * 
	 * @param privateKeyPrincipal private key principal configuration
	 * @return Verifiable {@link IuPrincipalIdentity} if trusted; else null
	 */
	IuPrincipalIdentity getPrincipal(IuPrivateKeyPrincipal privateKeyPrincipal);

}
