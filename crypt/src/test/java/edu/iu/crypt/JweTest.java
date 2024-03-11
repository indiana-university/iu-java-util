package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
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

		final var jwe = WebEncryption.encrypt(new WebEncryptionHeader() {
			@Override
			public WebKey getKey() {
				return key;
			}

			@SuppressWarnings("deprecation")
			@Override
			public Algorithm getAlgorithm() {
				return Algorithm.RSA1_5;
			}

			@Override
			public Encryption getEncryption() {
				return Encryption.AES_128_CBC_HMAC_SHA_256;
			}
		}, message.getBytes());

		final var fromCompact = Jwe.readJwe(jwe.compact());
		assertEquals(fromCompact, jwe);

		final var fromSerial = Jwe.readJwe(jwe.toString());
		assertEquals(fromSerial, jwe);

		assertEquals(message, new String(jwe.decrypt(key)));
		assertEquals(message, new String(fromCompact.decrypt(key)));
		assertEquals(message, new String(fromSerial.decrypt(key)));
	}

}
