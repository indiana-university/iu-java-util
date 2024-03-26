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

import java.net.URI;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import jakarta.json.JsonObjectBuilder;

/**
 * JSON Web Key (JWK) implementation.
 */
class Jwk implements WebKey {

	/**
	 * Adds RSA key pair attributes to an in-progress JWK builder.
	 * 
	 * @param jwkBuilder JWK builder
	 * @param pub        public key
	 * @param priv       private key
	 */
	static void writeRSA(JsonObjectBuilder jwkBuilder, RSAPublicKey pub, RSAPrivateKey priv) {
		IuJson.add(jwkBuilder, "n", pub::getModulus, UnsignedBigInteger.JSON);
		IuJson.add(jwkBuilder, "e", pub::getPublicExponent, UnsignedBigInteger.JSON);
		if (priv == null)
			return;

		IuJson.add(jwkBuilder, "d", priv::getPrivateExponent, UnsignedBigInteger.JSON);
		if (priv instanceof RSAPrivateCrtKey) {
			final var crt = (RSAPrivateCrtKey) priv;
			IuJson.add(jwkBuilder, "p", crt::getPrimeP, UnsignedBigInteger.JSON);
			IuJson.add(jwkBuilder, "q", crt::getPrimeQ, UnsignedBigInteger.JSON);
			IuJson.add(jwkBuilder, "dp", crt::getPrimeExponentP, UnsignedBigInteger.JSON);
			IuJson.add(jwkBuilder, "dq", crt::getPrimeExponentQ, UnsignedBigInteger.JSON);
			IuJson.add(jwkBuilder, "qi", crt::getCrtCoefficient, UnsignedBigInteger.JSON);
			return;
		}
	}

	/**
	 * Adds Elliptic Curve key pair attributes to an in-progress JWK builder.
	 * 
	 * @param jwkBuilder JWK builder
	 * @param type       key type
	 * @param pub        public key
	 * @param priv       private key
	 */
	static void writeEC(JsonObjectBuilder jwkBuilder, Type type, ECPublicKey pub, ECPrivateKey priv) {
		jwkBuilder.add("crv", type.crv);

		final var w = pub.getW();
		IuJson.add(jwkBuilder, "x", w::getAffineX, UnsignedBigInteger.JSON);
		IuJson.add(jwkBuilder, "y", w::getAffineY, UnsignedBigInteger.JSON);

		if (priv != null)
			IuJson.add(jwkBuilder, "d", priv::getS, UnsignedBigInteger.JSON);
	}

	private final String id;
	private final Type type;
	private final Use use;
	private final byte[] key;
	private final PublicKey publicKey;
	private final PrivateKey privateKey;
	private final Set<Op> ops;
	private final Algorithm algorithm;
	private final URI certificateUri;
	private final X509Certificate[] certificateChain;
	private final byte[] certificateThumbprint;
	private final byte[] certificateSha256Thumbprint;

	/**
	 * Constructor.
	 * 
	 * <p>
	 * Only for use by {@link JwkBuilder}.
	 * </p>
	 * 
	 * @param id                          key ID
	 * @param type                        key type
	 * @param use                         public key use
	 * @param key                         raw encoded key
	 * @param publicKey                   public key
	 * @param privateKey                  private key
	 * @param ops                         key operations
	 * @param algorithm                   algorithm
	 * @param certificateUri              certificate URI
	 * @param certificateChain            certificate chain
	 * @param certificateThumbprint       certificate thumbprint
	 * @param certificateSha256Thumbprint certificate SHA-256 thumbprint
	 */
	Jwk(String id, Type type, Use use, byte[] key, PublicKey publicKey, PrivateKey privateKey, Set<Op> ops,
			Algorithm algorithm, URI certificateUri, X509Certificate[] certificateChain, byte[] certificateThumbprint,
			byte[] certificateSha256Thumbprint) {
		this.id = id;
		this.type = type;
		this.use = use;
		this.key = key;
		this.publicKey = publicKey;
		this.privateKey = privateKey;
		this.ops = ops;
		this.algorithm = algorithm;
		this.certificateUri = certificateUri;
		this.certificateChain = certificateChain;
		this.certificateThumbprint = certificateThumbprint;
		this.certificateSha256Thumbprint = certificateSha256Thumbprint;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public Use getUse() {
		return use;
	}

	@Override
	public byte[] getKey() {
		return key;
	}

	@Override
	public PublicKey getPublicKey() {
		return publicKey;
	}

	@Override
	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	@Override
	public Set<Op> getOps() {
		return ops;
	}

	@Override
	public Algorithm getAlgorithm() {
		return algorithm;
	}

	@Override
	public URI getCertificateUri() {
		return certificateUri;
	}

	@Override
	public X509Certificate[] getCertificateChain() {
		return certificateChain;
	}

	@Override
	public byte[] getCertificateThumbprint() {
		return certificateThumbprint;
	}

	@Override
	public byte[] getCertificateSha256Thumbprint() {
		return certificateSha256Thumbprint;
	}

	@Override
	public Jwk wellKnown() {
		if (privateKey == null && key == null)
			return this;
		else
			return new Jwk(id, type, use, null, publicKey, null, ops, algorithm, certificateUri, certificateChain,
					certificateThumbprint, certificateSha256Thumbprint);
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(id, type, use, key, publicKey, privateKey, ops, algorithm, certificateUri,
				certificateChain, certificateThumbprint, certificateSha256Thumbprint);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		Jwk other = (Jwk) obj;
		return algorithm == other.algorithm //
				&& IuObject.equals(certificateChain, other.certificateChain)
				&& IuObject.equals(certificateSha256Thumbprint, other.certificateSha256Thumbprint)
				&& IuObject.equals(certificateThumbprint, other.certificateThumbprint)
				&& IuObject.equals(certificateUri, other.certificateUri) //
				&& IuObject.equals(id, other.id) //
				&& IuObject.equals(key, other.key) //
				&& IuObject.equals(ops, other.ops) //
				&& IuObject.equals(privateKey, other.privateKey) //
				&& IuObject.equals(publicKey, other.publicKey) //
				&& type == other.type && use == other.use;
	}

	@Override
	public String toString() {
		final var jwkBuilder = IuJson.object();
		serializeTo(jwkBuilder);
		return jwkBuilder.build().toString();
	}

	/**
	 * Adds serialized JWK attributes to a JSON object builder.
	 * 
	 * @param jwkBuilder {@link JsonObjectBuilder}
	 */
	void serializeTo(JsonObjectBuilder jwkBuilder) {
		IuJson.add(jwkBuilder, "kid", id);
		IuJson.add(jwkBuilder, "use", () -> use, Use.JSON);
		IuJson.add(jwkBuilder, "kty", () -> type, IuJsonAdapter.to(a -> IuJson.string(a.kty)));
		IuJson.add(jwkBuilder, "alg", () -> algorithm, Algorithm.JSON);
		IuJson.add(jwkBuilder, "key_ops", () -> ops, IuJsonAdapter.of(Set.class, Op.JSON));
		IuJson.add(jwkBuilder, "x5u", () -> certificateUri, IuJsonAdapter.of(URI.class));
		IuJson.add(jwkBuilder, "x5c", () -> certificateChain,
				IuJsonAdapter.of(X509Certificate[].class, PemEncoded.CERT_JSON));
		IuJson.add(jwkBuilder, "x5t", () -> certificateThumbprint, UnpaddedBinary.JSON);
		IuJson.add(jwkBuilder, "x5t#S256", () -> certificateSha256Thumbprint, UnpaddedBinary.JSON);

		if (publicKey instanceof ECPublicKey) {
			final Type type;
			if (this.type != null)
				type = this.type;
			else
				type = algorithm.type;
			writeEC(jwkBuilder, type, (ECPublicKey) publicKey, (ECPrivateKey) privateKey);
		} else if (publicKey instanceof RSAPublicKey)
			writeRSA(jwkBuilder, (RSAPublicKey) publicKey, (RSAPrivateKey) privateKey);
		else
			IuJson.add(jwkBuilder, "k", () -> key, UnpaddedBinary.JSON);
	}

	private static boolean eitherNullOrBothEquals(Object a, Object b) {
		return a == null || b == null || IuObject.equals(a, b);
	}

	/**
	 * Determines whether or not the known components of this key match the known
	 * components of another key.
	 * 
	 * @param key {@link WebKey}
	 * @return true if all non-null components of both keys match
	 */
	boolean represents(Jwk key) {
		return eitherNullOrBothEquals(algorithm, key.algorithm) //
				&& eitherNullOrBothEquals(certificateChain, key.certificateChain)
				&& eitherNullOrBothEquals(certificateSha256Thumbprint, key.certificateSha256Thumbprint)
				&& eitherNullOrBothEquals(certificateThumbprint, key.certificateThumbprint)
				&& eitherNullOrBothEquals(certificateUri, key.certificateUri) //
				&& eitherNullOrBothEquals(id, key.id) //
				&& eitherNullOrBothEquals(key, key.key) //
				&& eitherNullOrBothEquals(ops, key.ops) //
				&& eitherNullOrBothEquals(privateKey, key.privateKey) //
				&& eitherNullOrBothEquals(publicKey, key.publicKey) //
				&& eitherNullOrBothEquals(type, key.type) //
				&& (algorithm == null || key.type == null || algorithm.type == key.type) //
				&& eitherNullOrBothEquals(use, key.use) //
				&& (algorithm == null || key.use == null || algorithm.use == key.use);

	}

}
