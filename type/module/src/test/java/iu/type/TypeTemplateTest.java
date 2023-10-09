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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class TypeTemplateTest {

	private void assertRaw(Class<?> baseClass, TypeTemplate<?> baseTemplate) {
		assertNull(baseTemplate.reference());
		assertSame(baseClass, baseTemplate.deref());
		assertSame(baseTemplate, baseTemplate.erase());
		assertSame(baseClass, baseTemplate.erasedClass());
		assertSame(baseClass.getName(), baseTemplate.name());
		assertEquals("IuType[" + baseClass + ']', baseTemplate.toString());
	}

	private void assertGeneric(Type genericType, TypeTemplate<?> genericTemplate) {
		assertNull(genericTemplate.reference());
		assertSame(genericType, genericTemplate.deref());
		assertNotSame(genericTemplate, genericTemplate.erase());
		assertSame(genericTemplate.erasedClass().getName(), genericTemplate.name());
		assertEquals("IuType[" + genericType + ']', genericTemplate.toString());

		var erasedClass = genericTemplate.erasedClass();
		var erasedTemplate = genericTemplate.erase();
		assertEquals(IuReferenceKind.ERASURE, erasedTemplate.reference().kind());
		assertSame(erasedTemplate, erasedTemplate.reference().referent());
		assertSame(genericTemplate, erasedTemplate.reference().referrer());
		assertSame(erasedClass, erasedTemplate.deref());
		assertNotSame(erasedTemplate, erasedTemplate.erase());
		assertSame(erasedClass, erasedTemplate.erasedClass());
		assertSame(erasedClass.getName(), erasedTemplate.name());
		assertEquals("IuType[" + erasedClass + " ERASURE " + genericType + ']', erasedTemplate.toString());
	}

	@Test
	public void testRawBuilderIsValid() {
		var raw = TypeTemplate.builder(Object.class).build();
		assertRaw(Object.class, raw);

		var raw2 = TypeTemplate.builder(Object.class).build();
		assertTrue(Set.of(raw).contains(raw2));
		assertEquals(raw, raw2);
	}

	@Test
	public void testGenericBuilderIsValid() throws NoSuchFieldException {
		@SuppressWarnings("unused")
		class HasAFieldWithAParameterizedType {
			Optional<?> fieldWithAParameterizedType;
		}
		var type = HasAFieldWithAParameterizedType.class.getDeclaredField("fieldWithAParameterizedType")
				.getGenericType();
		assertGeneric(type, TypeTemplate.builder(type, TypeTemplate.builder(Optional.class).build()).build());
	}

	@Test
	public void testGenericBuilderAssertsNotClass() {
		assertThrows(AssertionError.class, () -> TypeTemplate.builder(Object.class, null));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGenericBuilderAssertsErasureIsClass() {
		var mockType = mock(Type.class);
		var mockTemplate = mock(TypeTemplate.class);
		when(mockTemplate.deref()).thenReturn(mockType);
		assertThrows(AssertionError.class, () -> TypeTemplate.builder(mockType, mockTemplate));
	}

	@Test
	public void testAssertsBuilderAcceptsHierarchyOnlyOnce() {
		assertThrows(AssertionError.class,
				() -> TypeTemplate.builder(Object.class).hierarchy(List.of()).hierarchy(List.of()));
	}

	@Test
	public void testEqualsTypeChecks() {
		var t1 = TypeTemplate.builder(Object.class).build();
		var t2 = mock(IuType.class);
		assertNotEquals(t1, t2);
	}

	@Test
	public void testSameRawAreEquals() {
		var t1 = TypeTemplate.builder(Object.class).build();
		var t2 = TypeTemplate.builder(Object.class).build();
		assertEquals(t1, t2);
		assertEquals(t2, t1);
	}

	@Test
	public void testDifferentRawNotEquals() {
		var t1 = TypeTemplate.builder(Object.class).build();
		var t2 = TypeTemplate.builder(Number.class).build();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

	@Test
	public void testSetsOfRaw() {
		Set<IuType<?>> set = new HashSet<>();
		assertTrue(set.add(TypeTemplate.builder(Object.class).build()));
		assertFalse(set.add(TypeTemplate.builder(Object.class).build()));
		assertTrue(set.add(TypeTemplate.builder(Number.class).build()));
		assertTrue(set.contains(TypeTemplate.builder(Object.class).build()));
		assertTrue(set.contains(TypeTemplate.builder(Number.class).build()));
	}

	@Test
	public void testSetsOfGeneric() {
		interface HasTypeParam<T> {
		}
		var e = TypeTemplate.builder(Object.class).build();
		Set<IuType<?>> set = new HashSet<>();
		assertTrue(set.add(TypeTemplate.builder(HasTypeParam.class.getTypeParameters()[0], e).build()));
		assertTrue(set.add(TypeTemplate.builder(HasTypeParam.class.getTypeParameters()[0], e).build()));
	}

	@Test
	public void testGenericNotEqualsRaw() {
		interface HasTypeParam<T> {
		}
		var t1 = TypeTemplate.builder(Object.class).build();
		var t2 = TypeTemplate.builder(HasTypeParam.class.getTypeParameters()[0], t1).build();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

	@Test
	public void testErasureNotEqualsRaw() {
		interface HasTypeParam<T> {
		}
		var t1 = TypeTemplate.builder(Object.class).build();
		var t2 = TypeTemplate.builder(HasTypeParam.class.getTypeParameters()[0], t1).build().erase();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

	@Test
	public void testSameGenericsIsEquals() {
		interface HasTypeParam<T> {
		}
		var t1 = TypeTemplate
				.builder(HasTypeParam.class.getTypeParameters()[0], TypeTemplate.builder(Object.class).build()).build()
				.erase();
		assertEquals(t1, t1);
	}

	@Test
	public void testEquivalentGenericsAreNotEquals() {
		interface HasTypeParam<T> {
		}
		var t1 = TypeTemplate
				.builder(HasTypeParam.class.getTypeParameters()[0], TypeTemplate.builder(Object.class).build()).build()
				.erase();
		var t2 = TypeTemplate
				.builder(HasTypeParam.class.getTypeParameters()[0], TypeTemplate.builder(Object.class).build()).build()
				.erase();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

	@Test
	public void testTypeParamsWithDifferentBoundsNotEqua() {
		interface HasTypeParam<T> {
		}
		interface HasBoundedTypeParam<T extends Number> {
		}
		var t1 = TypeTemplate
				.builder(HasTypeParam.class.getTypeParameters()[0], TypeTemplate.builder(Object.class).build()).build()
				.erase();
		var t2 = TypeTemplate
				.builder(HasBoundedTypeParam.class.getTypeParameters()[0], TypeTemplate.builder(Number.class).build())
				.build().erase();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

}
