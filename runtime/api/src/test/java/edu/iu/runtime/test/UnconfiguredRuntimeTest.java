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
package edu.iu.runtime.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Type;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import edu.iu.runtime.IuRuntime;
import edu.iu.runtime.IuRuntimeConfiguration;
import iu.runtime.EmptyRuntime;

public class UnconfiguredRuntimeTest {

	@Test
	public void testFailsafe() {
		assertTrue(IuRuntime.PROVIDER instanceof EmptyRuntime);
	}

	@Test
	public void testSameSame() {
		var env = IuRuntime.PROVIDER.getEnvironment();
		assertSame(env, IuRuntime.PROVIDER.getBuildConfiguration());
		assertSame(env, IuRuntime.PROVIDER.getSecret(null));
	}

	@Test
	public void testLogsFailures() {
		try (var logger = mockStatic(Logger.class)) {
			var log = mock(Logger.class);
			logger.when(() -> Logger.getLogger(IuRuntimeConfiguration.class.getName())).thenReturn(log);
			assertEquals("bar", IuRuntime.PROVIDER.getEnvironment().getValue("foo", "bar"));
			verify(log).log(eq(Level.FINEST), isA(IllegalArgumentException.class),
					argThat(new ArgumentMatcher<Supplier<String>>() {
						@Override
						public boolean matches(Supplier<String> argument) {
							assertNotNull(argument.get());
							return true;
						}
					}));
		}
	}

	@Test
	public void testGetValue() {
		var env = IuRuntime.PROVIDER.getEnvironment();
		assertThrows(IllegalArgumentException.class, () -> env.getValue("foo"));
		assertThrows(IllegalArgumentException.class, () -> env.getValue("foo", String.class));
		assertThrows(IllegalArgumentException.class, () -> env.getValue("foo", (Type) String.class));
		assertEquals("bar", env.getValue("foo", "bar"));
		assertEquals("bar", env.getValue("foo", String.class, "bar"));
		assertEquals("bar", env.getValue("foo", (Type) String.class, "bar"));
	}

}
