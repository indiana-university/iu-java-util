/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
import iu.logging.IuLoggingTestCase;
import iu.logging.LogContext;
import iu.logging.LogEnvironment;

@SuppressWarnings("javadoc")
public class IuLogEventTest extends IuLoggingTestCase {

	@Test
	public void testMostlyNull() {
		final var msg = IdGenerator.generateId();
		final var rec = new LogRecord(Level.INFO, msg);

		final var context = mock(LogEnvironment.class);

		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(context);

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
			assertEquals("INFO,,,,,,,," + Thread.currentThread().getName() + ",,,,," + event.getTimestamp() + ",,"
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
		final var runtime = IdGenerator.generateId();
		final var component = IdGenerator.generateId();
		final var nodeId = IdGenerator.generateId();
		final var callerIpAddress = IdGenerator.generateId();
		final var calledUrl = IdGenerator.generateId();
		final var callerPrincipalName = IdGenerator.generateId();
		final var impersonatedPrincipalName = IdGenerator.generateId();

		final var env = mock(LogEnvironment.class);
		when(env.getNodeId()).thenReturn(nodeId);
		when(env.getEnvironment()).thenReturn(environment);
		when(env.getApplication()).thenReturn(application);
		when(env.getModule()).thenReturn(module);
		when(env.getRuntime()).thenReturn(runtime);
		when(env.getComponent()).thenReturn(component);

		final var context = mock(LogContext.class);
		when(context.getRequestId()).thenReturn(requestId);
		when(context.getCallerIpAddress()).thenReturn(callerIpAddress);
		when(context.getCalledUrl()).thenReturn(calledUrl);
		when(context.getCallerPrincipalName()).thenReturn(callerPrincipalName);
		when(context.getImpersonatedPrincipalName()).thenReturn(impersonatedPrincipalName);

		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			mockProcessLogger.when(() -> ProcessLogger.getActiveContext()).thenReturn(context);

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
			assertEquals("FINE," + requestId + "," + application + "," + environment + "," + module + "," + runtime
					+ "," + component + "," + nodeId + "," + Thread.currentThread().getName() + "," + callerIpAddress
					+ "," + calledUrl + "," + callerPrincipalName + "," + impersonatedPrincipalName + ","
					+ event.getTimestamp() + "," + loggerName + "," + sourceClassName + "." + sourceMethodName + "()"
					+ System.lineSeparator() + msg + System.lineSeparator(), event.toString());
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
		final var nodeId = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var runtime = IdGenerator.generateId();
		final var component = IdGenerator.generateId();
		final var callerIpAddress = IdGenerator.generateId();
		final var calledUrl = IdGenerator.generateId();
		final var callerPrincipalName = IdGenerator.generateId();
		final var impersonatedPrincipalName = IdGenerator.generateId();
		final var env = mock(LogEnvironment.class);
		final var context = mock(LogContext.class);
		when(context.getRequestId()).thenReturn(requestId);
		when(env.getNodeId()).thenReturn(nodeId);
		when(env.getEnvironment()).thenReturn(environment);
		when(env.getApplication()).thenReturn(application);
		when(env.getModule()).thenReturn(module);
		when(env.getRuntime()).thenReturn(runtime);
		when(env.getComponent()).thenReturn(component);
		when(context.getCallerIpAddress()).thenReturn(callerIpAddress);
		when(context.getCalledUrl()).thenReturn(calledUrl);
		when(context.getCallerPrincipalName()).thenReturn(callerPrincipalName);
		when(context.getImpersonatedPrincipalName()).thenReturn(impersonatedPrincipalName);

		final var processLog = IdGenerator.generateId();
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			mockProcessLogger.when(() -> ProcessLogger.export()).thenReturn(processLog);
			mockProcessLogger.when(() -> ProcessLogger.getActiveContext()).thenReturn(context);

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
			assertEquals("SEVERE," + requestId + "," + application + "," + environment + "," + module + "," + runtime
					+ "," + component + "," + nodeId + "," + Thread.currentThread().getName() + "," + callerIpAddress
					+ "," + calledUrl + "," + callerPrincipalName + "," + impersonatedPrincipalName + ","
					+ event.getTimestamp() + "," + loggerName + "," + sourceClassName + "." + sourceMethodName + "()"
					+ System.lineSeparator() + msg + System.lineSeparator() + processLog + System.lineSeparator() + sw
					+ System.lineSeparator(), event.format());
		}
	}

}
