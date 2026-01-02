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

import java.io.InputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.XECPrivateKey;
import java.security.spec.NamedParameterSpec;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Builder;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

/**
 * JWK {@link Builder} implementation.
 */
public class JwkBuilder extends KeyReferenceBuilder<JwkBuilder> implements Builder<JwkBuilder> {
	static {
		IuObject.assertNotOpen(JweBuilder.class);
	}

	private static final BigInteger X25519_P = BigInteger.TWO.pow(255).add(BigInteger.valueOf(-19L));
	private static final BigInteger X448_P = BigInteger.TWO.pow(448)
			.add(BigInteger.TWO.pow(224).multiply(BigInteger.valueOf(-1L))).add(BigInteger.valueOf(-1L));

	/**
	 * Creates a new {@link Builder}.
	 * 
	 * @param type key type
	 * @return {@link Builder}
	 */
	public static Builder<?> of(Type type) {
		return new JwkBuilder().type(type);
	}

	private JwkBuilder() {
	}

	@Override
	public JwkBuilder type(Type type) {
		param("kty", type.kty);
		if (type.crv != null)
			param("crv", type.crv);
		return this;
	}

	@Override
	public JwkBuilder use(Use use) {
		return param("use", use, CryptJsonAdapters.USE);
	}

	@Override
	public JwkBuilder ops(Operation... ops) {
		return param("key_ops", ops, IuJsonAdapter.of(Operation[].class, CryptJsonAdapters.OP));
	}

	@Override
	public JwkBuilder ephemeral() {
		return ephemeral(alg());
	}

	@SuppressWarnings("deprecation")
	@Override
	public JwkBuilder ephemeral(Algorithm algorithm) {
		switch (algorithm) {
		case A128GCMKW:
		case A192GCMKW:
		case A256GCMKW:
		case A128KW:
		case A192KW:
		case A256KW:
			key(EphemeralKeys.secret("AES", algorithm.size));
			break;

		case HS256:
		case HS384:
		case HS512:
			key(EphemeralKeys.secret(algorithm.algorithm, algorithm.size));
			break;

		case ECDH_ES:
		case ECDH_ES_A128KW:
		case ECDH_ES_A192KW:
		case ECDH_ES_A256KW:
		case ES256:
		case ES384:
		case ES512:
		case EDDSA:
			key(EphemeralKeys.ec(
					Objects.requireNonNull(WebKey.algorithmParams(type().algorithmParams), type() + " not supported")));
			break;

		case PS256:
		case PS384:
		case PS512:
		case RS256:
		case RS384:
		case RS512:
		case RSA1_5:
		case RSA_OAEP:
		case RSA_OAEP_256:
			key(EphemeralKeys.rsa(algorithm.type[0].kty, 2048));
			break;

		case PBES2_HS256_A128KW:
		case PBES2_HS384_A192KW:
		case PBES2_HS512_A256KW:
			key(IuText.utf8(IdGenerator.generateId()));
			break;

		default:
			throw new UnsupportedOperationException();
		}
		return this;
	}

	@Override
	public JwkBuilder ephemeral(Encryption encryption) {
		return key(EphemeralKeys.contentEncryptionKey(encryption.mac, encryption.size));
	}

	@Override
	public JwkBuilder key(byte[] key) {
		type(Type.RAW);
		return param("k", key, CryptJsonAdapters.B64URL);
	}

	@Override
	public JwkBuilder key(PublicKey key) {
		type(key);
		if (key instanceof RSAPublicKey) {
			final var rsa = (RSAPublicKey) key;
			return param("n", rsa.getModulus(), CryptJsonAdapters.BIGINT) //
					.param("e", rsa.getPublicExponent(), CryptJsonAdapters.BIGINT);
		} else if (key instanceof ECPublicKey) {
			final var w = ((ECPublicKey) key).getW();
			return param("x", w.getAffineX(), CryptJsonAdapters.BIGINT) //
					.param("y", w.getAffineY(), CryptJsonAdapters.BIGINT);
		} else
			return IuException.unchecked(() -> {
				// TODO: convert to compiled code for source level 17+
				// EdDSA support was introduced in JDK 15
				// XDH was experimental in JDK 11

				final var xkeyClass = ClassLoader.getPlatformClassLoader()
						.loadClass("java.security.interfaces.XECPublicKey");
				if (xkeyClass.isInstance(key)) {
					final var spec = (NamedParameterSpec) xkeyClass.getMethod("getParams").invoke(key);
					final var u = (BigInteger) xkeyClass.getMethod("getU").invoke(key);
					final int l;
					final BigInteger p;
					if (spec.getName().equals(Type.X25519.algorithmParams)) {
						l = 32;
						p = X25519_P;
					} else {
						l = 57;
						p = X448_P;
					}
					return param("x", Arrays.copyOf(EncodingUtils.reverse(UnsignedBigInteger.bigInt(u.mod(p))), l),
							CryptJsonAdapters.B64URL);
				} else {
					final var keyClass = ClassLoader.getPlatformClassLoader()
							.loadClass("java.security.interfaces.EdECPublicKey");
					final var spec = (NamedParameterSpec) keyClass.getMethod("getParams").invoke(key);
					final var l = spec.getName().equals(Type.ED25519.algorithmParams) ? 32 : 57;

					final var pointClass = ClassLoader.getPlatformClassLoader()
							.loadClass("java.security.spec.EdECPoint");
					final var point = keyClass.getMethod("getPoint").invoke(key);
					final var yint = (BigInteger) pointClass.getMethod("getY").invoke(point);
					final var xodd = (boolean) pointClass.getMethod("isXOdd").invoke(point);

					// Convert from JCE EdECPoint to RFC-8032 encoded format
					// https://datatracker.ietf.org/doc/html/rfc8032#section-5.1.2
					// https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/EdECPoint.html
					final var a = UnsignedBigInteger.bigInt(yint);
					final var y = Arrays.copyOf(EncodingUtils.reverse(a), l);
					if (xodd)
						y[l - 1] |= 0x80;

					// convert from big- to little-endian
					return param("x", y, CryptJsonAdapters.B64URL);
				}
			});
	}

	@Override
	public JwkBuilder key(PrivateKey key) {
		type(key);
		if (key instanceof RSAPrivateKey) {
			final var rsa = (RSAPrivateKey) key;
			param("n", rsa.getModulus(), CryptJsonAdapters.BIGINT);
			param("d", rsa.getPrivateExponent(), CryptJsonAdapters.BIGINT);
			if (rsa instanceof RSAPrivateCrtKey) {
				final var crt = (RSAPrivateCrtKey) rsa;
				param("e", crt.getPublicExponent(), CryptJsonAdapters.BIGINT);
				param("p", crt.getPrimeP(), CryptJsonAdapters.BIGINT);
				param("q", crt.getPrimeQ(), CryptJsonAdapters.BIGINT);
				param("dp", crt.getPrimeExponentP(), CryptJsonAdapters.BIGINT);
				param("dq", crt.getPrimeExponentQ(), CryptJsonAdapters.BIGINT);
				param("qi", crt.getCrtCoefficient(), CryptJsonAdapters.BIGINT);
			}
			return this;
		} else if (key instanceof ECPrivateKey)
			return param("d", ((ECPrivateKey) key).getS(), CryptJsonAdapters.BIGINT);
		else if (key instanceof XECPrivateKey)
			return param("d", ((XECPrivateKey) key).getScalar().get(), CryptJsonAdapters.B64URL);
		else
			return IuException.unchecked(() -> {
				// EdDSA support was introduced in JDK 15, not supported by JDK 11
				// TODO: convert to compiled code for source level 17+
				final var keyClass = ClassLoader.getPlatformClassLoader()
						.loadClass("java.security.interfaces.EdECPrivateKey");
				@SuppressWarnings("unchecked")
				final var bytes = (Optional<byte[]>) keyClass.getMethod("getBytes").invoke(key);
				return param("d", bytes.get(), CryptJsonAdapters.B64URL);
			});
	}

	@Override
	public JwkBuilder key(KeyPair keyPair) {
		IuObject.convert(keyPair, a -> key(a.getPublic()));
		return IuObject.convert(keyPair, a -> key(a.getPrivate()));
	}

	@Override
	public JwkBuilder pem(InputStream pemEncoded) {
		pem(PemEncoded.parse(pemEncoded));
		return this;
	}

	@Override
	public JwkBuilder pem(String pemEncoded) {
		pem(PemEncoded.parse(pemEncoded));
		return this;
	}

	@Override
	public Jwk build() {
		return new Jwk(toJson());
	}

	@Override
	protected JsonObjectBuilder build(JsonObjectBuilder builder) {
		return super.build(builder);
	}

	private void type(Key key) {
		final var params = WebKey.algorithmParams(key);
		if (params == null)
			type(Type.from(key.getAlgorithm(), null));
		else
			type(Objects.requireNonNull(Type.from(params), params + " " + key));
	}

	private Type type() {
		return Type.from(Objects.requireNonNull((JsonString) param("kty"), "Missing key type").getString(),
				IuObject.convert((JsonString) param("crv"), JsonString::getString));
	}

	private Algorithm alg() {
		return CryptJsonAdapters.ALG.fromJson(Objects.requireNonNull(param("alg"), "algorithm is required"));
	}

	private void pem(Iterator<PemEncoded> pem) {
		final var type = type();
		final var keyAlg = type.kty.equals("OKP") ? type.algorithmParams : type.kty;
		final Queue<X509Certificate> certChain = new ArrayDeque<>();
		while (pem.hasNext()) {
			final var next = pem.next();

			switch (next.getKeyType()) {
			case PRIVATE_KEY:
				key(next.asPrivate(keyAlg));
				break;

			case PUBLIC_KEY:
				key(next.asPublic(keyAlg));
				break;

			case CERTIFICATE:
			default:
				certChain.offer(next.asCertificate());
				break;
			}
		}

		if (!certChain.isEmpty())
			cert(certChain.toArray(X509Certificate[]::new));
	}

}
