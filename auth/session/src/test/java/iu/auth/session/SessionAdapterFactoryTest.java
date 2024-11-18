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
package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuSessionConfiguration;
import edu.iu.client.IuJsonAdapter;

@SuppressWarnings("javadoc")
public class SessionAdapterFactoryTest {

	public interface TestResource {
		URI getUri();

		String getValue();
	}

	public interface TestSession {
		Iterable<TestResource> getResources();

		void setResources(Iterable<TestResource> resources);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testApply() {
		final Queue<TestResource> resources = new ArrayDeque<>();
		final var uri = URI.create(IdGenerator.generateId());
		final var value = IdGenerator.generateId();
		final var resource = mock(TestResource.class);
		when(resource.getUri()).thenReturn(uri);
		when(resource.getValue()).thenReturn(value);
		resources.add(resource);
		final var session = mock(TestSession.class);
		when(session.getResources()).thenReturn(resources);

		final var factory = new SessionAdapterFactory<>(TestSession.class);
		final var adapter = (IuJsonAdapter) factory.apply(TestSession.class);
		final var serialized = adapter.toJson(session);
		final var copy = (TestSession) adapter.fromJson(serialized);
		final var copyResources = copy.getResources();
		final var copyResource = copyResources.iterator().next();
		assertEquals(uri, copyResource.getUri());
		assertEquals(value, copyResource.getValue());
	}

	public interface IllegalSession {
		IuSessionConfiguration getConfiguration(); // disallowed, not same module
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIllegalSession() {
		final var factory = new SessionAdapterFactory<>(IllegalSession.class);
        final var adapter = (IuJsonAdapter) factory.apply(IllegalSession.class);
        final var session = mock(IllegalSession.class);
        assertThrows(UnsupportedOperationException.class, () -> adapter.toJson(session));
	}
	
}
