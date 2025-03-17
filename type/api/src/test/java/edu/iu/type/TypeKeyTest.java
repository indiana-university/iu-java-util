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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TypeKeyTest {

	@Test
	public void testReferToSame() {
		assertSame(Object.class, IuTypeKey.referTo(Object.class, Object.class));
	}

	@Test
	public void testReferToSimpleObject() {
		assertSame(Object.class, IuTypeKey.referTo(getClass(), Object.class));
	}

	@Test
	public void testReferToObject() {
		assertSame(Object.class, IuTypeKey.referTo(String.class, Object.class));
	}

	@Test
	public void testReferToSecondInterface() {
		assertSame(String.class,
				((ParameterizedType) IuTypeKey.referTo(String.class, Comparable.class)).getActualTypeArguments()[0]);
	}

	@Test
	public void testReferToInterfaceImplementedBySuper() {
		assertSame(Serializable.class, IuTypeKey.referTo(Integer.class, Serializable.class));
	}

	@Test
	public void testReferToMissingType() {
		assertEquals("String is not assignable from Integer",
				assertThrows(IllegalArgumentException.class, () -> IuTypeKey.referTo(Integer.class, String.class))
						.getMessage());
	}

	@Test
	public void testToStringClass() {
		assertEquals("CLASS String", IuTypeKey.of(String.class).toString());
	}

	@Test
	public void testParameterizedToString() {
		assertEquals("PARAMETERIZED [CLASS Comparable, CLASS String]",
				IuTypeKey.of(IuTypeKey.referTo(String.class, Comparable.class)).toString());
	}

	@Test
	public void testUnsupportedType() {
		final var type = mock(Type.class);
		assertEquals("Not supported in this version: " + type.getClass().getSimpleName(),
				assertThrows(UnsupportedOperationException.class, () -> IuTypeKey.of(type)).getMessage());
	}

	@Test
	public void testTypeCheck() {
		final var k = IuTypeKey.of(Object.class);
		assertNotEquals(k, new Object());
	}

	@Test
	public void testSameAreEqual() {
		final var k1 = IuTypeKey.of(Object.class);
		final var k2 = IuTypeKey.of(Object.class);
		assertSame(k1, k2);
		assertEquals(k1, k2);
		assertEquals(k2, k1);
	}

	@Test
	public void testNotSameAreNotEqual() {
		final var k1 = IuTypeKey.of(Object.class);
		final var k2 = IuTypeKey.of(getClass());
		assertNotSame(k1, k2);
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
		assertNotEquals(k1.hashCode(), k2.hashCode());
	}

	@Test
	public void testDifferentKindsAreNotEqual() {
		final var k1 = IuTypeKey.of(IuTypeKey.referTo(String.class, Comparable.class));
		final var k2 = IuTypeKey.of(Comparable.class);
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

	@Test
	public void testRawTypeMatches() {
		final var k1 = IuTypeKey.of(IuTypeKey.referTo(String.class, Comparable.class));
		final var k2 = IuTypeKey.of(IuTypeKey.referTo(Integer.class, Comparable.class));
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

	@Test
	public void testDifferentTypeArgs() {
		final var k1 = IuTypeKey.of(IuTypeKey.referTo(String.class, Comparable.class));
		final var k2 = IuTypeKey.of(IuTypeKey.referTo(Integer.class, Comparable.class));
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

	@Test
	public void testDifferentRawTypes() throws Exception {
		@SuppressWarnings("unused")
		class HasParameterizedTypes {
			Iterable<String> stringIter;
			List<String> stringList;
		}
		final var k1 = IuTypeKey.of(HasParameterizedTypes.class.getDeclaredField("stringIter").getGenericType());
		final var k2 = IuTypeKey.of(HasParameterizedTypes.class.getDeclaredField("stringList").getGenericType());
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

	@Test
	public void testTypeVariableNamesMismatch() throws Exception {
		final var k1 = IuTypeKey.of(Iterable.class.getTypeParameters()[0]);
		final var k2 = IuTypeKey.of(List.class.getTypeParameters()[0]);
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

	@Test
	public void testTypeVariablesMatch() throws Exception {
		final var k1 = IuTypeKey.of(Collection.class.getTypeParameters()[0]);
		final var k2 = IuTypeKey.of(List.class.getTypeParameters()[0]);
		assertEquals(k1, k2);
		assertEquals(k2, k1);
	}

	@Test
	public void testWildcardsMatch() throws Exception {
		@SuppressWarnings("unused")
		class HasWildcards {
			Map<? extends Number, ? super Number> map;
		}
		final var mapType = (ParameterizedType) HasWildcards.class.getDeclaredField("map").getGenericType();
		final var k1 = IuTypeKey.of(mapType.getActualTypeArguments()[0]);
		final var k2 = IuTypeKey.of(mapType.getActualTypeArguments()[1]);
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

	@Test
	public void testArraysMatch() throws Exception {
		@SuppressWarnings("unused")
		class HasArrays<N extends Number> {
			N[] array1;
			N[] array2;
		}
		final var k1 = IuTypeKey.of(HasArrays.class.getDeclaredField("array1").getGenericType());
		final var k2 = IuTypeKey.of(HasArrays.class.getDeclaredField("array2").getGenericType());
		assertEquals(k1, k2);
		assertEquals(k2, k1);
	}

	@Test
	public void testPrintsOk() throws Exception {
		var e = IuTypeKey.of(IuTypeKey.referTo(Enum.class, Comparable.class));
		assertEquals("PARAMETERIZED [CLASS Comparable, VARIABLE E [PARAMETERIZED [CLASS Enum, VARIABLE E]]]", e.toString());
	}
}
