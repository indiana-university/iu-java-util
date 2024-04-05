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

import java.io.InputStream;
import java.net.URI;
import java.security.cert.X509Certificate;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonBuilder;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCertificateReference;

/**
 * Common base class for JSON web security object builders.
 * 
 * @param <B> builder type
 */
class CertificateReferenceBuilder<B extends CertificateReferenceBuilder<B>> extends IuJsonBuilder<B>
		implements WebCertificateReference.Builder<B> {

	@Override
	public B cert(URI uri) {
		return param("x5u", uri, IuJsonAdapter.of(URI.class));
	}

	@Override
	public B cert(X509Certificate... chain) {
		return param("x5c", chain, IuJsonAdapter.of(X509Certificate[].class, PemEncoded.CERT_JSON));
	}

	@Override
	public B x5t(byte[] certificateThumbprint) {
		return param("x5t", certificateThumbprint, UnpaddedBinary.JSON);
	}

	@Override
	public B x5t256(byte[] certificateSha256Thumbprint) {
		return param("x5t#S256", certificateSha256Thumbprint, UnpaddedBinary.JSON);
	}

	@Override
	public B pem(InputStream pemEncoded) {
		return cert(PemEncoded.getCertificateChain(PemEncoded.parse(pemEncoded)));
	}

	@Override
	public B pem(String pemEncoded) {
		return cert(PemEncoded.getCertificateChain(PemEncoded.parse(pemEncoded)));
	}

}
