/*
 * Copyright © 2025 Indiana University
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

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebKey;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * Encapsulates JSON properties that refer to or verify an X.509 certificate
 * chain.
 * 
 * @param <R> reference type
 */
class JsonCertificateReference<R extends JsonCertificateReference<R>> implements WebCertificateReference {

	private final URI certificateUri;
	private final X509Certificate[] certificateChain;
	private final byte[] certificateThumbprint;
	private final byte[] certificateSha256Thumbprint;
	private final X509Certificate[] verifiedCertificateChain;

	/**
	 * Constructor.
	 * 
	 * @param certRef {@link JsonObject}
	 */
	JsonCertificateReference(JsonValue certRef) {
		final var jwk = certRef.asJsonObject();
		certificateUri = IuJson.get(jwk, "x5u", IuJsonAdapter.of(URI.class));
		certificateChain = IuJson.get(jwk, "x5c", IuJsonAdapter.of(X509Certificate[].class, CryptJsonAdapters.CERT));
		certificateThumbprint = IuJson.get(jwk, "x5t", CryptJsonAdapters.B64URL);
		certificateSha256Thumbprint = IuJson.get(jwk, "x5t#S256", CryptJsonAdapters.B64URL);
		verifiedCertificateChain = WebCertificateReference.verify(this);
	}

	@Override
	public URI getCertificateUri() {
		return certificateUri;
	}

	@Override
	public X509Certificate[] getCertificateChain() {
		return certificateChain;
	}

	@Override
	public byte[] getCertificateThumbprint() {
		return certificateThumbprint;
	}

	@Override
	public byte[] getCertificateSha256Thumbprint() {
		return certificateSha256Thumbprint;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(certificateUri, certificateChain, certificateThumbprint, certificateSha256Thumbprint);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		JsonCertificateReference<?> other = (JsonCertificateReference<?>) obj;
		return IuObject.equals(certificateChain, other.certificateChain)
				&& IuObject.equals(certificateSha256Thumbprint, other.certificateSha256Thumbprint)
				&& IuObject.equals(certificateThumbprint, other.certificateThumbprint)
				&& IuObject.equals(certificateUri, other.certificateUri);
	}

	@Override
	public String toString() {
		final var jwkBuilder = IuJson.object();
		serializeTo(jwkBuilder);
		return jwkBuilder.build().toString();
	}

	/**
	 * Gets the verified certificate chain resolved by this reference.
	 * 
	 * @return Array of {@link X509Certificate}; verified as consistent with the
	 *         other parameters in this reference, at minimum
	 *         {@link WebCertificateReference#verify(WebCertificateReference)}
	 */
	X509Certificate[] verifiedCertificateChain() {
		return verifiedCertificateChain;
	}

	/**
	 * Adds serialized JWK attributes to a JSON object builder.
	 * 
	 * @param jwkBuilder {@link JsonObjectBuilder}
	 * @return jwkBuilder
	 */
	JsonObjectBuilder serializeTo(JsonObjectBuilder jwkBuilder) {
		IuJson.add(jwkBuilder, "x5u", () -> certificateUri, IuJsonAdapter.of(URI.class));
		IuJson.add(jwkBuilder, "x5c", () -> certificateChain,
				IuJsonAdapter.of(X509Certificate[].class, CryptJsonAdapters.CERT));
		IuJson.add(jwkBuilder, "x5t", () -> certificateThumbprint, CryptJsonAdapters.B64URL);
		IuJson.add(jwkBuilder, "x5t#S256", () -> certificateSha256Thumbprint, CryptJsonAdapters.B64URL);
		return jwkBuilder;
	}

	/**
	 * Determines whether or not the known components of this key match the known
	 * components of another key.
	 * 
	 * @param key {@link WebKey}
	 * @return true if all non-null components of both keys match
	 */
	boolean represents(R key) {
		final var ref = (JsonCertificateReference<R>) key;
		return IuObject.represents(certificateChain, ref.certificateChain)
				&& IuObject.represents(certificateSha256Thumbprint, ref.certificateSha256Thumbprint)
				&& IuObject.represents(certificateThumbprint, ref.certificateThumbprint)
				&& IuObject.represents(certificateUri, ref.certificateUri);
	}

}
