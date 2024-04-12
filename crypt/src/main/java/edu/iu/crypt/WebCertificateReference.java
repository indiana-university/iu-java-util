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

import java.io.InputStream;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.X509TrustManager;

import edu.iu.IuException;
import iu.crypt.DigestUtils;

/**
 * Common super-interface for components that hold a reference to a web
 * certificate and/or chain.
 */
public interface WebCertificateReference {

	/**
	 * Defines basic verification rules for objects that define a certificate
	 * reference.
	 * 
	 * <ul>
	 * <li>Hard reference to cert chain is used if provided; URI is ignored</li>
	 * <li>URI is referenced and parsed if provided, and hard reference is not</li>
	 * <li>SHA-1 and SHA-256 are verified against the first cert found either by
	 * hard reference or URI</li>
	 * </ul>
	 * 
	 * <p>
	 * Further verification, i.e., via {@link X509TrustManager}, is not handled by
	 * this library and <em>should</em> be handled according to the application's
	 * trust configuration.
	 * </p>
	 * 
	 * @param reference certificate reference
	 * @return resolved and verified {@link X509Certificate} chain, null if not
	 *         populated
	 */
	static X509Certificate[] verify(WebCertificateReference reference) {
		var certificateChain = reference.getCertificateChain();
		if (certificateChain == null) {
			final var certificateUri = reference.getCertificateUri();
			if (certificateUri != null)
				certificateChain = PemEncoded.getCertificateChain(certificateUri);
		}

		if (certificateChain != null) {
			if (certificateChain.length < 1)
				throw new IllegalArgumentException("At least one certificate is required");
			final var cert = certificateChain[0];

			final var certificateThumbprint = reference.getCertificateThumbprint();
			if (certificateThumbprint != null //
					&& !Arrays.equals(certificateThumbprint,
							IuException.unchecked(() -> DigestUtils.sha1(cert.getEncoded()))))
				throw new IllegalArgumentException("Certificate SHA-1 thumbprint mismatch");

			final var certificateSha256Thumbprint = reference.getCertificateSha256Thumbprint();
			if (certificateSha256Thumbprint != null //
					&& !Arrays.equals(certificateSha256Thumbprint,
							IuException.unchecked(() -> DigestUtils.sha256(cert.getEncoded()))))
				throw new IllegalArgumentException("Certificate SHA-256 thumbprint mismatch");
		}

		return certificateChain;
	}

	/**
	 * Builder interface for creating {@link WebCertificateReference} instances.
	 * 
	 * @param <B> builder type
	 */
	interface Builder<B extends Builder<B>> {
		/**
		 * Sets the URI where X.509 certificate associated with this key can be
		 * retrieved.
		 * 
		 * <p>
		 * The URI will be validated and resolved when this method is invoked. To ensure
		 * dependency on a remote URI won't impact application startup, always store
		 * certificates locally and use {@link #cert(X509Certificate...)} instead of
		 * this method for critical initialization in production environments.
		 * </p>
		 * 
		 * @param uri {@link URI}
		 * @return this
		 */
		B cert(URI uri);

		/**
		 * Sets the URI where X.509 certificate associated with this key can be
		 * retrieved.
		 * 
		 * @param chain one or more {@link X509Certificate}s
		 * @return this
		 */
		B cert(X509Certificate... chain);

		/**
		 * Sets the certificate thumbprint.
		 * 
		 * @param certificateThumbprint JSON x5t attribute value
		 * @return this
		 */
		B x5t(byte[] certificateThumbprint);

		/**
		 * Sets the certificate SHA-256 thumbprint.
		 * 
		 * @param certificateSha256Thumbprint JSON x5t attribute value
		 * @return this
		 */
		B x5t256(byte[] certificateSha256Thumbprint);

		/**
		 * Sets key data from potentially concatenated PEM-encoded input.
		 * 
		 * @param pemEncoded {@link InputStream} of PEM encoded key data, potentially
		 *                   concatenated
		 * @return this
		 */
		B pem(InputStream pemEncoded);

		/**
		 * Sets key data from potentially concatenated PEM-encoded input.
		 * 
		 * @param pemEncoded potentially concatenated PEM encoded key data
		 * @return this
		 */
		B pem(String pemEncoded);
	}

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
	default URI getCertificateUri() {
		return null;
	}

	/**
	 * Gets the certificate chain.
	 * 
	 * @return parsed JSON x5c attribute value
	 */
	default X509Certificate[] getCertificateChain() {
		return null;
	}

	/**
	 * Gets the certificate thumbprint.
	 * 
	 * @return JSON x5t attribute value
	 */
	default byte[] getCertificateThumbprint() {
		return null;
	}

	/**
	 * Gets the certificate SHA-256 thumbprint.
	 * 
	 * @return JSON x5t#S256 attribute value
	 */
	default byte[] getCertificateSha256Thumbprint() {
		return null;
	}

}
