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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TypeValueTest {

	private static class ApplicationType {
	}

	@SuppressWarnings("unused")
	private static class GenericTypes<T extends ApplicationType> {
		private T[] array;
		private GenericOwner<ApplicationType>.Member<Object> member;
		private List<ApplicationType> parameterized;
		private List<? extends ApplicationType> upperBounded;
		private List<? super ApplicationType> lowerBounded;
	}

	private static class GenericOwner<T> {
		private class Member<U> {
		}
	}

	private static class CountingTypeValue extends TypeValue<Integer> {
		private final AtomicInteger calls = new AtomicInteger();

		@Override
		protected Integer computeValue(Type type) {
			return calls.incrementAndGet();
		}
	}

	@Test
	public void testCachesReflectiveTypes() throws Exception {
		final var value = new CountingTypeValue();
		final var variable = GenericTypes.class.getTypeParameters()[0];
		final var parameterized = typeOf("parameterized");
		final var upperBounded = ((ParameterizedType) typeOf("upperBounded")).getActualTypeArguments()[0];
		final var lowerBounded = ((ParameterizedType) typeOf("lowerBounded")).getActualTypeArguments()[0];
		final var array = (GenericArrayType) typeOf("array");

		assertEquals(1, value.get(ApplicationType.class));
		assertEquals(2, value.get(parameterized));
		assertEquals(3, value.get(variable));
		assertEquals(4, value.get(upperBounded));
		assertEquals(5, value.get(lowerBounded));
		assertEquals(6, value.get(array));
		assertSame(value.get(variable), value.get(variable));
		assertEquals(6, value.calls.get());
	}

	@Test
	public void testRejectsNullAndUnsupportedTypes() {
		final var value = new CountingTypeValue();

		assertThrows(NullPointerException.class, () -> value.get(null));
		assertThrows(IllegalArgumentException.class, () -> value.get(new Type() {
		}));
	}

	@Test
	public void testHandlesNestedAndIncompleteParameterizedTypes() throws Exception {
		final var value = new CountingTypeValue();

		assertEquals(1, value.get(typeOf("member")));
		assertEquals(2, value.get(incompleteParameterizedType()));
	}

	private static Type typeOf(String name) throws NoSuchFieldException {
		final Field field = GenericTypes.class.getDeclaredField(name);
		return field.getGenericType();
	}

	private static ParameterizedType incompleteParameterizedType() {
		return new ParameterizedType() {
			@Override
			public Type[] getActualTypeArguments() {
				return new Type[] { null };
			}

			@Override
			public Type getRawType() {
				return List.class;
			}

			@Override
			public Type getOwnerType() {
				return ApplicationType.class;
			}
		};
	}
}
