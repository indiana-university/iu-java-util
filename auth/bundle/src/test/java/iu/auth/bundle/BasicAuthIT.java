package iu.auth.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import edu.iu.auth.IuApiCredentials;

@SuppressWarnings("javadoc")
public class BasicAuthIT {

	@Test
	public void testBasic() {
		final var basic = IuApiCredentials.basic("foo", "bar");
		assertEquals("foo", basic.getName());
		assertEquals("bar", basic.getPassword());
	}

}
