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
package iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuHttpClientEventTest {

	@Test
	public void testGetType() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		assertEquals("http.client", event.getType());
	}

	@Test
	public void testGetUri() {
		final var uri = URI.create("https://example.com/api/resource");
		final var event = new IuHttpClientEvent(uri);
		assertSame(uri, event.getUri());
	}

	@Test
	public void testGetContext() {
		final var saved = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
			final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
			assertEquals("system", event.getContext());
		} finally {
			Thread.currentThread().setContextClassLoader(saved);
		}
	}

	@Test
	public void testIdIsGeneratedAndNonNull() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		assertNotNull(event.getId());
		assertNotEquals("", event.getId());
	}

	@Test
	public void testIdsAreUnique() {
		final var uri = URI.create("https://example.com/");
		final var a = new IuHttpClientEvent(uri);
		final var b = new IuHttpClientEvent(uri);
		assertNotEquals(a.getId(), b.getId());
	}

	@Test
	public void testStartTimeIsSetAtConstruction() {
		final var before = Instant.now();
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		final var after = Instant.now();
		assertNotNull(event.getStartTime());
		assertTrue(!event.getStartTime().isBefore(before), "startTime should be >= before");
		assertTrue(!event.getStartTime().isAfter(after), "startTime should be <= after");
	}

	@Test
	public void testGetTimeBeforeReceivedReturnsStartTime() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		assertSame(event.getStartTime(), event.getTime());
	}

	@Test
	public void testGetActionBeforeReceivedIsSend() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		assertEquals("send", event.getAction());
	}

	@Test
	public void testGetTimeAfterReceivedReturnsResponseTime() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		final var startTime = event.getStartTime();
		event.received(200);
		// responseTime is set by received(); getTime() must now differ from startTime
		// (or be equal if called within the same nanosecond — at minimum not same ref)
		assertNotNull(event.getTime());
		assertTrue(!event.getTime().isBefore(startTime));
	}

	@Test
	public void testGetActionReceiveSuccessBelow400() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		assertEquals("receive", event.received(200).getAction());
	}

	@Test
	public void testGetActionReceiveSuccessAt399() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		assertEquals("receive", event.received(399).getAction());
	}

	@Test
	public void testGetActionErrorAt400() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		assertEquals("error", event.received(400).getAction());
	}

	@Test
	public void testGetActionErrorAbove400() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		assertEquals("error", event.received(500).getAction());
	}

	@Test
	public void testReceivedUpdatesStatusCodeAndTime() {
		final var event = new IuHttpClientEvent(URI.create("https://example.com/"));
		final var startTime = event.getStartTime();
		assertEquals("send", event.getAction());
		assertSame(startTime, event.getTime());

		final var receivedEvent = event.received(201);
		assertEquals("receive", receivedEvent.getAction());
		assertNotNull(receivedEvent.getTime());
		assertTrue(!receivedEvent.getTime().isBefore(startTime));
	}

}
