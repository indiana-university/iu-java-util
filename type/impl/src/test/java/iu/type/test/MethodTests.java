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
package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;
import edu.iu.type.IuType;
import edu.iu.type.testresources.EnclosedClassesSupport;
import edu.iu.type.testresources.HasAroundInvokeMethod;
import edu.iu.type.testresources.HasInterceptors;
import edu.iu.type.testresources.HasInterceptorsOnMethod;
import edu.iu.type.testresources.MethodTestSupport;
import edu.iu.type.testresources.OverrideTestSupport.StringGreeter;
import edu.iu.type.testresources.inherit.CollectionApi;
import edu.iu.type.testresources.inherit.FromHidden;
import edu.iu.type.testresources.inherit.FromHiddenOverrides;
import edu.iu.type.testresources.inherit.FromOpen;
import edu.iu.type.testresources.inherit.OpenBase;
import edu.iu.type.testresources.inherit.ServiceFromHidden;
import edu.iu.type.testresources.inherit.ServiceFromOpen;
import edu.iu.type.testresources.inherit.ServiceFromOpenSubclass;
import edu.iu.type.testresources.inherit.Sized;
import edu.iu.type.testresources.inherit.SizedCollection;
import edu.iu.type.testresources.inherit.SubApiFirst;
import edu.iu.type.testresources.inherit.SubApiLast;
import edu.iu.type.testresources.inherit.SubApiViaSuperclass;
import edu.iu.type.testresources.inherit.SubCollectionApi;
import iu.type.IuTypeTestCase;

@SuppressWarnings("javadoc")
public class MethodTests extends IuTypeTestCase {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.type.ParameterizedElement", Level.FINEST, "replaced type argument .*");
	}

	@Test
	public void testInstanceInvocation() throws Exception {
		var method = IuType.of(MethodTestSupport.class).method("echo", String.class);
		assertFalse(method.isStatic());
		assertEquals("echo", method.name());
		assertSame(String.class, method.returnType().erasedClass());
		assertEquals("foobar", method.exec(new MethodTestSupport(), "foobar"));
	}

	@Test
	public void testStaticInvocation() throws Exception {
		var method = IuType.of(MethodTestSupport.class).method("add", int.class, int.class);
		assertTrue(method.isStatic());
		assertEquals("add", method.name());
		assertSame(int.class, method.returnType().erasedClass());
		assertEquals(7, method.exec(3, 4));
	}

	@Test
	public void testPublicMethod() {
		assertTrue(IuType.of(EnclosedClassesSupport.class).method("getMethodLevelClass").isPublic());
	}

	@Test
	public void testNonPublicMethod() {
		assertFalse(IuType.of(MethodTestSupport.class).method("echo", String.class).isPublic());
	}

	private long count(IuType<?, ?> type, String name) {
		long count = 0;
		for (var method : type.methods())
			if (method.name().equals(name))
				count++;
		return count;
	}

	@Test
	public void testOverriddenMethodsDropped() throws Exception {
		var type = IuType.of(StringGreeter.class);
		assertEquals(1, count(type, "name"));
		assertEquals(1, count(type, "greet"));
		assertEquals(1, count(type, "inherited"));
		assertEquals(2, count(type, "util"));
		assertEquals(2, count(type, "hidden"));
		assertEquals("sub", type.method("name").exec(new StringGreeter()));
	}

	private void assertInheritsBaseMethods(IuType<?, ?> type, Object instance) throws Exception {
		assertEquals(1, count(type, "size"));
		assertEquals(1, count(type, "post"));
		assertEquals(1, count(type, "get"));
		assertEquals(1, type.method("size").exec(instance));
		assertEquals(List.of(), type.method("list", int.class, Integer.class).exec(instance, 0, 10));
		type.method("post", Object.class).exec(instance, "value");
	}

	@Test
	public void testInheritsFromPublicGenericBase() throws Exception {
		final var type = IuType.of(FromOpen.class);
		assertInheritsBaseMethods(type, new FromOpen());
		assertEquals(1, count(type, "list"));
		assertNull(type.method("get", String.class).exec(new FromOpen(), "id"));
	}

	@Test
	public void testVisibilityBridgesDontHideInheritedMethods() throws Exception {
		final var bridges = new ArrayList<String>();
		for (final var method : FromHidden.class.getDeclaredMethods())
			if (method.isBridge())
				bridges.add(method.getName());
		assertTrue(bridges.containsAll(List.of("size", "list", "post", "get")), bridges::toString);

		final var type = IuType.of(FromHidden.class);
		assertInheritsBaseMethods(type, new FromHidden());
		assertEquals(1, count(type, "list"));
		assertNull(type.method("get", String.class).exec(new FromHidden(), "id"));
	}

	private void assertResolves(Class<?> expected, IuType<?, ?> type, String name, Class<?>... params) {
		assertEquals(1, count(type, name), name);
		assertSame(expected, type.method(name, params).declaringType().erasedClass(), name);
	}

	private void assertSuperclassPreferred(Class<?> superclass, IuType<?, ?> type, Object instance) throws Exception {
		assertResolves(superclass, type, "size");
		assertResolves(superclass, type, "list", int.class, Integer.class);
		assertEquals(1, type.method("size").exec(instance));
		assertEquals(List.of(), type.method("list", int.class, Integer.class).exec(instance, 0, 10));
	}

	@Test
	public void testSuperclassMethodPreferredOverInterface() throws Exception {
		assertSuperclassPreferred(OpenBase.class, IuType.of(ServiceFromOpen.class), new ServiceFromOpen());
	}

	@Test
	public void testInheritedSuperclassMethodPreferredOverInterface() throws Exception {
		assertSuperclassPreferred(OpenBase.class, IuType.of(ServiceFromOpenSubclass.class),
				new ServiceFromOpenSubclass());
	}

	@Test
	public void testHiddenSuperclassMethodPreferredOverInterface() throws Exception {
		assertSuperclassPreferred(Class.forName("edu.iu.type.testresources.inherit.HiddenBase"),
				IuType.of(ServiceFromHidden.class), new ServiceFromHidden());
	}

	@Test
	public void testMoreSpecificInterfacePreferred() throws Exception {
		assertResolves(SubCollectionApi.class, IuType.of(SubApiFirst.class), "size");
		assertResolves(SubCollectionApi.class, IuType.of(SubApiLast.class), "size");
		assertResolves(CollectionApi.class, IuType.of(SubApiLast.class), "list", int.class, Integer.class);
		// CollectionApi precedes the superclass in the hierarchy, then is replaced
		assertResolves(SubCollectionApi.class, IuType.of(SubApiViaSuperclass.class), "size");
	}

	@Test
	public void testUnrelatedInterfacesResolveOnce() throws Exception {
		// neither is more specific; either may be resolved, but only one
		final var type = IuType.of(SizedCollection.class);
		assertEquals(1, count(type, "size"));
		final var declaringClass = type.method("size").declaringType().erasedClass();
		assertTrue(declaringClass == CollectionApi.class || declaringClass == Sized.class,
				declaringClass::toString);
	}

	@Test
	public void testGenericAndVisibilityBridges() throws Exception {
		final var type = IuType.of(FromHiddenOverrides.class);
		final var instance = new FromHiddenOverrides();
		assertInheritsBaseMethods(type, instance);
		assertEquals(2, count(type, "list"));
		assertEquals(List.of("filter"), type.method("list", String.class).exec(instance, "filter"));
		assertEquals("id", type.method("get", String.class).exec(instance, "id"));
		assertEquals(String.class, type.method("get", String.class).returnType().erasedClass());
	}

	@Test
	public void testInterceptorsOnTypeNotSupported() throws Exception {
		var method = IuType.of(HasInterceptors.class).method("fail");
		assertEquals("@AroundInvoke not supported in this version",
				assertThrows(UnsupportedOperationException.class, () -> method.exec(new HasInterceptors()))
						.getMessage());
	}

	@Test
	public void testInterceptorsOnMethodNotSupported() throws Exception {
		var method = IuType.of(HasInterceptorsOnMethod.class).method("fail");
		assertEquals("@AroundInvoke not supported in this version",
				assertThrows(UnsupportedOperationException.class, () -> method.exec(new HasInterceptorsOnMethod()))
						.getMessage());
	}

	@Test
	public void testAroundInvokeNotSupported() throws Exception {
		var method = IuType.of(HasAroundInvokeMethod.class).method("fail");
		assertEquals("@AroundInvoke not supported in this version",
				assertThrows(UnsupportedOperationException.class, () -> method.exec(new HasAroundInvokeMethod()))
						.getMessage());
	}

}
