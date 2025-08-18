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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;
import edu.iu.type.IuComponent;
import edu.iu.type.IuMethod;
import edu.iu.type.IuResourceKey;
import edu.iu.type.IuType;
import edu.iu.type.testresources.AnInterface;
import edu.iu.type.testresources.AnInterfaceImpl;
import edu.iu.type.testresources.AnInterfaceSerializableImpl;
import edu.iu.type.testresources.ApplicationResource;
import edu.iu.type.testresources.CreateProxyInterface;
import edu.iu.type.testresources.CreateProxyInvocationHandler;
import edu.iu.type.testresources.MultiResource;
import edu.iu.type.testresources.NamedResource;
import edu.iu.type.testresources.NonSharedResource;
import edu.iu.type.testresources.ProxyNonResource;
import edu.iu.type.testresources.ProxyResource;
import edu.iu.type.testresources.ThrownFromDefaultFactory;
import edu.iu.type.testresources.ThrowsFromDefaultFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.annotation.Resources;

@SuppressWarnings("javadoc")
public class ComponentResourceTest extends IuTypeTestCase {

	private <T> ComponentResource<T> assertComponentResource(Class<T> type, Supplier<?> factory) {
		return assertComponentResource(type, type, factory);
	}

	private <T> ComponentResource<T> assertComponentResource(String name, Class<T> type, Supplier<?> factory) {
		return assertComponentResource(true, true, -1, name, type, type, factory);
	}

	private <T> ComponentResource<T> assertComponentResource(Class<T> type, Class<?> impl, Supplier<?> factory) {
		return assertComponentResource(true, true, -1, IuResourceKey.getDefaultResourceName(type), type, impl, factory);
	}

	private <T> ComponentResource<T> assertComponentResource(boolean needsAuthentication, boolean shared, int priority,
			String name, Class<T> type, Class<?> impl, Supplier<?> factory) {
		ComponentResource<T> last = null;
		for (final var r : ComponentResource.getResources(impl)) {
			r.factory(factory);
			last = assertComponentResource(needsAuthentication, shared, priority, name, type, r);
		}
		assertNotNull(last);
		return last;
	}

	@SuppressWarnings("unchecked")
	private <T> ComponentResource<T> assertComponentResource(boolean needsAuthentication, boolean shared, int priority,
			String name, Class<T> type, ComponentResource<?> r) {
		assertEquals(shared, r.shared());
		assertEquals(needsAuthentication, r.needsAuthentication());
		assertEquals(priority, r.priority());
		assertEquals(name, r.name());
		assertSame(type, r.type().erasedClass());

		var instance = r.get();
		assertTrue(type.isInstance(instance));
		if (shared)
			assertSame(instance, r.get());
		else
			assertNotSame(instance, r.get());
		return (ComponentResource<T>) r;
	}

	@Test
	public void testWebResource() {
		var d = new byte[0];
		var r = ComponentResource.createWebResource("", d);
		assertTrue(r.needsAuthentication());
		assertTrue(r.shared());
		assertEquals("", r.name());
		assertSame(IuType.of(d.getClass()), r.type());
		assertSame(d, r.get());
	}

	@Test
	public void testApplicationResourceIsApplicationResource() {
		assertTrue(ComponentResource.getResources(ApplicationResource.class).iterator().hasNext());
	}

	@Test
	public void testProxyResourceIsApplicationResource() {
		assertTrue(ComponentResource.getResources(ProxyResource.class).iterator().hasNext());
	}

	@Test
	public void testProxyResourceIsApplicationResourceWithoutAnInterface() {
		assertFalse(ComponentResource.getResources(ProxyNonResource.class).iterator().hasNext());
	}

	@Test
	public void testPriority() throws Exception {
		IuTestLogger.allow("", Level.WARNING);
		try (final var r = IuComponent.of(TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"));
				final var c = r.extend(TestArchives.getComponentArchive("testcomponent"))) {
			final var loader = c.classLoader();
			final var type = loader.loadClass("edu.iu.type.testcomponent.PriorityResource");
			final var resource = ComponentResource.getResources(type).iterator().next();
			assertComponentResource(true, true, 34, "priorityResource", type, resource);
		}
	}

	@Test
	public void testCreateResourceInstance() throws Exception {
		var resource = ComponentResource.getResources(ApplicationResource.class).iterator().next();
		resource.factory(ApplicationResource::new);
		assertInstanceOf(ApplicationResource.class, resource.get());
	}

	@Test
	public void testCreateProxyResourceInstance() throws Exception {
		final var resource = ComponentResource.getResources(ProxyResource.class).iterator().next();
		resource.factory(ProxyResource::new);

		final var resourceInstance = resource.get();
		assertInstanceOf(AnInterface.class, resourceInstance);
		assertTrue(Proxy.isProxyClass(resourceInstance.getClass()));
		assertTrue(Proxy.getInvocationHandler(resourceInstance) instanceof ProxyResource);
	}

	@Test
	public void testCreatePlainResource() {
		assertComponentResource(ApplicationResource.class, ApplicationResource::new);
	}

	@Test
	public void testCreateResourceImplementsInterface() {
		var resource = assertComponentResource(AnInterface.class, AnInterfaceImpl.class, AnInterfaceImpl::new);
		assertTrue(resource.get() instanceof AnInterfaceImpl);
	}

	@Test
	public void testCreateResourceSelectsFirstNonPlatformInterface() {
		var resource = assertComponentResource(AnInterface.class, AnInterfaceSerializableImpl.class,
				AnInterfaceSerializableImpl::new);
		assertTrue(resource.get() instanceof AnInterfaceSerializableImpl);
	}

	@Test
	public void testCreateNamedResource() {
		assertComponentResource("java:comp/env/resourceName", NamedResource.class, NamedResource::new);
	}

	@Test
	public void testCreateProxyResource() {
		var proxyResource = assertComponentResource(CreateProxyInterface.class, CreateProxyInvocationHandler.class,
				CreateProxyInvocationHandler::new);
		assertEquals("bar", ((CreateProxyInterface) proxyResource.get()).getFoo());
	}

	@Test
	public void testNonSharedResource() {
		assertComponentResource(false, false, -1, "nonSharedResource", NonSharedResource.class, NonSharedResource.class,
				NonSharedResource::new);
	}

	@Test
	public void testCollectsSelfReferences() {
		final var resources = ComponentResource.getResources(MultiResource.class);
		final var resourceIterator = resources.iterator();

		assertTrue(resourceIterator.hasNext());
		var resource = resourceIterator.next();
		resource.factory(MultiResource::new);
		assertComponentResource(true, true, -1, "multiResource", MultiResource.class, resource);

		assertTrue(resourceIterator.hasNext());
		resource = resourceIterator.next();
		resource.factory(MultiResource::new);
		assertComponentResource(true, false, -1, "sharedMultiResource", MultiResource.class, resource);
		assertFalse(resourceIterator.hasNext());
	}

	@Test
	public void testDoesntCollectNonSelfReferences() {
		@Resources(@Resource(type = ComponentResource.class))
		@Resource(type = ComponentResourceTest.class)
		class MultiResource {
		}
		var resources = ComponentResource.getResources(MultiResource.class).iterator();
		assertFalse(resources.hasNext());
	}

	@Test
	public void testDoesntCollectNothing() {
		class NonResource {
		}
		var resources = ComponentResource.getResources(NonResource.class).iterator();
		assertFalse(resources.hasNext());
	}

	@Test
	public void testToString() {
		@Resource
		class ToStringResource {
		}
		assertEquals(
				"ComponentResource [needsAuthentication=true, shared=true, name=toStringResource, type=IuType[ToStringResource]]",
				ComponentResource.getResources(ToStringResource.class).iterator().next().toString());
	}

	@Test
	public void testDefaultFactory() {
		assertInstanceOf(ApplicationResource.class,
				ComponentResource.getResources(ApplicationResource.class).iterator().next().get());
	}

	@Test
	public void testDefaultFactoryThrows() {
		assertThrows(ThrownFromDefaultFactory.class,
				() -> ComponentResource.getResources(ThrowsFromDefaultFactory.class).iterator().next().factory().get());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testPostConstruct() throws Exception {
		final var i = new Object();
		final var name = IdGenerator.generateId();
		final var type = mock(TypeTemplate.class);
		final var m = mock(IuMethod.class);
		when(type.annotatedMethods(PostConstruct.class)).thenReturn(List.of(m));
		when(type.erasedClass()).thenReturn(Object.class);
		final var res = new ComponentResource<>(false, true, 0, name, type, () -> i);
		res.postConstruct();
		verify(m).exec(i);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testPostConstructNotShared() throws Exception {
		final var i = new Object();
		final var name = IdGenerator.generateId();
		final var type = mock(TypeTemplate.class);
		final var res = new ComponentResource<>(false, false, 0, name, type, () -> i);
		final var error = assertThrows(IllegalStateException.class, res::postConstruct);
		assertEquals("not shared", error.getMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testPreDestroy() throws Exception {
		final var i = new Object();
		final var name = IdGenerator.generateId();
		final var type = mock(TypeTemplate.class);
		final var m = mock(IuMethod.class);
		when(type.annotatedMethods(PreDestroy.class)).thenReturn(List.of(m));
		when(type.erasedClass()).thenReturn(Object.class);
		final var res = new ComponentResource<>(false, true, 0, name, type, () -> i);
		res.preDestroy();
		verify(m).exec(i);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testPreDestroyNotShared() throws Exception {
		final var i = new Object();
		final var name = IdGenerator.generateId();
		final var type = mock(TypeTemplate.class);
		final var res = new ComponentResource<>(false, false, 0, name, type, () -> i);
		final var error = assertThrows(IllegalStateException.class, res::preDestroy);
		assertEquals("not shared", error.getMessage());
	}

}
