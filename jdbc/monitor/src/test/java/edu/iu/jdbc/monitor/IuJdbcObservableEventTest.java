/*
 * Copyright © 2026 Indiana University
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
package edu.iu.jdbc.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuJdbcObservableEventTest {

	private static final URI TEST_URI = URI.create("jdbc:mock://localhost/test");

	@Test
	public void testOpenEventHasNullTime() {
		final var event = new IuJdbcObservableEvent(TEST_URI, "jdbc.connection", "open");
		assertNull(event.getTime());
	}

	@Test
	public void testOpenEventFieldsAreSet() {
		final var before = Instant.now();
		final var event = new IuJdbcObservableEvent(TEST_URI, "jdbc.connection", "open");
		final var after = Instant.now();

		assertNotNull(event.getId());
		assertFalse(event.getId().isBlank());
		assertFalse(event.getStartTime().isBefore(before));
		assertFalse(event.getStartTime().isAfter(after));
		assertEquals(TEST_URI, event.getUri());
		assertEquals("jdbc.connection", event.getType());
		assertEquals("open", event.getAction());
		assertNotNull(event.getContext());
		assertFalse(event.getContext().isBlank());
	}

	@Test
	public void testEndEventPreservesIdentityFields() {
		final var openEvent = new IuJdbcObservableEvent(TEST_URI, "jdbc.connection", "open");
		final var closeEvent = openEvent.end("close");

		assertEquals(openEvent.getId(), closeEvent.getId());
		assertEquals(openEvent.getStartTime(), closeEvent.getStartTime());
		assertEquals(openEvent.getUri(), closeEvent.getUri());
		assertEquals(openEvent.getType(), closeEvent.getType());
		assertEquals(openEvent.getContext(), closeEvent.getContext());
	}

	@Test
	public void testEndEventSetsCloseTimeAndAction() {
		final var openEvent = new IuJdbcObservableEvent(TEST_URI, "jdbc.statement", "open");
		final var before = Instant.now();
		final var execEvent = openEvent.end("exec");
		final var after = Instant.now();

		assertEquals("exec", execEvent.getAction());
		assertNotNull(execEvent.getTime());
		assertFalse(execEvent.getTime().isBefore(before));
		assertFalse(execEvent.getTime().isAfter(after));
	}

	@Test
	public void testEndEventDoesNotMutateOpenEvent() {
		final var openEvent = new IuJdbcObservableEvent(TEST_URI, "jdbc.connection", "open");
		openEvent.end("close");
		assertNull(openEvent.getTime());
		assertEquals("open", openEvent.getAction());
	}

	@Test
	public void testEndEventsForDifferentActionsHaveDistinctTimes() throws InterruptedException {
		final var openEvent = new IuJdbcObservableEvent(TEST_URI, "jdbc.statement", "open");
		final var execEvent = openEvent.end("exec");
		Thread.sleep(1);
		final var closeEvent = openEvent.end("close");

		assertEquals("exec", execEvent.getAction());
		assertEquals("close", closeEvent.getAction());
		assertNotEquals(execEvent.getTime(), closeEvent.getTime());
	}

	@Test
	public void testDistinctOpenEventsHaveDistinctIds() {
		final var a = new IuJdbcObservableEvent(TEST_URI, "jdbc.connection", "open");
		final var b = new IuJdbcObservableEvent(TEST_URI, "jdbc.connection", "open");
		assertNotEquals(a.getId(), b.getId());
	}

	@Test
	public void testAllEventTypesAreSupported() {
		for (final var type : new String[] { "jdbc.connection", "jdbc.statement", "jdbc.preparedstatement",
				"jdbc.resultset" }) {
			final var event = new IuJdbcObservableEvent(TEST_URI, type, "open");
			assertEquals(type, event.getType());
			assertEquals("exec", event.end("exec").getAction());
			assertEquals("close", event.end("close").getAction());
		}
	}

}
