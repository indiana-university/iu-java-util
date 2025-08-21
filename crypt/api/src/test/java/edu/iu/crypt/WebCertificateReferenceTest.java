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
package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IuDigest;

@SuppressWarnings("javadoc")
public class WebCertificateReferenceTest {

	@Test
	public void testDefaults() {
		final var ref = mock(WebCertificateReference.class, CALLS_REAL_METHODS);
		assertNull(ref.getCertificateChain());
		assertNull(ref.getCertificateThumbprint());
		assertNull(ref.getCertificateSha256Thumbprint());
		assertNull(ref.getCertificateUri());
	}

	@Test
	public void testNull() {
		final var ref = mock(WebCertificateReference.class);
		assertNull(WebCertificateReference.verify(ref));
	}

	@Test
	public void testEmpty() {
		final var ref = mock(WebCertificateReference.class);
		when(ref.getCertificateChain()).thenReturn(new X509Certificate[0]);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebCertificateReference.verify(ref));
		assertEquals("At least one certificate is required", error.getMessage());
	}

	@Test
	public void testSha1Mismatch() {
		final var encoded = new byte[16];
		ThreadLocalRandom.current().nextBytes(encoded);
		final var cert = mock(X509Certificate.class);
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));

		final var wrongThumbprint = new byte[16];
		ThreadLocalRandom.current().nextBytes(wrongThumbprint);

		final var ref = mock(WebCertificateReference.class);
		when(ref.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		when(ref.getCertificateThumbprint()).thenReturn(wrongThumbprint);

		final var error = assertThrows(IllegalArgumentException.class, () -> WebCertificateReference.verify(ref));
		assertEquals("Certificate SHA-1 thumbprint mismatch", error.getMessage());
	}

	@Test
	public void testSha256Mismatch() {
		final var encoded = new byte[16];
		ThreadLocalRandom.current().nextBytes(encoded);
		final var cert = mock(X509Certificate.class);
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));

		final var wrongThumbprint = new byte[16];
		ThreadLocalRandom.current().nextBytes(wrongThumbprint);

		final var ref = mock(WebCertificateReference.class);
		when(ref.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		when(ref.getCertificateSha256Thumbprint()).thenReturn(wrongThumbprint);

		final var error = assertThrows(IllegalArgumentException.class, () -> WebCertificateReference.verify(ref));
		assertEquals("Certificate SHA-256 thumbprint mismatch", error.getMessage());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testShaMatch() {
		final var encoded = new byte[16];
		ThreadLocalRandom.current().nextBytes(encoded);
		final var cert = mock(X509Certificate.class);
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));

		final var ref = mock(WebCertificateReference.class);
		when(ref.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		when(ref.getCertificateThumbprint()).thenReturn(IuDigest.sha1(encoded));
		when(ref.getCertificateSha256Thumbprint()).thenReturn(IuDigest.sha256(encoded));

		assertDoesNotThrow(() -> WebCertificateReference.verify(ref));
	}

	@Test
	public void testByUri() {
		final var certificateUri = mock(URI.class);
		final var cert = mock(X509Certificate.class);
		final var ref = mock(WebCertificateReference.class);
		when(ref.getCertificateUri()).thenReturn(certificateUri);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.getCertificateChain(certificateUri))
					.thenReturn(new X509Certificate[] { cert });
			assertDoesNotThrow(() -> WebCertificateReference.verify(ref));
		}
	}

}