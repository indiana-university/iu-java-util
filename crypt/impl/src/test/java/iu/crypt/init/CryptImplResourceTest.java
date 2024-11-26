package iu.crypt.init;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class CryptImplResourceTest {

	@Test
	public void testInit() {
		assertDoesNotThrow(CryptImplResource::new);
	}
}
