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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateEncodingException;

import org.junit.jupiter.api.Test;

import edu.iu.crypt.DigestUtils;
import edu.iu.crypt.IuCryptTestCase;
import edu.iu.crypt.PemEncoded;

@SuppressWarnings("javadoc")
public class CertificateReferenceBuilderTest extends IuCryptTestCase {

	private static class Builder extends CertificateReferenceBuilder<Builder> {
		private JsonCertificateReference<?> build() {
			return new JsonCertificateReference<>(toJson());
		}
	}

	@Test
	public void testCertUri() {
		final var builder = new Builder();
		builder.cert(uri(CERT_TEXT));
		assertEquals(CERT, PemEncoded.getCertificateChain(builder.build().getCertificateUri())[0]);
	}

	@Test
	public void testCertChain() {
		final var builder = new Builder();
		builder.cert(CERT);
		assertThrows(IllegalArgumentException.class, () -> builder.cert(ANOTHER_CERT));
		builder.cert(CERT);
		assertSame(CERT, builder.build().getCertificateChain()[0]);
	}

	@Test
	public void testCertThumbprint() throws CertificateEncodingException {
		final var builder = new Builder();
		builder.x5t(DigestUtils.sha1(CERT.getEncoded()));
		builder.cert(CERT);
		builder.x5t(DigestUtils.sha1(CERT.getEncoded()));
		final var ref = builder.build();
		assertArrayEquals(DigestUtils.sha1(CERT.getEncoded()), ref.getCertificateThumbprint());
		assertSame(CERT, ref.getCertificateChain()[0]);
	}

	@Test
	public void testCertThumbprintUri() throws CertificateEncodingException {
		final var builder = new Builder();
		builder.x5t(DigestUtils.sha1(CERT.getEncoded()));
		builder.cert(uri(CERT_TEXT));
		builder.x5t(DigestUtils.sha1(CERT.getEncoded()));
	}

	@Test
	public void testCert256Thumbprint() throws CertificateEncodingException {
		final var builder = new Builder();
		builder.x5t256(DigestUtils.sha256(CERT.getEncoded()));
		builder.cert(CERT);
		builder.x5t256(DigestUtils.sha256(CERT.getEncoded()));
		final var ref = builder.build();
		assertArrayEquals(DigestUtils.sha256(CERT.getEncoded()), ref.getCertificateSha256Thumbprint());
		assertSame(CERT, ref.getCertificateChain()[0]);
	}

	@Test
	public void testCert256ThumbprintUri() throws CertificateEncodingException {
		final var builder = new Builder();
		builder.x5t256(DigestUtils.sha256(CERT.getEncoded()));
		builder.cert(uri(CERT_TEXT));
		builder.x5t256(DigestUtils.sha256(CERT.getEncoded()));
	}

	@Test
	public void testPemInputStream() {
		assertEquals(CERT,
				new Builder().pem(new ByteArrayInputStream(CERT_TEXT.getBytes())).build().getCertificateChain()[0]);
	}

	@Test
	public void testPemString() {
		assertEquals(CERT, new Builder().pem(CERT_TEXT).build().getCertificateChain()[0]);
	}

}
