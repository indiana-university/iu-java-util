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
package iu.crypt;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.crypt.PemEncoded;

/**
 * Common base class for JSON web security object builders.
 * 
 * @param <B> builder type
 */
abstract class CertificateReferenceBuilder<B extends CertificateReferenceBuilder<B>> {

	private URI certificateUri;
	private X509Certificate[] certificateChain;
	private byte[] certificateThumbprint;
	private byte[] certificateSha256Thumbprint;

	/**
	 * Next builder reference.
	 * 
	 * @return this
	 */
	abstract protected B next();

	/**
	 * Sets certificate URI
	 * 
	 * @param uri certificate URI
	 * @return this
	 */
	public B cert(URI uri) {
		acceptCertChain(PemEncoded.getCertificateChain(uri));
		this.certificateUri = uri;
		return next();
	}

	/**
	 * Sets certificate chain
	 * 
	 * @param chain certificate chain
	 * @return this
	 */
	public B cert(X509Certificate... chain) {
		acceptCertChain(chain);
		this.certificateChain = chain;
		return next();
	}

	/**
	 * Sets certificate thumbprint
	 * 
	 * @param certificateThumbprint cetiticate thumbprint
	 * @return certificate thumbprint
	 */
	public B x5t(byte[] certificateThumbprint) {
		Objects.requireNonNull(certificateThumbprint);

		final var cert = getCert();
		if (cert != null //
				&& !Arrays.equals(certificateThumbprint, DigestUtils.sha1(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-1 thumbprint mismatch");

		this.certificateThumbprint = certificateThumbprint;
		return next();
	}

	/**
	 * Sets certificate SHA-256 thumbprint
	 * 
	 * @param certificateSha256Thumbprint certificate SHA-256 thumbprint
	 * @return this
	 */
	public B x5t256(byte[] certificateSha256Thumbprint) {
		Objects.requireNonNull(certificateSha256Thumbprint);

		final var cert = getCert();
		if (cert != null //
				&& !Arrays.equals(certificateSha256Thumbprint, DigestUtils.sha256(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-256 thumbprint mismatch");

		this.certificateSha256Thumbprint = certificateSha256Thumbprint;
		return next();
	}

	/**
	 * Verifies set-once behavior and matches thumbprints against encoded checksums.
	 * 
	 * <p>
	 * Does not set the certificate chain.
	 * </p>
	 * 
	 * @param certChain certificate chain
	 */
	protected void acceptCertChain(X509Certificate[] certChain) {
		if (this.certificateChain != null //
				&& !Arrays.equals(certChain, this.certificateChain))
			throw new IllegalStateException("Certificate chain mismatch");

		final var cert = certChain[0];
		if (certificateThumbprint != null //
				&& !Arrays.equals(certificateThumbprint, DigestUtils.sha1(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-1 thumbprint mismatch");
		if (certificateSha256Thumbprint != null //
				&& !Arrays.equals(certificateSha256Thumbprint, DigestUtils.sha256(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-256 thumbprint mismatch");
	}

	/**
	 * Gets certificate URI
	 * 
	 * @return certificate URI
	 */
	URI certificateUri() {
		return certificateUri;
	}

	/**
	 * Gets certificate chain
	 * 
	 * @return certificate chain
	 */
	X509Certificate[] certificateChain() {
		return certificateChain;
	}

	/**
	 * Gets certificate thumbprint
	 * 
	 * @return certificate thumbprint
	 */
	byte[] certificateThumbprint() {
		return certificateThumbprint;
	}

	/**
	 * Gets certificate SHA-256 thumbprint
	 * 
	 * @return certificate SHA-256 thumbprint
	 */
	byte[] certificateSha256Thumbprint() {
		return certificateSha256Thumbprint;
	}

	private X509Certificate getCert() {
		if (this.certificateChain != null)
			return certificateChain[0];
		else if (this.certificateUri != null)
			return PemEncoded.getCertificateChain(certificateUri)[0];
		else
			return null;
	}

}
