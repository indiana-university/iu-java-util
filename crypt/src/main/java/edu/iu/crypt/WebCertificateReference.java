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
package edu.iu.crypt;

import java.net.URI;
import java.security.cert.X509Certificate;

/**
 * Common super-interface for components that hold a reference to a web
 * certificate and/or chain.
 */
public interface WebCertificateReference {

	/**
	 * Gets the URI where X.509 certificate associated with this key can be
	 * retrieved.
	 * 
	 * <p>
	 * The protocol used to acquire the resource MUST provide integrity protection;
	 * an HTTP GET request to retrieve the certificate MUST use TLS [RFC2818]
	 * [RFC5246]; the identity of the server MUST be validated, as per Section 6 of
	 * RFC 6125 [RFC6125].
	 * </p>
	 * 
	 * @return {@link URI}
	 */
	URI getCertificateUri();

	/**
	 * Gets the certificate chain.
	 * 
	 * @return parsed JSON x5c attribute value
	 */
	X509Certificate[] getCertificateChain();

	/**
	 * Gets the certificate thumbprint.
	 * 
	 * @return JSON x5t attribute value
	 */
	byte[] getCertificateThumbprint();

	/**
	 * Gets the certificate SHA-256 thumbprint.
	 * 
	 * @return JSON x5t#S256 attribute value
	 */
	byte[] getCertificateSha256Thumbprint();

}
