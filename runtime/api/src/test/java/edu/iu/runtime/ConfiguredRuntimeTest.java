/*
 * Copyright © 2023 Indiana University
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
package edu.iu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import iu.runtime.EmptyRuntime;
import iu.runtime.RuntimeFactory;

@SuppressWarnings("javadoc")
public class ConfiguredRuntimeTest {

	@Test
	public void testGetValue() {
		assertTrue(IuRuntime.PROVIDER instanceof EmptyRuntime);

		IuRuntimeConfiguration env;
		try (var serviceLoader = mockStatic(ServiceLoader.class)) {
			var runtime = mock(IuRuntime.class);
			when(runtime.getEnvironment()).thenReturn((reference, type) -> {
				assertEquals(String.class, type);
				if ("foo".equals(reference))
					return "baz";
				else
					throw new IllegalArgumentException();
			});

			var iter = mock(Iterator.class);
			when(iter.hasNext()).thenReturn(true, false);
			when(iter.next()).thenReturn(runtime).thenThrow(NoSuchElementException.class);

			var loader = mock(ServiceLoader.class);
			when(loader.iterator()).thenReturn(iter);

			serviceLoader.when(() -> ServiceLoader.load(IuRuntime.class, IuRuntime.class.getClassLoader()))
					.thenReturn(loader);

			env = RuntimeFactory.getProvider().getEnvironment();
		}
		assertThrows(IllegalArgumentException.class, () -> env.getValue("bar"));
		assertEquals("baz", env.getValue("foo"));
		assertEquals("baz", env.getValue("foo", String.class));
		assertEquals("baz", env.getValue("foo", (Type) String.class));
		assertEquals("baz", env.getValue("foo", "bar"));
		assertEquals("baz", env.getValue("foo", String.class, "bar"));
		assertEquals("baz", env.getValue("foo", (Type) String.class, "bar"));
	}

}
