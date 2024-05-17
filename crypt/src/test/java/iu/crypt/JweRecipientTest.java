package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class JweRecipientTest {

	@Test
	public void testPasswordSaltTooShort() {
		final var jose = new Jose(IuJson.object() //
				.add("alg", Algorithm.PBES2_HS256_A128KW.alg) //
				.add("enc", Encryption.A128GCM.enc) //
				.add("p2c", 1000) //
				.add("p2s", UnpaddedBinary.JSON.toJson("foo".getBytes())) //
				.build());
		final var jweRecipient = new JweRecipient(jose, null);
		final var password = IdGenerator.generateId();
		assertThrows(IllegalArgumentException.class, () -> jweRecipient.passphraseDerivedKey(password));
	}

	@Test
	public void testPasswordCountTooLow() {
		final var jose = new Jose(IuJson.object() //
				.add("alg", Algorithm.PBES2_HS256_A128KW.alg) //
				.add("enc", Encryption.A128GCM.enc) //
				.add("p2c", 4) //
				.add("p2s", UnpaddedBinary.JSON.toJson(IdGenerator.generateId().getBytes())) //
				.build());
		final var jweRecipient = new JweRecipient(jose, null);
		final var password = IdGenerator.generateId();
		assertThrows(IllegalArgumentException.class, () -> jweRecipient.passphraseDerivedKey(password));
	}

}
