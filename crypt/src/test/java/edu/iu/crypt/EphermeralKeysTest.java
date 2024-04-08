package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.security.spec.AlgorithmParameterSpec;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class EphermeralKeysTest {

	@Test
	public void testIllegalEc() {
		final var spec = mock(AlgorithmParameterSpec.class);
		assertThrows(IllegalArgumentException.class, () -> EphemeralKeys.ec(spec));
	}

	@Test
	public void testCek() {
		 assertEquals(16, EphemeralKeys.contentEncryptionKey(128).length);
		 assertEquals(32, EphemeralKeys.contentEncryptionKey("HmacSHA256", 256).length);
	}

}
