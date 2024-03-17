package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import iu.crypt.Jwe;

@SuppressWarnings("javadoc")
public class JweTest {

	@Test
	public void testRsaLegacy() throws NoSuchAlgorithmException {
		final var gen = KeyPairGenerator.getInstance("RSA");
		gen.initialize(2048);
		final var key = WebKey.from(null, gen.generateKeyPair());

		final var message = IdGenerator.generateId();

		final var jwe = WebEncryption.encrypt(List.of(new WebEncryptionHeader() {
			@Override
			public WebKey getKey() {
				return key;
			}

			@Override
			public Algorithm getAlgorithm() {
				return Algorithm.RSA1_5;
			}

			@Override
			public Encryption getEncryption() {
				return Encryption.AES_128_CBC_HMAC_SHA_256;
			}
		}), message.getBytes());

		final var fromCompact = Jwe.readJwe(jwe.getRecipients().iterator().next().compact());
		final var compactHeader = fromCompact.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.RSA1_5, compactHeader.getAlgorithm());
		assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, compactHeader.getEncryption());
		assertNull(compactHeader.getKey());

		final var fromSerial = Jwe.readJwe(jwe.toString());
		final var serialHeader = fromSerial.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.RSA1_5, serialHeader.getAlgorithm());
		assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, serialHeader.getEncryption());
		assertNotNull(serialHeader.getKey());
		assertNull(serialHeader.getKey().getPrivateKey());

		assertEquals(message, new String(jwe.decrypt(key)));
		assertEquals(message, new String(fromCompact.decrypt(key)));
		assertEquals(message, new String(fromSerial.decrypt(key)));
	}

}
