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
package iu.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

@SuppressWarnings({ "javadoc", "rawtypes", "unused" })
public class GenericTypesTest {

	interface Source<X> {
	}

	interface Pair<A, B> {
	}

	interface StringSource extends Source<String> {
	}

	static class ViaInterface implements StringSource {
	}

	static abstract class Base<T> implements Source<T> {
	}

	static class Concrete extends Base<Integer> {
	}

	static class RawBase extends Base {
	}

	static class Outer<T> {
		class Inner<U> {
		}
	}

	static class OwnerBinding<T> implements Source<Outer<T>.Inner<String>> {
	}

	static class OwnerBound extends OwnerBinding<Integer> {
	}

	static class ArrayBase<T> implements Pair<T[], List<T>[]> {
	}

	static class ArrayArgs extends ArrayBase<Integer> {
	}

	static class WildcardBase<T> implements Pair<List<? extends T>, List<? super T>> {
	}

	static class WildcardArgs extends WildcardBase<Number> {
	}

	static class ObjectWildcard extends WildcardBase<Object> {
	}

	static class TwoArgs<K, V> implements Pair<K, V> {
	}

	static class BothBound extends TwoArgs<String, Integer> {
	}

	static class Unchanged implements Pair<List<String>[], List<?>> {
	}

	static class Nested<T> {
	}

	static class NestedSource<T> implements Source<Nested<T>> {
	}

	static class NestedBound extends NestedSource<String> {
	}

	static class Variables<T extends Number, C extends Number & Comparable<C>> {
		List<T> listOfT;
		T t;
		C c;
	}

	List<Integer> listOfInteger;
	List<Number> listOfNumber;
	ArrayList<Integer> arrayListOfInteger;
	ArrayList<String> arrayListOfString;
	List<Integer>[] arrayOfListOfInteger;
	ArrayList<Integer>[] arrayOfArrayListOfInteger;
	List<? extends Number> listOfExtendsNumber;
	List<? super Number> listOfSuperNumber;
	List<? super Integer> listOfSuperInteger;
	List<? extends Object> listOfAny;
	List<String>[] arrayOfListOfString;
	List<?> listOfWildcard;
	Map<String, Integer> mapOfStringInteger;
	HashMap<String, Integer> hashMapOfStringInteger;
	Outer<Integer>.Inner<String> inner;
	Nested<String> nestedOfString;
	Nested<Integer> nestedOfInteger;

	static Type field(String name) {
		try {
			return GenericTypesTest.class.getDeclaredField(name).getGenericType();
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException(e);
		}
	}

	static Type variablesField(String name) {
		try {
			return Variables.class.getDeclaredField(name).getGenericType();
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException(e);
		}
	}

	static Type argument(String fieldName) {
		return ((ParameterizedType) field(fieldName)).getActualTypeArguments()[0];
	}

	static void assertInterop(Type reflected, Type resolved) {
		assertEquals(reflected, resolved);
		assertEquals(resolved, reflected);
		assertEquals(reflected.hashCode(), resolved.hashCode());
		assertEquals(reflected.getTypeName(), resolved.getTypeName());
	}

	@Test
	public void testBox() {
		assertSame(Integer.class, GenericTypes.box(int.class));
		assertSame(Void.class, GenericTypes.box(void.class));
		assertSame(String.class, GenericTypes.box(String.class));
		assertSame(field("listOfInteger"), GenericTypes.box(field("listOfInteger")));
	}

	@Test
	public void testNotASubtype() {
		assertNull(GenericTypes.typeArguments(String.class, List.class));
	}

	@Test
	public void testFromParameterizedType() {
		assertArrayEquals(new Type[] { Integer.class },
				GenericTypes.typeArguments(field("arrayListOfInteger"), List.class));
		assertArrayEquals(new Type[] { String.class, Integer.class },
				GenericTypes.typeArguments(field("hashMapOfStringInteger"), Map.class));
	}

	@Test
	public void testThroughSubInterface() {
		assertArrayEquals(new Type[] { String.class }, GenericTypes.typeArguments(ViaInterface.class, Source.class));
	}

	@Test
	public void testThroughGenericSuperclass() {
		assertArrayEquals(new Type[] { Integer.class }, GenericTypes.typeArguments(Concrete.class, Source.class));
	}

	@Test
	public void testUnboundVariable() {
		final var raw = GenericTypes.typeArguments(Base.class, Source.class)[0];
		assertSame(Base.class.getTypeParameters()[0], raw);
		assertSame(Base.class.getTypeParameters()[0], GenericTypes.typeArguments(RawBase.class, Source.class)[0]);
	}

	@Test
	public void testSameClass() {
		assertArrayEquals(Source.class.getTypeParameters(), GenericTypes.typeArguments(Source.class, Source.class));
	}

	@Test
	public void testOwnerType() {
		final var resolved = GenericTypes.typeArguments(OwnerBound.class, Source.class)[0];
		assertInterop(field("inner"), resolved);
		assertEquals(field("inner").toString(), resolved.toString());
		final var parameterized = (ParameterizedType) resolved;
		assertEquals(Outer.Inner.class, parameterized.getRawType());
		assertInterop(((ParameterizedType) field("inner")).getOwnerType(), parameterized.getOwnerType());
		assertArrayEquals(new Type[] { String.class }, parameterized.getActualTypeArguments());

		assertArrayEquals(new Type[] { String.class }, GenericTypes.typeArguments(field("inner"), Outer.Inner.class));
	}

	@Test
	public void testNestedClassOwner() {
		final var resolved = GenericTypes.typeArguments(NestedBound.class, Source.class)[0];
		assertInterop(field("nestedOfString"), resolved);
	}

	@Test
	public void testArrays() {
		final var resolved = GenericTypes.typeArguments(ArrayArgs.class, Pair.class);
		assertSame(Integer[].class, resolved[0]);
		assertInterop(field("arrayOfListOfInteger"), resolved[1]);
		assertInterop(field("listOfInteger"), ((GenericArrayType) resolved[1]).getGenericComponentType());
		assertNotEquals(resolved[1], field("listOfInteger"));
		assertNotEquals(resolved[1], field("arrayOfListOfString"));
	}

	@Test
	public void testWildcards() {
		final var resolved = GenericTypes.typeArguments(WildcardArgs.class, Pair.class);
		assertInterop(field("listOfExtendsNumber"), resolved[0]);
		assertInterop(field("listOfSuperNumber"), resolved[1]);

		final var extendsNumber = (WildcardType) ((ParameterizedType) resolved[0]).getActualTypeArguments()[0];
		assertArrayEquals(new Type[] { Number.class }, extendsNumber.getUpperBounds());
		assertArrayEquals(new Type[0], extendsNumber.getLowerBounds());
		assertNotEquals(extendsNumber, Number.class);
		assertNotEquals(extendsNumber, argument("listOfSuperNumber"));
		assertNotEquals(extendsNumber, argument("listOfWildcard"));

		assertInterop(field("listOfAny"), GenericTypes.typeArguments(ObjectWildcard.class, Pair.class)[0]);
	}

	@Test
	public void testUnchanged() {
		final var resolved = GenericTypes.typeArguments(Unchanged.class, Pair.class);
		assertEquals(field("arrayOfListOfString"), resolved[0]);
		assertEquals(field("listOfWildcard"), resolved[1]);
	}

	@Test
	public void testSeveralBindings() {
		assertArrayEquals(new Type[] { String.class, Integer.class },
				GenericTypes.typeArguments(BothBound.class, Pair.class));
	}

	@Test
	public void testParameterizedTypeEquality() {
		final var resolved = GenericTypes.typeArguments(OwnerBound.class, Source.class)[0];
		assertEquals(resolved, resolved);
		assertNotEquals(resolved, String.class);
		// different owner
		assertNotEquals(resolved, field("nestedOfString"));

		final var nested = GenericTypes.typeArguments(NestedBound.class, Source.class)[0];
		// same owner, different raw type
		assertNotEquals(nested, ((ParameterizedType) field("inner")).getOwnerType());
		// same owner and raw type, different arguments
		assertNotEquals(nested, field("nestedOfInteger"));
	}

	@Test
	public void testAssignableClasses() {
		assertTrue(GenericTypes.isAssignable(String.class, String.class));
		assertTrue(GenericTypes.isAssignable(CharSequence.class, String.class));
		assertTrue(GenericTypes.isAssignable(Integer.class, int.class));
		assertTrue(GenericTypes.isAssignable(List.class, field("arrayListOfInteger")));
		assertFalse(GenericTypes.isAssignable(String.class, Integer.class));
	}

	@Test
	public void testAssignableParameterized() {
		assertTrue(GenericTypes.isAssignable(field("listOfInteger"), field("arrayListOfInteger")));
		assertFalse(GenericTypes.isAssignable(field("listOfInteger"), String.class));
		assertFalse(GenericTypes.isAssignable(field("listOfInteger"), field("listOfNumber")));
		assertTrue(GenericTypes.isAssignable(field("mapOfStringInteger"), field("hashMapOfStringInteger")));
		assertTrue(GenericTypes.isAssignable(field("listOfExtendsNumber"), field("listOfInteger")));
		assertFalse(GenericTypes.isAssignable(field("listOfExtendsNumber"), field("arrayListOfString")));
	}

	@Test
	public void testAssignableArrays() {
		assertTrue(GenericTypes.isAssignable(field("arrayOfListOfInteger"), field("arrayOfArrayListOfInteger")));
		assertFalse(GenericTypes.isAssignable(field("arrayOfListOfInteger"), List[].class));
		assertFalse(GenericTypes.isAssignable(field("arrayOfListOfInteger"), String.class));
		assertFalse(GenericTypes.isAssignable(field("arrayOfListOfInteger"), field("listOfInteger")));
	}

	@Test
	public void testAssignableVariablesAndWildcards() {
		final var t = variablesField("t");
		assertTrue(GenericTypes.isAssignable(t, Integer.class));
		assertFalse(GenericTypes.isAssignable(t, String.class));
		assertTrue(GenericTypes.isAssignable(argument("listOfExtendsNumber"), Integer.class));
		assertTrue(GenericTypes.isAssignable(Number.class, t));
		assertTrue(GenericTypes.isAssignable(Number.class, argument("listOfExtendsNumber")));

		final var c = variablesField("c");
		assertTrue(GenericTypes.isAssignable(c, Integer.class));
		assertFalse(GenericTypes.isAssignable(c, Number.class));
	}

	@Test
	public void testSuperWildcardArguments() {
		final var superInteger = field("listOfSuperInteger");
		assertTrue(GenericTypes.isAssignable(superInteger, field("listOfNumber")));
		assertFalse(GenericTypes.isAssignable(superInteger, field("arrayListOfString")));
		assertTrue(GenericTypes.isAssignable(superInteger, field("listOfSuperNumber")));
		assertFalse(GenericTypes.isAssignable(superInteger, field("listOfExtendsNumber")));
		assertFalse(GenericTypes.isAssignable(superInteger, variablesField("listOfT")));
	}

	@Test
	public void testAssignableUnboundArrays() {
		final var arrayOfListOfT = GenericTypes.typeArguments(ArrayBase.class, Pair.class)[1];
		assertInstanceOf(GenericArrayType.class, arrayOfListOfT);
		assertTrue(GenericTypes.isAssignable(arrayOfListOfT, field("arrayOfListOfInteger")));
		assertTrue(GenericTypes.isAssignable(arrayOfListOfT, List[].class));
		assertFalse(GenericTypes.isAssignable(field("arrayOfListOfInteger"), field("arrayOfListOfString")));
	}

	@Test
	public void testUnboundArgumentsMatchByBounds() {
		final var listOfT = variablesField("listOfT");
		assertTrue(GenericTypes.isAssignable(listOfT, field("listOfInteger")));
		assertFalse(GenericTypes.isAssignable(listOfT, field("arrayListOfString")));
		assertInstanceOf(TypeVariable.class, ((ParameterizedType) listOfT).getActualTypeArguments()[0]);
	}

}
