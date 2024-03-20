package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class WebEncryptionTest {

	@Test
	@SuppressWarnings("deprecation")
	public void testRsaLegacy() throws NoSuchAlgorithmException {
		final var key = WebKey.builder().algorithm(Algorithm.RSA1_5).ephemeral().build();
		final var message = IdGenerator.generateId();

		final var jwe = WebEncryption.of(Algorithm.RSA1_5, Encryption.AES_128_CBC_HMAC_SHA_256).addRecipient().jwk(key)
				.then().encrypt(message);
		assertNull(jwe.getAdditionalData());

		final var fromCompact = WebEncryption.parse(jwe.getRecipients().findFirst().get().compact());

		final var compactHeader = fromCompact.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.RSA1_5, compactHeader.getAlgorithm());
		assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, compactHeader.getEncryption());
		assertNull(compactHeader.getKey());

		final var fromSerial = WebEncryption.parse(jwe.getRecipients().findFirst().get().compact());
		final var serialHeader = fromSerial.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.RSA1_5, serialHeader.getAlgorithm());
		assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, serialHeader.getEncryption());
		assertNotNull(serialHeader.getKey());
		assertNull(serialHeader.getKey().getPrivateKey());

		assertEquals(message, new String(jwe.getRecipients().findFirst().get().decrypt(key)));
		assertEquals(fromCompact, new String(jwe.getRecipients().findFirst().get().decrypt(key)));
		assertEquals(fromSerial, new String(jwe.getRecipients().findFirst().get().decrypt(key)));
	}

}
