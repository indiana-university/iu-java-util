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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.testresources.HasStringListParam;

@SuppressWarnings("javadoc")
public class TypeTemplateTest extends IuTypeTestCase {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.type.ParameterizedElement", Level.FINEST, "replaced type argument .*");
	}

	private void assertRaw(Class<?> baseClass, TypeTemplate<?, ?> baseTemplate) {
		assertNull(baseTemplate.reference());
		assertSame(baseClass, baseTemplate.deref());
		assertSame(baseTemplate, baseTemplate.erase());
		assertSame(baseClass, baseTemplate.erasedClass());
		assertSame(baseClass.getName(), baseTemplate.name());
		assertEquals("IuType[" + TypeUtils.printType(baseClass) + ']', baseTemplate.toString());
	}

	private void assertGeneric(Type genericType, TypeTemplate<?, ?> genericTemplate) {
		assertNull(genericTemplate.reference());
		assertSame(genericType, genericTemplate.deref());
		assertNotSame(genericTemplate, genericTemplate.erase());
		assertSame(genericTemplate.erasedClass().getName(), genericTemplate.name());
		assertEquals("IuType[" + TypeUtils.printType(genericType) + ']', genericTemplate.toString());

		var erasedClass = genericTemplate.erasedClass();
		var erasedTemplate = genericTemplate.erase();
		assertEquals(IuReferenceKind.ERASURE, erasedTemplate.reference().kind());
		assertSame(erasedTemplate, erasedTemplate.reference().referent());
		assertSame(genericTemplate, erasedTemplate.reference().referrer());
		assertSame(erasedClass, erasedTemplate.deref());
		assertSame(erasedClass, erasedTemplate.erasedClass());
		assertSame(erasedClass.getName(), erasedTemplate.name());
		assertEquals(
				"IuType[" + TypeUtils.printType(erasedClass) + " ERASURE " + TypeUtils.printType(genericType) + "]",
				erasedTemplate.toString());
	}

	@Test
	public void testRawBuilderIsValid() {
		var raw = new TypeTemplate<>(Object.class, a -> {
		});
		raw.sealHierarchy(List.of());
		assertRaw(Object.class, raw);
	}

	@Test
	public void testPrimitiveBuilderIsValid() {
		var raw = new TypeTemplate<>(int.class, a -> {
		});
		raw.sealHierarchy(List.of());
		assertRaw(int.class, raw);
	}

	@Test
	public void testGenericBuilderIsValid() throws NoSuchFieldException {
		@SuppressWarnings("unused")
		class HasAFieldWithAParameterizedType {
			Optional<?> fieldWithAParameterizedType;
		}
		var type = HasAFieldWithAParameterizedType.class.getDeclaredField("fieldWithAParameterizedType")
				.getGenericType();
		var template = new TypeTemplate<>(Object.class, a -> {
		});
		template.sealHierarchy(List.of());
		var o = new TypeTemplate<>(Optional.class, a -> {
		});
		o.sealHierarchy(List.of(template));
		assertGeneric(type, new TypeTemplate<>(a -> {
		}, type, o));
	}

	@Test
	public void testGenericBuilderAssertsNotClass() {
		var raw = new TypeTemplate<>(Object.class, a -> {
		});
		raw.sealHierarchy(List.of());
		assertThrows(AssertionError.class, () -> new TypeTemplate<>(a -> {
		}, Object.class, raw));
	}

	@Test
	public void testGenericBuilderAssertsErasureIsClass() {
		var mockType = mock(WildcardType.class);
		when(mockType.getUpperBounds()).thenReturn(new Class<?>[] { Object.class });
		assertThrows(AssertionError.class, () -> new TypeTemplate<>(a -> {
		}, mockType, TypeFactory.resolveRawClass(Number.class)));
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void testTypeOfIterable() {
		final var type = (TypeFacade) TypeFactory.resolveType(HasStringListParam.class).field("stringList").type();
		assertSame(String.class, type.referTo(Iterable.class).typeParameter("T").erasedClass());
		assertSame(String.class, type.template.referTo(Iterable.class).typeParameter("T").erasedClass());
	}

	@Test
	public void testSealAssertsMatchingTypeArgLength() {
		var type = mock(ParameterizedType.class);
		when(type.getRawType()).thenReturn(Iterable.class);
		when(type.getActualTypeArguments()).thenReturn(new Type[0]);
		assertThrows(AssertionError.class, () -> new TypeTemplate<>(t -> {
		}, type, TypeFactory.resolveRawClass(Iterable.class)));
	}

	@Test
	public void testHierarchyRequiresSealed() {
		var template = new TypeTemplate<>(Object.class, a -> {
		});
		assertEquals("hierarchy not sealed",
				assertThrows(IllegalStateException.class, template::hierarchy).getMessage());
	}

	@Test
	public void testFieldsRequiresSealed() {
		var template = new TypeTemplate<>(Object.class, a -> {
		});
		assertEquals("fields not sealed", assertThrows(IllegalStateException.class, template::fields).getMessage());
	}

	@Test
	public void testMethodsRequiresSealed() {
		var template = new TypeTemplate<>(Object.class, a -> {
		});
		assertEquals("methods not sealed", assertThrows(IllegalStateException.class, template::methods).getMessage());
	}

	@Test
	public void testPropertiesRequiresSealed() {
		var template = new TypeTemplate<>(Object.class, a -> {
		});
		assertEquals("properties not sealed",
				assertThrows(IllegalStateException.class, template::properties).getMessage());
	}

	@Test
	public void testCheckSealed() {
		var template = new TypeTemplate<>(Object.class, a -> {
		});
		assertEquals("not sealed", assertThrows(IllegalStateException.class, template::checkSealed).getMessage());
	}

	@Test
	public void testCantSealDirectly() {
		var template = new TypeTemplate<>(Object.class, a -> {
		});
		assertEquals("use sealHierarchy() only with TypeTemplate", assertThrows(UnsupportedOperationException.class, template::seal).getMessage());
	}

}
