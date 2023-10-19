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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.beans.Transient;
import java.lang.annotation.Documented;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
@ExtendWith(IuTypeOfMockSupport.class)
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
		when(annotatedElement.annotations()).thenReturn((List) List.of(annotation));
		assertTrue(annotatedElement.hasAnnotation(DefaultInterceptor.class));
		assertFalse(annotatedElement.hasAnnotation(Documented.class));
		assertSame(annotation, annotatedElement.annotation(DefaultInterceptor.class));
		assertNull(annotatedElement.annotation(Documented.class));
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
		when(read.annotations()).thenReturn((List) List.of(transientAnnotation));
		when(property.read()).thenReturn(read);

		var write = IuTest.mockWithDefaults(IuMethod.class);
		when(property.write()).thenReturn(write);

		assertTrue(property.canRead());
		assertTrue(property.canWrite());
		assertFalse(property.printSafe());
		assertFalse(property.serializable());
	}

	@Test
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

	@Test
	@SuppressWarnings("unchecked")
	public void testConstructors() {
		var con1 = mock(IuConstructor.class);
		when(con1.getKey()).thenReturn(IuExecutableKey.of(null));
		var con2 = mock(IuConstructor.class);
		var key2 = IuExecutableKey.of(null, Object.class);
		when(con2.getKey()).thenReturn(key2);
		var type = IuTest.mockWithDefaults(IuType.class);
		when(type.constructors()).thenReturn(List.of(con1, con2));
		assertSame(con1, type.constructor());
		assertSame(con2, type.constructor(Object.class));
		assertSame(con1, type.constructor(List.of()));
		assertSame(con2, type.constructor(List.of(IuType.of(Object.class))));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testConstructorHashCollisions() {
		var key1 = IuExecutableKey.of("", Object.class);
		var key2 = IuExecutableKey.of(null, Object.class);
		assertEquals(key1.hashCode(), key2.hashCode());

		var con1 = mock(IuConstructor.class);
		when(con1.getKey()).thenReturn(key1);
		var con2 = mock(IuConstructor.class);
		when(con2.getKey()).thenReturn(key2);
		var type = IuTest.mockWithDefaults(IuType.class);
		when(type.constructors()).thenReturn(List.of(con1, con2));
		assertEquals(type + " missing constructor <init>()",
				assertThrows(IllegalArgumentException.class, () -> type.constructor()).getMessage());
		assertSame(con2, type.constructor(Object.class));
		assertEquals(type + " missing constructor <init>()",
				assertThrows(IllegalArgumentException.class, () -> type.constructor(List.of())).getMessage());
		assertSame(con2, type.constructor(List.of(IuType.of(Object.class))));
	}

	@Test
	public void testFields() {
		var f1 = mock(IuField.class);
		when(f1.name()).thenReturn("a");
		var f2 = mock(IuField.class);
		when(f2.name()).thenReturn("b");
		var f3 = mock(IuField.class);
		when(f3.name()).thenReturn("b");
		var type = IuTest.mockWithDefaults(IuType.class);
		when(type.fields()).thenReturn(List.of(f1, f2, f3));
		assertSame(f1, type.field("a"));
		assertSame(f2, type.field("b"));
		assertEquals(type + " missing field c",
				assertThrows(IllegalArgumentException.class, () -> type.field("c")).getMessage());
	}

	@Test
	public void testProperties() {
		var f1 = mock(IuProperty.class);
		when(f1.name()).thenReturn("a");
		var f2 = mock(IuProperty.class);
		when(f2.name()).thenReturn("b");
		var f3 = mock(IuProperty.class);
		when(f3.name()).thenReturn("b");
		var type = IuTest.mockWithDefaults(IuType.class);
		when(type.properties()).thenReturn(List.of(f1, f2, f3));
		assertSame(f1, type.property("a"));
		assertSame(f2, type.property("b"));
		assertEquals(type + " missing property c",
				assertThrows(IllegalArgumentException.class, () -> type.property("c")).getMessage());
	}

	@Test
	public void testPropertiesArePermitted() {
		var arm = mock(IuMethod.class);
		when(arm.permitted(any())).thenReturn(true);
		var drm = mock(IuMethod.class);

		var awm = mock(IuMethod.class);
		when(awm.permitted(any())).thenReturn(true);
		var dwm = mock(IuMethod.class);

		var p = IuTest.mockWithDefaults(IuProperty.class);
		assertFalse(p.permitted());

		p = IuTest.mockWithDefaults(IuProperty.class);
		when(p.read()).thenReturn(arm);
		assertTrue(p.permitted());

		p = IuTest.mockWithDefaults(IuProperty.class);
		when(p.write()).thenReturn(awm);
		assertTrue(p.permitted());

		p = IuTest.mockWithDefaults(IuProperty.class);
		when(p.read()).thenReturn(drm);
		assertFalse(p.permitted());

		p = IuTest.mockWithDefaults(IuProperty.class);
		when(p.write()).thenReturn(dwm);
		assertFalse(p.permitted());

		p = IuTest.mockWithDefaults(IuProperty.class);
		when(p.read()).thenReturn(arm);
		when(p.write()).thenReturn(awm);
		assertTrue(p.permitted());

		p = IuTest.mockWithDefaults(IuProperty.class);
		when(p.read()).thenReturn(drm);
		when(p.write()).thenReturn(awm);
		assertFalse(p.permitted());

		p = IuTest.mockWithDefaults(IuProperty.class);
		when(p.read()).thenReturn(drm);
		when(p.write()).thenReturn(dwm);
		assertFalse(p.permitted());

		p = IuTest.mockWithDefaults(IuProperty.class);
		when(p.read()).thenReturn(drm);
		when(p.write()).thenReturn(dwm);
		assertFalse(p.permitted());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testMethods() {
		var m1 = mock(IuMethod.class);
		when(m1.getKey()).thenReturn(IuExecutableKey.of(null));
		var m2 = mock(IuMethod.class);
		when(m2.getKey()).thenReturn(IuExecutableKey.of(""));
		var m3 = mock(IuMethod.class);
		var k3 = IuExecutableKey.of("", Object.class);
		when(m3.getKey()).thenReturn(k3);
		var type = IuTest.mockWithDefaults(IuType.class);
		when(type.methods()).thenReturn(List.of(m1, m2, m3));
		assertSame(m2, type.method(""));
		assertSame(m3, type.method("", Object.class));
		assertSame(m2, type.method("", List.of()));
		assertSame(m3, type.method("", List.of(IuType.of(Object.class))));
		assertEquals(type + " missing method c(); [" + m1 + ", " + m2 + ", " + m3 + "]",
				assertThrows(IllegalArgumentException.class, () -> type.method("c")).getMessage());
		assertEquals(type + " missing method c(Object); [" + m1 + ", " + m2 + ", " + m3 + "]",
				assertThrows(IllegalArgumentException.class, () -> type.method("c", List.of(IuType.of(Object.class))))
						.getMessage());
	}

}
