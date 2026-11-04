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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonProperties;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Configures {@link Jsonb} for web crypto serialization.
 */
public class CryptJsonAdapters {

	/**
	 * Only allow public getters.
	 */
	private static class Visibility implements PropertyVisibilityStrategy {
		@Override
		public boolean isVisible(Field field) {
			return false;
		}

		@Override
		public boolean isVisible(Method method) {
			if (method.getReturnType() == Void.TYPE)
				return false;

			var mod = method.getModifiers();
			return (mod | Modifier.PUBLIC) == mod;
		}
	}

	/**
	 * JSON-B adapter for {@link BigInteger}, as unsigned big-endian binary data
	 * encoded as {@link #B64URL}.
	 */
	private static final JsonbAdapter<BigInteger, byte[]> BIGINT = new JsonbAdapter<BigInteger, byte[]>() {
		@Override
		public byte[] adaptToJson(BigInteger obj) throws Exception {
			return IuObject.convert(obj, UnsignedBigInteger::bigInt);
		}

		@Override
		public BigInteger adaptFromJson(byte[] obj) throws Exception {
			return IuObject.convert(obj, UnsignedBigInteger::bigInt);
		}
	};

	/**
	 * JSON type adapter for {@link X509Certificate}.
	 */
	private static final JsonbAdapter<X509Certificate, String> CERT = new JsonbAdapter<X509Certificate, String>() {
		@Override
		public String adaptToJson(X509Certificate obj) throws Exception {
			return IuObject.convert(obj, a -> IuText.base64(IuException.unchecked(a::getEncoded)));
		}

		@Override
		public X509Certificate adaptFromJson(String obj) throws Exception {
			return IuObject.convert(obj, c -> PemEncoded.asCertificate(IuText.base64(c)));
		}
	};

	/**
	 * JSON type adapter for {@link X509CRL}.
	 */
	private static final JsonbAdapter<X509CRL, String> CRL = new JsonbAdapter<X509CRL, String>() {
		@Override
		public String adaptToJson(X509CRL obj) throws Exception {
			return IuObject.convert(obj, a -> IuText.base64(IuException.unchecked(a::getEncoded)));
		}

		@Override
		public X509CRL adaptFromJson(String obj) throws Exception {
			return IuObject.convert(obj, c -> PemEncoded.asCRL(IuText.base64(c)));
		}
	};

	/**
	 * JSON type adapter for {@link Use}.
	 */
	private static final JsonbAdapter<Use, String> USE = new JsonbAdapter<WebKey.Use, String>() {
		@Override
		public String adaptToJson(Use obj) throws Exception {
			return IuObject.convert(obj, u -> u.use);
		}

		@Override
		public Use adaptFromJson(String obj) throws Exception {
			return IuObject.convert(obj, Use::from);
		}
	};

	/**
	 * JSON type adapter for {@link Operation}.
	 */
	private static final JsonbAdapter<Operation, String> OP = new JsonbAdapter<WebKey.Operation, String>() {
		@Override
		public String adaptToJson(Operation obj) throws Exception {
			return IuObject.convert(obj, op -> op.keyOp);
		}

		@Override
		public Operation adaptFromJson(String obj) throws Exception {
			return IuObject.convert(obj, Operation::from);
		}
	};

	/**
	 * JSON type adapter for {@link Algorithm}.
	 */
	private static final JsonbAdapter<Algorithm, String> ALG = new JsonbAdapter<WebKey.Algorithm, String>() {
		@Override
		public String adaptToJson(Algorithm obj) throws Exception {
			return IuObject.convert(obj, a -> a.alg);
		}

		@Override
		public Algorithm adaptFromJson(String obj) throws Exception {
			return IuObject.convert(obj, Algorithm::from);
		}
	};

	/**
	 * JSON type adapter
	 */
	private static final JsonbAdapter<Encryption, String> ENC = new JsonbAdapter<WebEncryption.Encryption, String>() {
		@Override
		public String adaptToJson(Encryption obj) throws Exception {
			return IuObject.convert(obj, e -> e.enc);
		}

		@Override
		public Encryption adaptFromJson(String obj) throws Exception {
			return IuObject.convert(obj, Encryption::from);
		}
	};

	/**
	 * JSON type adapter for {@link WebCryptoHeader}.
	 */
	private static final JsonbAdapter<WebCertificateReference, IuJsonProperties> CERT_REF = new JsonbAdapter<WebCertificateReference, IuJsonProperties>() {
		@Override
		public IuJsonProperties adaptToJson(WebCertificateReference obj) throws Exception {
			return IuObject.convert(obj, ref -> {
				final var builder = builder();
				((JsonCertificateReference<?>) obj).append(builder);
				return builder.build();
			});
		}

		@Override
		public WebCertificateReference adaptFromJson(IuJsonProperties obj) throws Exception {
			return IuObject.convert(obj, JsonCertificateReference::new);
		}
	};

	/**
	 * JSON type adapter for {@link WebCryptoHeader}.
	 */
	private static final JsonbAdapter<WebCryptoHeader, IuJsonProperties> JOSE = new JsonbAdapter<WebCryptoHeader, IuJsonProperties>() {
		@Override
		public IuJsonProperties adaptToJson(WebCryptoHeader obj) throws Exception {
			return IuObject.convert(obj, jose -> ((Jose) jose).values(a -> true));
		}

		@Override
		public WebCryptoHeader adaptFromJson(IuJsonProperties obj) throws Exception {
			return IuObject.convert(obj, Jose::new);
		}
	};

	/**
	 * JSON type adapter for {@link WebKey}.
	 */
	private static final JsonbAdapter<WebKey, IuJsonProperties> JWK = new JsonbAdapter<WebKey, IuJsonProperties>() {
		@Override
		public IuJsonProperties adaptToJson(WebKey obj) throws Exception {
			return CERT_REF.adaptToJson(obj);
		}

		@Override
		public WebKey adaptFromJson(IuJsonProperties obj) throws Exception {
			return IuObject.convert(obj, Jwk::new);
		}
	};

	/**
	 * JSON type adapter for {@link WebSignature}.
	 */
	private static final JsonbSerializer<WebSignature> JWS_SER = new JsonbSerializer<WebSignature>() {
		@Override
		public void serialize(WebSignature jws, JsonGenerator generator, SerializationContext context) {
			((Jws) jws).serialize(generator, context);
		}
	};

	/**
	 * JSON type adapter for {@link WebSignature}.
	 */
	private static final JsonbDeserializer<WebSignature> JWS_DES = new JsonbDeserializer<WebSignature>() {
		@Override
		public WebSignature deserialize(JsonParser parser, DeserializationContext context, Type rtType) {
			return Jws.deserialize(parser, context, rtType);
		}
	};

	/**
	 * JSON type adapter for {@link WebSignature}.
	 */
	private static final JsonbSerializer<WebSignedPayload> JWS_PAYLOAD_SER = new JsonbSerializer<WebSignedPayload>() {
		@Override
		public void serialize(WebSignedPayload jws, JsonGenerator generator, SerializationContext context) {
			((JwsSignedPayload) jws).serialize(generator, context);
		}
	};

	/**
	 * JSON type adapter for {@link WebSignature}.
	 */
	private static final JsonbDeserializer<WebSignedPayload> JWS_PAYLOAD_DES = new JsonbDeserializer<WebSignedPayload>() {
		@Override
		public WebSignedPayload deserialize(JsonParser parser, DeserializationContext context, Type rtType) {
			return JwsSignedPayload.deserialize(parser, context, rtType);
		}
	};

	/**
	 * JSON type adapter for {@link WebEncryption}.
	 */
	private static final JsonbSerializer<WebEncryption> JWE_SER = new JsonbSerializer<WebEncryption>() {
		@Override
		public void serialize(WebEncryption jwe, JsonGenerator generator, SerializationContext context) {
			((Jwe) jwe).serialize(generator, context);
		}
	};

	/**
	 * JSON type adapter for {@link WebEncryption}.
	 */
	private static final JsonbDeserializer<WebEncryption> JWE_DES = new JsonbDeserializer<WebEncryption>() {
		@Override
		public WebEncryption deserialize(JsonParser parser, DeserializationContext context, Type rtType) {
			return Jwe.deserialize(parser, context, rtType);
		}
	};


	/**
	 * {@link Jsonb} instance for internal use by this module.
	 *
	 * <p>
	 * The IU provider is named rather than discovered: the conversions here read
	 * JSON through {@link IuJsonProperties}, which only it provides.
	 * </p>
	 */
	static final Jsonb JSONB = JsonbBuilder.newBuilder("iu.client.jsonb.IuJsonbProvider").withConfig(config()).build();

	/**
	 * Gets a {@link IuJsonProperties.Builder} instance configured with
	 * {@link JSONB}.
	 * 
	 * @return {@link IuJsonProperties.Builder}
	 */
	static IuJsonProperties.Builder builder() {
		return IuJsonProperties.builder(JSONB);
	}

	/**
	 * Gets a {@link JsonbConfig} instance that supports crypto types.
	 * 
	 * @return {@link JsonbConfig}
	 */
	static JsonbConfig config() {
		return new JsonbConfig() //
				.withNullValues(false) //
				.withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES) //
				.withBinaryDataStrategy(BinaryDataStrategy.BASE_64_URL) //
				.setProperty(IuJsonAdapter.BASE64_URL_UNPADDED, true) //
				.withPropertyVisibilityStrategy(new Visibility()) //
				.withSerializers(JWS_SER, JWS_PAYLOAD_SER, JWE_SER) //
				.withDeserializers(JWS_DES, JWS_PAYLOAD_DES, JWE_DES) //
				.withAdapters(BIGINT, CERT, CRL, USE, OP, ALG, ENC, CERT_REF, JOSE, JWK);
	}

	private CryptJsonAdapters() {
	}

}
