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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.type.InstanceReference;
import edu.iu.type.IuComponent.Kind;
import edu.iu.type.IuResource;
import edu.iu.type.IuResourceReference;
import edu.iu.type.IuType;
import edu.iu.type.testresources.inherit.ExtendsLibrary;
import edu.iu.type.testresources.inherit.ExtendsLibraryChild;
import edu.iu.type.testresources.inherit.GenericHolderA;
import edu.iu.type.testresources.inherit.GenericHolderB;
import edu.iu.type.testresources.inherit.ResourceHolderA;
import edu.iu.type.testresources.inherit.ResourceHolderB;
import edu.iu.type.testresources.inherit.ResourceHolderC;
import edu.iu.type.testresources.inherit.lib.LibraryBase;
import jakarta.annotation.Resource;

/**
 * Covers {@literal @}Resource attributes declared on a superclass and injected
 * into instances of its subclasses.
 */
@SuppressWarnings("javadoc")
public class InheritedResourceTest extends IuTypeTestCase {

	private static final String ABSTRACT_HOLDER = "edu.iu.type.testresources.inherit.AbstractResourceHolder";
	private static final String GENERIC_HOLDER = "edu.iu.type.testresources.inherit.AbstractGenericHolder";

	private final Map<String, Object> values = new HashMap<>();
	private final Map<String, AtomicInteger> injections = new HashMap<>();

	@BeforeEach
	public void setup() {
		values.put("shared", new Object());
		values.put("named", 34);
		values.put("prop", new Object());
		values.put("own", "own value");
		values.put("library", new Object());
		values.put("genericShared", new Object());
		values.put("genericValue", "generic value");
		values.put("genericProp", new Object());
	}

	private Component component(Component parent, String... classNames) {
		final var archive = new ComponentArchive(null, Kind.JAR, null, new Properties(),
				new LinkedHashSet<>(List.of(classNames)), Map.of(), List.of());
		return new Component(parent, getClass().getClassLoader(), ModuleLayer.boot(), List.of(archive), null);
	}

	private static String[] names(String first, Class<?>... classes) {
		return Stream.concat(Stream.ofNullable(first), Stream.of(classes).map(Class::getName))
				.toArray(String[]::new);
	}

	private Map<String, Class<?>> referrers(Component component) {
		final Map<String, Class<?>> referrers = new HashMap<>();
		for (final var ref : component.resourceReferences())
			assertNull(referrers.put(ref.name(), ref.referrerType().erasedClass()), () -> "duplicate " + ref);
		return referrers;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void bind(Component component) {
		for (final IuResourceReference ref : component.resourceReferences()) {
			final var name = ref.name();
			final var count = injections.computeIfAbsent(name, n -> new AtomicInteger());
			final IuResource resource = mock(IuResource.class);
			when(resource.name()).thenReturn(name);
			when(resource.type()).thenReturn(ref.type());
			when(resource.get()).thenAnswer(a -> {
				count.incrementAndGet();
				return values.get(name);
			});
			ref.bind(resource);
		}
		injections.values().forEach(a -> a.set(0));
	}

	private void assertInjectedOnce(String... names) {
		for (final var name : names)
			assertEquals(1, injections.get(name).getAndSet(0), name);
		for (final var e : injections.entrySet())
			assertEquals(0, e.getValue().get(), e.getKey());
	}

	private Object value(Class<?> type, String name, Object instance) {
		return TypeFactory.resolveRawClass(type).field(name).get(instance);
	}

	@Test
	public void testInheritedReferencesDeclaredOnce() throws Exception {
		final var abstractHolder = Class.forName(ABSTRACT_HOLDER);
		try (final var component = component(null,
				names(ABSTRACT_HOLDER, ResourceHolderA.class, ResourceHolderB.class, ResourceHolderC.class))) {
			assertEquals(Map.of( //
					"shared", abstractHolder, //
					"named", abstractHolder, //
					"prop", abstractHolder, //
					"own", ResourceHolderA.class), referrers(component));
		}
	}

	@Test
	public void testInheritedReferencesDeclaredOnceSubclassesFirst() throws Exception {
		final var abstractHolder = Class.forName(ABSTRACT_HOLDER);
		final var names = new LinkedHashSet<>(
				List.of(names(null, ResourceHolderC.class, ResourceHolderB.class, ResourceHolderA.class)));
		names.add(ABSTRACT_HOLDER);
		try (final var component = component(null, names.toArray(String[]::new))) {
			assertEquals(Map.of( //
					"shared", abstractHolder, //
					"named", abstractHolder, //
					"prop", abstractHolder, //
					"own", ResourceHolderA.class), referrers(component));
		}
	}

	@Test
	public void testObserveInjectsInheritedResources() throws Exception {
		final var abstractHolder = Class.forName(ABSTRACT_HOLDER);
		try (final var component = component(null,
				names(ABSTRACT_HOLDER, ResourceHolderA.class, ResourceHolderB.class, ResourceHolderC.class))) {
			bind(component);

			final var a = new ResourceHolderA();
			IuType.of(ResourceHolderA.class).observe(a);
			assertInjectedOnce("shared", "named", "prop", "own");
			assertSame(values.get("shared"), value(abstractHolder, "shared", a));
			assertSame(values.get("named"), value(abstractHolder, "named", a));
			assertSame(values.get("prop"), value(abstractHolder, "prop", a));
			assertSame(values.get("own"), value(ResourceHolderA.class, "own", a));

			final var b = new ResourceHolderB();
			IuType.of(ResourceHolderB.class).observe(b);
			assertInjectedOnce("shared", "named", "prop");
			assertSame(values.get("shared"), value(abstractHolder, "shared", b));
			assertSame(values.get("named"), value(abstractHolder, "named", b));
			assertSame(values.get("prop"), value(abstractHolder, "prop", b));

			final var c = new ResourceHolderC();
			IuType.of(ResourceHolderC.class).observe(c);
			assertInjectedOnce("shared", "named", "prop", "own");
			assertSame(values.get("shared"), value(abstractHolder, "shared", c));
			assertSame(values.get("own"), value(ResourceHolderA.class, "own", c));

			IuType.of(ResourceHolderA.class).destroy(a);
			IuType.of(ResourceHolderB.class).destroy(b);
			IuType.of(ResourceHolderC.class).destroy(c);
			for (final var instance : List.<Object>of(a, b, c)) {
				assertNull(value(abstractHolder, "shared", instance));
				assertNull(value(abstractHolder, "named", instance));
				assertNull(value(abstractHolder, "prop", instance));
				assertEquals(1, value(abstractHolder, "preDestroyCount", instance));
			}
			assertNull(value(ResourceHolderA.class, "own", a));
			assertNull(value(ResourceHolderA.class, "own", c));

			// destroyed instances are no longer bound
			bind(component);
			for (final var instance : List.<Object>of(a, b, c))
				assertNull(value(abstractHolder, "shared", instance));
			assertInjectedOnce();
		}
	}

	@Test
	public void testGenericSuperclass() throws Exception {
		final var genericHolder = Class.forName(GENERIC_HOLDER);
		try (final var component = component(null,
				names(GENERIC_HOLDER, GenericHolderA.class, GenericHolderB.class))) {
			assertEquals(Map.of( //
					"genericShared", genericHolder, //
					"genericValue", genericHolder, //
					"genericProp", genericHolder), referrers(component));
			bind(component);

			final var a = new GenericHolderA();
			IuType.of(GenericHolderA.class).observe(a);
			assertInjectedOnce("genericShared", "genericValue", "genericProp");
			assertSame(values.get("genericShared"), value(genericHolder, "genericShared", a));
			assertSame(values.get("genericValue"), value(genericHolder, "value", a));
			assertSame(values.get("genericProp"), value(genericHolder, "genericProp", a));

			final var b = new GenericHolderB();
			IuType.of(GenericHolderB.class).observe(b);
			assertInjectedOnce("genericShared", "genericValue", "genericProp");
			assertSame(values.get("genericShared"), value(genericHolder, "genericShared", b));

			IuType.of(GenericHolderA.class).destroy(a);
			IuType.of(GenericHolderB.class).destroy(b);
			for (final var instance : List.<Object>of(a, b)) {
				assertNull(value(genericHolder, "genericShared", instance));
				assertNull(value(genericHolder, "value", instance));
				assertNull(value(genericHolder, "genericProp", instance));
				assertEquals(1, value(genericHolder, "preDestroyCount", instance));
			}

			bind(component);
			assertInjectedOnce();
		}
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testSubscribeThroughParameterizedType() throws Exception {
		final var genericHolder = Class.forName(GENERIC_HOLDER);
		final IuType parameterized = IuType.of(GenericHolderA.class).referTo(genericHolder);
		assertNotSame(IuType.of(genericHolder), parameterized);

		final InstanceReference ref = mock(InstanceReference.class);
		parameterized.subscribe(ref);

		final var a = new GenericHolderA();
		IuType.of(GenericHolderA.class).observe(a);
		verify(ref).accept(a);
		IuType.of(GenericHolderA.class).destroy(a);
		verify(ref).clear(a);
	}

	@Test
	public void testSuperclassInParentComponent() throws Exception {
		final var abstractHolder = Class.forName(ABSTRACT_HOLDER);
		try (final var parent = component(null, ABSTRACT_HOLDER);
				final var component = component(parent, names(null, ResourceHolderA.class, ResourceHolderB.class))) {
			assertEquals(Map.of( //
					"shared", abstractHolder, //
					"named", abstractHolder, //
					"prop", abstractHolder), referrers(parent));
			assertEquals(Map.of("own", ResourceHolderA.class), referrers(component));

			bind(parent);
			bind(component);
			final var a = new ResourceHolderA();
			IuType.of(ResourceHolderA.class).observe(a);
			assertInjectedOnce("shared", "named", "prop", "own");
			assertSame(values.get("shared"), value(abstractHolder, "shared", a));
		}
	}

	@Test
	public void testSuperclassInGrandparentComponent() throws Exception {
		final var abstractHolder = Class.forName(ABSTRACT_HOLDER);
		try (final var grandparent = component(null, ABSTRACT_HOLDER);
				final var parent = component(grandparent, names(null, ResourceHolderB.class));
				final var component = component(parent,
						names(null, ResourceHolderA.class, ExtendsLibraryChild.class))) {
			assertEquals(Map.of( //
					"shared", abstractHolder, //
					"named", abstractHolder, //
					"prop", abstractHolder), referrers(grandparent));
			assertEquals(Map.of(), referrers(parent));
			assertEquals(Map.of( //
					"own", ResourceHolderA.class, //
					"library", ExtendsLibraryChild.class), referrers(component));

			bind(grandparent);
			bind(component);
			final var a = new ResourceHolderA();
			IuType.of(ResourceHolderA.class).observe(a);
			assertInjectedOnce("shared", "named", "prop", "own");
			assertSame(values.get("own"), value(ResourceHolderA.class, "own", a));

			final var c = new ExtendsLibraryChild();
			IuType.of(ExtendsLibraryChild.class).observe(c);
			assertInjectedOnce("library");
			assertSame(values.get("library"), c.library);
		}
	}

	@Test
	public void testSuperclassOutsideComponent() throws Exception {
		try (final var component = component(null, names(null, ExtendsLibraryChild.class, ExtendsLibrary.class))) {
			assertEquals(Map.of("library", ExtendsLibrary.class), referrers(component));
			bind(component);

			final var e = new ExtendsLibrary();
			IuType.of(ExtendsLibrary.class).observe(e);
			assertInjectedOnce("library");
			assertSame(values.get("library"), e.library);

			final var c = new ExtendsLibraryChild();
			IuType.of(ExtendsLibraryChild.class).observe(c);
			assertInjectedOnce("library");
			assertSame(values.get("library"), c.library);

			IuType.of(ExtendsLibraryChild.class).destroy(c);
			assertNull(c.library);
			assertSame(values.get("library"), e.library);
		}
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testReferenceToSuperclassObservesSubclass() throws Exception {
		final var field = TypeFactory.resolveRawClass(LibraryBase.class).field("library");
		final ComponentResourceReference ref = new ComponentResourceReference<>(field,
				field.annotation(Resource.class));
		assertSame(LibraryBase.class, ref.referrerType().erasedClass());

		final var value = new Object();
		final IuResource resource = mock(IuResource.class);
		when(resource.name()).thenReturn("library");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(Object.class));
		when(resource.get()).thenReturn(value);
		ref.bind(resource);

		final var c = new ExtendsLibraryChild();
		IuType.of(ExtendsLibraryChild.class).observe(c);
		assertSame(value, c.library);

		IuType.of(ExtendsLibraryChild.class).destroy(c);
		assertNull(c.library);
	}

}
