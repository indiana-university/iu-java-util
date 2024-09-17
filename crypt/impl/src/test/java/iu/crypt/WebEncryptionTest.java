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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import javax.crypto.AEADBadTagException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTestLogger;
import iu.crypt.Jose.Extension;

@SuppressWarnings("javadoc")
public class WebEncryptionTest {

	@Test
	public void testEncryption() {
		for (final var algorithm : IuIterable.filter(IuIterable.iter(Algorithm.values()),
				a -> a.use.equals(Use.ENCRYPT)))
			for (final var encryption : Encryption.values())
				assertEncryption(algorithm, encryption);
		assertNull(Jwe.JSON.fromJson(Jwe.JSON.toJson(null)));
	}

	@Test
	public void testInvalidAesCbcHmacTag() {
		final var key = WebKey.ephemeral(Encryption.AES_128_CBC_HMAC_SHA_256);
		final var id = IdGenerator.generateId();
		final var jwe = WebEncryption.builder(Encryption.AES_128_CBC_HMAC_SHA_256).addRecipient(Algorithm.DIRECT)
				.key(key).then().encrypt(id);
		final var json = IuJson.object(IuJson.parse(jwe.toString()).asJsonObject());
		json.add("tag", "");
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertInstanceOf(AEADBadTagException.class, assertThrows(IllegalStateException.class,
				() -> WebEncryption.parse(json.build().toString()).decrypt(key)).getCause());
	}

	@Test
	public void testNotDeflated() {
		final var key = WebKey.ephemeral(Encryption.A128GCM);
		final var id = IdGenerator.generateId();
		final var jwe = WebEncryption.builder(Encryption.A128GCM, false).addRecipient(Algorithm.DIRECT).key(key).then()
				.encrypt(id);
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(id, jwe.decryptText(key));
	}

	@Test
	public void testNoHeader() {
		final var key = WebKey.ephemeral(Encryption.A192GCM);
		final var id = IdGenerator.generateId();
		final var jwe = WebEncryption.builder(Encryption.A192GCM).protect(new String[0]).addRecipient(Algorithm.DIRECT)
				.key(key).then().encrypt(id);

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(id, WebEncryption.parse(jwe.toString()).decryptText(key));
	}

	@Test
	public void testMultipleRecipients() {
		final var key1 = WebKey.ephemeral(Algorithm.A256GCMKW);
		final var key2 = WebKey.ephemeral(Algorithm.RSA_OAEP_256);
		final var key3 = WebKey.ephemeral(Algorithm.ECDH_ES_A192KW);
		final var id = IdGenerator.generateId();
		final var protext = IdGenerator.generateId();
		Jose.register(protext, new StringExtension());
		final var pertext = IdGenerator.generateId();
		Jose.register(pertext, new StringExtension());
		final var original = WebEncryption.builder(Encryption.A256GCM, false) //
				.addRecipient(Algorithm.A256GCMKW).key(key1).type("example").param(protext, "foo")
				.param(pertext, IdGenerator.generateId()).then() //
				.addRecipient(Algorithm.RSA_OAEP_256).key(key2).type("example").param(protext, "foo")
				.param(pertext, IdGenerator.generateId()).then() //
				.addRecipient(Algorithm.ECDH_ES_A192KW).key(key3).type("example").param(protext, "foo")
				.param(pertext, IdGenerator.generateId()).then();
		original.protect(Param.TYPE);
		original.protect(protext);
		final var jwe = WebEncryption.parse(original.encrypt(id).toString());
		assertThrows(IllegalStateException.class, () -> jwe.compact());

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for {\"kty\":\"oct\"}");
		assertEquals(id, jwe.decryptText(key1));
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", IllegalArgumentException.class);
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key2.wellKnown());
		assertEquals(id, jwe.decryptText(key2));
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", ClassCastException.class);
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", IllegalArgumentException.class);
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key3.wellKnown());
		assertEquals(id, jwe.decryptText(key3));
		IuTestLogger.assertExpectedMessages();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExtension() {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE, "CEK decryption successful.*");

		final var ext = mock(Extension.class, CALLS_REAL_METHODS);
		final var id = IdGenerator.generateId();
		when(ext.toJson(id)).thenReturn(IuJson.string(id));
		when(ext.fromJson(IuJson.string(id))).thenReturn(id);

		Jose.register("urn:example:iu:id", ext);

		final var key = WebKey.ephemeral(Encryption.A128GCM);
		final var jwe = WebEncryption.to(Encryption.A128GCM, Algorithm.DIRECT).key(key).param("urn:example:iu:id", id)
				.encrypt(id);
		verify(ext).validate(eq(id), any());
		verify(ext).verify(any(), argThat(a -> {
			assertEquals(id, a.getHeader().getExtendedParameter("urn:example:iu:id"));
			return true;
		}));
		assertEquals(id, jwe.decryptText(key));

		assertThrows(NullPointerException.class,
				() -> WebEncryption.to(Encryption.AES_128_CBC_HMAC_SHA_256, Algorithm.DIRECT)
						.crit("urn:example:iu:unsupported").key(key).encrypt(id));
	}

	private void assertEncryption(Algorithm algorithm, Encryption encryption) {
		final var keyBuilder = WebKey.builder(algorithm);
		if (algorithm.equals(Algorithm.DIRECT))
			keyBuilder.ephemeral(encryption);
		else
			keyBuilder.ephemeral();
		final var key = keyBuilder.build();

		final var length = 16384;
		final var data = new byte[length];
		for (int i = 0; i < length; i++)
			data[i] = (byte) ThreadLocalRandom.current().nextInt(32, 127);
		final var message = IuText.ascii(data);

		{
			final var compactJwe = WebEncryption.to(encryption, algorithm).key(key).then().compact().encrypt(message);
			IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
			assertEquals(message, compactJwe.decryptText(key));
			assertNull(compactJwe.getAdditionalData());
			assertEquals(IuJson.parse(compactJwe.toString()),
					IuJson.parse(Jwe.JSON.fromJson(Jwe.JSON.toJson(compactJwe)).toString()));

			final var fromCompact = WebEncryption.parse(compactJwe.compact());
			final var compactHeader = fromCompact.getRecipients().iterator().next().getHeader();
			assertEquals(algorithm, compactHeader.getAlgorithm());
			assertEquals(encryption, fromCompact.getEncryption());
			assertNull(compactHeader.getKey());

			IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
			assertEquals(message, fromCompact.decryptText(key));
		}

		final var aad = new byte[32];
		ThreadLocalRandom.current().nextBytes(aad);

		{
			final var serialJwe = WebEncryption.to(encryption, algorithm).wellKnown(key).then().encrypt(message);
			assertNull(serialJwe.getAdditionalData());
			assertEquals(IuJson.parse(serialJwe.toString()),
					IuJson.parse(Jwe.JSON.fromJson(Jwe.JSON.toJson(serialJwe)).toString()));

			final var fromSerial = WebEncryption.parse(serialJwe.toString());
			final var serialHeader = fromSerial.getRecipients().iterator().next().getHeader();
			assertEquals(algorithm, serialHeader.getAlgorithm());
			assertEquals(encryption, fromSerial.getEncryption());
			assertEquals(key.wellKnown(), serialHeader.getKey());

			IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
			assertEquals(message, fromSerial.decryptText(key));
		}

		{
			final var slientJwe = WebEncryption.builder(encryption).addRecipient(algorithm).key(key).then().aad(aad)
					.encrypt(message);
			assertNotNull(slientJwe.getAdditionalData());

			final var fromSilent = Jwe.JSON.fromJson(Jwe.JSON.toJson(slientJwe));
			final var silentHeader = fromSilent.getRecipients().iterator().next().getHeader();
			assertEquals(algorithm, silentHeader.getAlgorithm());
			assertEquals(encryption, fromSilent.getEncryption());
			assertNull(silentHeader.getKey());
			assertArrayEquals(aad, fromSilent.getAdditionalData());

			IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
			assertEquals(message, fromSilent.decryptText(key));
		}
	}

	@Test
	public void testSimpleExample() {
		IuTestLogger.allow("iu.crypt", Level.FINE);
		final var uri = URI.create(IdGenerator.generateId());
		String jsonString = IuJson.object().add("sessionId", IdGenerator.generateId()).add("returnUrl", uri.toString())
				.build().toString();

		final var key = WebKey.ephemeral(Encryption.A128GCM);
		final var enc = WebEncryption.builder(Encryption.A128GCM).compact().addRecipient(Algorithm.DIRECT).key(key)
				.encrypt(jsonString).compact();
		final var dec = WebEncryption.parse(enc);
		assertEquals(jsonString, dec.decryptText(key));
	}

	@Test
	public void testProtected() {
		final var key = WebKey.ephemeral(Encryption.A128GCM);
		assertThrows(IllegalArgumentException.class, () -> WebEncryption.builder(Encryption.A128GCM)
				.protect(Param.CONTENT_TYPE).addRecipient(Algorithm.DIRECT).key(key).encrypt("foo"));
		assertThrows(IllegalArgumentException.class, () -> WebEncryption.builder(Encryption.A128GCM).compact()
				.protect(Param.CONTENT_TYPE).addRecipient(Algorithm.DIRECT).key(key).encrypt("foo"));
	}

	@Test
	public void testBadECDGKeys() {
		final var key = WebKey.ephemeral(Algorithm.ECDH_ES);
		assertThrows(IllegalArgumentException.class,
				() -> WebEncryption.builder(Encryption.A128GCM).addRecipient(Algorithm.ES256).key(key).encrypt("foo"));
		final var enc = WebEncryption.builder(Encryption.A128GCM).addRecipient(Algorithm.ECDH_ES).key(key)
				.encrypt("foo");

		// Decrypt only logs key failure details at FINE level
		// thrown ISE always implies successful decryption w/ rejected AEAD
		// authentication tag
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", IllegalArgumentException.class,
				e -> "Private key type doesn't match epk".equals(e.getMessage()));
		assertInstanceOf(AEADBadTagException.class,
				assertThrows(IllegalStateException.class,
						() -> enc.decrypt(WebKey.builder(Type.EC_P384).ephemeral(Algorithm.ECDH_ES).build()))
						.getCause());

		final var encw = WebEncryption.builder(Encryption.A128GCM).addRecipient(Algorithm.ECDH_ES).wellKnown(key)
				.encrypt("foo");
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", IllegalArgumentException.class,
				e -> "Key is not valid for recipient".equals(e.getMessage()));
		assertInstanceOf(AEADBadTagException.class,
				assertThrows(IllegalStateException.class,
						() -> encw.decrypt(WebKey.builder(Type.EC_P384).ephemeral(Algorithm.ECDH_ES).build()))
						.getCause());

		final var encb = WebEncryption.builder(Encryption.A128GCM).addRecipient(Algorithm.ECDH_ES).wellKnown(key);
		final var enco = IuJson.parse(encb.encrypt("foo").toString()).asJsonObject();
		final var ence = WebEncryption.parse(IuJson.object(enco)
				.add("encrypted_key", IuText.base64Url(WebKey.ephemeral(Encryption.A128GCM).getKey())).build()
				.toString());
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", IllegalArgumentException.class,
				e -> "encrypted key must be empty for ECDH_ES".equals(e.getMessage()));
		assertInstanceOf(AEADBadTagException.class,
				assertThrows(IllegalStateException.class, () -> ence.decrypt(key)).getCause());
	}

	@Test
	public void testBadDirectKeys() {
		final var key = WebKey.ephemeral(Encryption.AES_128_CBC_HMAC_SHA_256);
		final var enc = WebEncryption.builder(Encryption.AES_128_CBC_HMAC_SHA_256).addRecipient(Algorithm.DIRECT)
				.key(key).encrypt("foo");

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", NullPointerException.class,
				e -> "DIRECT requires a secret key".equals(e.getMessage()));
		assertInstanceOf(AEADBadTagException.class,
				assertThrows(IllegalStateException.class,
						() -> enc.decrypt(WebKey.builder(Type.EC_P384).ephemeral(Algorithm.ECDH_ES).build()))
						.getCause());

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", IllegalArgumentException.class,
				e -> "Invalid key size for AES_128_CBC_HMAC_SHA_256".equals(e.getMessage()));
		assertInstanceOf(AEADBadTagException.class, assertThrows(IllegalStateException.class,
				() -> enc.decrypt(WebKey.ephemeral(Encryption.AES_192_CBC_HMAC_SHA_384))).getCause());

		final var encb = WebEncryption.builder(Encryption.AES_128_CBC_HMAC_SHA_256).addRecipient(Algorithm.DIRECT)
				.wellKnown(key);
		final var enco = IuJson.parse(encb.encrypt("foo").toString()).asJsonObject();
		final var ence = WebEncryption.parse(IuJson.object(enco)
				.add("encrypted_key", IuText.base64Url(WebKey.ephemeral(Encryption.A128GCM).getKey())).build()
				.toString());
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", IllegalArgumentException.class,
				e -> "encrypted key must be empty for DIRECT".equals(e.getMessage()));
		assertInstanceOf(AEADBadTagException.class,
				assertThrows(IllegalStateException.class, () -> ence.decrypt(key)).getCause());
	}

	@Test
	public void testBadGCMKWKeys() {
		final var key = WebKey.ephemeral(Algorithm.A192GCMKW);
		final var enc = WebEncryption.builder(Encryption.A192GCM).addRecipient(Algorithm.A192GCMKW).key(key);
		final var enco = IuJson.parse(enc.encrypt("foo").toString()).asJsonObject();
		final var ench = IuJson.object(enco.getJsonObject("header"))
				.add("iv", IuText.base64Url(new byte[] { 1, 2, 3, 4, 5 })).build();
		final var ence = WebEncryption.parse(IuJson.object(enco).add("header", ench).build().toString());
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", IllegalArgumentException.class,
				e -> "iv must be 96 bits".equals(e.getMessage()));
		assertInstanceOf(AEADBadTagException.class,
				assertThrows(IllegalStateException.class, () -> ence.decrypt(key)).getCause());

		final var encth = IuJson.object(enco.getJsonObject("header"))
				.add("tag", IuText.base64Url(new byte[] { 1, 2, 3, 4, 5 })).build();
		final var enct = WebEncryption.parse(IuJson.object(enco).add("header", encth).build().toString());
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption failed", IllegalArgumentException.class,
				e -> "tag must be 128 bits".equals(e.getMessage()));
		assertInstanceOf(AEADBadTagException.class,
				assertThrows(IllegalStateException.class, () -> enct.decrypt(key)).getCause());
	}

}
