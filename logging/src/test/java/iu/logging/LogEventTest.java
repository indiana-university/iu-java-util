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
package iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

import edu.iu.logging.IuLoggingContext;
import edu.iu.logging.IuLoggingEnvironment;
import edu.iu.logging.IuLoggingEnvironment.RuntimeMode;

/**
 * Test class for IuLogEvent.
 */
public class LogEventTest {

	/**
	 * Test default methods.
	 */
	@Test
	public void testDefaults() {
		LogEvent event = new LogEvent() {

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
		assertNull(event.getRuntime());
		assertNull(event.getSourceClassName());
		assertNull(event.getSourceMethodName());
		assertEquals("main", event.getThread());
		assertNull(event.getThrown());
		assertNull(event.getUserPrincipal());
	}

	/**
	 * Test overridden methods.
	 */
	@Test
	public void testOverrides() {
		IuLoggingEnvironment testLoggingEnvironment = new TestIuLoggingEnvironmentImpl();
		IuLoggingContext testLoggingContext = new TestIuLoggingContextImpl();
		LogRecord testRecord = new LogRecord(Level.FINE, "Test Message");
		testRecord.setInstant(Instant.EPOCH);
		testRecord.setLoggerName("Test Logger Name");
		testRecord.setSourceClassName("Test Source Class Name");
		testRecord.setSourceMethodName("Test Source Method Name");
		testRecord.setThrown(new Throwable("Test Thrown"));
		LogEvent event = new LogEvent(testRecord, testLoggingContext, testLoggingEnvironment);

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
		assertEquals("Test Runtime", event.getRuntime(), "Incorrect Overridden Runtime");
		assertEquals("Test Source Class Name", event.getSourceClassName(), "Incorrect Overridden Source Class Name");
		assertEquals("Test Source Method Name", event.getSourceMethodName(), "Incorrect Overridden Source Method Name");
		assertEquals("main", event.getThread());
		assertEquals("java.lang.Throwable: Test Thrown",
				event.getThrown().split(System.lineSeparator(), 2)[0],
				"Incorrect Overridden Thrown");
		assertEquals("Test User Principal", event.getUserPrincipal(), "Incorrect Overridden User Principal");
	}

	/**
	 * Test overridden methods.
	 */
	@Test
	public void testOverridesThrownNotSet() {
		IuLoggingEnvironment testLoggingEnvironment = new TestIuLoggingEnvironmentImpl();
		IuLoggingContext testLoggingContext = new TestIuLoggingContextImpl();
		LogRecord testRecord = new LogRecord(Level.FINE, "Test Message");
		LogEvent event = new LogEvent(testRecord, testLoggingContext, testLoggingEnvironment);

		assertNull(event.getThrown());
	}

	/**
	 * Test setters.
	 */
	@Test
	public void testSetters() {
		IuLoggingEnvironment e = new TestIuLoggingEnvironmentImpl();
		IuLoggingContext c = new TestIuLoggingContextImpl();
		LogRecord testRecord = new LogRecord(Level.FINE, "Test Message");
		testRecord.setInstant(Instant.EPOCH);
		testRecord.setLoggerName("Test Logger Name");
		testRecord.setSourceClassName("Test Source Class Name");
		testRecord.setSourceMethodName("Test Source Method Name");
		testRecord.setThrown(new Throwable("Test Thrown"));
		LogEvent event = new LogEvent();
		event.setApplication(e.getApplication());
		event.setAuthenticatedPrincipal(c.getAuthenticatedPrincipal());
		event.setCalledUrl(c.getCalledUrl());
		event.setComponent(e.getComponent());
		event.setEnvironment(e.getEnvironment());
		event.setHostname(e.getHostname());
		event.setInstant(testRecord.getInstant());
		event.setLevel(Level.FINE);
		event.setLoggerName(testRecord.getLoggerName());
		event.setMessage(testRecord.getMessage());
		event.setMode(e.getMode());
		event.setModule(e.getModule());
		event.setNodeId(e.getNodeId());
		event.setRemoteAddr(c.getRemoteAddr());
		event.setReqNum(c.getReqNum());
		event.setRuntime(e.getRuntime());
		event.setSourceClassName(testRecord.getSourceClassName());
		event.setSourceMethodName(testRecord.getSourceMethodName());
		event.setThread("main");
		event.setThrown("java.lang.Throwable: Test Thrown");
		event.setUserPrincipal(c.getUserPrincipal());

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
		assertEquals("Test Runtime", event.getRuntime(), "Incorrect Overridden Runtime");
		assertEquals("Test Source Class Name", event.getSourceClassName(), "Incorrect Overridden Source Class Name");
		assertEquals("Test Source Method Name", event.getSourceMethodName(), "Incorrect Overridden Source Method Name");
		assertEquals("main", event.getThread());
		assertEquals("java.lang.Throwable: Test Thrown", event.getThrown().split("\r\n")[0],
				"Incorrect Overridden Thrown");
		assertEquals("Test User Principal", event.getUserPrincipal(), "Incorrect Overridden User Principal");
	}
}
