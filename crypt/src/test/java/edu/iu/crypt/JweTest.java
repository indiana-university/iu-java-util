package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class JweTest {

	@Test
	public void testRsaLegacy() throws NoSuchAlgorithmException {
		assertEquals(256L, new BigInteger(new byte[] {0,0,0,0,0,0,1,0}).longValue());

		final var gen = KeyPairGenerator.getInstance("RSA");
		gen.initialize(2048);
		final var key = WebKey.from(null, gen.generateKeyPair());

		final var message = IdGenerator.generateId();

		assertEquals(message, new String(WebEncryption.encrypt(new WebEncryptionHeader() {
			@SuppressWarnings("deprecation")
			@Override
			public Algorithm getAlgorithm() {
				return Algorithm.RSA1_5;
			}

			@Override
			public Encryption getEncryption() {
				return Encryption.AES_128_CBC_HMAC_SHA_256;
			}
		}, message.getBytes()).decrypt(key)));
	}

}
