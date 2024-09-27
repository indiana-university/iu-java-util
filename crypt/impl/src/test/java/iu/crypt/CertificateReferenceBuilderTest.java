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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.crypt.PemEncoded;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class CertificateReferenceBuilderTest {

	private static class Builder extends CertificateReferenceBuilder<Builder> {
		@Override
		protected JsonObject toJson() {
			return super.toJson();
		}
	}

	@Test
	public void testCert() {
		final var builder = new Builder();
		final var uri = URI.create(IdGenerator.generateId());
		builder.cert(uri);
		assertEquals(uri.toString(), builder.toJson().getString("x5u"));
	}

	@Test
	public void testCertChain() {
		final var builder = new Builder();
		final var cert = mock(X509Certificate.class);
		final var encoded = IdGenerator.generateId();
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(IuText.utf8(encoded)));
		builder.cert(cert);
		assertEquals(IuText.base64(IuText.utf8(encoded)), builder.toJson().getJsonArray("x5c").getString(0));
	}

	@Test
	public void testCertThumbprint() {
		final var builder = new Builder();
		final var thumbprint = IuText.utf8(IdGenerator.generateId());
		builder.x5t(thumbprint);
		assertEquals(IuText.base64Url(thumbprint), builder.toJson().getString("x5t"));
	}

	@Test
	public void testCert256Thumbprint() {
		final var builder = new Builder();
		final var thumbprint = IuText.utf8(IdGenerator.generateId());
		builder.x5t256(thumbprint);
		assertEquals(IuText.base64Url(thumbprint), builder.toJson().getString("x5t#S256"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPemInputStream() {
		final var builder = new Builder();
		final var cert = mock(X509Certificate.class);
		final var encoded = IdGenerator.generateId();
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(IuText.utf8(encoded)));
		final var in = mock(InputStream.class);
		final var pemIter = mock(Iterator.class);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.parse(in)).thenReturn(pemIter);
			mockPemEncoded.when(() -> PemEncoded.getCertificateChain(pemIter))
					.thenReturn(new X509Certificate[] { cert });
			builder.pem(in);
			assertEquals(IuText.base64(IuText.utf8(encoded)), builder.toJson().getJsonArray("x5c").getString(0));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPemString() {
		final var builder = new Builder();
		final var cert = mock(X509Certificate.class);
		final var encoded = IdGenerator.generateId();
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(IuText.utf8(encoded)));
		final var pem = IdGenerator.generateId();
		final var pemIter = mock(Iterator.class);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.parse(pem)).thenReturn(pemIter);
			mockPemEncoded.when(() -> PemEncoded.getCertificateChain(pemIter))
					.thenReturn(new X509Certificate[] { cert });
			builder.pem(pem);
			assertEquals(IuText.base64(IuText.utf8(encoded)), builder.toJson().getJsonArray("x5c").getString(0));
		}
	}

}
