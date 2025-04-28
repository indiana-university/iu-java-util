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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class JwkTest extends CryptImplTestCase {

	@Test
	public void testUse() {
		assertEquals(Use.SIGN,
				WebKey.builder(Type.EC_P256).algorithm(Algorithm.ES256).ephemeral().use(Use.SIGN).build().getUse());
	}

	@Test
	public void testOps() {
		assertEquals(Operation.SIGN, WebKey.builder(Type.EC_P256).algorithm(Algorithm.ES256).ephemeral()
				.ops(Operation.SIGN).build().getOps().iterator().next());
	}

	@Test
	public void testEdCurve() {
		// For testing and demonstration purposes only.
		// NOT FOR PRODUCTION USE
		// $ openssl genpkey -algorithm ed25519 | tee /tmp/k
		// $ openssl pkey -pubout < /tmp/k
		final var text = "-----BEGIN PRIVATE KEY-----\n"
				+ "MC4CAQAwBQYDK2VwBCIEIE1WN1m6gOixo9+AJWGsFf4x3/3qX2bEGm8hZKLEiuSf\n" + "-----END PRIVATE KEY-----\n"
				+ "-----BEGIN PUBLIC KEY-----\n" + "MCowBQYDK2VwAyEAXyF4/YMJMAOzPvTLx7k1LCenfnj9pRQlBnjbaRF4pgM=\n"
				+ "-----END PUBLIC KEY-----\n";
		final var jwk = WebKey.builder(Type.ED25519).pem(text).build();
		assertNotNull(jwk.getPublicKey(), jwk::toString);
		assertNotNull(jwk.getPrivateKey(), jwk::toString);
		assertEquals(jwk, WebKey.builder(Type.ED25519).pem(new ByteArrayInputStream(text.getBytes())).build());
	}

	@Test
	public void testRsaNoCrt() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var rsa = (RSAPrivateCrtKey) EphemeralKeys.rsa("RSA", 2048).getPrivate();
		final var noCrt = KeyFactory.getInstance("RSA")
				.generatePrivate(new RSAPrivateKeySpec(rsa.getModulus(), rsa.getPrivateExponent()));
		assertEquals(noCrt, WebKey.builder(Type.RSA).key(noCrt).build().getPrivateKey());
	}

	@Test
	public void testRsaNoPrivate() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var rsa = EphemeralKeys.rsa("RSA", 2048).getPublic();
		assertEquals(rsa, WebKey.builder(Type.RSA).key(rsa).build().getPublicKey());
	}

	@Test
	public void testRsaMultiCrtUnsupported() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var json = IuJson.object();
		((Jwk) WebKey.ephemeral(Algorithm.PS256)).serializeTo(json);
		json.add("oth", true);
		assertThrows(UnsupportedOperationException.class, () -> new Jwk(json.build()));
	}

	@Test
	public void testWellKnownSameSame() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var cert = mock(X509Certificate.class);
		final var encoded = IuText.utf8(IdGenerator.generateId());
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));
		final var uri = URI.create(IdGenerator.generateId());
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.getCertificateChain(uri)).thenReturn(new X509Certificate[] { cert });
			mockPemEncoded.when(() -> PemEncoded.asCertificate(encoded)).thenReturn(cert);
			final var key = WebKey.builder(Type.EC_P384).cert(uri).build();
			final var wellKnown = key.wellKnown();
			assertEquals(WebKey.verify(key), wellKnown.getPublicKey());
			assertEquals(wellKnown, wellKnown.wellKnown());
		}
	}

	@Test
	public void testDifferentTypeNotEquals() {
		final var key = (Jwk) WebKey.builder(Type.EC_P384).build();
		final var key2 = (Jwk) WebKey.builder(Type.EC_P521).build();
		assertNotEquals(key, key2);
	}

	@Test
	public void testEqualsHashCode() {
		for (final var alg : Set.of(Algorithm.DIRECT, Algorithm.RSA_OAEP_256, Algorithm.ECDH_ES_A192KW)) {
			final var ao = (Jwk) (alg.equals(Algorithm.DIRECT) ? WebKey.builder(alg)
					.ephemeral(IuTest.rand(Encryption.class)).use(Use.ENCRYPT).ops(Operation.ENCRYPT)
					: WebKey.builder(alg).ephemeral(alg).use(alg.use).ops(alg.keyOps)).build();
			final var type = ao.getType();

			final var ab = IuJson.object();
			ao.serializeTo(ab);
			final var a = ab.build();

			final var bo = (Jwk) (alg.equals(Algorithm.DIRECT) ? WebKey.builder(alg)
					.ephemeral(IuTest.rand(Encryption.class)).use(Use.ENCRYPT).ops(Operation.ENCRYPT)
					: WebKey.builder(alg).ephemeral(alg).use(alg.use).ops(alg.keyOps)).build();

			final var bb = IuJson.object();
			bo.serializeTo(bb);
			final var b = bb.build();

			assertNotEquals(ao, null);
			assertNotEquals(ao.hashCode(), bo.hashCode());

			try (final var mockWebKey = mockStatic(WebKey.class, CALLS_REAL_METHODS)) {
				mockWebKey.when(() -> WebKey.verify(any())).thenReturn(null);
				assertFalse(ao.represents(
						(Jwk) WebKey.builder(alg).use(alg.use == Use.ENCRYPT ? Use.SIGN : Use.ENCRYPT).build()));
			}
			assertFalse(((Jwk) WebKey.builder(alg).keyId(IdGenerator.generateId()).build())
					.represents((Jwk) WebKey.builder(alg).keyId(IdGenerator.generateId()).build()));

			final var altRsa = (Jwk) WebKey.ephemeral(Algorithm.RSA_OAEP);
			final var altEc = (Jwk) WebKey.ephemeral(Algorithm.ES384);
			for (var i = 1; i < 16; i++)
				for (var j = 1; j < 16; j++)
					if (i != j) {
						final var differentType = ((Supplier<Type>) () -> {
							Type t;
							do
								t = IuTest.rand(Type.class);
							while (t.equals(type));
							return t;
						}).get();

						final Jwk keyWithDifferentType;
						switch (differentType) {
						case EC_P256:
						case EC_P384:
						case EC_P521:
							keyWithDifferentType = altRsa;
							break;
						case RSA:
						case RSASSA_PSS:
						case RAW:
						default:
							keyWithDifferentType = altEc;
							break;
						}
						assertNotEquals(ao, keyWithDifferentType);
						assertNotEquals(keyWithDifferentType, ao);
						assertNotEquals(bo, keyWithDifferentType);
						assertNotEquals(keyWithDifferentType, bo);
						assertFalse(ao.represents(keyWithDifferentType));
						assertFalse(bo.represents(keyWithDifferentType));

						final var ai = IuJson.object();
						ai.add("kty", type.kty);
						IuJson.add(ai, "crv", type.crv);
						if ((i & 1) == 1)
							IuJson.add(ai, "use", a.get("use"));
						if ((i & 2) == 2)
							IuJson.add(ai, "key_ops", a.get("key_ops"));
						if ((i & 4) == 4) {
							IuJson.add(ai, "k", a.get("k"));
							IuJson.add(ai, "n", a.get("n"));
							IuJson.add(ai, "e", a.get("e"));
							IuJson.add(ai, "d", a.get("d"));
							IuJson.add(ai, "p", a.get("p"));
							IuJson.add(ai, "q", a.get("q"));
							IuJson.add(ai, "dp", a.get("dp"));
							IuJson.add(ai, "dq", a.get("dq"));
							IuJson.add(ai, "qi", a.get("qi"));
						}
						if ((i & 8) == 8) {
							if ((i & 4) != 4) {
								IuJson.add(ai, "n", a.get("n"));
								IuJson.add(ai, "e", a.get("e"));
							}
							IuJson.add(ai, "x", a.get("x"));
							IuJson.add(ai, "y", a.get("y"));
						}
						final Jwk ac;
						try {
							ac = new Jwk(ai.build());
						} catch (IllegalArgumentException e) {
							continue;
						}

						final var bj = IuJson.object();
						bj.add("kty", type.kty);
						IuJson.add(bj, "crv", type.crv);
						if ((j & 1) == 1)
							IuJson.add(bj, "use", b.get("use"));
						if ((j & 2) == 2)
							IuJson.add(bj, "key_ops", b.get("key_ops"));
						if ((j & 4) == 4) {
							IuJson.add(bj, "k", b.get("k"));
							IuJson.add(bj, "n", b.get("n"));
							IuJson.add(bj, "e", b.get("e"));
							IuJson.add(bj, "d", b.get("d"));
							IuJson.add(bj, "p", b.get("p"));
							IuJson.add(bj, "q", b.get("q"));
							IuJson.add(bj, "dp", b.get("dp"));
							IuJson.add(bj, "dq", b.get("dq"));
							IuJson.add(bj, "qi", b.get("qi"));
						}
						if ((j & 8) == 8) {
							IuJson.add(bj, "x", b.get("x"));
							IuJson.add(bj, "y", b.get("y"));
							if ((j & 4) != 4) {
								IuJson.add(bj, "n", b.get("n"));
								IuJson.add(bj, "e", b.get("e"));
							}
						}

						final Jwk bc;
						try {
							bc = new Jwk(bj.build());
						} catch (IllegalArgumentException e) {
							continue;
						}

						assertEquals(ac, new Jwk(IuJson.parse(ac.toString()).asJsonObject()));
						assertEquals(bc, new Jwk(IuJson.parse(bc.toString()).asJsonObject()));
						assertEquals(ac.equals(bc), bc.equals(ac));
						assertTrue(ac.represents(ao));
						assertTrue(bc.represents(bo), bc + " " + bo);
						assertEquals(ac.represents(bc), bc.represents(ac));
					}
		}
	}

	@Test
	public void testEphemerals() {
		assertThrows(UnsupportedOperationException.class, () -> WebKey.ephemeral(Algorithm.DIRECT));
		for (int i = 0; i < 4; i++)
			for (Algorithm algorithm : Algorithm.values()) {
				if (algorithm.equals(Algorithm.DIRECT))
					for (Encryption encryption : Encryption.values())
						assertEphemeral((Jwk) WebKey.builder(Type.RAW).keyId(IdGenerator.generateId())
								.ephemeral(encryption).build());
				else
					for (Type type : algorithm.type)
						assertEphemeral((Jwk) WebKey.builder(type).keyId(IdGenerator.generateId()).ephemeral(algorithm)
								.build());
			}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testRFC8037_A_1_2() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {
		final var jwk = WebKey.parse("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\n"
				+ "   \"d\":\"nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A\",\n"
				+ "   \"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}");
		assertEquals("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}",
				jwk.wellKnown().toString());

		assertArrayEquals(
				new byte[] { (byte) 0x9d, (byte) 0x61, (byte) 0xb1, (byte) 0x9d, (byte) 0xef, (byte) 0xfd, (byte) 0x5a,
						(byte) 0x60, (byte) 0xba, (byte) 0x84, (byte) 0x4a, (byte) 0xf4, (byte) 0x92, (byte) 0xec,
						(byte) 0x2c, (byte) 0xc4, (byte) 0x44, (byte) 0x49, (byte) 0xc5, (byte) 0x69, (byte) 0x7b,
						(byte) 0x32, (byte) 0x69, (byte) 0x19, (byte) 0x70, (byte) 0x3b, (byte) 0xac, (byte) 0x03,
						(byte) 0x1c, (byte) 0xae, (byte) 0x7f, (byte) 0x60 },
				((Optional<byte[]>) Class.forName("java.security.interfaces.EdECPrivateKey").getMethod("getBytes")
						.invoke(jwk.getPrivateKey())).get());

		final var point = Class.forName("java.security.interfaces.EdECPublicKey").getMethod("getPoint")
				.invoke(jwk.getPublicKey());
		final var yint = (BigInteger) Class.forName("java.security.spec.EdECPoint").getMethod("getY").invoke(point);
		final var y = EncodingUtils.reverse(UnsignedBigInteger.bigInt(yint));
		assertArrayEquals(new byte[] { (byte) 0xd7, (byte) 0x5a, (byte) 0x98, (byte) 0x01, (byte) 0x82, (byte) 0xb1,
				(byte) 0x0a, (byte) 0xb7, (byte) 0xd5, (byte) 0x4b, (byte) 0xfe, (byte) 0xd3, (byte) 0xc9, (byte) 0x64,
				(byte) 0x07, (byte) 0x3a, (byte) 0x0e, (byte) 0xe1, (byte) 0x72, (byte) 0xf3, (byte) 0xda, (byte) 0xa6,
				(byte) 0x23, (byte) 0x25, (byte) 0xaf, (byte) 0x02, (byte) 0x1a, (byte) 0x68, (byte) 0xf7, (byte) 0x07,
				(byte) 0x51, (byte) 0x1a }, y);
	}

	@Test
	public void testJceBuilder() {
		KeyPair pair;

		pair = EphemeralKeys.ec(WebKey.algorithmParams(Type.X448.crv));
		WebKey.builder(pair.getPrivate()).key(pair).build();
		WebKey.builder(pair.getPublic()).key(pair).build();

		pair = EphemeralKeys.rsa("RSA", 2048);
		WebKey.builder(pair.getPrivate()).key(pair).build();
		WebKey.builder(pair.getPublic()).key(pair).build();

		final var key = new SecretKeySpec(EphemeralKeys.contentEncryptionKey(256), "AES");
		WebKey.builder(key).build();
	}

	@Test
	public void testWellKnownOps() {
		final var key = (Jwk) WebKey.builder(Algorithm.ES384).ephemeral().ops(Operation.SIGN).build().wellKnown();
		assertSame(key, key.wellKnown());
		final var key2 = (Jwk) WebKey.builder(Algorithm.ES384).ephemeral().ops(Operation.VERIFY).build().wellKnown();
		assertNotEquals(key, key2);
		assertFalse(key.represents(key2));
	}

	private void assertEphemeral(Jwk jwk) {
		assertEquals(jwk, new Jwk(IuJson.parse(jwk.toString()).asJsonObject()));

		final var wellKnown = jwk.wellKnown();
		assertNull(wellKnown.getKey());
		assertNull(wellKnown.getPrivateKey());
		assertEquals(wellKnown.getPublicKey(), WebKey.verify(jwk));
		assertArrayEquals(wellKnown.getCertificateChain(), WebCertificateReference.verify(jwk));

		final var jwksText = Jwk.asJwks(IuIterable.iter(jwk)).toString();
		final var fromInput = Jwk.readJwks(new ByteArrayInputStream(IuText.utf8(jwksText)));
		final var fromParse = Jwk.parseJwks(IuJson.parse(jwksText).asJsonObject());
		assertTrue(IuIterable.remaindersAreEqual(fromInput.iterator(), fromParse.iterator()));

		final var out = new ByteArrayOutputStream();
		Jwk.writeJwks(fromInput, out);
		assertEquals(jwksText, IuText.utf8(out.toByteArray()));

		final var jwks = mock(URI.class);
		try (final var mockIuHttp = mockStatic(IuHttp.class)) {
			mockIuHttp.when(() -> IuHttp.get(jwks, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.parse(jwksText));
			final var fromJwks = Jwk.readJwks(jwks).iterator().next();
			assertEquals(jwk, fromJwks);
			assertSame(fromJwks, Jwk.readJwks(jwks).iterator().next());
		}
	}

}
