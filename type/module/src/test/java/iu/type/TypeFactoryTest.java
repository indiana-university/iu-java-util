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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.constant.Constable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class TypeFactoryTest {

	private void assertErased(Class<?> erasedClass, IuType<?, ?> shouldBeErased) {
		assertSame(erasedClass, shouldBeErased.deref());
		assertEquals(IuReferenceKind.ERASURE, shouldBeErased.reference().kind());
		assertSame(shouldBeErased, ((IuType<?, ?>) shouldBeErased.reference().referrer()).erase());
		assertSame(shouldBeErased, ((IuType<?, ?>) shouldBeErased.reference().referent()));
	}

	@Test
	public void testSameSame() {
		assertSame(TypeFactory.resolveType(Object.class), TypeFactory.resolveType(Object.class));
	}

	@Test
	public void testErasesRawTypeFromParameterizedType() throws NoSuchMethodException {
		interface HasAMethodWithParameterizedReturnType {
			Optional<Object> methodWithParameterizedReturnType();
		}
		var type = HasAMethodWithParameterizedReturnType.class.getDeclaredMethod("methodWithParameterizedReturnType")
				.getGenericReturnType();
		var facade = TypeFactory.resolveType(type);
		assertErased(Optional.class, facade.erase());
	}

	@Test
	public void testErasesUpperBoundsFromBoundedWilcardType() throws NoSuchMethodException {
		interface HasAMethodWithBoundedWildCardType {
			Optional<? extends Number> methodWithBoundedWildcardType();
		}
		var type = ((ParameterizedType) HasAMethodWithBoundedWildCardType.class
				.getDeclaredMethod("methodWithBoundedWildcardType").getGenericReturnType()).getActualTypeArguments()[0];
		var facade = TypeFactory.resolveType(type);
		assertErased(Number.class, facade.erase());
	}

	@Test
	public void testErasesUpperBoundFromUnboundedWilcardType() throws NoSuchMethodException {
		interface HasAMethodWithWildCardType {
			Optional<?> methodWithWildcardType();
		}
		var type = ((ParameterizedType) HasAMethodWithWildCardType.class.getDeclaredMethod("methodWithWildcardType")
				.getGenericReturnType()).getActualTypeArguments()[0];
		var facade = TypeFactory.resolveType(type);
		assertErased(Object.class, facade.erase());
	}

	@Test
	public void testErasesBoundFromTypeVariable() throws NoSuchMethodException {
		interface HasATypeVariable<N extends Number> {
		}
		var type = HasATypeVariable.class.getTypeParameters()[0];
		var facade = TypeFactory.resolveType(type);
		assertErased(Number.class, facade.erase());
	}

	@Test
	public void testErasesGenericArrayTypeToArrayOfErasedComponents() throws NoSuchMethodException {
		interface HasMethodThatReturnsAGenericArray<N extends Number> {
			N[] methodThatReturnsAGenericArray();
		}
		var type = ((GenericArrayType) HasMethodThatReturnsAGenericArray.class
				.getDeclaredMethod("methodThatReturnsAGenericArray").getGenericReturnType());
		var facade = TypeFactory.resolveType(type);
		assertErased(Number[].class, facade.erase());
	}

	@Test
	public void testErasesGenericMultiDimensionalArrayTypeToMultiDimensionalArrayOfErasedComponents()
			throws NoSuchMethodException {
		interface HasMethodThatReturnsAMultiDimensionalGenericArray<N extends Number> {
			N[][] methodThatReturnsAMultiDimensionalGenericArray();
		}
		var type = ((GenericArrayType) HasMethodThatReturnsAMultiDimensionalGenericArray.class
				.getDeclaredMethod("methodThatReturnsAMultiDimensionalGenericArray").getGenericReturnType());
		var facade = TypeFactory.resolveType(type);
		assertErased(Number[][].class, facade.erase());
	}

	@Test
	public void testHierarchy() {
		interface AnInterface<T> {
		}
		abstract class AnAbstractClass<A extends AnAbstractClass<A>> implements AnInterface<Number> {
		}
		class AClass extends AnAbstractClass<AClass> implements Serializable {
			private static final long serialVersionUID = 1L;
		}
		var type = TypeFactory.resolveRawClass(AClass.class);
		var hierarchy = type.hierarchy().iterator();
		var h = hierarchy.next();
		assertSame(Serializable.class, h.erasedClass());
		assertSame(type, h.reference().referrer());
		assertTrue(hierarchy.hasNext());

		h = hierarchy.next();
		assertSame(AnAbstractClass.class, h.erasedClass());
		assertSame(type, h.reference().referrer());
		assertTrue(hierarchy.hasNext());

		var anAbstractClass = h;
		h = hierarchy.next();
		assertSame(AnInterface.class, h.erasedClass());
		assertSame(anAbstractClass, h.reference().referrer());
		assertTrue(hierarchy.hasNext());

		h = hierarchy.next();
		assertSame(Object.class, h.erasedClass());
		assertSame(anAbstractClass, h.reference().referrer());
		assertFalse(hierarchy.hasNext());
	}

	@Test
	public void testEnumHierarchy() {
		enum AnEnum {
			A, B, C;
		}

		var type = TypeFactory.resolveRawClass(AnEnum.class);
		var hierarchy = type.hierarchy().iterator();
		var h = hierarchy.next();
		assertSame(Enum.class, h.erasedClass());
		assertSame(type, h.reference().referrer());
		assertTrue(hierarchy.hasNext());

		final var hEnum = h;
		h = hierarchy.next();
		assertSame(Serializable.class, h.erasedClass());
		assertSame(hEnum, h.reference().referrer());
		assertTrue(hierarchy.hasNext());

		h = hierarchy.next();
		assertSame(Comparable.class, h.erasedClass());
		assertSame(hEnum, h.reference().referrer());
		assertTrue(hierarchy.hasNext());

		h = hierarchy.next();
		assertSame(Constable.class, h.erasedClass());
		assertSame(hEnum, h.reference().referrer());
		assertTrue(hierarchy.hasNext());

		h = hierarchy.next();
		assertSame(Object.class, h.erasedClass());
		assertSame(hEnum, h.reference().referrer());
		assertFalse(hierarchy.hasNext(), () -> hierarchy.next().toString());
	}

}
