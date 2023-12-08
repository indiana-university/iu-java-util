package edu.iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import iu.logging.TestIuLoggingContextImpl;

/**
 * Test class for IuLoggingContext.
 */
public class IuLoggingContextTest {

	/**
	 * Test default methods.
	 */
	@Test
	public void TestIuLoggingContextDefaults() {
		IuLoggingContext context = new IuLoggingContext() {

		};
		assertNull(context.getAuthenticatedPrincipal());
		assertNull(context.getCalledUrl());
		assertNull(context.getRemoteAddr());
		assertNull(context.getReqNum());
		assertNull(context.getUserPrincipal());
	}

	/**
	 * Test default methods overridden.
	 */
	@Test
	public void TestIuLoggingContextOverridden() {
		IuLoggingContext context = new TestIuLoggingContextImpl();
		assertEquals("Test Authenticated Principal", context.getAuthenticatedPrincipal(),
				"Incorrect Overridden Authenticated Princpal");
		assertEquals("Test Called URL", context.getCalledUrl(), "Incorrect Overridden Called URL");
		assertEquals("Test Remote Address", context.getRemoteAddr(), "Incorrect Overridden Remote Address");
		assertEquals("Test Request Number", context.getReqNum(), "Incorrect Overridden Request Number");
		assertEquals("Test User Principal", context.getUserPrincipal(), "Incorrect Overridden User Principal");
	}
}
