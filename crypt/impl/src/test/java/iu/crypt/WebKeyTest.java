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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class WebKeyTest extends CryptImplTestCase {
	// Includes test cases from RFC-7517 appendices A and B

	@Test
	public void testBadAlgorithmParams() {
		assertNull(WebKey.algorithmParams("foo"));
	}

	@Test
	public void testType() {
		IuIterable.iter(Type.values()).forEach(t -> assertSame(t, Type.from(t.kty, t.crv)));
		assertNull(Type.from("foo", "bar"));
		assertNull(Type.from(null));
	}

	@Test
	public void testUse() {
		IuIterable.iter(Use.values()).forEach(use -> {
			assertSame(use, Use.from(use.use));
			assertEquals(use, CryptJsonAdapters.USE.fromJson(CryptJsonAdapters.USE.toJson(use)));
		});
		assertThrows(NoSuchElementException.class, () -> Use.from("foobar"));
	}

	@Test
	public void testOp() {
		IuIterable.iter(Operation.values()).forEach(op -> {
			assertSame(op, Operation.from(op.keyOp));
			assertEquals(op, CryptJsonAdapters.OP.fromJson(CryptJsonAdapters.OP.toJson(op)));
		});
		assertThrows(NoSuchElementException.class, () -> Operation.from("foobar"));
	}

	@Test
	public void testBadAlgorithm() {
		assertThrows(IllegalArgumentException.class,
				() -> WebKey.builder(Type.EC_P256).algorithm(Algorithm.A128GCMKW).build());
	}

	@Test
	public void testRaw() {
		final var k1 = mock(WebKey.class);
		when(k1.getType()).thenReturn(Type.RAW);
		final var priv = mock(PrivateKey.class);
		when(k1.getPrivateKey()).thenReturn(priv);
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k1));

		final var k2 = mock(WebKey.class);
		when(k2.getType()).thenReturn(Type.RAW);
		final var pub = mock(PublicKey.class);
		when(k2.getPublicKey()).thenReturn(pub);
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k2));

		final var k3 = mock(WebKey.class);
		when(k3.getType()).thenReturn(Type.RAW);
		final var cert = mock(X509Certificate.class);
		when(k3.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k3));

		final var k4 = mock(WebKey.class);
		when(k4.getType()).thenReturn(Type.RSA);
		when(k4.getKey()).thenReturn(new byte[0]);
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k4));
	}

	@Test
	public void testMissingParams() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.RSA);
		final var pub = mock(PublicKey.class);
		when(k.getPublicKey()).thenReturn(pub);
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k));
		final var priv = mock(PrivateKey.class);
		when(k.getPrivateKey()).thenReturn(priv);
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k));
	}

	@Test
	public void testPubCertMismatch() {
		final var cert = mock(X509Certificate.class);
		final var pub = EphemeralKeys.rsa("RSA", 1024).getPublic();
		when(cert.getPublicKey()).thenReturn(pub);
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.RSA);
		when(k.getPublicKey()).thenReturn(EphemeralKeys.rsa("RSA", 1024).getPublic());
		when(k.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k));
	}

	@Test
	public void testAlgorithmMismatch() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.RSA);
		when(k.getPublicKey())
				.thenReturn(EphemeralKeys.ec(WebKey.algorithmParams(Type.ED25519.algorithmParams)).getPublic());
		when(k.getPrivateKey())
				.thenReturn(EphemeralKeys.ec(WebKey.algorithmParams(Type.ED448.algorithmParams)).getPrivate());
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k));
	}

	@Test
	public void testEcAlgorithmMismatch() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.RSA);
		when(k.getPublicKey())
				.thenReturn(EphemeralKeys.ec(WebKey.algorithmParams(Type.EC_P256.algorithmParams)).getPublic());
		when(k.getPrivateKey())
				.thenReturn(EphemeralKeys.ec(WebKey.algorithmParams(Type.ED448.algorithmParams)).getPrivate());
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k));
	}

	@Test
	public void testRSAModulusMismatch() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.RSA);
		when(k.getPublicKey()).thenReturn(EphemeralKeys.rsa("RSA", 1024).getPublic());
		when(k.getPrivateKey()).thenReturn(EphemeralKeys.rsa("RSA", 1024).getPrivate());
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k));
	}

	@Test
	public void testRSADerivesPublicKey() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.RSA);

		final var rsa = EphemeralKeys.rsa("RSA", 1024);
		when(k.getPrivateKey()).thenReturn(rsa.getPrivate());
		assertEquals(rsa.getPublic(), WebKey.verify(k));
	}

	@Test
	public void testRSANoCrt() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.RSA);

		final var rsa = EphemeralKeys.rsa("RSA", 1024);
		final var priv = KeyFactory.getInstance("RSA").generatePrivate(new RSAPrivateKeySpec(
				((RSAKey) rsa.getPrivate()).getModulus(), ((RSAPrivateKey) rsa.getPrivate()).getPrivateExponent()));
		when(k.getPrivateKey()).thenReturn(priv);
		when(k.getPublicKey()).thenReturn(rsa.getPublic());
		assertEquals(rsa.getPublic(), WebKey.verify(k));
	}

	@Test
	public void testRSAExponentMismatch() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.RSA);

		final var rsa = EphemeralKeys.rsa("RSA", 1024);
		final var pub = KeyFactory.getInstance("RSA")
				.generatePublic(new RSAPublicKeySpec(((RSAKey) rsa.getPublic()).getModulus(), BigInteger.TEN));
		when(k.getPublicKey()).thenReturn(pub);
		when(k.getPrivateKey()).thenReturn(rsa.getPrivate());
		assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k));
	}

	@Test
	public void testJwks() {
		final var jwks = WebKey.asJwks(IuIterable.of(() -> new Iterator<WebKey>() {
			int i = 0;

			@Override
			public boolean hasNext() {
				return i < 100;
			}

			@Override
			public WebKey next() {
				i++;
				final var algorithm = Algorithm.values()[ThreadLocalRandom.current()
						.nextInt(Algorithm.values().length)];
				if (algorithm.equals(Algorithm.DIRECT))
					return WebKey.ephemeral(
							Encryption.values()[ThreadLocalRandom.current().nextInt(Encryption.values().length)]);
				else
					return WebKey.ephemeral(algorithm);
			}
		}));
		final var uri = mock(URI.class);
		try (final var mockIuHttp = mockStatic(IuHttp.class)) {
			mockIuHttp.when(() -> IuHttp.get(uri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.parse(jwks));
			assertEquals(jwks, WebKey.asJwks(WebKey.readJwks(uri)));
		}
		assertEquals(jwks, WebKey.asJwks(WebKey.readJwks(new ByteArrayInputStream(jwks.getBytes()))));
		final var out = new ByteArrayOutputStream();
		WebKey.writeJwks(WebKey.parseJwks(jwks), out);
		assertEquals(jwks, IuText.utf8(out.toByteArray()));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testRFC7517_A_1() {
		final var text = "{\"keys\":\n" //
				+ "       [\n" //
				+ "         {\"kty\":\"EC\",\n" //
				+ "          \"crv\":\"P-256\",\n" //
				+ "          \"x\":\"MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4\",\n" //
				+ "          \"y\":\"4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM\",\n" //
				+ "          \"use\":\"enc\",\n" //
				+ "          \"kid\":\"1\"},\n" //
				+ "\n" //
				+ "         {\"kty\":\"RSA\",\n" //
				+ "          \"n\": \"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx" //
				+ "4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMs" //
				+ "tn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2" //
				+ "QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbI" //
				+ "SD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqb" //
				+ "w0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\",\n" //
				+ "          \"e\":\"AQAB\",\n" //
				+ "          \"alg\":\"RS256\",\n" //
				+ "          \"kid\":\"2011-04-29\"}\n" //
				+ "       ]\n" //
				+ "     }\n";
		final var parsed = WebKey.parseJwks(text);
		final var jwks = StreamSupport.stream(parsed.spliterator(), false)
				.collect(Collectors.toMap(WebKey::getKeyId, a -> a));
		assertEquals(jwks,
				StreamSupport.stream(parsed.spliterator(), false).collect(Collectors.toMap(WebKey::getKeyId, a -> a)));

		final var rsa = jwks.get("2011-04-29");
		assertEquals(Algorithm.RS256, rsa.getAlgorithm());
		assertInstanceOf(RSAPublicKey.class, rsa.getPublicKey());
		assertNull(rsa.getPrivateKey());

		final var ec = jwks.get("1");
		assertNotEquals(rsa, ec);
		assertNotEquals(ec, rsa);
		assertNotEquals(rsa.hashCode(), ec.hashCode());
		assertEquals(Type.EC_P256, ec.getType());
		assertEquals(Use.ENCRYPT, ec.getUse());
		final var pub = assertInstanceOf(ECPublicKey.class, ec.getPublicKey());
		assertEquals("secp256r1 [NIST P-256,X9.62 prime256v1] (1.2.840.10045.3.1.7)", pub.getParams().toString());
		assertNull(ec.getPrivateKey());

		assertEquals(IuJson.parse(text),
				IuJson.object()
						.add("keys", IuJson.array().add(IuJson.parse(ec.toString())).add(IuJson.parse(rsa.toString())))
						.build());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testRFC7517_A_2() {
		final var text = "{\"keys\":\n" //
				+ "       [\n" //
				+ "         {\"kty\":\"EC\",\n" //
				+ "          \"crv\":\"P-256\",\n" //
				+ "          \"x\":\"MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4\",\n" //
				+ "          \"y\":\"4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM\",\n" //
				+ "          \"d\":\"870MB6gfuTJ4HtUnUvYMyJpr5eUZNP4Bk43bVdj3eAE\",\n" //
				+ "          \"use\":\"enc\",\n" //
				+ "          \"kid\":\"1\"},\n" //
				+ "\n" //
				+ "         {\"kty\":\"RSA\",\n" //
				+ "          \"n\":\"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4" //
				+ "cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMst" //
				+ "n64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2Q" //
				+ "vzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbIS" //
				+ "D08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw" //
				+ "0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\",\n" //
				+ "          \"e\":\"AQAB\",\n" //
				+ "          \"d\":\"X4cTteJY_gn4FYPsXB8rdXix5vwsg1FLN5E3EaG6RJoVH-HLLKD9" //
				+ "M7dx5oo7GURknchnrRweUkC7hT5fJLM0WbFAKNLWY2vv7B6NqXSzUvxT0_YSfqij" //
				+ "wp3RTzlBaCxWp4doFk5N2o8Gy_nHNKroADIkJ46pRUohsXywbReAdYaMwFs9tv8d" //
				+ "_cPVY3i07a3t8MN6TNwm0dSawm9v47UiCl3Sk5ZiG7xojPLu4sbg1U2jx4IBTNBz" //
				+ "nbJSzFHK66jT8bgkuqsk0GjskDJk19Z4qwjwbsnn4j2WBii3RL-Us2lGVkY8fkFz" //
				+ "me1z0HbIkfz0Y6mqnOYtqc0X4jfcKoAC8Q\",\n" //
				+ "          \"p\":\"83i-7IvMGXoMXCskv73TKr8637FiO7Z27zv8oj6pbWUQyLPQBQxtPV" //
				+ "nwD20R-60eTDmD2ujnMt5PoqMrm8RfmNhVWDtjjMmCMjOpSXicFHj7XOuVIYQyqV" //
				+ "WlWEh6dN36GVZYk93N8Bc9vY41xy8B9RzzOGVQzXvNEvn7O0nVbfs\",\n" //
				+ "          \"q\":\"3dfOR9cuYq-0S-mkFLzgItgMEfFzB2q3hWehMuG0oCuqnb3vobLyum" //
				+ "qjVZQO1dIrdwgTnCdpYzBcOfW5r370AFXjiWft_NGEiovonizhKpo9VVS78TzFgx" //
				+ "kIdrecRezsZ-1kYd_s1qDbxtkDEgfAITAG9LUnADun4vIcb6yelxk\",\n" //
				+ "          \"dp\":\"G4sPXkc6Ya9y8oJW9_ILj4xuppu0lzi_H7VTkS8xj5SdX3coE0oim" //
				+ "YwxIi2emTAue0UOa5dpgFGyBJ4c8tQ2VF402XRugKDTP8akYhFo5tAA77Qe_Nmtu" //
				+ "YZc3C3m3I24G2GvR5sSDxUyAN2zq8Lfn9EUms6rY3Ob8YeiKkTiBj0\",\n" //
				+ "          \"dq\":\"s9lAH9fggBsoFR8Oac2R_E2gw282rT2kGOAhvIllETE1efrA6huUU" //
				+ "vMfBcMpn8lqeW6vzznYY5SSQF7pMdC_agI3nG8Ibp1BUb0JUiraRNqUfLhcQb_d9" //
				+ "GF4Dh7e74WbRsobRonujTYN1xCaP6TO61jvWrX-L18txXw494Q_cgk\",\n" //
				+ "          \"qi\":\"GyM_p6JrXySiz1toFgKbWV-JdI3jQ4ypu9rbMWx3rQJBfmt0FoYzg" //
				+ "UIZEVFEcOqwemRN81zoDAaa-Bk0KWNGDjJHZDdDmFhW3AN7lI-puxk_mHZGJ11rx" //
				+ "yR8O55XLSe3SPmRfKwZI6yU24ZxvQKFYItdldUKGzO6Ia6zTKhAVRU\",\n" //
				+ "          \"alg\":\"RS256\",\n" //
				+ "          \"kid\":\"2011-04-29\"}\n" //
				+ "       ]\n" //
				+ "     }";
		final var parsed = WebKey.parseJwks(text);
		final var jwks = StreamSupport.stream(parsed.spliterator(), false)
				.collect(Collectors.toMap(WebKey::getKeyId, a -> a));
		assertEquals(jwks,
				StreamSupport.stream(parsed.spliterator(), false).collect(Collectors.toMap(WebKey::getKeyId, a -> a)));

		final var rsa = jwks.get("2011-04-29");
		assertEquals(Algorithm.RS256, rsa.getAlgorithm());
		final var pub = assertInstanceOf(RSAPublicKey.class, rsa.getPublicKey());
		final var priv = assertInstanceOf(RSAPrivateCrtKey.class, rsa.getPrivateKey());
		assertEquals(pub.getModulus(), priv.getModulus());
		assertEquals(pub.getPublicExponent(), priv.getPublicExponent());

		final var ec = jwks.get("1");
		assertNotEquals(rsa, ec);
		assertNotEquals(ec, rsa);
		assertNotEquals(rsa.hashCode(), ec.hashCode());
		assertEquals(Type.EC_P256, ec.getType());
		assertEquals(Use.ENCRYPT, ec.getUse());
		final var epub = assertInstanceOf(ECPublicKey.class, ec.getPublicKey());
		final var epriv = assertInstanceOf(ECPrivateKey.class, ec.getPrivateKey());
		assertEquals("secp256r1 [NIST P-256,X9.62 prime256v1] (1.2.840.10045.3.1.7)", epub.getParams().toString());
		assertEquals(epub.getParams(), epriv.getParams());

		assertEquals(IuJson.parse(text),
				IuJson.object()
						.add("keys", IuJson.array().add(IuJson.parse(ec.toString())).add(IuJson.parse(rsa.toString())))
						.build());
	}

	@Test
	public void testRFC7517_A_3() {
		final var text = "{\"keys\":\n" //
				+ "       [\n" //
				+ "         {\"kty\":\"oct\",\n" //
				+ "          \"alg\":\"A128KW\",\n" //
				+ "          \"k\":\"GawgguFyGrWKav7AX4VKUg\"},\n" //
				+ "\n" //
				+ "         {\"kty\":\"oct\",\n" //
				+ "          \"k\":\"AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow\",\n" //
				+ "          \"kid\":\"HMAC key used in JWS spec Appendix A.1 example\"}\n" //
				+ "       ]\n" //
				+ "     }";
		final var parsed = WebKey.parseJwks(text);
		final var jwks = StreamSupport.stream(parsed.spliterator(), false).toArray(WebKey[]::new);
		assertArrayEquals(jwks, StreamSupport.stream(parsed.spliterator(), false).toArray(WebKey[]::new));

		assertEquals(Type.RAW, jwks[0].getType());
		assertEquals(Algorithm.A128KW, jwks[0].getAlgorithm());
		assertEquals(16, jwks[0].getKey().length);
		assertEquals(Type.RAW, jwks[1].getType());
		assertEquals("HMAC key used in JWS spec Appendix A.1 example", jwks[1].getKeyId());
		assertEquals(64, jwks[1].getKey().length);

		assertEquals(IuJson.parse(text),
				IuJson.object().add("keys",
						IuJson.array().add(IuJson.parse(jwks[0].toString())).add(IuJson.parse(jwks[1].toString())))
						.build());
	}

	@Test
	public void testRFC7517_B() {
		final var text = "{\"kty\":\"RSA\",\n" //
				+ "      \"use\":\"sig\",\n" //
				+ "      \"kid\":\"1b94c\",\n" //
				+ "      \"n\":\"vrjOfz9Ccdgx5nQudyhdoR17V-IubWMeOZCwX_jj0hgAsz2J_pqYW08" //
				+ "PLbK_PdiVGKPrqzmDIsLI7sA25VEnHU1uCLNwBuUiCO11_-7dYbsr4iJmG0Q" //
				+ "u2j8DsVyT1azpJC_NG84Ty5KKthuCaPod7iI7w0LK9orSMhBEwwZDCxTWq4a" //
				+ "YWAchc8t-emd9qOvWtVMDC2BXksRngh6X5bUYLy6AyHKvj-nUy1wgzjYQDwH" //
				+ "MTplCoLtU-o-8SNnZ1tmRoGE9uJkBLdh5gFENabWnU5m1ZqZPdwS-qo-meMv" //
				+ "VfJb6jJVWRpl2SUtCnYG2C32qvbWbjZ_jBPD5eunqsIo1vQ\",\n" //
				+ "      \"e\":\"AQAB\",\n" //
				+ "      \"x5c\":\n" //
				+ "       [\"MIIDQjCCAiqgAwIBAgIGATz/FuLiMA0GCSqGSIb3DQEBBQUAMGIxCzAJB" //
				+ "gNVBAYTAlVTMQswCQYDVQQIEwJDTzEPMA0GA1UEBxMGRGVudmVyMRwwGgYD" //
				+ "VQQKExNQaW5nIElkZW50aXR5IENvcnAuMRcwFQYDVQQDEw5CcmlhbiBDYW1" //
				+ "wYmVsbDAeFw0xMzAyMjEyMzI5MTVaFw0xODA4MTQyMjI5MTVaMGIxCzAJBg" //
				+ "NVBAYTAlVTMQswCQYDVQQIEwJDTzEPMA0GA1UEBxMGRGVudmVyMRwwGgYDV" //
				+ "QQKExNQaW5nIElkZW50aXR5IENvcnAuMRcwFQYDVQQDEw5CcmlhbiBDYW1w" //
				+ "YmVsbDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL64zn8/QnH" //
				+ "YMeZ0LncoXaEde1fiLm1jHjmQsF/449IYALM9if6amFtPDy2yvz3YlRij66" //
				+ "s5gyLCyO7ANuVRJx1NbgizcAblIgjtdf/u3WG7K+IiZhtELto/A7Fck9Ws6" //
				+ "SQvzRvOE8uSirYbgmj6He4iO8NCyvaK0jIQRMMGQwsU1quGmFgHIXPLfnpn" //
				+ "fajr1rVTAwtgV5LEZ4Iel+W1GC8ugMhyr4/p1MtcIM42EA8BzE6ZQqC7VPq" //
				+ "PvEjZ2dbZkaBhPbiZAS3YeYBRDWm1p1OZtWamT3cEvqqPpnjL1XyW+oyVVk" //
				+ "aZdklLQp2Btgt9qr21m42f4wTw+Xrp6rCKNb0CAwEAATANBgkqhkiG9w0BA" //
				+ "QUFAAOCAQEAh8zGlfSlcI0o3rYDPBB07aXNswb4ECNIKG0CETTUxmXl9KUL" //
				+ "+9gGlqCz5iWLOgWsnrcKcY0vXPG9J1r9AqBNTqNgHq2G03X09266X5CpOe1" //
				+ "zFo+Owb1zxtp3PehFdfQJ610CDLEaS9V9Rqp17hCyybEpOGVwe8fnk+fbEL" //
				+ "2Bo3UPGrpsHzUoaGpDftmWssZkhpBJKVMJyf/RuP2SmmaIzmnw9JiSlYhzo" //
				+ "4tpzd5rFXhjRbg4zW9C+2qok+2+qDM1iJ684gPHMIY8aLWrdgQTxkumGmTq" //
				+ "gawR+N5MDtdPTEQ0XfIBc2cJEUyMTY5MPvACWpkA6SdS4xSvdXK3IVfOWA==\"]\n" //
				+ "     }";
		final var jwk = WebKey.parse(text);
		assertEquals(jwk, WebKey.parse(text));
		assertEquals(jwk.hashCode(), WebKey.parse(text).hashCode());
		assertEquals("1b94c", jwk.getKeyId());
		assertEquals(Type.RSA, jwk.getType());
		assertEquals(Use.SIGN, jwk.getUse());
		final var pub = assertInstanceOf(RSAPublicKey.class, jwk.getPublicKey());

		assertEquals(pub, jwk.getCertificateChain()[0].getPublicKey());
		assertEquals(IuJson.parse(text), IuJson.parse(jwk.toString()));
	}

	@Test
	public void testRSAFromPEMNoCert() throws NoSuchAlgorithmException, InvalidKeySpecException {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl genrsa
		final var rsa = WebKey.builder(Type.RSA).pem( //
				"-----BEGIN PRIVATE KEY-----\r\n"
						+ "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDYXFUKjgq4Iblp\r\n"
						+ "mU3Ymww0LgMjaWIO/DmFQF+EViO+rCRteffNzKFWR3+raINMH4uKXL7d9NGJa1TF\r\n"
						+ "pbrj3XdsdKk/uhrmWfvnClvs79e8J/+UBQ59h5Da7C3f19rVfdIxf+jkPYff+lSw\r\n"
						+ "JLCLlZVsdn71BPAKpOvsu5qr9Nc04EMcPMklbc+n882hPsyeopAgZ01l928RX7/U\r\n"
						+ "NU3Uw+MQuYYia54XI3P6PPKDNfqd9dMY0KHLUeo6b/5FZZkLZvnikuvNVO4H+fQO\r\n"
						+ "OVDvezhXFxO2zM9Q2eCJbvayR2p0TthK2N7O48cKofgMdk1U4Un2vMDXF7pTdGtI\r\n"
						+ "udC1OzJXAgMBAAECggEACObvptIGVeIpV1Nz9QQYIfN8tJHK85PkJ/vokjDbIqbB\r\n"
						+ "jvGURRb00nB5q8tOj6zCmIxNXCONFYrhf4pcoLCFj+RS7GjTX4P3Td/KvXp21WqN\r\n"
						+ "5QC6Qmb4ClHqZ0nh2qPlKJ07L1zqwMfzgRXZX7zlW4OaoKk12TJE9MYZTJbz3dyC\r\n"
						+ "7Dl6Z6o2PM7HEUXfw7ge6CFDTUV6/cQxfNieKrpVEsCOSj3XUf1hCscWBa7JApWe\r\n"
						+ "ejhz3YEqFHwprIPe21ZkPbVGz1hkhNCMfBFLw2ZJmiu/yyV9/LhefIul+4nJyIGE\r\n"
						+ "InYzbjnYPn+gI46i9I8S6v/WQYCJu+q1ZD4mHPnMDQKBgQD8d92fLDIZwxnbC99J\r\n"
						+ "sJemmhxvcX3F8PvfqG+JcyNf6dgiaIUECUnvfgDipdDmKzHjzD6OTKyerzRVmidj\r\n"
						+ "qpDkivHVhTbuqNZpQaWt+8tSpjxN5oZySfizeOeBLrphcay61h6q6ne3HHiWIa82\r\n"
						+ "6qDVQUe0qYb18SP/RLmofMizUwKBgQDbYyioRlvzgbwZ+lhXD0gimx1+QRxb90/d\r\n"
						+ "+9GiE3IbSCTWwz1efyOdDy+xCzh8/L7NX8E0ZQ6e4pmuBBnDt8aZ3boyRt0e0ui/\r\n"
						+ "Sepg20iCfBVOJ37i3n9GhBprkzzIPBb4YoPQQwyOvBgjYhak8hrpADJUWJeIYzi0\r\n"
						+ "pdGCQdrIbQKBgQCH9ZEe7/EHGJ8q7EjR6Uyxxpp7lXWzDCTH/HAcaCnrtAXV+c1w\r\n"
						+ "MARl+chGRh+qZCaY01v4y+fGCPo5AywlKyyeNwknAHdlrPzScCzl9gw3tRgSp4tN\r\n"
						+ "rvJEzF53ng92/H2VnEulpWDU9nsl9nviKhZ04ZPZAdaRScwl4v/McW6vywKBgHq0\r\n"
						+ "MDZF/AHrKvjgo242FuN8HHfUFPd/EIWY5bwf4i9OH4Sa+IUU2SdsKgF8xCBsAI+/\r\n"
						+ "ocEbUJ0fIlNI6dwkuoiukgiyx9QIpLLwtY1suFZ67jOjNX3QciFPm7NVS6a2rSZJ\r\n"
						+ "e24NQkXHAD0yDHY/DzwIpx2z2zUmQb4QDGktSh/VAoGBAI+93qCHtVU5rUeY3771\r\n"
						+ "V541cJqy1gKCob3w9wfhbCTM8ynVREZyUpljcnDBQ9H+gkaoHtPy000FlbUHNyBf\r\n"
						+ "K1ixXXvUZZEvN/8UyQp3VJipKbL+NDXaq8qE8eixPwkG1L2ebqlbjZsxKXKbotnp\r\n"
						+ "Jh+eDKPGD66PxfmLT9GtZxS+\r\n" //
						+ "-----END PRIVATE KEY-----\r\n")
				.build();

		final var pub = assertInstanceOf(RSAPublicKey.class, rsa.getPublicKey());
		final var priv = assertInstanceOf(RSAPrivateCrtKey.class, rsa.getPrivateKey());
		assertEquals(pub.getModulus(), priv.getModulus());
		assertEquals(pub.getPublicExponent(), priv.getPublicExponent());
	}

	@Test
	public void testECFromPEMWithCert()
			throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl ecparam -genkey -name secp384r1 | \
		// openssl pkcs8 -topk8 -nocrypt > /tmp/k
		// $ openssl ec -no_public < /tmp/k | openssl pkcs8 -topk8 -nocrypt
		// $ openssl req -days 410 -x509 -key /tmp/k
		final var ec = WebKey.builder(Type.EC_P384).pem("-----BEGIN PRIVATE KEY-----\r\n" //
				+ "ME4CAQAwEAYHKoZIzj0CAQYFK4EEACIENzA1AgEBBDDrK9MiCo6waL7wOKbfvrsq\r\n" //
				+ "IMldpggDYj9UyDWEiapLgXG/IKS0tFs68srJBzHGSqc=\r\n" //
				+ "-----END PRIVATE KEY-----\r\n" //
				+ "-----BEGIN CERTIFICATE-----\r\n" //
				+ "MIIClzCCAhygAwIBAgIURBnmOnYrSqsKrszgC751/Iat0uEwCgYIKoZIzj0EAwIw\r\n" //
				+ "gYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9v\r\n" //
				+ "bWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZT\r\n" //
				+ "VEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5cHQtdGVzdDAgFw0yNDAzMTAxOTE2\r\n" //
				+ "MjRaGA8yMTI0MDMxMTE5MTYyNFowgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJ\r\n" //
				+ "bmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBV\r\n" //
				+ "bml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5\r\n" //
				+ "cHQtdGVzdDB2MBAGByqGSM49AgEGBSuBBAAiA2IABB21Lelr9GqaBwPWN9aNn+ms\r\n" //
				+ "rjbWINECr3iEkqnCKMta7Zii6Gg8cjmUiLgVIpPfAXGUIo8Jr6SPH+Vb6845xRVj\r\n" //
				+ "ls4Gd/mhzbs1UeBKORACUCwt2PKWiIJFPXMgTpEY+aNTMFEwHQYDVR0OBBYEFIol\r\n" //
				+ "C3PH9md71NuPiuJQXhDl888QMB8GA1UdIwQYMBaAFIolC3PH9md71NuPiuJQXhDl\r\n" //
				+ "888QMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDaQAwZgIxAKHtm01BrBpO\r\n" //
				+ "+uNkzwxfsk8o5/Y3V31T53VN0N22+IMc2Fo0fX6EiRj7JUINzTJN/QIxAOKD0Dab\r\n" //
				+ "ieNBfzWg9IDvuGnDWNEzN0l6IrnHdnEwVDQUpzFNw8UjGz8ohdztRSVKlQ==\r\n" //
				+ "-----END CERTIFICATE-----\r\n").build();

		assertEquals(Type.EC_P384, ec.getType());
		final var epub = assertInstanceOf(ECPublicKey.class, ec.wellKnown().getPublicKey());
		final var epriv = assertInstanceOf(ECPrivateKey.class, ec.getPrivateKey());
		assertEquals("secp384r1 [NIST P-384] (1.3.132.0.34)", epub.getParams().toString());
		assertEquals(epub.getParams(), epriv.getParams());
	}

	@Test
	public void testRFC7517_C_PBES() throws Exception {
		final var text = "{\n" //
				+ "      \"kty\":\"RSA\",\n" //
				+ "      \"kid\":\"juliet@capulet.lit\",\n" //
				+ "      \"use\":\"enc\",\n" //
				+ "      \"n\":\"t6Q8PWSi1dkJj9hTP8hNYFlvadM7DflW9mWepOJhJ66w7nyoK1gPNqFMSQRy" //
				+ "O125Gp-TEkodhWr0iujjHVx7BcV0llS4w5ACGgPrcAd6ZcSR0-Iqom-QFcNP" //
				+ "8Sjg086MwoqQU_LYywlAGZ21WSdS_PERyGFiNnj3QQlO8Yns5jCtLCRwLHL0" //
				+ "Pb1fEv45AuRIuUfVcPySBWYnDyGxvjYGDSM-AqWS9zIQ2ZilgT-GqUmipg0X" //
				+ "OC0Cc20rgLe2ymLHjpHciCKVAbY5-L32-lSeZO-Os6U15_aXrk9Gw8cPUaX1" //
				+ "_I8sLGuSiVdt3C_Fn2PZ3Z8i744FPFGGcG1qs2Wz-Q\",\n" //
				+ "      \"e\":\"AQAB\",\n" //
				+ "      \"d\":\"GRtbIQmhOZtyszfgKdg4u_N-R_mZGU_9k7JQ_jn1DnfTuMdSNprTeaSTyWfS" //
				+ "NkuaAwnOEbIQVy1IQbWVV25NY3ybc_IhUJtfri7bAXYEReWaCl3hdlPKXy9U" //
				+ "vqPYGR0kIXTQRqns-dVJ7jahlI7LyckrpTmrM8dWBo4_PMaenNnPiQgO0xnu" //
				+ "ToxutRZJfJvG4Ox4ka3GORQd9CsCZ2vsUDmsXOfUENOyMqADC6p1M3h33tsu" //
				+ "rY15k9qMSpG9OX_IJAXmxzAh_tWiZOwk2K4yxH9tS3Lq1yX8C1EWmeRDkK2a" //
				+ "hecG85-oLKQt5VEpWHKmjOi_gJSdSgqcN96X52esAQ\",\n" //
				+ "      \"p\":\"2rnSOV4hKSN8sS4CgcQHFbs08XboFDqKum3sc4h3GRxrTmQdl1ZK9uw-PIHf" //
				+ "QP0FkxXVrx-WE-ZEbrqivH_2iCLUS7wAl6XvARt1KkIaUxPPSYB9yk31s0Q8" //
				+ "UK96E3_OrADAYtAJs-M3JxCLfNgqh56HDnETTQhH3rCT5T3yJws\",\n" //
				+ "      \"q\":\"1u_RiFDP7LBYh3N4GXLT9OpSKYP0uQZyiaZwBtOCBNJgQxaj10RWjsZu0c6I" //
				+ "edis4S7B_coSKB0Kj9PaPaBzg-IySRvvcQuPamQu66riMhjVtG6TlV8CLCYK" //
				+ "rYl52ziqK0E_ym2QnkwsUX7eYTB7LbAHRK9GqocDE5B0f808I4s\",\n" //
				+ "      \"dp\":\"KkMTWqBUefVwZ2_Dbj1pPQqyHSHjj90L5x_MOzqYAJMcLMZtbUtwKqvVDq3" //
				+ "tbEo3ZIcohbDtt6SbfmWzggabpQxNxuBpoOOf_a_HgMXK_lhqigI4y_kqS1w" //
				+ "Y52IwjUn5rgRrJ-yYo1h41KR-vz2pYhEAeYrhttWtxVqLCRViD6c\",\n" //
				+ "      \"dq\":\"AvfS0-gRxvn0bwJoMSnFxYcK1WnuEjQFluMGfwGitQBWtfZ1Er7t1xDkbN9" //
				+ "GQTB9yqpDoYaN06H7CFtrkxhJIBQaj6nkF5KKS3TQtQ5qCzkOkmxIe3KRbBy" //
				+ "mXxkb5qwUpX5ELD5xFc6FeiafWYY63TmmEAu_lRFCOJ3xDea-ots\",\n" //
				+ "      \"qi\":\"lSQi-w9CpyUReMErP1RsBLk7wNtOvs5EQpPqmuMvqW57NBUczScEoPwmUqq" //
				+ "abu9V0-Py4dQ57_bapoKRu1R90bvuFnU63SHWEFglZQvJDMeAvmj4sm-Fp0o" //
				+ "Yu_neotgQ0hzbI5gry7ajdYy9-2lNx_76aBZoOUu9HCJ-UsfSOI8\"\n" //
				+ "     }"; //

		final var jwk = WebKey.parse(text);
		final var plaintext = new byte[] { 123, 34, 107, 116, 121, 34, 58, 34, 82, 83, 65, 34, 44, 34, 107, 105, 100,
				34, 58, 34, 106, 117, 108, 105, 101, 116, 64, 99, 97, 112, 117, 108, 101, 116, 46, 108, 105, 116, 34,
				44, 34, 117, 115, 101, 34, 58, 34, 101, 110, 99, 34, 44, 34, 110, 34, 58, 34, 116, 54, 81, 56, 80, 87,
				83, 105, 49, 100, 107, 74, 106, 57, 104, 84, 80, 56, 104, 78, 89, 70, 108, 118, 97, 100, 77, 55, 68,
				102, 108, 87, 57, 109, 87, 101, 112, 79, 74, 104, 74, 54, 54, 119, 55, 110, 121, 111, 75, 49, 103, 80,
				78, 113, 70, 77, 83, 81, 82, 121, 79, 49, 50, 53, 71, 112, 45, 84, 69, 107, 111, 100, 104, 87, 114, 48,
				105, 117, 106, 106, 72, 86, 120, 55, 66, 99, 86, 48, 108, 108, 83, 52, 119, 53, 65, 67, 71, 103, 80,
				114, 99, 65, 100, 54, 90, 99, 83, 82, 48, 45, 73, 113, 111, 109, 45, 81, 70, 99, 78, 80, 56, 83, 106,
				103, 48, 56, 54, 77, 119, 111, 113, 81, 85, 95, 76, 89, 121, 119, 108, 65, 71, 90, 50, 49, 87, 83, 100,
				83, 95, 80, 69, 82, 121, 71, 70, 105, 78, 110, 106, 51, 81, 81, 108, 79, 56, 89, 110, 115, 53, 106, 67,
				116, 76, 67, 82, 119, 76, 72, 76, 48, 80, 98, 49, 102, 69, 118, 52, 53, 65, 117, 82, 73, 117, 85, 102,
				86, 99, 80, 121, 83, 66, 87, 89, 110, 68, 121, 71, 120, 118, 106, 89, 71, 68, 83, 77, 45, 65, 113, 87,
				83, 57, 122, 73, 81, 50, 90, 105, 108, 103, 84, 45, 71, 113, 85, 109, 105, 112, 103, 48, 88, 79, 67, 48,
				67, 99, 50, 48, 114, 103, 76, 101, 50, 121, 109, 76, 72, 106, 112, 72, 99, 105, 67, 75, 86, 65, 98, 89,
				53, 45, 76, 51, 50, 45, 108, 83, 101, 90, 79, 45, 79, 115, 54, 85, 49, 53, 95, 97, 88, 114, 107, 57, 71,
				119, 56, 99, 80, 85, 97, 88, 49, 95, 73, 56, 115, 76, 71, 117, 83, 105, 86, 100, 116, 51, 67, 95, 70,
				110, 50, 80, 90, 51, 90, 56, 105, 55, 52, 52, 70, 80, 70, 71, 71, 99, 71, 49, 113, 115, 50, 87, 122, 45,
				81, 34, 44, 34, 101, 34, 58, 34, 65, 81, 65, 66, 34, 44, 34, 100, 34, 58, 34, 71, 82, 116, 98, 73, 81,
				109, 104, 79, 90, 116, 121, 115, 122, 102, 103, 75, 100, 103, 52, 117, 95, 78, 45, 82, 95, 109, 90, 71,
				85, 95, 57, 107, 55, 74, 81, 95, 106, 110, 49, 68, 110, 102, 84, 117, 77, 100, 83, 78, 112, 114, 84,
				101, 97, 83, 84, 121, 87, 102, 83, 78, 107, 117, 97, 65, 119, 110, 79, 69, 98, 73, 81, 86, 121, 49, 73,
				81, 98, 87, 86, 86, 50, 53, 78, 89, 51, 121, 98, 99, 95, 73, 104, 85, 74, 116, 102, 114, 105, 55, 98,
				65, 88, 89, 69, 82, 101, 87, 97, 67, 108, 51, 104, 100, 108, 80, 75, 88, 121, 57, 85, 118, 113, 80, 89,
				71, 82, 48, 107, 73, 88, 84, 81, 82, 113, 110, 115, 45, 100, 86, 74, 55, 106, 97, 104, 108, 73, 55, 76,
				121, 99, 107, 114, 112, 84, 109, 114, 77, 56, 100, 87, 66, 111, 52, 95, 80, 77, 97, 101, 110, 78, 110,
				80, 105, 81, 103, 79, 48, 120, 110, 117, 84, 111, 120, 117, 116, 82, 90, 74, 102, 74, 118, 71, 52, 79,
				120, 52, 107, 97, 51, 71, 79, 82, 81, 100, 57, 67, 115, 67, 90, 50, 118, 115, 85, 68, 109, 115, 88, 79,
				102, 85, 69, 78, 79, 121, 77, 113, 65, 68, 67, 54, 112, 49, 77, 51, 104, 51, 51, 116, 115, 117, 114, 89,
				49, 53, 107, 57, 113, 77, 83, 112, 71, 57, 79, 88, 95, 73, 74, 65, 88, 109, 120, 122, 65, 104, 95, 116,
				87, 105, 90, 79, 119, 107, 50, 75, 52, 121, 120, 72, 57, 116, 83, 51, 76, 113, 49, 121, 88, 56, 67, 49,
				69, 87, 109, 101, 82, 68, 107, 75, 50, 97, 104, 101, 99, 71, 56, 53, 45, 111, 76, 75, 81, 116, 53, 86,
				69, 112, 87, 72, 75, 109, 106, 79, 105, 95, 103, 74, 83, 100, 83, 103, 113, 99, 78, 57, 54, 88, 53, 50,
				101, 115, 65, 81, 34, 44, 34, 112, 34, 58, 34, 50, 114, 110, 83, 79, 86, 52, 104, 75, 83, 78, 56, 115,
				83, 52, 67, 103, 99, 81, 72, 70, 98, 115, 48, 56, 88, 98, 111, 70, 68, 113, 75, 117, 109, 51, 115, 99,
				52, 104, 51, 71, 82, 120, 114, 84, 109, 81, 100, 108, 49, 90, 75, 57, 117, 119, 45, 80, 73, 72, 102, 81,
				80, 48, 70, 107, 120, 88, 86, 114, 120, 45, 87, 69, 45, 90, 69, 98, 114, 113, 105, 118, 72, 95, 50, 105,
				67, 76, 85, 83, 55, 119, 65, 108, 54, 88, 118, 65, 82, 116, 49, 75, 107, 73, 97, 85, 120, 80, 80, 83,
				89, 66, 57, 121, 107, 51, 49, 115, 48, 81, 56, 85, 75, 57, 54, 69, 51, 95, 79, 114, 65, 68, 65, 89, 116,
				65, 74, 115, 45, 77, 51, 74, 120, 67, 76, 102, 78, 103, 113, 104, 53, 54, 72, 68, 110, 69, 84, 84, 81,
				104, 72, 51, 114, 67, 84, 53, 84, 51, 121, 74, 119, 115, 34, 44, 34, 113, 34, 58, 34, 49, 117, 95, 82,
				105, 70, 68, 80, 55, 76, 66, 89, 104, 51, 78, 52, 71, 88, 76, 84, 57, 79, 112, 83, 75, 89, 80, 48, 117,
				81, 90, 121, 105, 97, 90, 119, 66, 116, 79, 67, 66, 78, 74, 103, 81, 120, 97, 106, 49, 48, 82, 87, 106,
				115, 90, 117, 48, 99, 54, 73, 101, 100, 105, 115, 52, 83, 55, 66, 95, 99, 111, 83, 75, 66, 48, 75, 106,
				57, 80, 97, 80, 97, 66, 122, 103, 45, 73, 121, 83, 82, 118, 118, 99, 81, 117, 80, 97, 109, 81, 117, 54,
				54, 114, 105, 77, 104, 106, 86, 116, 71, 54, 84, 108, 86, 56, 67, 76, 67, 89, 75, 114, 89, 108, 53, 50,
				122, 105, 113, 75, 48, 69, 95, 121, 109, 50, 81, 110, 107, 119, 115, 85, 88, 55, 101, 89, 84, 66, 55,
				76, 98, 65, 72, 82, 75, 57, 71, 113, 111, 99, 68, 69, 53, 66, 48, 102, 56, 48, 56, 73, 52, 115, 34, 44,
				34, 100, 112, 34, 58, 34, 75, 107, 77, 84, 87, 113, 66, 85, 101, 102, 86, 119, 90, 50, 95, 68, 98, 106,
				49, 112, 80, 81, 113, 121, 72, 83, 72, 106, 106, 57, 48, 76, 53, 120, 95, 77, 79, 122, 113, 89, 65, 74,
				77, 99, 76, 77, 90, 116, 98, 85, 116, 119, 75, 113, 118, 86, 68, 113, 51, 116, 98, 69, 111, 51, 90, 73,
				99, 111, 104, 98, 68, 116, 116, 54, 83, 98, 102, 109, 87, 122, 103, 103, 97, 98, 112, 81, 120, 78, 120,
				117, 66, 112, 111, 79, 79, 102, 95, 97, 95, 72, 103, 77, 88, 75, 95, 108, 104, 113, 105, 103, 73, 52,
				121, 95, 107, 113, 83, 49, 119, 89, 53, 50, 73, 119, 106, 85, 110, 53, 114, 103, 82, 114, 74, 45, 121,
				89, 111, 49, 104, 52, 49, 75, 82, 45, 118, 122, 50, 112, 89, 104, 69, 65, 101, 89, 114, 104, 116, 116,
				87, 116, 120, 86, 113, 76, 67, 82, 86, 105, 68, 54, 99, 34, 44, 34, 100, 113, 34, 58, 34, 65, 118, 102,
				83, 48, 45, 103, 82, 120, 118, 110, 48, 98, 119, 74, 111, 77, 83, 110, 70, 120, 89, 99, 75, 49, 87, 110,
				117, 69, 106, 81, 70, 108, 117, 77, 71, 102, 119, 71, 105, 116, 81, 66, 87, 116, 102, 90, 49, 69, 114,
				55, 116, 49, 120, 68, 107, 98, 78, 57, 71, 81, 84, 66, 57, 121, 113, 112, 68, 111, 89, 97, 78, 48, 54,
				72, 55, 67, 70, 116, 114, 107, 120, 104, 74, 73, 66, 81, 97, 106, 54, 110, 107, 70, 53, 75, 75, 83, 51,
				84, 81, 116, 81, 53, 113, 67, 122, 107, 79, 107, 109, 120, 73, 101, 51, 75, 82, 98, 66, 121, 109, 88,
				120, 107, 98, 53, 113, 119, 85, 112, 88, 53, 69, 76, 68, 53, 120, 70, 99, 54, 70, 101, 105, 97, 102, 87,
				89, 89, 54, 51, 84, 109, 109, 69, 65, 117, 95, 108, 82, 70, 67, 79, 74, 51, 120, 68, 101, 97, 45, 111,
				116, 115, 34, 44, 34, 113, 105, 34, 58, 34, 108, 83, 81, 105, 45, 119, 57, 67, 112, 121, 85, 82, 101,
				77, 69, 114, 80, 49, 82, 115, 66, 76, 107, 55, 119, 78, 116, 79, 118, 115, 53, 69, 81, 112, 80, 113,
				109, 117, 77, 118, 113, 87, 53, 55, 78, 66, 85, 99, 122, 83, 99, 69, 111, 80, 119, 109, 85, 113, 113,
				97, 98, 117, 57, 86, 48, 45, 80, 121, 52, 100, 81, 53, 55, 95, 98, 97, 112, 111, 75, 82, 117, 49, 82,
				57, 48, 98, 118, 117, 70, 110, 85, 54, 51, 83, 72, 87, 69, 70, 103, 108, 90, 81, 118, 74, 68, 77, 101,
				65, 118, 109, 106, 52, 115, 109, 45, 70, 112, 48, 111, 89, 117, 95, 110, 101, 111, 116, 103, 81, 48,
				104, 122, 98, 73, 53, 103, 114, 121, 55, 97, 106, 100, 89, 121, 57, 45, 50, 108, 78, 120, 95, 55, 54,
				97, 66, 90, 111, 79, 85, 117, 57, 72, 67, 74, 45, 85, 115, 102, 83, 79, 73, 56, 34, 125 };
		assertEquals(jwk, WebKey.parse(IuText.utf8(plaintext)));

		final var p2s = new byte[] { (byte) 217, 96, (byte) 147, 112, (byte) 150, 117, 70, (byte) 247, 127, 8,
				(byte) 155, (byte) 137, (byte) 174, 42, 80, (byte) 215 };
		final var p2c = 4096;
		final var protHeader = "eyJhbGciOiJQQkVTMi1IUzI1NitBMTI4S1ciLCJwMnMiOiIyV0NUY0paMVJ2ZF9DSn"
				+ "VKcmlwUTF3IiwicDJjIjo0MDk2LCJlbmMiOiJBMTI4Q0JDLUhTMjU2IiwiY3R5IjoiandrK2pzb24ifQ";
		assertEquals(
				"{\"alg\":\"PBES2-HS256+A128KW\",\"p2s\":\"2WCTcJZ1Rvd_CJuJripQ1w\",\"p2c\":4096,\"enc\":\"A128CBC-HS256\",\"cty\":\"jwk+json\"}",
				IuText.utf8(IuText.base64Url(protHeader)));

		final var cek = new byte[] { 111, 27, 25, 52, 66, 29, 20, 78, 92, (byte) 176, 56, (byte) 240, 65, (byte) 208,
				82, 112, (byte) 161, (byte) 131, 36, 55, (byte) 202, (byte) 236, (byte) 185, (byte) 172, (byte) 129, 23,
				(byte) 153, (byte) 194, (byte) 195, 48, (byte) 253, (byte) 182 };

		final var pass = "Thus from my lips, by yours, my sin is purged.";
		assertArrayEquals(new byte[] { 84, 104, 117, 115, 32, 102, 114, 111, 109, 32, 109, 121, 32, 108, 105, 112, 115,
				44, 32, 98, 121, 32, 121, 111, 117, 114, 115, 44, 32, 109, 121, 32, 115, 105, 110, 32, 105, 115, 32,
				112, 117, 114, 103, 101, 100, 46 }, IuText.utf8(pass));

		final var alg = IuText.utf8(Algorithm.PBES2_HS256_A128KW.alg);
		final var saltValue = ByteBuffer.wrap(new byte[alg.length + 1 + p2s.length]);
		saltValue.put(alg);
		saltValue.put((byte) 0);
		saltValue.put(p2s);
		assertArrayEquals(new byte[] { 80, 66, 69, 83, 50, 45, 72, 83, 50, 53, 54, 43, 65, 49, 50, 56, 75, 87, 0,
				(byte) 217, 96, (byte) 147, 112, (byte) 150, 117, 70, (byte) 247, 127, 8, (byte) 155, (byte) 137,
				(byte) 174, 42, 80, (byte) 215 }, saltValue.array());

		final var key = SecretKeyFactory.getInstance(Algorithm.PBES2_HS256_A128KW.algorithm)
				.generateSecret(new PBEKeySpec(pass.toCharArray(), saltValue.array(), p2c, 128));

		assertArrayEquals(new byte[] { 110, (byte) 171, (byte) 169, 92, (byte) 129, 92, 109, 117, (byte) 233,
				(byte) 242, 116, (byte) 233, (byte) 170, 14, 24, 75 }, key.getEncoded());

		final var cipher = Cipher.getInstance("AESWrap");
		cipher.init(Cipher.WRAP_MODE, new SecretKeySpec(key.getEncoded(), "AES"));
		final var encryptedKey = cipher.wrap(new SecretKeySpec(cek, "AES"));

		assertArrayEquals(new byte[] { 78, (byte) 186, (byte) 151, 59, 11, (byte) 141, 81, (byte) 240, (byte) 213,
				(byte) 245, 83, (byte) 211, 53, (byte) 188, (byte) 134, (byte) 188, 66, 125, 36, (byte) 200, (byte) 222,
				124, 5, 103, (byte) 249, 52, 117, (byte) 184, (byte) 140, 81, (byte) 246, (byte) 158, (byte) 161,
				(byte) 177, 20, 33, (byte) 245, 57, 59, 4 }, encryptedKey);

		final var jwe = WebEncryption.parse("eyJhbGciOiJQQkVTMi1IUzI1NitBMTI4S1ciLCJwMnMiOiIyV0NUY0paMVJ2ZF9DSn"
				+ "VKcmlwUTF3IiwicDJjIjo0MDk2LCJlbmMiOiJBMTI4Q0JDLUhTMjU2IiwiY3R5IjoiandrK2pzb24ifQ."
				+ "TrqXOwuNUfDV9VPTNbyGvEJ9JMjefAVn-TR1uIxR9p6hsRQh9Tk7BA.Ye9j1qs22DmRSAddIh-VnA."
				+ "AwhB8lxrlKjFn02LGWEqg27H4Tg9fyZAbFv3p5ZicHpj64QyHC44qqlZ3JEmnZTgQo"
				+ "wIqZJ13jbyHB8LgePiqUJ1hf6M2HPLgzw8L-mEeQ0jvDUTrE07NtOerBk8bwBQyZ6g"
				+ "0kQ3DEOIglfYxV8-FJvNBYwbqN1Bck6d_i7OtjSHV-8DIrp-3JcRIe05YKy3Oi34Z_"
				+ "GOiAc1EK21B11c_AE11PII_wvvtRiUiG8YofQXakWd1_O98Kap-UgmyWPfreUJ3lJP"
				+ "nbD4Ve95owEfMGLOPflo2MnjaTDCwQokoJ_xplQ2vNPz8iguLcHBoKllyQFJL2mOWB"
				+ "wqhBo9Oj-O800as5mmLsvQMTflIrIEbbTMzHMBZ8EFW9fWwwFu0DWQJGkMNhmBZQ-3"
				+ "lvqTc-M6-gWA6D8PDhONfP2Oib2HGizwG1iEaX8GRyUpfLuljCLIe1DkGOewhKuKkZ"
				+ "h04DKNM5Nbugf2atmU9OP0Ldx5peCUtRG1gMVl7Qup5ZXHTjgPDr5b2N731UooCGAU"
				+ "qHdgGhg0JVJ_ObCTdjsH4CF1SJsdUhrXvYx3HJh2Xd7CwJRzU_3Y1GxYU6-s3GFPbi"
				+ "rfqqEipJDBTHpcoCmyrwYjYHFgnlqBZRotRrS95g8F95bRXqsaDY7UgQGwBQBwy665"
				+ "d0zpvTasvfXf_c0MWAl-neFaKOW_Px6g4EUDjG1GWSXV9cLStLw_0ovdApDIFLHYHe"
				+ "PyagyHjouQUuGiq7BsYwYrwaF06tgB8hV8omLNfMEmDPJaZUzMuHw6tBDwGkzD-tS_"
				+ "ub9hxrpJ4UsOWnt5rGUyoN2N_c1-TQlXxm5oto14MxnoAyBQBpwIEgSH3Y4ZhwKBhH"
				+ "PjSo0cdwuNdYbGPpb-YUvF-2NZzODiQ1OvWQBRHSbPWYz_xbGkgD504LRtqRwCO7CC"
				+ "_CyyURi1sEssPVsMJRX_U4LFEOc82TiDdqjKOjRUfKK5rqLi8nBE9soQ0DSaOoFQZi"
				+ "GrBrqxDsNYiAYAmxxkos-i3nX4qtByVx85sCE5U_0MqG7COxZWMOPEFrDaepUV-cOy"
				+ "rvoUIng8i8ljKBKxETY2BgPegKBYCxsAUcAkKamSCC9AiBxA0UOHyhTqtlvMksO7AE"
				+ "hNC2-YzPyx1FkhMoS4LLe6E_pFsMlmjA6P1NSge9C5G5tETYXGAn6b1xZbHtmwrPSc"
				+ "ro9LWhVmAaA7_bxYObnFUxgWtK4vzzQBjZJ36UTk4OTB-JvKWgfVWCFsaw5WCHj6Oo"
				+ "4jpO7d2yN7WMfAj2hTEabz9wumQ0TMhBduZ-QON3pYObSy7TSC1vVme0NJrwF_cJRe"
				+ "hKTFmdlXGVldPxZCplr7ZQqRQhF8JP-l4mEQVnCaWGn9ONHlemczGOS-A-wwtnmwjI"
				+ "B1V_vgJRf4FdpV-4hUk4-QLpu3-1lWFxrtZKcggq3tWTduRo5_QebQbUUT_VSCgsFc"
				+ "OmyWKoj56lbxthN19hq1XGWbLGfrrR6MWh23vk01zn8FVwi7uFwEnRYSafsnWLa1Z5"
				+ "TpBj9GvAdl2H9NHwzpB5NqHpZNkQ3NMDj13Fn8fzO0JB83Etbm_tnFQfcb13X3bJ15"
				+ "Cz-Ww1MGhvIpGGnMBT_ADp9xSIyAM9dQ1yeVXk-AIgWBUlN5uyWSGyCxp0cJwx7HxM"
				+ "38z0UIeBu-MytL-eqndM7LxytsVzCbjOTSVRmhYEMIzUAnS1gs7uMQAGRdgRIElTJE"
				+ "SGMjb_4bZq9s6Ve1LKkSi0_QDsrABaLe55UY0zF4ZSfOV5PMyPtocwV_dcNPlxLgNA"
				+ "D1BFX_Z9kAdMZQW6fAmsfFle0zAoMe4l9pMESH0JB4sJGdCKtQXj1cXNydDYozF7l8"
				+ "H00BV_Er7zd6VtIw0MxwkFCTatsv_R-GsBCH218RgVPsfYhwVuT8R4HarpzsDBufC4"
				+ "r8_c8fc9Z278sQ081jFjOja6L2x0N_ImzFNXU6xwO-Ska-QeuvYZ3X_L31ZOX4Llp-"
				+ "7QSfgDoHnOxFv1Xws-D5mDHD3zxOup2b2TppdKTZb9eW2vxUVviM8OI9atBfPKMGAO"
				+ "v9omA-6vv5IxUH0-lWMiHLQ_g8vnswp-Jav0c4t6URVUzujNOoNd_CBGGVnHiJTCHl"
				+ "88LQxsqLHHIu4Fz-U2SGnlxGTj0-ihit2ELGRv4vO8E1BosTmf0cx3qgG0Pq0eOLBD"
				+ "IHsrdZ_CCAiTc0HVkMbyq1M6qEhM-q5P6y1QCIrwg.0HFmhOzsQ98nNWJjIHkR7A");

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE,
				"CEK decryption successful for {\"alg\":\"PBES2-HS256+A128KW\",\"kty\":\"oct\"}");
		assertEquals(jwk, WebKey.parse(jwe.decryptText(
				WebKey.builder(Type.RAW).algorithm(Algorithm.PBES2_HS256_A128KW).key(IuText.utf8(pass)).build())));
	}

	@Test
	public void testBadUseForAlg() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.EC_P256);
		when(k.getAlgorithm()).thenReturn(Algorithm.ECDH_ES);
		when(k.getUse()).thenReturn(Use.SIGN);
		assertEquals("Illegal use SIGN for algorithm ECDH_ES",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
	}

	@Test
	public void testBadOpForAlg() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.EC_P256);
		when(k.getAlgorithm()).thenReturn(Algorithm.ES256);
		when(k.getOps()).thenReturn(Set.of(Operation.WRAP));
		assertEquals("Illegal ops [WRAP] for algorithm ES256",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
	}

	@Test
	public void testBadOpForSign() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.EC_P256);
		when(k.getUse()).thenReturn(Use.SIGN);
		when(k.getOps()).thenReturn(Set.of(Operation.WRAP));
		assertEquals("Illegal ops [WRAP] for use SIGN",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOpForUse() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.EC_P256);
		when(k.getUse()).thenReturn(Use.ENCRYPT, Use.ENCRYPT, Use.SIGN);
		when(k.getOps()).thenReturn(Set.of(Operation.VERIFY), Set.of(Operation.SIGN));
		assertEquals("Illegal ops [VERIFY] for use ENCRYPT",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
		assertEquals("Illegal ops [SIGN] for use ENCRYPT",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
		assertEquals("Private key required by ops [SIGN]",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
	}

	@Test
	public void testBadOpsLong() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.EC_P256);
		final var ops = Set.of(Operation.WRAP, Operation.DERIVE_BITS, Operation.VERIFY);
		when(k.getOps()).thenReturn(ops);
		assertEquals("Illegal ops " + ops,
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
	}

	@Test
	public void testBadOpsTwo() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.EC_P256);
		final var ops = Set.of(Operation.WRAP, Operation.DERIVE_BITS);
		when(k.getOps()).thenReturn(ops);
		assertEquals("Illegal ops " + ops,
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testBadOpsEncrypt() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.EC_P256);
		when(k.getOps()).thenReturn(Set.of(Operation.ENCRYPT), Set.of(Operation.DECRYPT));
		assertEquals("Secret key required by ops [ENCRYPT]",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
		assertEquals("Secret key required by ops [DECRYPT]",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testBadOpsPki() {
		final var k = mock(WebKey.class);
		when(k.getType()).thenReturn(Type.EC_P256);
		when(k.getOps()).thenReturn(Set.of(Operation.VERIFY), Set.of(Operation.DERIVE_KEY), Set.of(Operation.VERIFY));
		when(k.getPublicKey()).thenReturn(null, null,
				EphemeralKeys.ec(WebKey.algorithmParams(Type.EC_P256.algorithmParams)).getPublic());
		assertEquals("Public key required by ops [VERIFY]",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
		assertEquals("Public or private key required by ops [DERIVE_KEY]",
				assertThrows(IllegalArgumentException.class, () -> WebKey.verify(k)).getMessage());
		assertDoesNotThrow(() -> WebKey.verify(k));
	}

	@Test
	public void testWellKnownWithOps() {
		assertEquals(Set.of(Operation.DERIVE_KEY), WebKey.builder(Algorithm.ECDH_ES).ephemeral()
				.ops(Algorithm.ECDH_ES.keyOps).build().wellKnown().getOps());
		assertNull(WebKey.builder(Algorithm.A128GCMKW).ephemeral().ops(Algorithm.A128GCMKW.keyOps).build().wellKnown()
				.getOps());
		assertEquals(Set.of(Operation.WRAP), WebKey.builder(Algorithm.RSA_OAEP).ephemeral()
				.ops(Algorithm.RSA_OAEP.keyOps).build().wellKnown().getOps());
	}

}
