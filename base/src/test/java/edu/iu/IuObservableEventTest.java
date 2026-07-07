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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuObservableEventTest {

	@Test
	public void testDefaultMethodsReturnNull() {
		final var event = new IuObservableEvent() {
			@Override
			public String getId() {
				return "id";
			}

			@Override
			public Instant getTime() {
				return Instant.now();
			}

			@Override
			public Instant getStartTime() {
				return Instant.now();
			}

			@Override
			public String getType() {
				return "type";
			}
		};

		assertNull(event.getUri());
		assertNull(event.getContext());
		assertNull(event.getAction());
	}

	@Test
	public void testRequiredFieldsAreReturned() {
		final var id = "event-42";
		final var time = Instant.parse("2026-07-01T12:00:00Z");
		final var startTime = Instant.parse("2026-07-01T11:59:55Z");
		final var type = "LOGIN";

		final var event = new IuObservableEvent() {
			@Override
			public String getId() {
				return id;
			}

			@Override
			public Instant getTime() {
				return time;
			}

			@Override
			public Instant getStartTime() {
				return startTime;
			}

			@Override
			public String getType() {
				return type;
			}
		};

		assertEquals(id, event.getId());
		assertEquals(time, event.getTime());
		assertEquals(startTime, event.getStartTime());
		assertEquals(type, event.getType());
	}

	@Test
	public void testOptionalFieldsCanBeOverridden() {
		final var uri = URI.create("https://example.iu.edu/login");
		final var context = "myapp";
		final var action = "signin";

		final var event = new IuObservableEvent() {
			@Override
			public String getId() {
				return "id";
			}

			@Override
			public Instant getTime() {
				return Instant.now();
			}

			@Override
			public Instant getStartTime() {
				return Instant.now();
			}

			@Override
			public String getType() {
				return "type";
			}

			@Override
			public URI getUri() {
				return uri;
			}

			@Override
			public String getContext() {
				return context;
			}

			@Override
			public String getAction() {
				return action;
			}
		};

		assertEquals(uri, event.getUri());
		assertEquals(context, event.getContext());
		assertEquals(action, event.getAction());
	}

}
