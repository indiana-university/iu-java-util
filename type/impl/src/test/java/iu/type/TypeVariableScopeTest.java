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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import edu.iu.type.IuType;
import edu.iu.type.testresources.TypeVariableScopeSupport.AcceptsList;
import edu.iu.type.testresources.TypeVariableScopeSupport.EntityDao;
import edu.iu.type.testresources.TypeVariableScopeSupport.IntegerShadow;
import edu.iu.type.testresources.TypeVariableScopeSupport.ListsIterable;
import edu.iu.type.testresources.TypeVariableScopeSupport.Pair;
import edu.iu.type.testresources.TypeVariableScopeSupport.ReturnsArrayList;
import edu.iu.type.testresources.TypeVariableScopeSupport.ReturnsCollection;
import edu.iu.type.testresources.TypeVariableScopeSupport.ReturnsList;
import edu.iu.type.testresources.TypeVariableScopeSupport.Shadow;
import edu.iu.type.testresources.TypeVariableScopeSupport.Swapper;
import edu.iu.type.testresources.inherit.DiamondLeaf;
import edu.iu.type.testresources.inherit.DiamondService;
import edu.iu.type.testresources.inherit.DiamondServiceReversed;
import edu.iu.type.testresources.inherit.DiamondValue;

/**
 * Verifies type variables resolve in the scope they came from when names
 * collide across scopes, and that resolution terminates.
 */
@SuppressWarnings("javadoc")
public class TypeVariableScopeTest extends IuTypeTestCase {

	private static <R> R complete(ThrowingSupplier<R> supplier) {
		return assertTimeoutPreemptively(Duration.ofSeconds(10L), supplier);
	}

	private static TypeVariable<?> variable(GenericDeclaration declaration, String name) {
		for (final var typeVariable : declaration.getTypeParameters())
			if (typeVariable.getName().equals(name))
				return typeVariable;
		throw new AssertionError(name);
	}

	/**
	 * Asserts a collection type resolves its element type through each scope of
	 * its hierarchy, i.e., Collection&lt;E&gt; and Iterable&lt;T&gt; share the same
	 * type argument as the element type of {@code type}.
	 */
	private static IuType<?, ?> assertCollectionOf(IuType<?, ?> type) {
		final var element = type.referTo(Collection.class).typeParameter("E");
		assertSame(element, type.referTo(Iterable.class).typeParameter("T"), type::toString);
		return element;
	}

	@Test
	public void testListReturn() throws Exception {
		final var t = variable(ReturnsList.class.getMethod("f"), "T");
		final var returnType = complete(() -> {
			IuType.of(ReturnsList.class).methods();
			return IuType.of(ReturnsList.class).method("f").returnType();
		});
		final var element = returnType.typeParameter("E");
		assertEquals(t, element.deref());
		assertSame(element, assertCollectionOf(returnType));
	}

	@Test
	public void testCollectionReturn() throws Exception {
		final var t = variable(ReturnsCollection.class.getMethod("f"), "T");
		final var returnType = complete(() -> IuType.of(ReturnsCollection.class).method("f").returnType());
		final var element = returnType.typeParameter("E");
		assertEquals(t, element.deref());
		assertSame(element, assertCollectionOf(returnType));
	}

	@Test
	public void testArrayListReturn() throws Exception {
		final var t = variable(ReturnsArrayList.class.getMethod("f"), "T");
		final var returnType = complete(() -> IuType.of(ReturnsArrayList.class).method("f").returnType());
		final var element = returnType.typeParameter("E");
		assertEquals(t, element.deref());
		assertSame(element, returnType.referTo(List.class).typeParameter("E"));
		assertSame(element, assertCollectionOf(returnType));
	}

	@Test
	public void testListParameter() throws Exception {
		final var t = variable(AcceptsList.class.getMethod("f", List.class), "T");
		final var paramType = complete(() -> IuType.of(AcceptsList.class).method("f", List.class).parameter(0).type());
		final var element = paramType.typeParameter("E");
		assertEquals(t, element.deref());
		assertSame(element, assertCollectionOf(paramType));
	}

	@Test
	public void testListFromIterable() throws Exception {
		final var method = ListsIterable.class.getMethod("list", Iterable.class);
		final var t = variable(method, "T");
		final var list = complete(() -> IuType.of(ListsIterable.class).method("list", Iterable.class));
		final var element = list.returnType().typeParameter("E");
		assertEquals(t, element.deref());
		assertSame(element, assertCollectionOf(list.returnType()));
		assertEquals(method.getGenericParameterTypes()[0], list.parameter(0).type().deref());
	}

	@Test
	public void testClassTypeParameterNamedT() throws Exception {
		final var t = variable(EntityDao.class, "T");
		final var type = complete(() -> {
			final var dao = IuType.of(EntityDao.class);
			dao.methods();
			return dao;
		});
		assertEquals(t, type.typeParameter("T").deref());

		// EntityDao's T and Iterable's T are distinct variables
		final var found = type.method("selectAndPopulate", List.class).parameter(0).type();
		final var foundElement = found.typeParameter("E");
		assertEquals(t, foundElement.deref());
		assertSame(foundElement, assertCollectionOf(found));

		final var selected = type.method("select", Object.class).returnType();
		final var selectedElement = selected.typeParameter("E");
		assertEquals(t, selectedElement.deref());
		assertSame(selectedElement, assertCollectionOf(selected));
	}

	/**
	 * Asserts that methods inherited through a diamond keep their declaring
	 * type's variables, whichever path is resolved first.
	 */
	private static void assertDiamond(Class<?> service) throws Exception {
		final var type = complete(() -> IuType.of(service));
		final var leafK = variable(DiamondLeaf.class, "K");
		final var leafV = variable(DiamondLeaf.class, "V");
		final var valueK = variable(DiamondValue.class, "K");
		final var valueV = variable(DiamondValue.class, "V");

		final var get = type.method("get", Object.class);
		assertEquals(leafK, get.parameter(0).type().deref(), service::toString);
		assertEquals(leafV, get.returnType().deref(), service::toString);
		assertEquals(leafK, type.method("post", Object.class).parameter(0).type().deref(), service::toString);

		final var put = type.method("put", Object.class, Object.class);
		assertEquals(valueK, put.parameter(0).type().deref(), service::toString);
		assertEquals(valueV, put.parameter(1).type().deref(), service::toString);
		assertEquals(valueV, put.returnType().deref(), service::toString);
	}

	@Test
	public void testDiamondKeepsDeclaringTypeVariables() throws Exception {
		assertDiamond(DiamondService.class);
	}

	@Test
	public void testReversedDiamondKeepsDeclaringTypeVariables() throws Exception {
		assertDiamond(DiamondServiceReversed.class);
	}

	@Test
	public void testSwappedTypeParameters() {
		final var swapped = complete(() -> IuType.of(Swapper.class).method("swap").returnType());
		assertEquals(Pair.class, swapped.erasedClass());
		assertEquals("B", ((TypeVariable<?>) swapped.typeParameter("A").deref()).getName());
		assertEquals("A", ((TypeVariable<?>) swapped.typeParameter("B").deref()).getName());
	}

	/**
	 * Asserts a type parameter resolves to the executable's own
	 * {@code <T extends CharSequence>}, not to the class's
	 * {@code <T extends Number>} or its argument.
	 */
	private static void assertOwnT(GenericDeclaration declaration, IuType<?, ?> typeParameter) {
		final var t = (TypeVariable<?>) typeParameter.deref();
		assertEquals(variable(declaration, "T"), t);
		assertArrayEquals(new Type[] { CharSequence.class }, t.getBounds());
		assertEquals(CharSequence.class, typeParameter.erasedClass());
	}

	@Test
	public void testMethodShadowsClassTypeParameter() throws Exception {
		final var shadow = complete(() -> IuType.of(Shadow.class).method("shadow", CharSequence.class));
		assertOwnT(Shadow.class.getMethod("shadow", CharSequence.class), shadow.typeParameter("T"));
		assertEquals(CharSequence.class, shadow.returnType().erasedClass());
		assertEquals(CharSequence.class, shadow.parameter(0).type().erasedClass());
	}

	@Test
	public void testInheritedMethodShadowsClassTypeArgument() throws Exception {
		final var type = complete(() -> IuType.of(IntegerShadow.class));
		assertEquals(Integer.class, type.referTo(Shadow.class).typeParameter("T").erasedClass());
		assertOwnT(Shadow.class.getMethod("shadow", CharSequence.class),
				type.method("shadow", CharSequence.class).typeParameter("T"));
	}

	@Test
	public void testConstructorShadowsClassTypeParameter() throws Exception {
		final var shadow = complete(() -> IuType.of(Shadow.class).constructor(CharSequence.class));
		assertOwnT(Shadow.class.getConstructor(CharSequence.class), shadow.typeParameter("T"));
		assertEquals(CharSequence.class, shadow.parameter(0).type().erasedClass());
	}

}
