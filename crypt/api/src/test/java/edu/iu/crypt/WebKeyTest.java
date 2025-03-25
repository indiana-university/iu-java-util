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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.EnumSet;
import java.util.Set;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.crypt.PemEncoded.KeyType;
import edu.iu.crypt.WebKey.Builder;
import edu.iu.crypt.WebKey.Type;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class WebKeyTest extends IuCryptApiTestCase {

	@Test
	public void testAlgorithmParamsInvalid() {
		assertNull(WebKey.algorithmParams("invalid"));
	}

	@Test
	public void testAlgorithmParamsECGen() {
		final var algorithmParameters = mock(AlgorithmParameters.class);
		try (final var mockAlgorithmParameters = mockStatic(AlgorithmParameters.class);
				final var mockECGenParameterSpec = mockConstruction(ECGenParameterSpec.class, (a, ctx) -> {
					assertEquals("secp384r1", ctx.arguments().get(0));
				})) {
			mockAlgorithmParameters.when(() -> AlgorithmParameters.getInstance("EC")).thenReturn(algorithmParameters);
			assertDoesNotThrow(() -> {
				WebKey.algorithmParams("secp384r1");
				verify(algorithmParameters).init(mockECGenParameterSpec.constructed().get(0));
				verify(algorithmParameters).getParameterSpec(ECParameterSpec.class);
			});
		}
	}

	@Test
	public void testNamedParameterSpec() {
		final var spec = assertInstanceOf(NamedParameterSpec.class, WebKey.algorithmParams("Ed448"));
		assertEquals("Ed448", spec.getName());
	}

	@Test
	public void testAlgorithmParamsNull() {
		assertNull(WebKey.algorithmParams((Key) null));
		final var key = mock(Key.class);
		assertNull(WebKey.algorithmParams(key));
		when(key.getAlgorithm()).thenReturn("RSA");
		assertNull(WebKey.algorithmParams(key));
		assertNull(WebKey.Type.from(null));
	}

	@Test
	public void testAlgorithmParamsEC() {
		final var params = WebKey.algorithmParams("secp384r1");
		assertEquals(WebKey.Type.EC_P384, WebKey.Type.from(params));
		final var ec = EphemeralKeys.ec(params);
		assertInstanceOf(ECParameterSpec.class, WebKey.algorithmParams(ec.getPrivate()));
	}

	@Test
	public void testAlgorithmParamsXEC() {
		final var params = WebKey.algorithmParams("X25519");
		assertEquals(WebKey.Type.X25519, WebKey.Type.from(params));
		final var ec = EphemeralKeys.ec(params);
		assertInstanceOf(NamedParameterSpec.class, WebKey.algorithmParams(ec.getPrivate()));
	}

	@Test
	public void testAlgorithmParamsED() {
		final var params = WebKey.algorithmParams("Ed25519");
		assertEquals(WebKey.Type.ED25519, WebKey.Type.from(params));
		final var ec = EphemeralKeys.ec(params);
		assertInstanceOf(NamedParameterSpec.class, WebKey.algorithmParams(ec.getPrivate()));
	}

	@Test
	public void testTypes() {
		for (final var type : WebKey.Type.values())
			assertSame(type, WebKey.Type.from(type.kty, type.crv));
	}

	@Test
	public void testUses() {
		for (final var use : WebKey.Use.values())
			assertSame(use, WebKey.Use.from(use.use));
	}

	@Test
	public void testOps() {
		for (final var op : WebKey.Operation.values())
			assertSame(op, WebKey.Operation.from(op.keyOp));
	}

	@Test
	public void testAlgs() {
		for (final var alg : WebKey.Algorithm.values())
			assertSame(alg, WebKey.Algorithm.from(alg.alg));
	}

	@Test
	public void testVerifyMissingType() {
		final var jwk = mock(WebKey.class);
		final var error = assertThrows(NullPointerException.class, () -> WebKey.verify(jwk));
		assertEquals("Key type is required", error.getMessage());
	}

	@Test
	public void testVerifySimple() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		assertDoesNotThrow(() -> WebKey.verify(jwk));
	}

	@Test
	public void testVerifyIllegalAlgorithm() {
		final var jwk = mock(WebKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		when(jwk.getAlgorithm()).thenReturn(WebKey.Algorithm.A128GCMKW);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Illegal type RSA for algorithm A128GCMKW", error.getMessage());
	}

	@Test
	public void testVerifyValidAlgorithmInvalidUse() {
		final var jwk = mock(WebKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getAlgorithm()).thenReturn(WebKey.Algorithm.ES256);
		when(jwk.getUse()).thenReturn(WebKey.Use.ENCRYPT);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Illegal use ENCRYPT for algorithm ES256", error.getMessage());
	}

	@Test
	public void testVerifyValidAlgorithmValidUse() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getAlgorithm()).thenReturn(WebKey.Algorithm.ES256);
		when(jwk.getUse()).thenReturn(WebKey.Use.SIGN);
		assertDoesNotThrow(() -> WebKey.verify(jwk));
	}

	@Test
	public void testVerifyTooManyOps() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		final var ops = EnumSet.allOf(WebKey.Operation.class);
		when(jwk.getOps()).thenReturn(ops);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Illegal ops " + ops, error.getMessage());
	}

	@Test
	public void testVerifyOpsMismatch() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		final var ops = Set.of(WebKey.Operation.ENCRYPT, WebKey.Operation.WRAP);
		when(jwk.getOps()).thenReturn(ops);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Illegal ops " + ops, error.getMessage());
	}

	@Test
	public void testVerifyInvalidOpsForAlgorithm() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getAlgorithm()).thenReturn(WebKey.Algorithm.ES256);
		final var ops = Set.of(WebKey.Operation.DERIVE_KEY);
		when(jwk.getOps()).thenReturn(ops);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Illegal ops [DERIVE_KEY] for algorithm ES256", error.getMessage());
	}

	@Test
	public void testVerifyInvalidOpsForSign() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getUse()).thenReturn(WebKey.Use.SIGN);
		final var ops = Set.of(WebKey.Operation.DERIVE_KEY);
		when(jwk.getOps()).thenReturn(ops);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Illegal ops [DERIVE_KEY] for use SIGN", error.getMessage());
	}

	@Test
	public void testVerifyInvalidOpsForEncrypt() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getUse()).thenReturn(WebKey.Use.ENCRYPT);
		final var ops = Set.of(WebKey.Operation.SIGN, WebKey.Operation.VERIFY);
		when(jwk.getOps()).thenReturn(ops);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Illegal ops " + ops + " for use ENCRYPT", error.getMessage());
	}

	@Test
	public void testVerifyValidAlgUseAndOpsPublicOnly() {
		final var jwk = mock(WebKey.class, a -> null);
		final var pub = EphemeralKeys.ec(WebKey.algorithmParams("secp256r1")).getPublic();
		when(jwk.getPublicKey()).thenReturn(pub);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getAlgorithm()).thenReturn(WebKey.Algorithm.ES256);
		when(jwk.getUse()).thenReturn(WebKey.Use.SIGN);
		final var ops = Set.of(WebKey.Operation.VERIFY);
		when(jwk.getOps()).thenReturn(ops);
		assertDoesNotThrow(() -> WebKey.verify(jwk));
	}

	@Test
	public void testVerifyValidAlgAndOps() {
		final var jwk = mock(WebKey.class, a -> null);
		final var keypair = EphemeralKeys.ec(WebKey.algorithmParams("secp256r1"));
		when(jwk.getPublicKey()).thenReturn(keypair.getPublic());
		when(jwk.getPrivateKey()).thenReturn(keypair.getPrivate());
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getAlgorithm()).thenReturn(WebKey.Algorithm.ES256);
		final var ops = Set.of(WebKey.Operation.VERIFY, WebKey.Operation.SIGN);
		when(jwk.getOps()).thenReturn(ops);
		assertDoesNotThrow(() -> WebKey.verify(jwk));
	}

	@Test
	public void testVerifyValidAlgUseAndEncryptOps() {
		final var jwk = mock(WebKey.class, a -> null);
		final var keypair = EphemeralKeys.ec(WebKey.algorithmParams("secp256r1"));
		when(jwk.getPublicKey()).thenReturn(keypair.getPublic());
		when(jwk.getPrivateKey()).thenReturn(keypair.getPrivate());
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getAlgorithm()).thenReturn(WebKey.Algorithm.ECDH_ES);
		when(jwk.getUse()).thenReturn(WebKey.Use.ENCRYPT);
		final var ops = Set.of(WebKey.Operation.DERIVE_KEY);
		when(jwk.getOps()).thenReturn(ops);
		assertDoesNotThrow(() -> WebKey.verify(jwk));
	}

	@Test
	public void testVerifyValidKeyWithCert() {
		final var jwk = mock(WebKey.class, a -> null);
		final var cert = mock(X509Certificate.class);
		when(jwk.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		assertDoesNotThrow(() -> WebKey.verify(jwk));
	}

	@Test
	public void testVerifyInvalidPrivateKeyForRaw() {
		final var jwk = mock(WebKey.class, a -> null);
		final var keypair = EphemeralKeys.ec(WebKey.algorithmParams("secp256r1"));
		when(jwk.getPrivateKey()).thenReturn(keypair.getPrivate());
		when(jwk.getType()).thenReturn(WebKey.Type.RAW);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Unexpected private key", error.getMessage());
	}

	@Test
	public void testVerifyInvalidPublicKeyForRaw() {
		final var jwk = mock(WebKey.class, a -> null);
		final var keypair = EphemeralKeys.ec(WebKey.algorithmParams("secp256r1"));
		when(jwk.getPublicKey()).thenReturn(keypair.getPublic());
		when(jwk.getType()).thenReturn(WebKey.Type.RAW);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Unexpected public key", error.getMessage());
	}

	@Test
	public void testVerifyInvalidCertForRaw() {
		final var jwk = mock(WebKey.class, a -> null);
		final var cert = mock(X509Certificate.class);
		when(jwk.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		when(jwk.getType()).thenReturn(WebKey.Type.RAW);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Unexpected certificate", error.getMessage());
	}

	@Test
	public void testVerifyValidCertForRaw() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.RAW);
		assertDoesNotThrow(() -> WebKey.verify(jwk));
	}

	@Test
	public void testVerifyUnexpectedRawKeyForEC() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getKey()).thenReturn(new byte[0]);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Unexpected raw key data for EC_P256", error.getMessage());
	}

	@Test
	public void testVerifyNamedParameterSpecMismatch() {
		final var jwk = mock(WebKey.class, a -> null);
		final var pub = mock(PublicKey.class);
		final var priv = mock(PrivateKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.X25519);
		when(jwk.getPublicKey()).thenReturn(pub);
		when(jwk.getPrivateKey()).thenReturn(priv);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.algorithmParams(pub)).thenReturn(new NamedParameterSpec("X25519"));
			mockWebKey.when(() -> WebKey.algorithmParams(priv)).thenReturn(new NamedParameterSpec("ED25519"));
			mockWebKey.when(() -> WebKey.verify(jwk)).thenCallRealMethod();
			final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
			assertEquals("parameter spec mismatch", error.getMessage());
		}
	}

	@Test
	public void testVerifyParameterSpecPrivNull() {
		final var jwk = mock(WebKey.class, a -> null);
		final var pub = mock(PublicKey.class);
		final var priv = mock(PrivateKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.X25519);
		when(jwk.getPublicKey()).thenReturn(pub);
		when(jwk.getPrivateKey()).thenReturn(priv);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.algorithmParams(pub)).thenReturn(new NamedParameterSpec("X25519"));
			mockWebKey.when(() -> WebKey.verify(jwk)).thenCallRealMethod();
			assertDoesNotThrow(() -> WebKey.verify(jwk));
		}
	}

	@Test
	public void testVerifyNamedParameterSpecMatch() {
		final var jwk = mock(WebKey.class, a -> null);
		final var pub = mock(PublicKey.class);
		final var priv = mock(PrivateKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.X25519);
		when(jwk.getPublicKey()).thenReturn(pub);
		when(jwk.getPrivateKey()).thenReturn(priv);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.algorithmParams(pub)).thenReturn(new NamedParameterSpec("X25519"));
			mockWebKey.when(() -> WebKey.algorithmParams(priv)).thenReturn(new NamedParameterSpec("X25519"));
			mockWebKey.when(() -> WebKey.verify(jwk)).thenCallRealMethod();
			assertDoesNotThrow(() -> WebKey.verify(jwk));
		}
	}

	@Test
	public void testVerifyParameterSpecMismatch() {
		final var jwk = mock(WebKey.class, a -> null);
		final var pub = mock(PublicKey.class);
		final var priv = mock(PrivateKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.X25519);
		when(jwk.getPublicKey()).thenReturn(pub);
		when(jwk.getPrivateKey()).thenReturn(priv);
		final var params = mock(AlgorithmParameterSpec.class);
		final var params2 = mock(AlgorithmParameterSpec.class);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.algorithmParams(pub)).thenReturn(params);
			mockWebKey.when(() -> WebKey.algorithmParams(priv)).thenReturn(params2);
			mockWebKey.when(() -> WebKey.verify(jwk)).thenCallRealMethod();
			final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
			assertEquals("parameter spec mismatch", error.getMessage());
		}
	}

	@Test
	public void testVerifyParameterSpecMatch() {
		final var jwk = mock(WebKey.class, a -> null);
		final var pub = mock(PublicKey.class);
		final var priv = mock(PrivateKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.X25519);
		when(jwk.getPublicKey()).thenReturn(pub);
		when(jwk.getPrivateKey()).thenReturn(priv);
		final var params = mock(AlgorithmParameterSpec.class);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.algorithmParams(pub)).thenReturn(params);
			mockWebKey.when(() -> WebKey.algorithmParams(priv)).thenReturn(params);
			mockWebKey.when(() -> WebKey.verify(jwk)).thenCallRealMethod();
			assertDoesNotThrow(() -> WebKey.verify(jwk));
		}
	}

	@Test
	public void testVerifyValidRSAPublicKey() {
		final var jwk = mock(WebKey.class, a -> null);
		final var keypair = EphemeralKeys.rsa("RSA", 2048);
		when(jwk.getPublicKey()).thenReturn(keypair.getPublic());
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		assertDoesNotThrow(() -> WebKey.verify(jwk));
	}

	@Test
	public void testVerifyValidRSACrtPrivateKey() {
		final var jwk = mock(WebKey.class, a -> null);
		final var keypair = EphemeralKeys.rsa("RSA", 2048);
		when(jwk.getPrivateKey()).thenReturn(keypair.getPrivate());
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		assertEquals(keypair.getPublic(), WebKey.verify(jwk));
	}

	@Test
	public void testVerifyRSAKeyModulusMismatch() {
		final var jwk = mock(WebKey.class, a -> null);
		final var keypair = EphemeralKeys.rsa("RSA", 2048);
		final var keypair2 = EphemeralKeys.rsa("RSA", 2048);
		when(jwk.getPrivateKey()).thenReturn(keypair.getPrivate());
		when(jwk.getPublicKey()).thenReturn(keypair2.getPublic());
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("RSA public key modulus doesn't match private key", error.getMessage());
	}

	@Test
	public void testVerifyRSAKeyExponentMismatch() {
		final var jwk = mock(WebKey.class, a -> null);
		final var keypair = EphemeralKeys.rsa("RSA", 2048);
		final var pub = (RSAPublicKey) spy(keypair.getPublic());
		when(pub.getPublicExponent()).thenReturn(new BigInteger(new byte[1]));
		when(jwk.getPrivateKey()).thenReturn(keypair.getPrivate());
		when(jwk.getPublicKey()).thenReturn(pub);
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("RSA public key exponent doesn't match private key", error.getMessage());
	}

	@Test
	public void testVerifyValidRSACrtKeyPair() {
		final var jwk = mock(WebKey.class, a -> null);
		final var keypair = EphemeralKeys.rsa("RSA", 2048);
		when(jwk.getPublicKey()).thenReturn(keypair.getPublic());
		when(jwk.getPrivateKey()).thenReturn(keypair.getPrivate());
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		assertEquals(keypair.getPublic(), WebKey.verify(jwk));
	}

	@Test
	public void testVerifyValidRSAKeyPair() {
		final var jwk = mock(WebKey.class, a -> null);
		final var mod = new BigInteger(new byte[1]);
		final var pub = mock(RSAPublicKey.class);
		final var priv = mock(RSAPrivateKey.class);
		when(pub.getModulus()).thenReturn(mod);
		when(priv.getModulus()).thenReturn(mod);
		when(jwk.getPublicKey()).thenReturn(pub);
		when(jwk.getPrivateKey()).thenReturn(priv);
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		assertEquals(pub, WebKey.verify(jwk));
	}

	@Test
	public void testVerifyValidRSAPrivate() {
		final var jwk = mock(WebKey.class, a -> null);
		final var mod = new BigInteger(new byte[1]);
		final var priv = mock(RSAPrivateKey.class);
		when(priv.getModulus()).thenReturn(mod);
		when(jwk.getPrivateKey()).thenReturn(priv);
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		assertDoesNotThrow(() -> WebKey.verify(jwk));
	}

	@Test
	public void testVerifyMissingParams() {
		final var jwk = mock(WebKey.class, a -> null);
		final var priv = mock(PrivateKey.class);
		when(jwk.getPrivateKey()).thenReturn(priv);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Missing algorithm parameters", error.getMessage());
	}

	@Test
	public void testVerifySecretKeyRequiredForDecrypt() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getOps()).thenReturn(Set.of(WebKey.Operation.DECRYPT));
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Secret key required by ops [DECRYPT]", error.getMessage());
	}

	@Test
	public void testVerifySecretKeyRequiredForEncrypt() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getOps()).thenReturn(Set.of(WebKey.Operation.ENCRYPT));
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Secret key required by ops [ENCRYPT]", error.getMessage());
	}

	@Test
	public void testVerifyCertificatePublicKeyMismatch() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		final var pub = mock(PublicKey.class);
		when(jwk.getPublicKey()).thenReturn(pub);
		final var cert = mock(X509Certificate.class);
		final var pub2 = mock(PublicKey.class);
		when(cert.getPublicKey()).thenReturn(pub2);
		when(jwk.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("public key doesn't match X.509 certificate", error.getMessage());
	}

	@Test
	public void testVerifyWrapRequiresPublicKey() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getOps()).thenReturn(Set.of(WebKey.Operation.WRAP));
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Public key required by ops [WRAP]", error.getMessage());
	}

	@Test
	public void testVerifyVerifyRequiresPublicKey() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getOps()).thenReturn(Set.of(WebKey.Operation.VERIFY));
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Public key required by ops [VERIFY]", error.getMessage());
	}

	@Test
	public void testVerifyUnrapRequiresPrivateKey() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getOps()).thenReturn(Set.of(WebKey.Operation.UNWRAP));
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Private key required by ops [UNWRAP]", error.getMessage());
	}

	@Test
	public void testVerifySignRequiresPublicKey() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getOps()).thenReturn(Set.of(WebKey.Operation.SIGN));
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Private key required by ops [SIGN]", error.getMessage());
	}

	@Test
	public void testVerifyDeriveKeyRequiresPublicOrPrivateKey() {
		final var jwk = mock(WebKey.class, a -> null);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getOps()).thenReturn(Set.of(WebKey.Operation.DERIVE_KEY));
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKey.verify(jwk));
		assertEquals("Public or private key required by ops [DERIVE_KEY]", error.getMessage());
	}

	@Test
	public void testVerifyDeriveKeyValidWithPublicKey() {
		final var jwk = mock(WebKey.class, a -> null);
		final var pub = mock(PublicKey.class);
		final var params = mock(AlgorithmParameterSpec.class);
		when(jwk.getPublicKey()).thenReturn(pub);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		when(jwk.getOps()).thenReturn(Set.of(WebKey.Operation.DERIVE_KEY));
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.algorithmParams(pub)).thenReturn(params);
			mockWebKey.when(() -> WebKey.verify(jwk)).thenCallRealMethod();
			assertDoesNotThrow(() -> WebKey.verify(jwk));
		}
	}

	@Test
	public void testBuilderSecretKey() {
		final var key = mock(SecretKey.class);
		final var encoded = new byte[0];
		when(key.getEncoded()).thenReturn(encoded);
		final var builder = mock(Builder.class);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.builder(key)).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.builder(Type.RAW)).thenReturn(builder);
			when(builder.key(encoded)).thenReturn(builder);
			assertSame(builder, WebKey.builder(key));
		}
	}

	@Test
	public void testBuilderRSAPrivateKey() {
		final var key = EphemeralKeys.rsa("RSA", 2048).getPrivate();
		final var builder = mock(Builder.class);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.builder(key)).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.builder(Type.RSA)).thenReturn(builder);
			assertSame(builder, WebKey.builder(key));
			verify(builder).key(key);
		}
	}

	@Test
	public void testBuilderECPublicKey() {
		final var key = EphemeralKeys.ec(WebKey.algorithmParams("secp256r1")).getPublic();
		final var builder = mock(Builder.class);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.builder(key)).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.algorithmParams(key)).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.algorithmParams("secp256r1")).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.builder(Type.EC_P256)).thenReturn(builder);
			assertSame(builder, WebKey.builder(key));
			verify(builder).key(key);
		}
	}

	@Test
	public void testBuilderFromType() {
		final var type = IuTest.rand(WebKey.Type.class);
		WebKey.builder(type);
		verify(Init.SPI).getJwkBuilder(type);
	}

	@Test
	public void testBuilderFromAlgorithm() {
		final var alg = IuTest.rand(WebKey.Algorithm.class);
		final var builder = mock(Builder.class);
		when(builder.algorithm(alg)).thenReturn(builder);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.builder(alg)).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.builder(alg.type[0])).thenReturn(builder);
			assertSame(builder, WebKey.builder(alg));
			verify(builder).algorithm(alg);
		}
	}

	@Test
	public void testEphemeralFromEncryption() {
		final var key = mock(WebKey.class);
		final var enc = IuTest.rand(WebEncryption.Encryption.class);
		final var builder = mock(Builder.class);
		when(builder.build()).thenReturn(key);
		when(builder.ephemeral(enc)).thenReturn(builder);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.ephemeral(enc)).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.builder(Type.RAW)).thenReturn(builder);
			assertSame(key, WebKey.ephemeral(enc));
			verify(builder).ephemeral(enc);
		}
	}

	@Test
	public void testEphemeralFromAlgorithm() {
		final var alg = IuTest.rand(WebKey.Algorithm.class);
		final var key = mock(WebKey.class);
		final var builder = mock(Builder.class);
		when(builder.build()).thenReturn(key);
		when(builder.ephemeral(alg)).thenReturn(builder);
		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.ephemeral(alg)).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.builder(alg.type[0])).thenReturn(builder);
			assertSame(key, WebKey.ephemeral(alg));
			verify(builder).ephemeral(alg);
		}
	}

	@Test
	public void testParse() {
		final var jwk = IdGenerator.generateId();
		WebKey.parse(jwk);
		verify(Init.SPI).parseJwk(jwk);
	}

	@Test
	public void testParseJwks() {
		final var jwks = IdGenerator.generateId();
		WebKey.parseJwks(jwks);
		verify(Init.SPI).parseJwks(jwks);
	}

	@Test
	public void testPemOneCert() {
		final var encoded = IdGenerator.generateId();
		final var cert = mock(X509Certificate.class);
		final var pub = mock(PublicKey.class);
		when(cert.getPublicKey()).thenReturn(pub);
		final var pemEncoded = mock(PemEncoded.class);
		when(pemEncoded.asCertificate()).thenReturn(cert);
		when(pemEncoded.getKeyType()).thenReturn(KeyType.CERTIFICATE);
		final var builder = mock(Builder.class);
		final var key = mock(WebKey.class);
		when(builder.build()).thenReturn(key);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class); final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.pem(encoded)).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.builder(pub)).thenReturn(builder);
			mockPemEncoded.when(() -> PemEncoded.parse(encoded)).thenReturn(IuIterable.iter(pemEncoded).iterator());

			assertSame(key, WebKey.pem(encoded));
			verify(builder).cert(cert);
		}
	}

	@Test
	public void testPemWithKeyPair() {
		final var encoded = IdGenerator.generateId();
		final var cert = mock(X509Certificate.class);
		final var pub = mock(PublicKey.class);
		final var priv = mock(PrivateKey.class);
		final var algorithm = IdGenerator.generateId();
		when(priv.getAlgorithm()).thenReturn(algorithm);
		when(cert.getPublicKey()).thenReturn(pub);
		final var pemEncodedCert = mock(PemEncoded.class);
		when(pemEncodedCert.asCertificate()).thenReturn(cert);
		when(pemEncodedCert.getKeyType()).thenReturn(KeyType.CERTIFICATE);
		final var pemEncodedPub = mock(PemEncoded.class);
		when(pemEncodedPub.asPublic(algorithm)).thenReturn(pub);
		when(pemEncodedPub.getKeyType()).thenReturn(KeyType.PUBLIC_KEY);
		final var pemEncodedPriv = mock(PemEncoded.class);
		when(pemEncodedPriv.asPrivate(algorithm)).thenReturn(priv);
		when(pemEncodedPriv.getKeyType()).thenReturn(KeyType.PRIVATE_KEY);
		final var builder = mock(Builder.class);
		final var key = mock(WebKey.class);
		when(builder.build()).thenReturn(key);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class); final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.pem(encoded)).thenCallRealMethod();
			mockWebKey.when(() -> WebKey.builder(pub)).thenReturn(builder);
			mockPemEncoded.when(() -> PemEncoded.parse(encoded))
					.thenReturn(IuIterable.iter(pemEncodedCert, pemEncodedPub, pemEncodedPriv).iterator());

			assertSame(key, WebKey.pem(encoded));
			verify(builder).cert(cert);
		}
	}

	@Test
	public void testReadJwksUri() {
		final var jwks = mock(URI.class);
		WebKey.readJwks(jwks);
		verify(Init.SPI).readJwks(jwks);
	}

	@Test
	public void testReadJwksInputStream() {
		final var jwks = mock(InputStream.class);
		WebKey.readJwks(jwks);
		verify(Init.SPI).readJwks(jwks);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAsJwksIterable() {
		final var jwks = mock(Iterable.class);
		WebKey.asJwks(jwks);
		verify(Init.SPI).asJwks(jwks);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testWriteJwksIterable() {
		final var jwks = mock(Iterable.class);
		final var out = mock(OutputStream.class);
		WebKey.writeJwks(jwks, out);
		verify(Init.SPI).writeJwks(jwks, out);
	}

}