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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * JSON implementation of {@link WebSignature}.
 */
public class Jws implements WebSignature {
	static {
		IuObject.assertNotOpen(Jws.class);
	}

	private final JsonObject protectedHeader;
	private final Jose header;
	private final byte[] signature;

	/**
	 * Creates a new signed message;
	 * 
	 * @param protectedHeader JWS protected header
	 * @param header          JWS unprotected header
	 * @param signature       signature
	 */
	Jws(JsonObject protectedHeader, Jose header, byte[] signature) {
		this.protectedHeader = protectedHeader;
		this.header = header;
		this.signature = signature;

		final var algorithm = header.getAlgorithm();
		if (!algorithm.use.equals(Use.SIGN))
			throw new IllegalArgumentException("Signature algorithm is required");

		if (protectedHeader != null)
			for (final var protectedEntry : protectedHeader.entrySet()) {
				final var name = protectedEntry.getKey();
				final var value = protectedEntry.getValue();
				final var param = Param.from(name);

				if (param == null) {
					final var ext = Jose.getExtension(name);
					if (!IuObject.equals(ext.fromJson(value), header.getExtendedParameter(name)))
						throw new IllegalArgumentException(name + " must match protected header");
				} else if (!IuObject.equals(CryptJsonAdapters.of(param).fromJson(value), param.get(header)))
					throw new IllegalArgumentException(name + " must match protected header");
			}

		for (final var name : header.extendedParameters().keySet())
			Jose.getExtension(name).verify(this);
	}

	@Override
	public WebCryptoHeader getHeader() {
		return header;
	}

	@Override
	public byte[] getSignature() {
		return signature;
	}

	@Override
	public void verify(byte[] payload, WebKey key) {
		final var algorithm = header.getAlgorithm();
		final var signingInput = getSignatureInput(payload);
		final var dataToSign = IuText.utf8(signingInput);

		if (algorithm.algorithm.startsWith("Hmac")) {
			if (!Arrays.equals(signature, IuException.unchecked(() -> {
				final var mac = Mac.getInstance(algorithm.algorithm);
				mac.init(new SecretKeySpec(key.getKey(), "Hmac"));
				return mac.doFinal(dataToSign);
			})))
				throw new IllegalArgumentException(algorithm.algorithm + " verification failed");
		} else
			IuException.unchecked(() -> {
				final var sig = Signature.getInstance(algorithm.algorithm);
				switch (algorithm) {
				case PS256:
					sig.setParameter(new PSSParameterSpec(MGF1ParameterSpec.SHA256.getDigestAlgorithm(), "MGF1",
							MGF1ParameterSpec.SHA256, algorithm.size / 8, 1));
					break;
				case PS384:
					sig.setParameter(new PSSParameterSpec(MGF1ParameterSpec.SHA384.getDigestAlgorithm(), "MGF1",
							MGF1ParameterSpec.SHA384, algorithm.size / 8, 1));
					break;
				case PS512:
					sig.setParameter(new PSSParameterSpec(MGF1ParameterSpec.SHA512.getDigestAlgorithm(), "MGF1",
							MGF1ParameterSpec.SHA512, algorithm.size / 8, 1));
					break;
				default:
					break;
				}
				sig.initVerify(key.getPublicKey());
				sig.update(dataToSign);
				if (!sig.verify(toJce(key.getType(), algorithm, signature)))
					throw new IllegalArgumentException(algorithm.algorithm + " verification failed");
			});
	}

	/**
	 * Gets the expected signature component length by key type.
	 * 
	 * @param type key type
	 * @return expected signature component length
	 */
	static int componentLength(Type type) {
		switch (type) {
		case EC_P256:
			return 32;
		case EC_P384:
			return 48;
		case EC_P521:
			return 66;
		default:
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Converts a signature from JWA format to JCE
	 * 
	 * @param type         key type
	 * @param algorithm    algorithm
	 * @param jwaSignature JWA formatted signature
	 * @return JCE formatted signature
	 */
	static byte[] toJce(Type type, Algorithm algorithm, byte[] jwaSignature) {
		switch (algorithm) {
		case ES256:
		case ES384:
		case ES512: {
			// Validate expected length of R || S
			final int alen = componentLength(type);
			if (jwaSignature.length != alen * 2)
				throw new IllegalArgumentException();

			// Convert R and S to Java encoded BigInteger (w/ signum)
			final var ri = UnsignedBigInteger.bigInt(Arrays.copyOf(jwaSignature, alen));
			if (ri.compareTo(BigInteger.ZERO) <= 0)
				throw new IllegalArgumentException();
			final var r = ri.toByteArray();

			final var si = UnsignedBigInteger.bigInt(Arrays.copyOfRange(jwaSignature, alen, alen * 2));
			if (si.compareTo(BigInteger.ZERO) <= 0)
				throw new IllegalArgumentException();
			final var s = si.toByteArray();

			final var tlen = r.length + s.length + 4;
			final var sequenceEncodingBytes = type.equals(Type.EC_P521) ? 3 : 2;

			// converted length = includes 6 bytes for DER encoding
			final var converted = ByteBuffer.wrap(new byte[tlen + sequenceEncodingBytes]);
			converted.put((byte) 0x30); // DER constructed sequence
			if (type.equals(Type.EC_P521)) // DER single octet extended length (> 127)
				converted.put((byte) 0x81);
			converted.put((byte) tlen); // Sequence length
			converted.put((byte) 0x02); // DER integer
			converted.put((byte) r.length);
			converted.put(r);
			converted.put((byte) 0x02); // DER integer
			converted.put((byte) s.length);
			converted.put(s);
			return converted.array();
		}

		default:
			return jwaSignature;
		}
	}

	/**
	 * Converts a signature from JCE format to JWA
	 * 
	 * @param type         key type
	 * @param algorithm    algorithm
	 * @param jceSignature JCE calculated signature
	 * @return JWA formatted signature
	 */
	static byte[] fromJce(Type type, Algorithm algorithm, byte[] jceSignature) {
		switch (algorithm) {
		case ES256:
		case ES384:
		case ES512: {
			final var alen = componentLength(type); // expected length of R and S values
			if (jceSignature.length < 8) // at least one non-zero byte per component value
				throw new IllegalArgumentException();

			final var original = ByteBuffer.wrap(jceSignature);
			final var converted = ByteBuffer.wrap(new byte[alen * 2]);

			// Assert DER constructed sequence
			if (original.get() != 0x30)
				throw new IllegalArgumentException();

			// Assert constructed sequence length matches remaining count
			if (alen >= 62 && original.get() != (byte) 0x81) // DER 1-octet extended
				throw new IllegalArgumentException();
			if (original.get() != (byte) original.remaining())
				throw new IllegalArgumentException();

			for (var i = 0; i < 2; i++) {
				if (original.get() != 2) // Assert DER integer
					throw new IllegalArgumentException();
				int ilen = original.get(); // Integer length
				if (ilen < 0) // Convert to unsigned int
					ilen += 0x100;
				// Assert encoded BigInteger length
				if (ilen > alen + 1)
					throw new IllegalArgumentException();

				final var ibuf = new byte[ilen];
				original.get(ibuf); // Convert Java BigInteger to JWA format
				final var ib = UnsignedBigInteger.bigInt(new BigInteger(ibuf));
				for (var j = ib.length; j < alen; j++)
					converted.put((byte) 0); // pad left side
				converted.put(ib);
			}

			if (original.hasRemaining())
				throw new IllegalArgumentException();

			return converted.array();
		}

		default:
			return jceSignature;
		}
	}

	/**
	 * Parses per-signature JWS parameters from raw JSON.
	 * 
	 * @param jwsSignature JSON
	 * @return parsed JWS parameters
	 */
	static Jws parse(JsonValue jwsSignature) {
		final var parsed = jwsSignature.asJsonObject();
		final var protectedHeader = IuJson.get(parsed, "protected", IuJsonAdapter
				.from(v -> IuJson.parse(IuText.utf8(CryptJsonAdapters.B64URL.fromJson(v))).asJsonObject()));
		final var header = IuJson.get(parsed, "header", IuJsonAdapter.from(JsonValue::asJsonObject));
		final var signature = IuJson.get(parsed, "signature", CryptJsonAdapters.B64URL);
		return new Jws(protectedHeader, Jose.from(protectedHeader, null, header), signature);
	}

	/**
	 * Adds JWS per-signature parameters to a {@link JsonObjectBuilder}.
	 * 
	 * @param json {@link JsonObjectBuilder}
	 */
	void serializeTo(JsonObjectBuilder json) {
		IuJson.add(json, "protected", () -> protectedHeader,
				IuJsonAdapter.to(h -> CryptJsonAdapters.B64URL.toJson(IuText.utf8(h.toString()))));
		IuJson.add(json, "header", header.toJson(n -> protectedHeader == null || !protectedHeader.containsKey(n)));
		IuJson.add(json, "signature", () -> signature, CryptJsonAdapters.B64URL);
	}

	/**
	 * Gets the signature input.
	 * 
	 * @param payload payload
	 * @return signature input
	 */
	String getSignatureInput(byte[] payload) {
		return IuText.base64Url(IuText.utf8(Objects.requireNonNullElse(protectedHeader, "").toString())) + '.'
				+ IuText.base64Url(payload);
	}

}
