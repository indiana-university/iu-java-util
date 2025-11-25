package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class CallerAttributesTest {

	@Test
	public void testType() {
		final var a = mock(CallerAttributes.class, CALLS_REAL_METHODS);
		assertEquals(CallerAttributes.TYPE, a.getType());
	}

}
