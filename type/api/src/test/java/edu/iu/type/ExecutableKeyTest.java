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
package edu.iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(IuTypeOfMockSupport.class)
@SuppressWarnings("javadoc")
public class ExecutableKeyTest {

	@Test
	public void testEqualsFailSafes() {
		var k = IuExecutableKey.of(null, Object.class);
		assertNotEquals(k, null);
		assertNotEquals(k, new Object());
		assertEquals(k, k);
		assertEquals("<init>(Object)", k.toString());
	}

	@Test
	public void testMethodToString() {
		assertEquals("foo(String,Number,int)",
				IuExecutableKey.of("foo", String.class, Number.class, Integer.TYPE).toString());
	}

	@Test
	public void testTypesAreTypes() {
		var k1 = IuExecutableKey.of(null, Object.class);
		var k2 = IuExecutableKey.of(null, List.of(IuType.of(Object.class)));
		var kn = IuExecutableKey.of(null, getClass());
		assertEquals(k1, k2);
		assertEquals(k2, k1);
		assertNotEquals(k1, kn);
		assertNotEquals(kn, k1);
		assertTrue(new HashSet<>(List.of(k2, kn)).contains(k1));
		assertFalse(new HashSet<>(List.of(k1, k2)).contains(kn));
	}

	@Test
	public void testNameNoMatch() {
		var k1 = IuExecutableKey.of(null, Object.class);
		var k2 = IuExecutableKey.of("", Object.class);
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
		assertEquals(k1.hashCode(), k2.hashCode());
	}

	@Test
	public void testHashCollision() {
		assertEquals(IuExecutableKey.hashCode(null, Object.class),
				IuExecutableKey.hashCode("", List.of(IuType.of(Object.class))));
		assertEquals(IuExecutableKey.hashCode(null, List.of()), IuExecutableKey.hashCode(""));
		assertFalse(IuExecutableKey.of(null, Object.class).equals("", List.of(IuType.of(Object.class))));
		assertFalse(IuExecutableKey.of(null, List.of()).equals("", new Type[0]));
	}

	@Test
	public void testNotEquals() {
		var key = IuExecutableKey.of(null, Object.class);
		assertNotEquals(key, IuExecutableKey.of(null, Object.class, Object.class));
		assertNotEquals(key, IuExecutableKey.of(null, List.of(IuType.of(Object.class), IuType.of(Object.class))));
	}

	@Test
	public void testIterableEqualses() {
		var key = IuExecutableKey.of(null, Object.class);
		assertFalse(key.equals(null, List.of(IuType.of(Object.class), IuType.of(Object.class))));
		assertTrue(key.equals(null, List.of(IuType.of(Object.class))));
		assertFalse(key.equals(null, List.of(IuType.of(Number.class))));
		assertFalse(key.equals(null, List.of()));
	}

}
