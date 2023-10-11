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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.beans.Transient;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class TypeApiTest {

	@BeforeAll
	public static void setup() {
		IuType.class.getModule().addOpens(IuType.class.getPackageName(), IuTest.class.getModule());
	}

	@Test
	public void testReferenceKind() {
		assertSame(IuType.class, IuReferenceKind.ERASURE.referrerType());
		assertFalse(IuReferenceKind.ERASURE.named());
		assertFalse(IuReferenceKind.ERASURE.indexed());
	}

	@Test
	public void testDefaultInterceptor() {
		@DefaultInterceptor
		class IsADefaultInterceptor {
		}
		assertEquals(DefaultInterceptor.Scope.MODULE,
				IsADefaultInterceptor.class.getAnnotation(DefaultInterceptor.class).scope());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testAnnotations() {
		@DefaultInterceptor
		class IsAnnotated {
		}
		var annotation = IsAnnotated.class.getAnnotation(DefaultInterceptor.class);
		var annotatedElement = IuTest.mockWithDefaults(IuAnnotatedElement.class);
		when(annotatedElement.annotations()).thenReturn((Map) Map.of(DefaultInterceptor.class, annotation));
		assertTrue(annotatedElement.hasAnnotation(DefaultInterceptor.class));
		assertSame(annotation, annotatedElement.annotation(DefaultInterceptor.class));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testParameterized() {
		var parameterizedElement = IuTest.mockWithDefaults(IuParameterizedElement.class);
		var type = IuTest.mockWithDefaults(IuType.class);
		when(parameterizedElement.typeParameters()).thenReturn((Map) Map.of("foo", type));
		assertSame(type, parameterizedElement.typeParameter("foo"));
	}

	@Test
	public void testExecutable() {
		var executable = IuTest.mockWithDefaults(IuExecutable.class);
		var param = IuTest.mockWithDefaults(IuParameter.class);
		when(executable.parameters()).thenReturn(List.of(param));
		assertSame(param, executable.parameter(0));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testPermitted() {
		var element = IuTest.mockWithDefaults(IuAnnotatedElement.class);
		when(element.permitted(any())).then(a -> ((Predicate<String>) a.getArguments()[0]).test(""));
		assertFalse(element.permitted());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testReadOnlyProperty() {
		var property = IuTest.mockWithDefaults(IuProperty.class);

		var type = IuTest.mockWithDefaults(IuType.class);
		when(property.declaringType()).thenReturn(type);

		var method = IuTest.mockWithDefaults(IuMethod.class);
		when(property.read()).thenReturn(method);

		assertTrue(property.canRead());
		assertFalse(property.canWrite());
		assertFalse(property.printSafe());
		assertFalse(property.serializable());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testWriteOnlyProperty() {
		var property = IuTest.mockWithDefaults(IuProperty.class);

		var type = IuTest.mockWithDefaults(IuType.class);
		when(property.declaringType()).thenReturn(type);

		var method = IuTest.mockWithDefaults(IuMethod.class);
		when(property.write()).thenReturn(method);

		assertFalse(property.canRead());
		assertTrue(property.canWrite());
		assertFalse(property.printSafe());
		assertFalse(property.serializable());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testTransientProperty() {
		var property = IuTest.mockWithDefaults(IuProperty.class);

		var type = IuTest.mockWithDefaults(IuType.class);
		when(property.declaringType()).thenReturn(type);

		var read = IuTest.mockWithDefaults(IuMethod.class);
		var transientAnnotation = IuTest.mockWithDefaults(Transient.class);
		when(read.annotations()).thenReturn((Map) Map.of(Transient.class, transientAnnotation));
		when(property.read()).thenReturn(read);

		var write = IuTest.mockWithDefaults(IuMethod.class);
		when(property.write()).thenReturn(write);

		assertTrue(property.canRead());
		assertTrue(property.canWrite());
		assertFalse(property.printSafe());
		assertFalse(property.serializable());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testReadWritePropertyPermittedByMethodAnnotation() {
		var property = IuTest.mockWithDefaults(IuProperty.class);

		var type = IuTest.mockWithDefaults(IuType.class);
		when(property.declaringType()).thenReturn(type);

		var read = IuTest.mockWithDefaults(IuMethod.class);
		when(read.permitted()).thenReturn(true);
		when(property.read()).thenReturn(read);

		var write = IuTest.mockWithDefaults(IuMethod.class);
		when(property.write()).thenReturn(write);

		assertTrue(property.canRead());
		assertTrue(property.canWrite());
		assertTrue(property.printSafe());
		assertTrue(property.serializable());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testType() {
		var type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(Object.class);
		assertSame(type, type.sub(Object.class));
		assertSame(Object.class, type.autoboxClass());
		assertNull(type.autoboxDefault());

		type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(boolean.class);
		assertSame(Boolean.class, type.autoboxClass());
		assertSame(Boolean.FALSE, type.autoboxDefault());

		type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(char.class);
		assertSame(Character.class, type.autoboxClass());
		assertSame('\0', type.autoboxDefault());

		type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(byte.class);
		assertSame(Byte.class, type.autoboxClass());
		assertSame((byte) 0, type.autoboxDefault());

		type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(short.class);
		assertSame(Short.class, type.autoboxClass());
		assertSame((short) 0, type.autoboxDefault());

		type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(int.class);
		assertSame(Integer.class, type.autoboxClass());
		assertSame((int) 0, type.autoboxDefault());

		type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(long.class);
		assertSame(Long.class, type.autoboxClass());
		assertSame(0L, type.autoboxDefault());

		type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(float.class);
		assertSame(Float.class, type.autoboxClass());
		assertEquals(0.0f, type.autoboxDefault());

		type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(double.class);
		assertSame(Double.class, type.autoboxClass());
		assertEquals(0.0, type.autoboxDefault());

		type = IuTest.mockWithDefaults(IuType.class);
		when(type.erase()).thenReturn(type);
		when(type.deref()).thenReturn(void.class);
		assertSame(Void.class, type.autoboxClass());
		assertNull(type.autoboxDefault());
	}

}
