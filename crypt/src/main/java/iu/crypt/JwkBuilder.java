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

import java.io.InputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;

import edu.iu.IdGenerator;
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

	/**
	 * Constructor.
	 * 
	 * @param type {@link WebKey.Type}
	 */
	public JwkBuilder(Type type) {
		param("kty", type.kty);
		if (type.crv != null)
			param("crv", type.crv);
	}

	@Override
	public JwkBuilder use(Use use) {
		return param("use", use, Use.JSON);
	}

	@Override
	public JwkBuilder ops(Operation... ops) {
		return param("key_ops", ops, IuJsonAdapter.of(Operation[].class, Operation.JSON));
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
			key(EphemeralKeys.ec(algorithm.type.ecParam));
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
			key(EphemeralKeys.rsa(algorithm.type.kty, 2048));
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
		return param("k", key, UnpaddedBinary.JSON);
	}

	@Override
	public JwkBuilder key(PublicKey publicKey) {
		if (publicKey instanceof RSAPublicKey) {
			final var rsa = (RSAPublicKey) publicKey;
			return param("n", rsa.getModulus(), UnsignedBigInteger.JSON) //
					.param("e", rsa.getPublicExponent(), UnsignedBigInteger.JSON);
		} else if (publicKey instanceof ECPublicKey) {
			final var w = ((ECPublicKey) publicKey).getW();
			return param("x", w.getAffineX(), UnsignedBigInteger.JSON) //
					.param("y", w.getAffineY(), UnsignedBigInteger.JSON);
		} else
			throw new UnsupportedOperationException();
	}

	@Override
	public JwkBuilder key(PrivateKey key) {
		if (key instanceof RSAPrivateKey) {
			final var rsa = (RSAPrivateKey) key;
			param("n", rsa.getModulus(), UnsignedBigInteger.JSON);
			param("d", rsa.getPrivateExponent(), UnsignedBigInteger.JSON);
			if (rsa instanceof RSAPrivateCrtKey) {
				final var crt = (RSAPrivateCrtKey) rsa;
				param("e", crt.getPublicExponent(), UnsignedBigInteger.JSON);
				param("p", crt.getPrimeP(), UnsignedBigInteger.JSON);
				param("q", crt.getPrimeQ(), UnsignedBigInteger.JSON);
				param("dp", crt.getPrimeExponentP(), UnsignedBigInteger.JSON);
				param("dq", crt.getPrimeExponentQ(), UnsignedBigInteger.JSON);
				param("qi", crt.getCrtCoefficient(), UnsignedBigInteger.JSON);
			}
			return this;
		} else if (key instanceof ECPrivateKey)
			return param("d", ((ECPrivateKey) key).getS(), UnsignedBigInteger.JSON);
		else
			throw new UnsupportedOperationException();
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

	private Type type() {
		return Type.from(((JsonString) param("kty")).getString(),
				IuObject.convert((JsonString) param("crv"), JsonString::getString));
	}

	private Algorithm alg() {
		return Algorithm.JSON.fromJson(Objects.requireNonNull(param("alg"), "algorithm is required"));
	}

	private void pem(Iterator<PemEncoded> pem) {
		final Queue<X509Certificate> certChain = new ArrayDeque<>();
		while (pem.hasNext()) {
			final var next = pem.next();
			switch (next.getKeyType()) {
			case PRIVATE_KEY:
				key(next.asPrivate(type()));
				break;

			case PUBLIC_KEY:
				key(next.asPublic(type()));
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
