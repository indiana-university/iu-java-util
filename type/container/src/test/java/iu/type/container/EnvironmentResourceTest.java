/*
 * Copyright © 2025 Indiana University
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
package iu.type.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.type.IuType;
import iu.type.container.spi.IuEnvironment;

@SuppressWarnings("javadoc")
public class EnvironmentResourceTest {

	@Test
	public void testDefaultValue() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);
		final var name = IdGenerator.generateId();
		final var defaultValue = IdGenerator.generateId();
		final var resource = new EnvironmentResource<>(env, name, type, defaultValue);
		assertEquals(name, resource.name());
		assertEquals(type, resource.type());
		assertFalse(resource.needsAuthentication());
		assertTrue(resource.shared());
		assertEquals(0, resource.priority());
		assertThrows(UnsupportedOperationException.class, () -> resource.factory(null));

		assertEquals(defaultValue, resource.factory().get());
		verify(env).resolve(name, type.erasedClass());
	}

	@Test
	public void testMissingValue() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);
		final var name = IdGenerator.generateId();
		final var resource = new EnvironmentResource<>(env, name, type, null);
		assertEquals(name, resource.name());
		assertEquals(type, resource.type());
		assertFalse(resource.needsAuthentication());
		assertTrue(resource.shared());
		assertEquals(0, resource.priority());
		assertThrows(UnsupportedOperationException.class, () -> resource.factory(null));

		final var error = assertThrows(IllegalArgumentException.class, resource.factory()::get);
		assertEquals("Missing environment entry for " + name + "!" + type.name(), error.getMessage());
		verify(env).resolve(name, type.erasedClass());
	}

	@Test
	public void testExactMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);
		final var name = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		when(env.resolve(name, type.erasedClass())).thenReturn(value);
		final var resource = new EnvironmentResource<>(env, name, type, null);
		assertEquals(name, resource.name());
		assertEquals(type, resource.type());
		assertFalse(resource.needsAuthentication());
		assertTrue(resource.shared());
		assertEquals(0, resource.priority());
		assertThrows(UnsupportedOperationException.class, () -> resource.factory(null));

		assertEquals(value, resource.factory().get());
	}

	@Test
	public void testRelativeMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);

		final var context1 = IdGenerator.generateId();
		final var context2 = IdGenerator.generateId();
		final var name = IdGenerator.generateId();

		final var value = IdGenerator.generateId();
		when(env.resolve(name, type.erasedClass())).thenReturn(value);

		final var resource = new EnvironmentResource<>(env, context1 + '/' + context2 + '/' + name, type, null);
		assertEquals(value, resource.factory().get());
	}

	@Test
	public void testPrefixMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);

		final var context1 = IdGenerator.generateId();
		final var context2 = IdGenerator.generateId();
		final var name = IdGenerator.generateId();

		final var value = IdGenerator.generateId();
		when(env.resolve("/" + context1 + "/" + name, type.erasedClass())).thenReturn(value);

		final var resource = new EnvironmentResource<>(env, context1 + '/' + context2 + '/' + name, type, null);
		assertEquals(value, resource.factory().get());
	}

	@Test
	public void testSuffixMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);

		final var context1 = IdGenerator.generateId();
		final var context2 = IdGenerator.generateId();
		final var name = IdGenerator.generateId();

		final var value = IdGenerator.generateId();
		when(env.resolve(context2 + "/" + name, type.erasedClass())).thenReturn(value);

		final var resource = new EnvironmentResource<>(env, context1 + '/' + context2 + '/' + name, type, null);
		assertEquals(value, resource.factory().get());
	}

	@Test
	public void testNoMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);

		final var context1 = IdGenerator.generateId();
		final var context2 = IdGenerator.generateId();
		final var name = IdGenerator.generateId();

		final var value = IdGenerator.generateId();

		final var resource = new EnvironmentResource<>(env, context1 + '/' + context2 + '/' + name, type, value);
		assertEquals(value, resource.factory().get());
	}

}
