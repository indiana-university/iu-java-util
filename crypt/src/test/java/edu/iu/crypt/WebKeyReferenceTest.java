package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class WebKeyReferenceTest {

	@Test
	public void testIdAndAlgNullByDefault() {
		final var ref = mock(WebKeyReference.class, CALLS_REAL_METHODS);
		assertNull(ref.getKeyId());
		assertNull(ref.getAlgorithm());
	}
}
