package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class InitTest extends IuCryptApiTestCase {

	@Test
	public void testInit() {
		assertDoesNotThrow(Init::init);
	}
}
