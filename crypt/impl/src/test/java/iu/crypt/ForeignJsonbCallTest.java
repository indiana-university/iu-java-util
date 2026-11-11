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
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.client.IuJsonProperties;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.test.IuTestLogger;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * Verifies that JOSE values convert as {@link CryptJsonAdapters#JSONB} does
 * while another JSON-B instance, with none of its conversions, has a call in
 * progress on the thread: as when an application's getter parses a token while
 * the application's own JSON-B instance serializes its entity.
 */
@SuppressWarnings("javadoc")
public class ForeignJsonbCallTest extends CryptImplTestCase {

	private static final Jsonb FOREIGN = JsonbBuilder.newBuilder("iu.client.jsonb.IuJsonbProvider").build();
	private static final ThreadLocal<Supplier<?>> PENDING = new ThreadLocal<>();

	public static class Entity {
		private final Supplier<?> supplier;
		private Object value;

		private Entity(Supplier<?> supplier) {
			this.supplier = supplier;
		}

		public String getValue() {
			value = supplier.get();
			return "done";
		}
	}

	public static class Wrapper {
		private Object value;

		public void setValue(String name) {
			value = PENDING.get().get();
		}
	}

	/**
	 * Runs within a serialization, and again within a deserialization, by a JSON-B
	 * instance with no crypt conversions.
	 */
	@SuppressWarnings("unchecked")
	private static <T> T foreign(Supplier<T> supplier) {
		final var entity = new Entity(supplier);
		assertEquals("{\"value\":\"done\"}", FOREIGN.toJson(entity));
		assertNotNull(entity.value);

		PENDING.set(supplier);
		try {
			assertNotNull(FOREIGN.fromJson("{\"value\":\"done\"}", Wrapper.class).value);
		} finally {
			PENDING.remove();
		}
		return (T) entity.value;
	}

	@Test
	public void testIndexBoundToCryptConversions() {
		assertNull(CryptJsonAdapters.JSONB.fromJson("null", IuJsonProperties.class));

		final var read = CryptJsonAdapters.JSONB.fromJson(
				"{\"alg\":\"dir\",\"enc\":\"A128GCM\",\"nested\":{\"alg\":\"ECDH-ES\"},\"items\":[{\"use\":\"sig\"}],\"none\":null}",
				IuJsonProperties.class);
		final var built = CryptJsonAdapters.builder().put("alg", Algorithm.DIRECT).put("n", BigInteger.valueOf(65537L))
				.build();
		foreign(() -> {
			assertEquals(Algorithm.DIRECT, read.get("alg", Algorithm.class));
			assertEquals(Encryption.A128GCM, read.get("enc", Encryption.class));
			assertEquals(Algorithm.ECDH_ES, read.get("nested", IuJsonProperties.class).get("alg", Algorithm.class));
			assertEquals(Use.SIGN, read.get("items", IuJsonProperties[].class)[0].get("use", Use.class));
			assertNull(read.get("none", IuJsonProperties.class));
			assertEquals("{\"alg\":\"dir\",\"n\":\"AQAB\"}", built.toString());
			assertEquals("dir", built.get("alg", String.class));
			return read;
		});
	}

	@Test
	public void testCompactJweDirect() {
		final var key = WebKey.ephemeral(Encryption.A128GCM);
		final var id = IdGenerator.generateId();
		final var compact = WebEncryption.builder(Encryption.A128GCM).compact().addRecipient(Algorithm.DIRECT).key(key)
				.then().encrypt(id).compact();
		assertTrue(IuText.utf8(IuText.base64Url(compact.substring(0, compact.indexOf('.')))).contains("\"alg\":\"dir\""));

		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		final var jwe = foreign(() -> {
			final var parsed = WebEncryption.parse(compact);
			final var header = parsed.getRecipients().iterator().next().getHeader();
			assertEquals(Algorithm.DIRECT, header.getAlgorithm());
			assertEquals(Encryption.A128GCM, header.getExtendedParameter("enc"));
			assertEquals(id, parsed.decryptText(key));
			return parsed;
		});
		assertEquals(compact, jwe.compact());
		assertEquals(id, jwe.decryptText(key));

		final var header = foreign(() -> WebCryptoHeader.getProtectedHeader(compact));
		assertEquals(Algorithm.DIRECT, header.getAlgorithm());
		assertEquals(Encryption.A128GCM, header.getExtendedParameter("enc"));
	}

	@Test
	public void testSerializedJwe() {
		final var key = WebKey.builder(Algorithm.ECDH_ES_A128KW).keyId(IdGenerator.generateId()).ephemeral().build();
		final var key2 = WebKey.ephemeral(Algorithm.A256GCMKW);
		final var id = IdGenerator.generateId();
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);

		// flattened, with an ephemeral public key and encryption in the header
		final var flattened = WebEncryption.builder(Encryption.AES_128_CBC_HMAC_SHA_256).protect(Param.ENCRYPTION, Param.ALGORITHM, Param.EPHEMERAL_PUBLIC_KEY)
				.addRecipient(Algorithm.ECDH_ES_A128KW).key(key).keyId(key.getKeyId()).then().encrypt(id).toString();
		foreign(() -> {
			final var parsed = WebEncryption.parse(flattened);
			final var header = parsed.getRecipients().iterator().next().getHeader();
			assertEquals(Algorithm.ECDH_ES_A128KW, header.getAlgorithm());
			assertEquals(key.getKeyId(), header.getKeyId());
			assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, header.getExtendedParameter("enc"));
			assertEquals(key.getType(),
					((WebKey) header.getExtendedParameter(Param.EPHEMERAL_PUBLIC_KEY.name)).getType());
			assertEquals(id, parsed.decryptText(key));
			return parsed;
		});

		final var flattenedHeader = foreign(() -> WebCryptoHeader.getProtectedHeader(flattened));
		assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, flattenedHeader.getExtendedParameter("enc"));

		// general, with shared and per-recipient headers
		final var general = WebEncryption.builder(Encryption.A128GCM).protect(Param.ENCRYPTION)
				.addRecipient(Algorithm.ECDH_ES_A128KW).key(key).then().addRecipient(Algorithm.A256GCMKW).key(key2)
				.then().encrypt(id).toString();
		foreign(() -> {
			final var parsed = WebEncryption.parse(general);
			final var recipients = parsed.getRecipients().iterator();
			assertEquals(Algorithm.ECDH_ES_A128KW, recipients.next().getHeader().getAlgorithm());
			final var header = recipients.next().getHeader();
			assertEquals(Algorithm.A256GCMKW, header.getAlgorithm());
			assertEquals(Encryption.A128GCM, header.getExtendedParameter("enc"));
			assertEquals(id, parsed.decryptText(key));
			assertEquals(id, parsed.decryptText(key2));
			assertEquals(general, parsed.toString());
			return parsed;
		});
	}

	@Test
	public void testEncrypt() {
		final var key = WebKey.builder(Algorithm.ECDH_ES).ephemeral().build();
		final var id = IdGenerator.generateId();
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);

		final var jwe = foreign(() -> {
			final var encrypted = WebEncryption.builder(Encryption.A256GCM).compact().addRecipient(Algorithm.ECDH_ES)
					.wellKnown(key).then().encrypt(id);
			final var header = encrypted.getRecipients().iterator().next().getHeader();
			assertEquals(Algorithm.ECDH_ES, header.getAlgorithm());
			assertEquals(key.wellKnown(), header.getKey());
			assertEquals(id, WebEncryption.parse(encrypted.compact()).decryptText(key));
			assertEquals(id, WebEncryption.parse(encrypted.toString()).decryptText(key));
			return encrypted;
		});
		assertEquals(id, jwe.decryptText(key));
		assertEquals(id, WebEncryption.parse(jwe.compact()).decryptText(key));
	}

	@Test
	public void testCompactJws() {
		final var key = WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).use(Use.SIGN).ephemeral()
				.build();
		final var id = IdGenerator.generateId();
		final var compact = WebSignature.builder(Algorithm.ES256).compact().wellKnown(key)
				.keyId(key.getKeyId()).type("test").sign(id).compact();

		final var jws = foreign(() -> {
			final var parsed = WebSignedPayload.parse(compact);
			final var signature = parsed.getSignatures().iterator().next();
			final var header = signature.getHeader();
			assertEquals(Algorithm.ES256, header.getAlgorithm());
			assertEquals(key.getKeyId(), header.getKeyId());
			assertEquals(key.wellKnown(), header.getKey());
			assertEquals(Use.SIGN, header.getKey().getUse());
			assertEquals("test", header.getType());
			parsed.verify(key);
			assertEquals(compact, parsed.compact());
			return parsed;
		});
		assertArrayEquals(IuText.utf8(id), jws.getPayload());
		jws.verify(key);

		final var header = foreign(() -> WebCryptoHeader.getProtectedHeader(compact));
		assertEquals(Algorithm.ES256, header.getAlgorithm());
		assertEquals(key.wellKnown(), header.getKey());
	}

	@Test
	public void testSerializedJws() {
		final var key = WebKey.builder(Algorithm.EDDSA).keyId(IdGenerator.generateId()).ops(Operation.SIGN, Operation.VERIFY)
				.ephemeral().build();
		final var key2 = WebKey.ephemeral(Algorithm.HS256);
		final var id = IdGenerator.generateId();

		// flattened
		final var flattened = WebSignature.builder(Algorithm.EDDSA).wellKnown(key)
				.protect(Param.ALGORITHM).sign(id).toString();
		foreign(() -> {
			final var parsed = WebSignedPayload.parse(flattened);
			final var header = parsed.getSignatures().iterator().next().getHeader();
			assertEquals(Algorithm.EDDSA, header.getAlgorithm());
			assertEquals(key.wellKnown(), header.getKey());
			parsed.verify(key);
			assertEquals(flattened, parsed.toString());
			return parsed;
		});
		assertEquals(Algorithm.EDDSA, foreign(() -> WebCryptoHeader.getProtectedHeader(flattened)).getAlgorithm());

		// general
		final var general = WebSignature.builder(Algorithm.EDDSA).key(key).keyId(key.getKeyId())
				.protect(Param.ALGORITHM).next(Algorithm.HS256)
				.key(key2).sign(id).toString();
		foreign(() -> {
			final var parsed = WebSignedPayload.parse(general);
			final var signatures = parsed.getSignatures().iterator();
			assertEquals(Algorithm.EDDSA, signatures.next().getHeader().getAlgorithm());
			assertEquals(Algorithm.HS256, signatures.next().getHeader().getAlgorithm());
			parsed.verify(key);
			parsed.verify(key2);
			assertEquals(general, parsed.toString());
			return parsed;
		});
		assertEquals(Algorithm.EDDSA, foreign(() -> WebCryptoHeader.getProtectedHeader(general)).getAlgorithm());
	}

	@Test
	public void testSign() {
		final var key = WebKey.builder(Algorithm.PS256).keyId(IdGenerator.generateId()).ephemeral().build();
		final var id = IdGenerator.generateId();

		final var jws = foreign(() -> {
			final var signed = WebSignature.builder(Algorithm.PS256).compact().wellKnown(key)
					.keyId(key.getKeyId()).sign(id);
			final var header = signed.getSignatures().iterator().next().getHeader();
			assertEquals(Algorithm.PS256, header.getAlgorithm());
			assertEquals(key.wellKnown(), header.getKey());
			WebSignedPayload.parse(signed.compact()).verify(key);
			WebSignedPayload.parse(signed.toString()).verify(key);
			return signed;
		});
		jws.verify(key);
		WebSignedPayload.parse(jws.compact()).verify(key);
	}

	@Test
	public void testWebKey() {
		final var key = WebKey.builder(Algorithm.ES384).keyId(IdGenerator.generateId()).use(Use.SIGN)
				.ops(Operation.SIGN, Operation.VERIFY).ephemeral().build();
		final var serialized = key.toString();

		final var parsed = foreign(() -> {
			final var jwk = WebKey.parse(serialized);
			assertEquals(key, jwk);
			assertEquals(Algorithm.ES384, jwk.getAlgorithm());
			assertEquals(Use.SIGN, jwk.getUse());
			assertEquals(Set.of(Operation.SIGN, Operation.VERIFY), jwk.getOps());
			assertEquals(key, WebKey.parse(jwk.toString()));
			assertEquals(key.wellKnown(), jwk.wellKnown());

			final var jwks = WebKey.asJwks(java.util.List.of(jwk));
			assertEquals(key, WebKey.parseJwks(jwks).iterator().next());

			final var built = WebKey.builder(Algorithm.ES384).keyId(key.getKeyId()).use(Use.SIGN)
					.ops(Operation.SIGN, Operation.VERIFY).key(key.getPrivateKey())
					.key(key.getPublicKey()).build();
			assertEquals(key, built);
			assertEquals(key, WebKey.parse(built.toString()));
			return jwk;
		});
		assertEquals(key, parsed);
		assertEquals(key, WebKey.parse(parsed.toString()));
	}

}
