package iu.auth.impl;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class AuthImplementationTest {

	@Test
	public void testImpl() throws ClassNotFoundException {
		Class.forName(AuthImplementation.class.getName());
	}

}
