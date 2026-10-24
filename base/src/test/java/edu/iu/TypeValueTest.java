/*
 * Copyright © 2026 Indiana University
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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TypeValueTest {

	private static class ApplicationType {
	}

	private Class<?> redefine(Class<?> type) throws Exception {
		final byte[] resource;
		try (final var in = type.getClassLoader().getResourceAsStream(type.getName().replace(".", "/") + ".class")) {
			resource = IuStream.read(in);
		}
		final var loader = new ClassLoader(type.getClassLoader()) {
			Class<?> redefined = defineClass(type.getName(), resource, 0, resource.length);
		};
		return loader.redefined;
	}

	@Test
	public void testApplicationTypeIsMoreSpecificThanString() {
		assertEquals(ApplicationType.class, TypeValue.moreSpecific(ApplicationType.class, String.class));
		assertEquals(ApplicationType.class, TypeValue.moreSpecific(String.class, ApplicationType.class));
	}

	@Test
	public void testApplicationTypeIsMoreSpecificIfRedefined() throws Throwable {
		final var redefined = redefine(ApplicationType.class);
		assertEquals(redefined, TypeValue.moreSpecific(ApplicationType.class, redefined));
		assertEquals(redefined, TypeValue.moreSpecific(redefined, ApplicationType.class));
	}

	@Test
	public void testClassIsMostSpecific() {
		assertEquals(ApplicationType.class, TypeValue.mostSpecific(ApplicationType.class));
	}

	@Test
	public void testRawTypeIsMostSpecific() {
		final var type = mock(ParameterizedType.class);
		when(type.getRawType()).thenReturn(ApplicationType.class);
		when(type.getActualTypeArguments()).thenReturn(new Type[] { String.class });
		assertEquals(ApplicationType.class, TypeValue.mostSpecific(type));
	}

	@Test
	public void testOwnerTypeIsMostSpecific() {
		final var type = mock(ParameterizedType.class);
		when(type.getOwnerType()).thenReturn(ApplicationType.class);
		when(type.getActualTypeArguments()).thenReturn(new Type[] { String.class });
		assertEquals(ApplicationType.class, TypeValue.mostSpecific(type));
	}

	@Test
	public void testTypeArgIsMostSpecific() {
		final var type = mock(ParameterizedType.class);
		when(type.getRawType()).thenReturn(Iterable.class);
		when(type.getActualTypeArguments()).thenReturn(new Type[] { ApplicationType.class });
		assertEquals(ApplicationType.class, TypeValue.mostSpecific(type));
	}

	@Test
	public void testTypeValueBoundsAreMostSpecific() {
		final var type = mock(TypeVariable.class);
		when(type.getBounds()).thenReturn(new Type[] { ApplicationType.class });
		assertEquals(ApplicationType.class, TypeValue.mostSpecific(type));
	}

	@Test
	public void testWildcardUpperBoundsAreMostSpecific() {
		final var type = mock(WildcardType.class);
		when(type.getLowerBounds()).thenReturn(new Type[] { String.class });
		when(type.getUpperBounds()).thenReturn(new Type[] { ApplicationType.class });
		assertEquals(ApplicationType.class, TypeValue.mostSpecific(type));
	}

	@Test
	public void testWildcardLowerBoundsAreMostSpecific() {
		final var type = mock(WildcardType.class);
		when(type.getUpperBounds()).thenReturn(new Type[] { String.class });
		when(type.getLowerBounds()).thenReturn(new Type[] { ApplicationType.class });
		assertEquals(ApplicationType.class, TypeValue.mostSpecific(type));
	}

	@Test
	public void testGenericArrayComponentIsMostSpecific() {
		final var type = mock(GenericArrayType.class);
		when(type.getGenericComponentType()).thenReturn(ApplicationType.class);
		assertEquals(ApplicationType.class, TypeValue.mostSpecific(type));
	}

	@Test
	public void testInvalidTypeThrows() {
		final var type = mock(Type.class);
		assertThrows(IllegalArgumentException.class, () -> TypeValue.mostSpecific(type));
	}

	@Test
	public void testTypeValue() {
		final var typeValue = spy(new TypeValue<String>() {
			@Override
			protected String computeValue(Type type) {
				return type.toString();
			}
		});
		final var type = mock(TypeVariable.class);
		when(type.getBounds()).thenReturn(new Type[] { ApplicationType.class });
		assertEquals(type.toString(), typeValue.get(type));
		assertEquals(typeValue.get(type), typeValue.get(type));
		verify(typeValue).computeValue(type);
	}

}
