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
import java.util.Base64;
import java.util.Set;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey;
import jakarta.json.JsonObject;
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
		EncodingUtils.setBigInt(jwkBuilder, "n", pub.getModulus());
		EncodingUtils.setBigInt(jwkBuilder, "e", pub.getPublicExponent());
		if (priv == null)
			return;

		jwkBuilder.add("d", IuText.base64Url(priv.getPrivateExponent().toByteArray()));
		if (priv instanceof RSAPrivateCrtKey) {
			final var crt = (RSAPrivateCrtKey) priv;
			EncodingUtils.setBigInt(jwkBuilder, "p", crt.getPrimeP());
			EncodingUtils.setBigInt(jwkBuilder, "q", crt.getPrimeQ());
			EncodingUtils.setBigInt(jwkBuilder, "dp", crt.getPrimeExponentP());
			EncodingUtils.setBigInt(jwkBuilder, "dq", crt.getPrimeExponentQ());
			EncodingUtils.setBigInt(jwkBuilder, "qi", crt.getCrtCoefficient());
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
		EncodingUtils.setBigInt(jwkBuilder, "x", w.getAffineX());
		EncodingUtils.setBigInt(jwkBuilder, "y", w.getAffineY());

		if (priv != null)
			EncodingUtils.setBigInt(jwkBuilder, "d", priv.getS());
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
		IuJson.add(jwkBuilder, "use", () -> use, a -> IuJson.toJson(a.use));
		IuJson.add(jwkBuilder, "kty", () -> type, a -> IuJson.toJson(a.kty));
		IuJson.add(jwkBuilder, "alg", () -> algorithm, a -> IuJson.toJson(a.alg));
		IuJson.add(jwkBuilder, "key_ops", () -> ops, a -> IuJson.toJson(a.stream().map(op -> op.keyOp)));
		IuJson.add(jwkBuilder, "x5u", () -> certificateUri, a -> IuJson.toJson(a.toString()));
		IuJson.add(jwkBuilder, "x5c", () -> certificateChain, a -> IuJson.toJson(
				Stream.of(a).map(cert -> Base64.getEncoder().encodeToString(IuException.unchecked(cert::getEncoded)))));
		EncodingUtils.setBytes(jwkBuilder, "x5t", certificateThumbprint);
		EncodingUtils.setBytes(jwkBuilder, "x5t#S256", certificateSha256Thumbprint);

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
			EncodingUtils.setBytes(jwkBuilder, "k", key);
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
