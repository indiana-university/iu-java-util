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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeSupplier;
import edu.iu.type.IuType;
import jakarta.annotation.Resource;
import jakarta.annotation.Resource.AuthenticationType;
import jakarta.annotation.Resources;

@SuppressWarnings("javadoc")
public class ComponentResourceTest {

	private <T> ComponentResource<T> assertComponentResource(Class<T> type, UnsafeSupplier<T> factory) {
		return assertComponentResource(type, type, factory);
	}

	private <T> ComponentResource<T> assertComponentResource(String name, Class<T> type, UnsafeSupplier<T> factory) {
		return assertComponentResource(true, true, name, type, type, factory);
	}

	private <T> ComponentResource<T> assertComponentResource(Class<T> type, Class<?> impl, UnsafeSupplier<?> factory) {
		return assertComponentResource(true, true, type.getSimpleName(), type, impl, factory);
	}

	private <T> ComponentResource<T> assertComponentResource(boolean needsAuthentication, boolean shared, String name,
			Class<T> type, Class<?> impl, UnsafeSupplier<?> factory) {
		return assertComponentResource(needsAuthentication, shared, name, type,
				ComponentResource.createResource(impl.getAnnotation(Resource.class), impl, factory));
	}

	@SuppressWarnings("unchecked")
	private <T> ComponentResource<T> assertComponentResource(boolean needsAuthentication, boolean shared, String name,
			Class<T> type, ComponentResource<?> r) {
		assertEquals(shared, r.shared());
		assertEquals(needsAuthentication, r.needsAuthentication());
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
		@Resource
		class ApplicationResource {
		}
		assertTrue(ComponentResource.isApplicationResource(ApplicationResource.class.getAnnotation(Resource.class),
				ApplicationResource.class));
	}

	@Test
	public void testProxyResourceIsApplicationResource() {
		interface AnInterface {
		}
		@Resource(type = AnInterface.class)
		abstract class ProxyResource implements InvocationHandler {
		}
		assertTrue(ComponentResource.isApplicationResource(ProxyResource.class.getAnnotation(Resource.class),
				ProxyResource.class));
	}

	@Test
	public void testProxyResourceIsApplicationResourceWithoutAnInterface() {
		@Resource
		abstract class ProxyResource implements InvocationHandler {
		}
		assertFalse(ComponentResource.isApplicationResource(ProxyResource.class.getAnnotation(Resource.class),
				ProxyResource.class));
	}

	@Test
	public void testCreateResourceInstance() throws Exception {
		class ResourceClass {
		}
		var resource = ComponentResource.createResourceInstance(ResourceClass.class, ResourceClass::new);
		assertNotSame(resource, ComponentResource.createResourceInstance(ResourceClass.class, ResourceClass::new));
	}

	@Test
	public void testCreateProxyResourceInstance() throws Exception {
		interface ResourceInterface {
		}
		class ResourceInvocationHandler implements InvocationHandler {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				fail();
				return null;
			}
		}
		var resource = ComponentResource.createResourceInstance(ResourceInterface.class,
				ResourceInvocationHandler::new);
		assertTrue(Proxy.isProxyClass(resource.getClass()));
		assertTrue(Proxy.getInvocationHandler(resource) instanceof ResourceInvocationHandler);
	}

	@Test
	public void testCreateProxyResourceRequiresInterface() {
		@Resource
		abstract class ProxyResource implements InvocationHandler {
		}
		assertEquals("Application resource defined by InvocationHandler requires resource type to be an interface",
				assertThrows(IllegalArgumentException.class,
						() -> ComponentResource.createResource(ProxyResource.class.getAnnotation(Resource.class),
								ProxyResource.class, () -> new ProxyResource() {
									@Override
									public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
										throw new UnsupportedOperationException();
									}
								}))
						.getMessage());
	}

	@Test
	public void testCreateResourceRequiresSubclass() {
		@Resource(type = ComponentResource.class)
		class ApplicationResource {
		}
		assertEquals(
				"Application resource implementation class must be an InvocationHandler or assignable from resource type",
				assertThrows(IllegalArgumentException.class,
						() -> ComponentResource.createResource(ApplicationResource.class.getAnnotation(Resource.class),
								ApplicationResource.class, ApplicationResource::new))
						.getMessage());
	}

	@Test
	public void testCreatePlainResource() {
		@Resource
		class PlainResource {
		}
		assertComponentResource(PlainResource.class, PlainResource::new);
	}

	@Test
	public void testCreateResourceImplementsInterface() {
		interface ResourceInterface {
		}
		@Resource
		class ResourceImplementation implements ResourceInterface {
		}
		var resource = assertComponentResource(ResourceInterface.class, ResourceImplementation.class,
				ResourceImplementation::new);
		assertTrue(resource.get() instanceof ResourceImplementation);
	}

	@Test
	public void testCreateResourceSelectsFirstNonPlatformInterface() {
		interface ResourceInterface {
		}
		@Resource
		class ResourceImplementation implements Serializable, ResourceInterface {
			private static final long serialVersionUID = 1L;
		}
		var resource = assertComponentResource(ResourceInterface.class, ResourceImplementation.class,
				ResourceImplementation::new);
		assertTrue(resource.get() instanceof ResourceImplementation);
	}

	@Test
	public void testCreateNamedResource() {
		@Resource(name = "java:comp/env/resourceName")
		class NamedResource {
		}
		assertComponentResource("java:comp/env/resourceName", NamedResource.class, NamedResource::new);
	}

	@Test
	public void testCreateProxyResource() {
		interface AnInterface {
			String getFoo();
		}
		@Resource(type = AnInterface.class)
		class ProxyResource implements InvocationHandler {
			boolean invoked;

			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				assertEquals(method, AnInterface.class.getDeclaredMethod("getFoo"));
				assertFalse(invoked);
				invoked = true;
				return "bar";
			}
		}
		var proxyResource = assertComponentResource(AnInterface.class, ProxyResource.class, ProxyResource::new);
		assertEquals("bar", ((AnInterface) proxyResource.get()).getFoo());
	}

	@Test
	public void testNonSharedResource() {
		@Resource(shareable = false, authenticationType = AuthenticationType.APPLICATION)
		class NonSharedResource {
		}
		assertComponentResource(false, false, "NonSharedResource", NonSharedResource.class, NonSharedResource.class,
				NonSharedResource::new);
	}

	@Test
	public void testCollectsSelfReferences() {
		@Resources(@Resource)
		@Resource(name = "sharedMultiResource", shareable = false)
		class MultiResource {
		}
		var resources = ComponentResource.getResources(MultiResource.class, MultiResource::new);
		var resourceIterator = resources.iterator();
		assertTrue(resourceIterator.hasNext());
		assertComponentResource(true, true, "MultiResource", MultiResource.class, resourceIterator.next());
		assertTrue(resourceIterator.hasNext());
		assertComponentResource(true, false, "sharedMultiResource", MultiResource.class, resourceIterator.next());
		assertFalse(resourceIterator.hasNext());
	}

	@Test
	public void testDoesntCollectNonSelfReferences() {
		@Resources(@Resource(type = ComponentResource.class))
		@Resource(type = ComponentResourceTest.class)
		class MultiResource {
		}
		var resources = ComponentResource.getResources(MultiResource.class, MultiResource::new).iterator();
		assertFalse(resources.hasNext());
	}

	@Test
	public void testDoesntCollectNothing() {
		class NonResource {
		}
		var resources = ComponentResource.getResources(NonResource.class, NonResource::new).iterator();
		assertFalse(resources.hasNext());
	}

	@Test
	public void testToString() {
		@Resource
		class ToStringResource {
		}
		assertEquals(
				"[ComponentResource [needsAuthentication=true, shared=true, name=ToStringResource, type="
						+ IuType.of(ToStringResource.class) + "]]",
				ComponentResource.getResources(ToStringResource.class, ToStringResource::new).toString());
	}
}
