package iu.auth.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class BasicAuthCredentialsTest {

	@Test
	public void testBasicAuth() {
		final var auth = new BasicAuthCredentials("foo", "bar");
		assertEquals("foo", auth.getName());
		assertEquals("bar", auth.getPassword());
	}

}
