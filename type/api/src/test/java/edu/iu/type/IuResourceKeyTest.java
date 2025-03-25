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
package edu.iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuResourceKeyTest {

	@Test
	public void testSameSame() {
		final var k1 = IuResourceKey.of("", Object.class);
		assertEquals("", k1.name());
		assertSame(Object.class, k1.type());
		assertEquals("", k1.toString());
		assertEquals(k1.hashCode(), k1.hashCode());
		assertEquals(k1, k1);
	}

	@Test
	public void testDefaultNameForPlainClass() {
		assertEquals("object", IuResourceKey.getDefaultResourceName(Object.class));
	}

	@Test
	public void testDefaultName() {
		interface Foo {
		}
		class Bar implements Cloneable, Foo {
		}
		assertEquals("foo", IuResourceKey.getDefaultResourceName(Foo.class));
		assertEquals("foo", IuResourceKey.getDefaultResourceName(Bar.class));
		assertEquals("foo!" + Bar.class.getName(), IuResourceKey.of(Bar.class).toString());
	}

	@Test
	public void testTypeCheck() {
		final var k1 = IuResourceKey.of("", Object.class);
		assertNotEquals(k1, this);
	}

	@Test
	public void testDifferentNames() {
		final var k1 = IuResourceKey.of("a", Object.class);
		final var k2 = IuResourceKey.of("b", Object.class);
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

	@Test
	public void testDifferentTypes() {
		final var k1 = IuResourceKey.of("", Object.class);
		final var k2 = IuResourceKey.of("", Test.class);
		assertEquals("!" + Test.class.getName(), k2.toString());
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFromResource() {
		final var r = mock(IuResource.class);
		when(r.name()).thenReturn("a");
		final var t = mock(IuType.class);
		when(t.erasedClass()).thenReturn(getClass());
		when(r.type()).thenReturn(t);
		assertEquals("a!" + getClass().getName(), IuResourceKey.from(r).toString());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFromResourceRef() {
		final var r = mock(IuResourceReference.class);
		when(r.name()).thenReturn("a");
		final var t = mock(IuType.class);
		when(t.erasedClass()).thenReturn(getClass());
		when(r.type()).thenReturn(t);
		assertEquals("a!" + getClass().getName(), IuResourceKey.from(r).toString());
	}

}
