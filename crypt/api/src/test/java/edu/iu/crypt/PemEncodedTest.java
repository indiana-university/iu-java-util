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
package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction.Context;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.crypt.PemEncoded.KeyType;

@SuppressWarnings("javadoc")
public class PemEncodedTest extends IuCryptApiTestCase {

	@Test
	public void testInvalid() {
		final var i = PemEncoded.parse("_-$" + IdGenerator.generateId());
		assertTrue(i.hasNext());
		assertThrows(IllegalArgumentException.class, () -> i.next());
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, () -> i.next());
		assertFalse(i.hasNext());
	}

	@Test
	public void testInvalidSecondEntry() {
		final var i = PemEncoded
				.parse("-----BEGIN CERTIFICATE-----\n-----END CERTIFICATE-----\n" + IdGenerator.generateId());
		assertTrue(i.hasNext());
		i.next();
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, () -> i.next());
	}

	@Test
	public void testInvalidType() {
		final var i = PemEncoded.parse("-----BEGIN INVALID DATA-----\n-----END INVALID DATA-----\n");
		assertTrue(i.hasNext());
		assertThrows(IllegalArgumentException.class, () -> i.next());
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, () -> i.next());
		assertFalse(i.hasNext());
	}

	@Test
	public void testMissingEnd() {
		final var i = PemEncoded.parse("-----BEGIN PUBLIC KEY-----\n");
		assertTrue(i.hasNext());
		assertThrows(IllegalArgumentException.class, () -> i.next());
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, () -> i.next());
		assertFalse(i.hasNext());
	}

	@Test
	public void testParseInputStream() {
		try (final var mockPemEncoded = mockStatic(PemEncoded.class);
				final var mockStream = mockStatic(IuStream.class)) {
			final var in = mock(InputStream.class);
			final var encoded = IdGenerator.generateId();
			mockStream.when(() -> IuStream.read(in)).thenReturn(IuText.utf8(encoded));
			mockPemEncoded.when(() -> PemEncoded.parse(in)).thenCallRealMethod();

			PemEncoded.parse(in);
			mockPemEncoded.verify(() -> PemEncoded.parse(encoded));
		}
	}

	@Test
	public void testParseTwoElements() {
		final var e1 = IdGenerator.generateId();
		final var e2 = IdGenerator.generateId();
		final var encoded = "-----BEGIN CERTIFICATE-----\n" + IuText.base64(IuText.utf8(e1))
				+ "\n-----END CERTIFICATE-----\n-----BEGIN CERTIFICATE-----\n" + IuText.base64(IuText.utf8(e2))
				+ "\n-----END CERTIFICATE-----\n";
		final var pem = PemEncoded.parse(encoded);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			assertTrue(pem.hasNext());
			final var p1 = pem.next();
			assertEquals(KeyType.CERTIFICATE, p1.getKeyType());
			assertThrows(IllegalStateException.class, p1::asCRL);
			assertThrows(IllegalStateException.class, () -> p1.asPrivate(null));
			assertThrows(IllegalStateException.class, () -> p1.asPublic(null));
			assertEquals(
					"-----BEGIN CERTIFICATE-----\n" + IuText.base64(IuText.utf8(e1)) + "\n-----END CERTIFICATE-----\n",
					p1.toString());
			p1.asCertificate();
			mockPemEncoded.verify(() -> PemEncoded.asCertificate(IuText.utf8(e1)));
			assertTrue(pem.hasNext());
			final var p2 = pem.next();
			p2.asCertificate();
			mockPemEncoded.verify(() -> PemEncoded.asCertificate(IuText.utf8(e2)));
		}
	}

	@Test
	public void testParseCrl() {
		final var e1 = IdGenerator.generateId();
		final var encoded = "-----BEGIN X509 CRL-----\n" + IuText.base64(IuText.utf8(e1))
				+ "\n-----END X509 CRL-----\n";
		final var pem = PemEncoded.parse(encoded);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			assertTrue(pem.hasNext());
			final var p1 = pem.next();
			assertEquals(KeyType.X509_CRL, p1.getKeyType());
			assertThrows(IllegalStateException.class, p1::asCertificate);
			assertThrows(IllegalStateException.class, () -> p1.asPrivate(null));
			assertThrows(IllegalStateException.class, () -> p1.asPublic(null));
			assertEquals("-----BEGIN X509 CRL-----\n" + IuText.base64(IuText.utf8(e1)) + "\n-----END X509 CRL-----\n",
					p1.toString());
			p1.asCRL();
			mockPemEncoded.verify(() -> PemEncoded.asCRL(IuText.utf8(e1)));
		}
	}

	@Test
	public void testPublicKey() {
		final var e1 = IdGenerator.generateId();
		final var encoded = "-----BEGIN PUBLIC KEY-----\n" + IuText.base64(IuText.utf8(e1))
				+ "\n-----END PUBLIC KEY-----\n";
		final var pem = PemEncoded.parse(encoded);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			assertTrue(pem.hasNext());
			final var p1 = pem.next();
			assertEquals(KeyType.PUBLIC_KEY, p1.getKeyType());
			assertThrows(IllegalStateException.class, p1::asCertificate);
			assertThrows(IllegalStateException.class, p1::asCRL);
			assertThrows(IllegalStateException.class, () -> p1.asPrivate(null));
			assertEquals(
					"-----BEGIN PUBLIC KEY-----\n" + IuText.base64(IuText.utf8(e1)) + "\n-----END PUBLIC KEY-----\n",
					p1.toString());

			final var algorithm = IdGenerator.generateId();
			final var pubkey = mock(PublicKey.class);
			final var keyFactory = mock(KeyFactory.class);
			assertDoesNotThrow(() -> when(keyFactory.generatePublic(any())).thenReturn(pubkey));
			try (final var mockKeyFactory = mockStatic(KeyFactory.class);
					final var mockX509EncodedKeySpec = mockConstruction(X509EncodedKeySpec.class, (a, ctx) -> {
						assertEquals(e1, IuText.utf8((byte[]) ctx.arguments().get(0)));
					})) {
				mockKeyFactory.when(() -> KeyFactory.getInstance(algorithm)).thenReturn(keyFactory);
				assertEquals(pubkey, p1.asPublic(algorithm));
				assertDoesNotThrow(
						() -> verify(keyFactory).generatePublic(mockX509EncodedKeySpec.constructed().get(0)));
			}
		}
	}

	@Test
	public void testPrivateKey() {
		final var e1 = IdGenerator.generateId();
		final var encoded = "-----BEGIN PRIVATE KEY-----\n" + IuText.base64(IuText.utf8(e1))
				+ "\n-----END PRIVATE KEY-----\n";
		final var pem = PemEncoded.parse(encoded);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			assertTrue(pem.hasNext());
			final var p1 = pem.next();
			assertEquals(KeyType.PRIVATE_KEY, p1.getKeyType());
			assertThrows(IllegalStateException.class, p1::asCertificate);
			assertThrows(IllegalStateException.class, p1::asCRL);
			assertThrows(IllegalStateException.class, () -> p1.asPublic(null));
			assertEquals(
					"-----BEGIN PRIVATE KEY-----\n" + IuText.base64(IuText.utf8(e1)) + "\n-----END PRIVATE KEY-----\n",
					p1.toString());

			final var algorithm = IdGenerator.generateId();
			final var privkey = mock(PrivateKey.class);
			final var keyFactory = mock(KeyFactory.class);
			assertDoesNotThrow(() -> when(keyFactory.generatePrivate(any())).thenReturn(privkey));
			try (final var mockKeyFactory = mockStatic(KeyFactory.class);
					final var mockPKCS8EncodedKeySpec = mockConstruction(PKCS8EncodedKeySpec.class, (a, ctx) -> {
						assertEquals(e1, IuText.utf8((byte[]) ctx.arguments().get(0)));
					})) {
				mockKeyFactory.when(() -> KeyFactory.getInstance(algorithm)).thenReturn(keyFactory);
				assertEquals(privkey, p1.asPrivate(algorithm));
				assertDoesNotThrow(
						() -> verify(keyFactory).generatePrivate(mockPKCS8EncodedKeySpec.constructed().get(0)));
			}
		}
	}

	@Test
	public void testSerialize() {
		final var c = mock(X509Certificate.class);
		final var e = IdGenerator.generateId();
		assertDoesNotThrow(() -> when(c.getEncoded()).thenReturn(IuText.utf8(e)));
		try (final var mockPemEncoded = mockConstruction(PemEncoded.class, (a, ctx) -> {
			assertEquals(KeyType.CERTIFICATE, ctx.arguments().get(0));
			assertEquals(e, IuText.utf8((byte[]) ctx.arguments().get(1)));
		})) {
			final var i = PemEncoded.serialize(c);
			final var n = i.next();
			assertEquals(mockPemEncoded.constructed().get(0), n);
		}
	}

	@Test
	public void testSerializeKeyPair() {
		final var pubkey = mock(PublicKey.class);

		final var privkey = mock(PrivateKey.class);
		final var encodedPrivKey = IdGenerator.generateId();
		when(privkey.getEncoded()).thenReturn(IuText.utf8(encodedPrivKey));

		final var cert = mock(X509Certificate.class);
		when(cert.getPublicKey()).thenReturn(pubkey);
		final var encodedCert = IdGenerator.generateId();
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(IuText.utf8(encodedCert)));

		final Queue<BiConsumer<Object, Context>> conAsserts = new ArrayDeque<>();
		conAsserts.offer((a, ctx) -> {
			assertEquals(KeyType.PRIVATE_KEY, ctx.arguments().get(0));
			assertEquals(encodedPrivKey, IuText.utf8((byte[]) ctx.arguments().get(1)));
		});
		conAsserts.offer((a, ctx) -> {
			assertEquals(KeyType.CERTIFICATE, ctx.arguments().get(0));
			assertEquals(encodedCert, IuText.utf8((byte[]) ctx.arguments().get(1)));
		});

		try (final var mockPemEncoded = mockConstruction(PemEncoded.class,
				(a, ctx) -> conAsserts.poll().accept(a, ctx))) {
			final var i = PemEncoded.serialize(new KeyPair(pubkey, privkey), cert);
			final var n1 = i.next();
			final var n2 = i.next();
			assertEquals(mockPemEncoded.constructed().get(0), n1);
			assertEquals(mockPemEncoded.constructed().get(1), n2);
		}
	}

	@Test
	public void testSerializeRSAKeyPair() {
		final var mod = new byte[16];
		ThreadLocalRandom.current().nextBytes(mod);
		final var exp = new byte[16];
		ThreadLocalRandom.current().nextBytes(exp);

		final var pubkey = mock(RSAPublicKey.class);
		when(pubkey.getPublicExponent()).thenReturn(new BigInteger(exp));
		when(pubkey.getModulus()).thenReturn(new BigInteger(mod));

		final var privkey = mock(RSAPrivateCrtKey.class);
		when(privkey.getPublicExponent()).thenReturn(new BigInteger(exp));
		when(privkey.getModulus()).thenReturn(new BigInteger(mod));
		final var encodedPrivKey = IdGenerator.generateId();
		when(privkey.getEncoded()).thenReturn(IuText.utf8(encodedPrivKey));

		final var cert = mock(X509Certificate.class);
		when(cert.getPublicKey()).thenReturn(pubkey);
		final var encodedCert = IdGenerator.generateId();
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(IuText.utf8(encodedCert)));

		final Queue<BiConsumer<Object, Context>> conAsserts = new ArrayDeque<>();
		conAsserts.offer((a, ctx) -> {
			assertEquals(KeyType.PRIVATE_KEY, ctx.arguments().get(0));
			assertEquals(encodedPrivKey, IuText.utf8((byte[]) ctx.arguments().get(1)));
		});
		conAsserts.offer((a, ctx) -> {
			assertEquals(KeyType.CERTIFICATE, ctx.arguments().get(0));
			assertEquals(encodedCert, IuText.utf8((byte[]) ctx.arguments().get(1)));
		});

		try (final var mockPemEncoded = mockConstruction(PemEncoded.class,
				(a, ctx) -> conAsserts.poll().accept(a, ctx))) {
			final var i = PemEncoded.serialize(new KeyPair(pubkey, privkey), cert);
			final var n1 = i.next();
			final var n2 = i.next();
			assertEquals(mockPemEncoded.constructed().get(0), n1);
			assertEquals(mockPemEncoded.constructed().get(1), n2);
		}
	}

	@Test
	public void testSerializeCertPubPrivRSAExpMismatch() {
		final var mod = new byte[16];
		ThreadLocalRandom.current().nextBytes(mod);
		final var exp = new byte[16];
		ThreadLocalRandom.current().nextBytes(exp);
		final var exp2 = new byte[16];
		ThreadLocalRandom.current().nextBytes(exp2);

		final var pubkey = mock(RSAPublicKey.class);
		when(pubkey.getPublicExponent()).thenReturn(new BigInteger(exp));
		when(pubkey.getModulus()).thenReturn(new BigInteger(mod));

		final var privkey = mock(RSAPrivateCrtKey.class);
		when(privkey.getPublicExponent()).thenReturn(new BigInteger(exp2));
		when(privkey.getModulus()).thenReturn(new BigInteger(mod));
		final var encodedPrivKey = IdGenerator.generateId();
		when(privkey.getEncoded()).thenReturn(IuText.utf8(encodedPrivKey));

		final var cert = mock(X509Certificate.class);
		when(cert.getPublicKey()).thenReturn(pubkey);
		final var encodedCert = IdGenerator.generateId();
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(IuText.utf8(encodedCert)));

		final var error = assertThrows(IllegalArgumentException.class,
				() -> PemEncoded.serialize(new KeyPair(null, privkey), cert));
		assertEquals("RSA Public key doesn't match private", error.getMessage());
	}

	@Test
	public void testSerializeCertPubPrivRSAModMismatch() {
		final var mod = new byte[16];
		ThreadLocalRandom.current().nextBytes(mod);
		final var mod2 = new byte[16];
		ThreadLocalRandom.current().nextBytes(mod2);
		final var exp = new byte[16];
		ThreadLocalRandom.current().nextBytes(exp);

		final var pubkey = mock(RSAPublicKey.class);
		when(pubkey.getPublicExponent()).thenReturn(new BigInteger(exp));
		when(pubkey.getModulus()).thenReturn(new BigInteger(mod));

		final var privkey = mock(RSAPrivateCrtKey.class);
		when(privkey.getPublicExponent()).thenReturn(new BigInteger(exp));
		when(privkey.getModulus()).thenReturn(new BigInteger(mod2));
		final var encodedPrivKey = IdGenerator.generateId();
		when(privkey.getEncoded()).thenReturn(IuText.utf8(encodedPrivKey));

		final var cert = mock(X509Certificate.class);
		when(cert.getPublicKey()).thenReturn(pubkey);
		final var encodedCert = IdGenerator.generateId();
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(IuText.utf8(encodedCert)));

		final var error = assertThrows(IllegalArgumentException.class,
				() -> PemEncoded.serialize(new KeyPair(null, privkey), cert));
		assertEquals("RSA Public key doesn't match private", error.getMessage());
	}

	@Test
	public void testSerializeCertPubKeyMismatch() {
		final var pubkey = mock(PublicKey.class);
		final var wrongPubkey = mock(PublicKey.class);

		final var cert = mock(X509Certificate.class);
		when(cert.getPublicKey()).thenReturn(wrongPubkey);

		final var error = assertThrows(IllegalArgumentException.class,
				() -> PemEncoded.serialize(new KeyPair(pubkey, null), cert));
		assertEquals("Public key doesn't match certificate", error.getMessage());
	}

	@Test
	public void testSerializePubKeyNoCert() {
		final var pubkey = mock(PublicKey.class);
		final var encodedPubKey = IdGenerator.generateId();
		when(pubkey.getEncoded()).thenReturn(IuText.utf8(encodedPubKey));

		final Queue<BiConsumer<Object, Context>> conAsserts = new ArrayDeque<>();
		conAsserts.offer((a, ctx) -> {
			assertEquals(KeyType.PUBLIC_KEY, ctx.arguments().get(0));
			assertEquals(encodedPubKey, IuText.utf8((byte[]) ctx.arguments().get(1)));
		});

		try (final var mockPemEncoded = mockConstruction(PemEncoded.class,
				(a, ctx) -> conAsserts.poll().accept(a, ctx))) {
			final var i = PemEncoded.serialize(new KeyPair(pubkey, null));
			final var n1 = i.next();
			assertEquals(mockPemEncoded.constructed().get(0), n1);
		}
	}

	@Test
	public void testGetCertificateChain() {
		final var cert = mock(X509Certificate.class);
		final var pem = mock(PemEncoded.class);
		when(pem.asCertificate()).thenReturn(cert);
		assertArrayEquals(new X509Certificate[] { cert },
				PemEncoded.getCertificateChain(IuIterable.iter(pem).iterator()));
	}

	@Test
	public void testGetCertificateChainByURI() {
		final var uri = mock(URI.class);
		assertDoesNotThrow(() -> PemEncoded.getCertificateChain(uri));
		verify(Init.SPI).getCertificateChain(uri);
	}

	@Test
	public void testAsCertificate() {
		final var cert = mock(X509Certificate.class);
		final var encoded = IdGenerator.generateId();
		final var certificateFactory = mock(CertificateFactory.class);
		assertDoesNotThrow(() -> when(certificateFactory.generateCertificate(any(InputStream.class))).thenReturn(cert));

		try (final var mockByteArrayInput = mockConstruction(ByteArrayInputStream.class, (a, ctx) -> {
			assertEquals(encoded, IuText.utf8((byte[]) ctx.arguments().get(0)));
		})) {
			try (final var mockCertificateFactory = mockStatic(CertificateFactory.class)) {
				mockCertificateFactory.when(() -> CertificateFactory.getInstance("X.509"))
						.thenReturn(certificateFactory);
				assertEquals(cert, PemEncoded.asCertificate(IuText.utf8(encoded)));
				assertDoesNotThrow(
						() -> verify(certificateFactory).generateCertificate(mockByteArrayInput.constructed().get(0)));
			}
		}
	}

	@Test
	public void testAsCRL() {
		final var crl = mock(X509CRL.class);
		final var encoded = IdGenerator.generateId();
		final var certificateFactory = mock(CertificateFactory.class);
		assertDoesNotThrow(() -> when(certificateFactory.generateCRL(any(InputStream.class))).thenReturn(crl));

		try (final var mockByteArrayInput = mockConstruction(ByteArrayInputStream.class, (a, ctx) -> {
			assertEquals(encoded, IuText.utf8((byte[]) ctx.arguments().get(0)));
		})) {
			try (final var mockCertificateFactory = mockStatic(CertificateFactory.class)) {
				mockCertificateFactory.when(() -> CertificateFactory.getInstance("X.509"))
						.thenReturn(certificateFactory);
				assertEquals(crl, PemEncoded.asCRL(IuText.utf8(encoded)));
				assertDoesNotThrow(
						() -> verify(certificateFactory).generateCRL(mockByteArrayInput.constructed().get(0)));
			}
		}
	}

}
