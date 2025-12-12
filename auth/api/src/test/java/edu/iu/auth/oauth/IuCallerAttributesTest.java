package edu.iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuCallerAttributesTest {

	@Test
	public void testType() {
		final var a = mock(IuCallerAttributes.class, CALLS_REAL_METHODS);
		assertEquals(IuCallerAttributes.TYPE, a.getType());
	}

}
