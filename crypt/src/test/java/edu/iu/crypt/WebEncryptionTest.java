package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class WebEncryptionTest {

	@Test
	@SuppressWarnings("deprecation")
	public void testRsaLegacy() throws NoSuchAlgorithmException {
		final var key = WebKey.builder().algorithm(Algorithm.RSA1_5).ephemeral().build();
		final var message = IdGenerator.generateId();

		final var jwe = WebEncryption.builder().enc(Encryption.AES_128_CBC_HMAC_SHA_256).addRecipient()
				.algorithm(Algorithm.RSA1_5).jwk(key, false).then().encrypt(message);
		assertNull(jwe.getAdditionalData());

		final var fromCompact = WebEncryption.parse(jwe.getRecipients().findFirst().get().compact());

		final var compactHeader = fromCompact.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.RSA1_5, compactHeader.getAlgorithm());
		assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, fromCompact.getEncryption());
		assertNull(compactHeader.getKey());

		final var fromSerial = WebEncryption.parse(jwe.toString());
		final var serialHeader = fromSerial.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.RSA1_5, serialHeader.getAlgorithm());
		assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, fromSerial.getEncryption());
		assertNotNull(serialHeader.getKey());
		assertNull(serialHeader.getKey().getPrivateKey());

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(message, new String(jwe.decrypt(key)));
		
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(message, new String(jwe.decrypt(key)));
		
		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(message, new String(jwe.decrypt(key)));
	}

}
