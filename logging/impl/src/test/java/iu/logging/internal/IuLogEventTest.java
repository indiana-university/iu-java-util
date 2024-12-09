package iu.logging.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import iu.logging.Bootstrap;
import iu.logging.IuProcessLogger;

@SuppressWarnings("javadoc")
public class IuLogEventTest {

	@Test
	public void testMostlyNull() {
		final var msg = IdGenerator.generateId();
		final var rec = new LogRecord(Level.INFO, msg);

		final var context = mock(DefaultLogContext.class);

		try (final var mockProcessLogger = mockStatic(IuProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);

			final var event = new IuLogEvent(rec);
			assertEquals(Level.INFO, event.getLevel());
			assertEquals(msg, event.getMessage());
			assertNull(event.getLoggerName());
			assertNull(event.getRequestId());
			assertNull(event.getEnvironment());
			assertNull(event.getApplication());
			assertNull(event.getModule());
			assertNull(event.getComponent());
			assertNull(event.getNodeId());
			assertEquals(Thread.currentThread().getName(), event.getThread());
			assertNull(event.getCallerIpAddress());
			assertNull(event.getCalledUrl());
			assertNull(event.getCallerPrincipalName());
			assertNull(event.getImpersonatedPrincipalName());
			assertNull(event.getSourceClassName());
			assertNull(event.getSourceMethodName());
			assertNull(event.getProcessLog());
			assertNull(event.getError());
			assertEquals(rec.getInstant(), event.getTimestamp());
			assertEquals("INFO,,,,,,," + Thread.currentThread().getName() + ",,,,," + event.getTimestamp() + ",,"
					+ System.lineSeparator() + msg + System.lineSeparator(), event.format());
		}
	}

	@Test
	public void testFine() {
		final var msg = IdGenerator.generateId();
		final var loggerName = IdGenerator.generateId();
		final var sourceClassName = IdGenerator.generateId();
		final var sourceMethodName = IdGenerator.generateId();
		final var rec = new LogRecord(Level.FINE, msg);
		rec.setLoggerName(loggerName);
		rec.setSourceClassName(sourceClassName);
		rec.setSourceMethodName(sourceMethodName);

		final var requestId = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var component = IdGenerator.generateId();
		final var nodeId = IdGenerator.generateId();
		final var callerIpAddress = IdGenerator.generateId();
		final var calledUrl = IdGenerator.generateId();
		final var callerPrincipalName = IdGenerator.generateId();
		final var impersonatedPrincipalName = IdGenerator.generateId();
		final var context = mock(DefaultLogContext.class);
		when(context.getRequestId()).thenReturn(requestId);
		when(context.getEnvironment()).thenReturn(environment);
		when(context.getApplication()).thenReturn(application);
		when(context.getModule()).thenReturn(module);
		when(context.getComponent()).thenReturn(component);
		when(context.getNodeId()).thenReturn(nodeId);
		when(context.getCallerIpAddress()).thenReturn(callerIpAddress);
		when(context.getCalledUrl()).thenReturn(calledUrl);
		when(context.getCallerPrincipalName()).thenReturn(callerPrincipalName);
		when(context.getImpersonatedPrincipalName()).thenReturn(impersonatedPrincipalName);

		try (final var mockProcessLogger = mockStatic(IuProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);

			final var event = new IuLogEvent(rec);
			assertEquals(Level.FINE, event.getLevel());
			assertEquals(msg, event.getMessage());
			assertEquals(loggerName, event.getLoggerName());
			assertEquals(requestId, event.getRequestId());
			assertEquals(environment, event.getEnvironment());
			assertEquals(application, event.getApplication());
			assertEquals(module, event.getModule());
			assertEquals(component, event.getComponent());
			assertEquals(nodeId, event.getNodeId());
			assertEquals(Thread.currentThread().getName(), event.getThread());
			assertEquals(callerIpAddress, event.getCallerIpAddress());
			assertEquals(calledUrl, event.getCalledUrl());
			assertEquals(callerPrincipalName, event.getCallerPrincipalName());
			assertEquals(impersonatedPrincipalName, event.getImpersonatedPrincipalName());
			assertEquals(sourceClassName, event.getSourceClassName());
			assertEquals(sourceMethodName, event.getSourceMethodName());
			assertNull(event.getProcessLog());
			assertNull(event.getError());
			assertEquals(rec.getInstant(), event.getTimestamp());
			assertEquals("FINE," + requestId + "," + environment + "," + application + "," + module + "," + component
					+ "," + nodeId + "," + Thread.currentThread().getName() + "," + callerIpAddress + "," + calledUrl
					+ "," + callerPrincipalName + "," + impersonatedPrincipalName + "," + event.getTimestamp() + ","
					+ loggerName + "," + sourceClassName + "." + sourceMethodName + "()" + System.lineSeparator() + msg
					+ System.lineSeparator(), event.format());
		}
	}

	@Test
	public void testSevere() {
		final var msg = IdGenerator.generateId();
		final var loggerName = IdGenerator.generateId();
		final var sourceClassName = IdGenerator.generateId();
		final var sourceMethodName = IdGenerator.generateId();
		final var thrown = new Throwable();
		final var rec = new LogRecord(Level.SEVERE, msg);
		rec.setThrown(thrown);
		rec.setLoggerName(loggerName);
		rec.setSourceClassName(sourceClassName);
		rec.setSourceMethodName(sourceMethodName);

		final var requestId = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var component = IdGenerator.generateId();
		final var nodeId = IdGenerator.generateId();
		final var callerIpAddress = IdGenerator.generateId();
		final var calledUrl = IdGenerator.generateId();
		final var callerPrincipalName = IdGenerator.generateId();
		final var impersonatedPrincipalName = IdGenerator.generateId();
		final var context = mock(DefaultLogContext.class);
		when(context.getRequestId()).thenReturn(requestId);
		when(context.getEnvironment()).thenReturn(environment);
		when(context.getApplication()).thenReturn(application);
		when(context.getModule()).thenReturn(module);
		when(context.getComponent()).thenReturn(component);
		when(context.getNodeId()).thenReturn(nodeId);
		when(context.getCallerIpAddress()).thenReturn(callerIpAddress);
		when(context.getCalledUrl()).thenReturn(calledUrl);
		when(context.getCallerPrincipalName()).thenReturn(callerPrincipalName);
		when(context.getImpersonatedPrincipalName()).thenReturn(impersonatedPrincipalName);

		final var processLog = IdGenerator.generateId();
		try (final var mockProcessLogger = mockStatic(IuProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
			mockProcessLogger.when(() -> IuProcessLogger.export()).thenReturn(processLog);

			final var event = new IuLogEvent(rec);
			assertEquals(Level.SEVERE, event.getLevel());
			assertEquals(msg, event.getMessage());
			assertEquals(loggerName, event.getLoggerName());
			assertEquals(requestId, event.getRequestId());
			assertEquals(environment, event.getEnvironment());
			assertEquals(application, event.getApplication());
			assertEquals(module, event.getModule());
			assertEquals(component, event.getComponent());
			assertEquals(nodeId, event.getNodeId());
			assertEquals(Thread.currentThread().getName(), event.getThread());
			assertEquals(callerIpAddress, event.getCallerIpAddress());
			assertEquals(calledUrl, event.getCalledUrl());
			assertEquals(callerPrincipalName, event.getCallerPrincipalName());
			assertEquals(impersonatedPrincipalName, event.getImpersonatedPrincipalName());
			assertEquals(sourceClassName, event.getSourceClassName());
			assertEquals(sourceMethodName, event.getSourceMethodName());
			assertEquals(processLog, event.getProcessLog());

			final var sw = new StringWriter();
			thrown.printStackTrace(new PrintWriter(sw));
			assertEquals(sw.toString(), event.getError());

			assertEquals(rec.getInstant(), event.getTimestamp());
			assertEquals("SEVERE," + requestId + "," + environment + "," + application + "," + module + "," + component
					+ "," + nodeId + "," + Thread.currentThread().getName() + "," + callerIpAddress + "," + calledUrl
					+ "," + callerPrincipalName + "," + impersonatedPrincipalName + "," + event.getTimestamp() + ","
					+ loggerName + "," + sourceClassName + "." + sourceMethodName + "()" + System.lineSeparator() + msg
					+ System.lineSeparator() + processLog + System.lineSeparator() + sw + System.lineSeparator(),
					event.format());
		}
	}

}
