package edu.iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class IuLoggingContextTest {

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

	@Test
	public void TestIuLoggingContextOverridden() {
		IuLoggingContext context = new IuLoggingContext() {
			@Override
			public String getAuthenticatedPrincipal() {
				return "Test Authenticated Principal";
			}

			@Override
			public String getCalledUrl() {
				return "Test Called URL";
			}

			@Override
			public String getRemoteAddr() {
				return "Test Remote Address";
			}

			@Override
			public String getReqNum() {
				return "Test Req Num";
			}

			@Override
			public String getUserPrincipal() {
				return "Test User Principal";
			}
		};
		assertEquals("Test Authenticated Principal", context.getAuthenticatedPrincipal(),
				"Incorrect Overridden Authenticated Princpal");
		assertEquals("Test Called URL", context.getCalledUrl(), "Incorrect Overridden Called URL");
		assertEquals("Test Remote Address", context.getRemoteAddr(), "Incorrect Overridden Remote Address");
		assertEquals("Test Req Num", context.getReqNum(), "Incorrect Overridden Req Num");
		assertEquals("Test User Principal", context.getUserPrincipal(), "Incorrect Overridden User Principal");
	}

	@Test
	public void TestGetCurrentContextDefault() {
		IuLoggingContext context = IuLoggingContext.getCurrentContext();
		assertNull(context);
	}

	@Test
	public void TestBoundDefaultContext() {
		IuLoggingContext context = new IuLoggingContext() {
			@Override
			public String getAuthenticatedPrincipal() {
				return "Test Authenticated Principal";
			}

			@Override
			public String getCalledUrl() {
				return "Test Called URL";
			}

			@Override
			public String getRemoteAddr() {
				return "Test Remote Address";
			}

			@Override
			public String getReqNum() {
				return "Test Req Num";
			}

			@Override
			public String getUserPrincipal() {
				return "Test User Principal";
			}
		};

		IuLoggingContext.bound(context, () -> {
			IuLoggingContext contextInRunnable = IuLoggingContext.getCurrentContext();
			assertNull(contextInRunnable.getAuthenticatedPrincipal());
			assertNull(contextInRunnable.getCalledUrl());
			assertNull(contextInRunnable.getRemoteAddr());
			assertNull(contextInRunnable.getReqNum());
			assertNull(contextInRunnable.getUserPrincipal());
		});
	}

	@Test
	public void TestBoundOverriddenContext() {
		IuLoggingContext context = new IuLoggingContext() {

		};

		IuLoggingContext.bound(context, () -> {
			IuLoggingContext contextInRunnable = IuLoggingContext.getCurrentContext();
			assertEquals("Test Authenticated Principal", contextInRunnable.getAuthenticatedPrincipal(),
					"Incorrect Bound Overridden Authenticated Princpal");
			assertEquals("Test Called URL", contextInRunnable.getCalledUrl(), "Incorrect Bound Overridden Called URL");
			assertEquals("Test Remote Address", contextInRunnable.getRemoteAddr(),
					"Incorrect Bound Overridden Remote Address");
			assertEquals("Test Req Num", contextInRunnable.getReqNum(), "Incorrect Bound Overridden Req Num");
			assertEquals("Test User Principal", contextInRunnable.getUserPrincipal(),
					"Incorrect Bound Overridden User Principal");
		});
	}
}
