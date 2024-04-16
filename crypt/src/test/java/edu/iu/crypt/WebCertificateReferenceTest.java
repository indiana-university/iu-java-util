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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class WebCertificateReferenceTest extends IuCryptTestCase {

	@Test
	public void testNull() {
		assertNull(WebCertificateReference.verify(new WebCertificateReference() {
		}));
	}

	@Test
	public void testUri() {
		assertArrayEquals(new X509Certificate[] { CERT }, WebCertificateReference.verify(new WebCertificateReference() {
			@Override
			public URI getCertificateUri() {
				return uri(CERT_TEXT);
			}
		}));
	}

	@Test
	public void testEmpty() {
		assertThrows(IllegalArgumentException.class,
				() -> WebCertificateReference.verify(new WebCertificateReference() {
					@Override
					public X509Certificate[] getCertificateChain() {
						return new X509Certificate[0];
					}
				}));
	}

	@Test
	public void testSha1Mismatch() {
		assertThrows(IllegalArgumentException.class,
				() -> WebCertificateReference.verify(new WebCertificateReference() {
					@Override
					public X509Certificate[] getCertificateChain() {
						return new X509Certificate[] { ANOTHER_CERT };
					}

					@Override
					public byte[] getCertificateThumbprint() {
						return CERT_S1;
					}
				}));
	}

	@Test
	public void testSha1Match() {
		assertArrayEquals(new X509Certificate[] { CERT }, WebCertificateReference.verify(new WebCertificateReference() {
			@Override
			public X509Certificate[] getCertificateChain() {
				return new X509Certificate[] { CERT };
			}

			@Override
			public byte[] getCertificateThumbprint() {
				return CERT_S1;
			}
		}));
	}

	@Test
	public void testSha256Mismatch() {
		assertThrows(IllegalArgumentException.class,
				() -> WebCertificateReference.verify(new WebCertificateReference() {
					@Override
					public X509Certificate[] getCertificateChain() {
						return new X509Certificate[] { ANOTHER_CERT };
					}

					@Override
					public byte[] getCertificateSha256Thumbprint() {
						return CERT_S256;
					}
				}));
	}

	@Test
	public void testSha256Match() {
		assertArrayEquals(new X509Certificate[] { CERT }, WebCertificateReference.verify(new WebCertificateReference() {
			@Override
			public X509Certificate[] getCertificateChain() {
				return new X509Certificate[] { CERT };
			}

			@Override
			public byte[] getCertificateSha256Thumbprint() {
				return CERT_S256;
			}
		}));
	}
}
