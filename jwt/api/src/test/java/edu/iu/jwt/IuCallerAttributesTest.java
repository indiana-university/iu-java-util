package edu.iu.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuCallerAttributesTest {

	@Test
	void testDefaultType() {
		final var attributes = mock(IuCallerAttributes.class, CALLS_REAL_METHODS);
		assertEquals(IuCallerAttributes.TYPE, attributes.getType());
		assertNull(attributes.getImpersonatedPrincipal());
	}
}
