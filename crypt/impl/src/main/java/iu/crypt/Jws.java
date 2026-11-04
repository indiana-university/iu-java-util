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
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJsonProperties;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * JSON implementation of {@link WebSignature}.
 */
public class Jws implements WebSignature {
	static {
		IuObject.assertNotOpen(Jws.class);
	}

	private final String encodedProtectedHeader;
	private final IuJsonProperties protectedHeader;
	private final Jose header;
	private final byte[] signature;

	/**
	 * Creates a new signed message;
	 *
	 * @param protectedHeader JWS protected header
	 * @param header          JWS header, protected and unprotected parameters
	 *                        combined
	 * @param signature       signature
	 */
	Jws(IuJsonProperties protectedHeader, Jose header, byte[] signature) {
		this(CompactEncoded.encodeHeader(protectedHeader), protectedHeader, header, signature);
	}

	/**
	 * Creates a signed message from a protected header in its encoded form, which
	 * the signature input is computed from.
	 *
	 * @param encodedProtectedHeader encoded JWS protected header
	 * @param header                 JWS header, protected and unprotected
	 *                               parameters combined
	 * @param signature              signature
	 */
	Jws(String encodedProtectedHeader, Jose header, byte[] signature) {
		this(encodedProtectedHeader, CompactEncoded.decodeHeader(encodedProtectedHeader), header, signature);
	}

	private Jws(String encodedProtectedHeader, IuJsonProperties protectedHeader, Jose header, byte[] signature) {
		this.encodedProtectedHeader = encodedProtectedHeader;
		this.protectedHeader = protectedHeader;
		this.header = header;
		this.signature = signature;

		final var algorithm = header.getAlgorithm();
		if (!algorithm.use.equals(Use.SIGN))
			throw new IllegalArgumentException("Signature algorithm is required");

		if (protectedHeader != null)
			for (final var name : protectedHeader.names()) {
				final var param = Param.from(name);
				final var type = param == null ? Jose.getExtension(name).type() : param.type;
				Object value = protectedHeader.get(name, type);

				final Object headerValue;
				if (param == null)
					headerValue = header.getExtendedParameter(name);
				else if (param.equals(Param.CRITICAL_PARAMS)) {
					// an array in JSON, a set in the header: order doesn't matter
					value = IuObject.convert((String[]) value, Set::of);
					headerValue = header.getCriticalParameters();
				} else
					headerValue = param.get(header);

				if (!IuObject.equals(value, headerValue))
					throw new IllegalArgumentException(name + " must match protected header");
			}

		for (final var name : header.extendedParameters().keySet()) {
			final var ext = Jose.getExtension(name);

			ext.verify(this);
		}
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
	 * {@link JsonbDeserializer} handle method.
	 * 
	 * @param parser  {@link JsonParser}
	 * @param context {@link DeserializationContext}
	 * @param type    {@link Type}
	 * @return parsed JWS signature
	 */
	static Jws deserialize(JsonParser parser, DeserializationContext context, java.lang.reflect.Type type) {
		return of(context.deserialize(IuJsonProperties.class, parser));
	}

	/**
	 * Reads a JWS signature from its JSON members: an element of "signatures",
	 * or the flattened serialization's top level.
	 *
	 * @param jwsSignature JSON members
	 * @return parsed JWS signature
	 */
	static Jws of(IuJsonProperties jwsSignature) {
		final var encodedProtectedHeader = jwsSignature.get("protected", String.class);
		final var protectedHeader = CompactEncoded.decodeHeader(encodedProtectedHeader);
		return new Jws(encodedProtectedHeader, protectedHeader,
				Jose.from(protectedHeader, null, jwsSignature.get("header", IuJsonProperties.class)),
				jwsSignature.get("signature", byte[].class));
	}

	/**
	 * {@link JsonbSerializer} handle method.
	 *
	 * @param generator {@link JsonGenerator}
	 * @param context   {@link SerializationContext}
	 */
	void serialize(JsonGenerator generator, SerializationContext context) {
		generator.writeStartObject();
		serializeMembers(generator, context);
		generator.writeEnd();
	}

	/**
	 * Writes this signature's JSON members to an object already started: an
	 * element of "signatures", or the flattened serialization's top level.
	 *
	 * @param generator {@link JsonGenerator}
	 * @param context   {@link SerializationContext}
	 */
	void serializeMembers(JsonGenerator generator, SerializationContext context) {
		if (encodedProtectedHeader != null)
			generator.write("protected", encodedProtectedHeader);

		// protected and unprotected parameter names are disjoint
		final var unprotected = header
				.values(name -> protectedHeader == null || !protectedHeader.containsKey(name));
		if (unprotected != null)
			context.serialize("header", unprotected, generator);

		context.serialize("signature", signature, generator);
	}

	/**
	 * Gets the signature input.
	 *
	 * @param payload payload
	 * @return signature input
	 */
	String getSignatureInput(byte[] payload) {
		return Objects.requireNonNullElse(encodedProtectedHeader, "") + '.' + IuText.base64Url(payload);
	}

}
