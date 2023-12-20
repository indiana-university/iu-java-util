package edu.iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.logging.IuLoggingEnvironment.RuntimeMode;

/**
 * Test class for IuLogEvent.
 */
public class IuLogEventTest {

	/**
	 * Test default methods.
	 */
	@Test
	public void testIuLogEventDefaults() {
		IuLogEvent event = new IuLogEvent() {

		};
		assertNull(event.getApplication());
		assertNull(event.getAuthenticatedPrincipal());
		assertNull(event.getCalledUrl());
		assertNull(event.getComponent());
		assertNull(event.getEnvironment());
		assertNull(event.getHostname());
		assertNull(event.getInstant());
		assertNull(event.getLevel());
		assertNull(event.getLoggerName());
		assertNull(event.getMessage());
		assertNull(event.getMode());
		assertNull(event.getModule());
		assertNull(event.getNodeId());
		assertNull(event.getRemoteAddr());
		assertNull(event.getReqNum());
		assertNull(event.getSourceClassName());
		assertNull(event.getSourceMethodName());
		assertNull(event.getThrown());
		assertNull(event.getUserPrincipal());
	}

	/**
	 * Test overridden methods.
	 */
	@Test
	public void testIuLogEventOverrides() {
		IuLogEvent event = new IuLogEvent() {
			@Override
			public String getApplication() {
				return "Test Application";
			}

			@Override
			public String getAuthenticatedPrincipal() {
				return "Test Authenticated Principal";
			}

			@Override
			public String getCalledUrl() {
				return "Test Called URL";
			}

			@Override
			public String getComponent() {
				return "Test Component";
			}

			@Override
			public String getEnvironment() {
				return "Test Environment";
			}

			@Override
			public String getHostname() {
				return "Test Hostname";
			}

			@Override
			public Instant getInstant() {
				return Instant.EPOCH;
			}

			@Override
			public Level getLevel() {
				return Level.FINE;
			}

			@Override
			public String getLoggerName() {
				return "Test Logger Name";
			}

			@Override
			public String getMessage() {
				return "Test Message";
			}

			@Override
			public RuntimeMode getMode() {
				return RuntimeMode.TEST;
			}

			@Override
			public String getModule() {
				return "Test Module";
			}

			@Override
			public String getNodeId() {
				return "Test Node Id";
			}

			@Override
			public String getRemoteAddr() {
				return "Test Remote Address";
			}

			@Override
			public String getReqNum() {
				return "Test Request Number";
			}

			@Override
			public String getSourceClassName() {
				return "Test Source Class Name";
			}

			@Override
			public String getSourceMethodName() {
				return "Test Source Method Name";
			}

			@Override
			public String getThrown() {
				return "Test Thrown";
			}

			@Override
			public String getUserPrincipal() {
				return "Test User Principal";
			}
		};
		assertEquals("Test Application", event.getApplication(), "Incorrect Overridden Application");
		assertEquals("Test Authenticated Principal", event.getAuthenticatedPrincipal(),
				"Incorrect Overridden Authenticated Principal");
		assertEquals("Test Called URL", event.getCalledUrl(), "Incorrect Overridden Called Url");
		assertEquals("Test Component", event.getComponent(), "Incorrect Overridden Component");
		assertEquals("Test Environment", event.getEnvironment(), "Incorrect Overridden Environment");
		assertEquals("Test Hostname", event.getHostname(), "Incorrect Overridden Hostname");
		assertEquals(Instant.EPOCH, event.getInstant(), "Incorrect Overridden Instant");
		assertEquals(Level.FINE, event.getLevel(), "Incorrect Overridden Level");
		assertEquals("Test Logger Name", event.getLoggerName(), "Incorrect Overridden Logger Name");
		assertEquals("Test Message", event.getMessage(), "Incorrect Overridden Message");
		assertEquals(RuntimeMode.TEST, event.getMode(), "Incorrect Overridden Runtime Mode");
		assertEquals("Test Module", event.getModule(), "Incorrect Overridden Module");
		assertEquals("Test Node Id", event.getNodeId(), "Incorrect Overridden Node Id");
		assertEquals("Test Remote Address", event.getRemoteAddr(), "Incorrect Overridden Remote Address");
		assertEquals("Test Request Number", event.getReqNum(), "Incorrect Overridden Request Number");
		assertEquals("Test Source Class Name", event.getSourceClassName(), "Incorrect Overridden Source Class Name");
		assertEquals("Test Source Method Name", event.getSourceMethodName(), "Incorrect Overridden Source Method Name");
		assertEquals("Test Thrown", event.getThrown(), "Incorrect Overridden Thrown");
		assertEquals("Test User Principal", event.getUserPrincipal(), "Incorrect Overridden User Principal");
	}
}
