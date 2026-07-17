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
package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuDigest;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.PemEncoded;

@SuppressWarnings("javadoc")
public class JsonCertificateReferenceTest {

	@Test
	public void testCert() {
		final var cert = mock(X509Certificate.class);
		final var encoded = IuText.utf8(IdGenerator.generateId());
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.asCertificate(encoded)).thenReturn(cert);
			final var ref = new JsonCertificateReference<>(
					IuJson.object().add("x5c", IuJsonAdapter.of(X509Certificate[].class, CryptJsonAdapters.CERT)
							.toJson(new X509Certificate[] { cert })).build());
			assertEquals(cert, ref.getCertificateChain()[0]);
		}
	}

	@Test
	public void testCertUri() {
		final var cert = mock(X509Certificate.class);
		final var uri = URI.create(IdGenerator.generateId());
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.getCertificateChain(uri)).thenReturn(new X509Certificate[] { cert });
			final var ref = new JsonCertificateReference<>(IuJson.object().add("x5u", uri.toString()).build());
			assertNull(ref.getCertificateChain());
			assertEquals(cert, ref.verifiedCertificateChain()[0]);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked", "deprecation" })
	@Test
	public void testEqualsHashCode() {
		final var cert = mock(X509Certificate.class);
		final var encoded = IuText.utf8(IdGenerator.generateId());
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));
		final var uri = URI.create(IdGenerator.generateId());
		final var thumbprint = IuDigest.sha1(encoded);
		final var thumbprint256 = IuDigest.sha256(encoded);
		final var a = IuJson.object() //
				.add("x5c", IuJson.array().add(IuText.base64(encoded))) //
				.add("x5u", uri.toString()) //
				.add("x5t", IuText.base64Url(thumbprint)) //
				.add("x5t#S256", IuText.base64Url(thumbprint256)) //
				.build();

		final var bcert = mock(X509Certificate.class);
		final var bencoded = IuText.utf8(IdGenerator.generateId());
		assertDoesNotThrow(() -> when(bcert.getEncoded()).thenReturn(bencoded));
		final var buri = URI.create(IdGenerator.generateId());
		final var bthumbprint = IuDigest.sha1(bencoded);
		final var bthumbprint256 = IuDigest.sha256(bencoded);
		final var b = IuJson.object() //
				.add("x5c", IuJson.array().add(IuText.base64(bencoded))) //
				.add("x5u", buri.toString()) //
				.add("x5t", IuText.base64Url(bthumbprint)) //
				.add("x5t#S256", IuText.base64Url(bthumbprint256)) //
				.build();

		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.asCertificate(encoded)).thenReturn(cert);
			mockPemEncoded.when(() -> PemEncoded.asCertificate(bencoded)).thenReturn(bcert);
			final var ao = new JsonCertificateReference(a);
			final var bo = new JsonCertificateReference(b);
			assertNotEquals(ao, null);
			assertNotEquals(ao.hashCode(), bo.hashCode());

			for (var i = 1; i < 16; i++)
				for (var j = 1; j < 16; j++) {
					final var ai = IuJson.object();
					if ((i & 1) == 1)
						ai.add("x5c", a.get("x5c"));
					if ((i & 2) == 2)
						ai.add("x5u", a.get("x5u"));
					if ((i & 4) == 4)
						ai.add("x5t", a.get("x5t"));
					if ((i & 8) == 8)
						ai.add("x5t#S256", a.get("x5t#S256"));
					final var ac = new JsonCertificateReference(ai.build());

					final var bj = IuJson.object();
					if ((j & 1) == 1)
						bj.add("x5c", b.get("x5c"));
					if ((j & 2) == 2)
						bj.add("x5u", b.get("x5u"));
					if ((j & 4) == 4)
						bj.add("x5t", b.get("x5t"));
					if ((j & 8) == 8)
						bj.add("x5t#S256", b.get("x5t#S256"));
					final var bc = new JsonCertificateReference(bj.build());

					assertEquals(ac, new JsonCertificateReference(IuJson.parse(ac.toString()).asJsonObject()));
					assertEquals(bc, new JsonCertificateReference(IuJson.parse(bc.toString()).asJsonObject()));
					assertNotEquals(ac, bc);
					assertNotEquals(bc, ac);
					assertTrue(ac.represents(ao));
					assertTrue(bc.represents(bo));
					assertEquals(ac.represents(bc), bc.represents(ac));
				}
		}
	}

}
