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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuResource;
import edu.iu.type.IuType;
import edu.iu.type.testresources.HasInvalidResourceRef;
import edu.iu.type.testresources.HasNamedTypedResourceRef;
import edu.iu.type.testresources.HasNoResourceRef;
import edu.iu.type.testresources.HasResourceRef;
import edu.iu.type.testresources.HasTypedResourceRef;
import jakarta.annotation.Resource;

@SuppressWarnings("javadoc")
public class ComponentResourceReferenceTest extends IuTypeTestCase {

	private ComponentResourceReference<?, ?> fieldRef(Class<?> referrer, String name) {
		final var field = TypeFactory.resolveRawClass(referrer).field(name);
		return new ComponentResourceReference<>(field, field.annotation(Resource.class));
	}

	private ComponentResourceReference<?, ?> setterRef(Class<?> referrer, String name) {
		final var property = TypeFactory.resolveRawClass(referrer).property(name);
		return new ComponentResourceReference<>(property, property.annotation(Resource.class));
	}

	@Test
	public void testUnbound() {
		final var ref = fieldRef(HasResourceRef.class, "resource");
		assertEquals("resource", ref.name());
		assertEquals(TypeFactory.resolveRawClass(Object.class), ref.type());
		assertEquals(HasResourceRef.class, ref.referrerType().erasedClass());
		assertEquals(
				"ComponentResourceReference [name=resource, type=IuType[Object], attribute=HasResourceRef#resource:Object, boundResource=null]",
				ref.toString());
	}

	@Test
	public void testMustBeAnnotated() {
		assertEquals("Missing @Resource: HasNoResourceRef#resource:Object",
				assertThrows(IllegalArgumentException.class, () -> fieldRef(HasNoResourceRef.class, "resource"))
						.getMessage());
	}

	@Test
	public void testMustBeAssignable() {
		assertEquals("attribute HasInvalidResourceRef#resource:Number is not assignable from IuType[String]",
				assertThrows(IllegalArgumentException.class, () -> fieldRef(HasInvalidResourceRef.class, "resource"))
						.getMessage());
	}

	@Test
	public void testSetsNameAndType() {
		final var ref = fieldRef(HasNamedTypedResourceRef.class, "resource");
		assertEquals("foo", ref.name());
		assertEquals(TypeFactory.resolveRawClass(Number.class), ref.type());
		assertEquals(HasNamedTypedResourceRef.class, ref.referrerType().erasedClass());
		assertEquals(
				"ComponentResourceReference [name=foo, type=IuType[Number], attribute=HasNamedTypedResourceRef#resource:Object, boundResource=null]",
				ref.toString());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testBind() throws Exception {
		final var ref = fieldRef(HasResourceRef.class, "resource");
		final var value = new Object();
		final var refer1 = TypeFactory.resolveRawClass(HasResourceRef.class).constructor().exec();
		assertNull(refer1.resource);

		final var resource = mock(IuResource.class);
		when(resource.name()).thenReturn("resource");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(Object.class));
		when(resource.get()).thenReturn(value);
		assertFalse(ref.isBound());
		ref.bind(resource);
		assertTrue(ref.isBound());
		assertSame(value, refer1.resource);

		final var refer2 = TypeFactory.resolveRawClass(HasResourceRef.class).constructor().exec();
		assertSame(value, refer2.resource);

		ref.bind(null);
		assertFalse(ref.isBound());
		assertNull(refer1.resource);
		assertNull(refer2.resource);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testClear() throws Exception {
		final ComponentResourceReference ref = fieldRef(HasResourceRef.class, "resource");
		final var value = new Object();
		final var refer1 = TypeFactory.resolveRawClass(HasResourceRef.class).constructor().exec();
		assertNull(refer1.resource);

		final var resource = mock(IuResource.class);
		when(resource.name()).thenReturn("resource");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(Object.class));
		when(resource.get()).thenReturn(value);
		assertFalse(ref.isBound());
		ref.bind(resource);
		assertTrue(ref.isBound());
		assertSame(value, refer1.resource);

		final var refer2 = new HasResourceRef();
		ref.accept(refer2);
		assertSame(value, refer2.resource);

		ref.clear(refer2);
		assertNull(refer2.resource);
		
		ref.clear(null); // no-op, no error
		ref.clear(refer2); // no-op, no error
		
		// no other effect on ref or refer1
		assertTrue(ref.isBound());
		assertSame(value, refer1.resource);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testBindBySetter() throws Exception {
		final ComponentResourceReference ref = setterRef(HasResourceRef.class, "propResource");
		final var value = new Object();
		final var refer1 = TypeFactory.resolveRawClass(HasResourceRef.class).constructor().exec();
		assertNull(refer1.propResource);

		final var resource = mock(IuResource.class);
		when(resource.name()).thenReturn("propResource");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(Object.class));
		when(resource.get()).thenReturn(value);
		ref.bind(resource);
		assertSame(value, refer1.propResource);

		final var refer2 = TypeFactory.resolveRawClass(HasResourceRef.class).constructor().exec();
		assertSame(value, refer2.propResource);

		// refer2 was already accepted by TypeTemplate, verify not accepted twice
		final var refer2spy = spy(refer2);
		ref.accept(refer2);
		verify(refer2spy, never()).setPropResource(value);

		ref.bind(null);
		assertNull(refer1.propResource);
		assertNull(refer2.propResource);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testBindBySetterNoGetter() throws Exception {
		final var ref = setterRef(HasResourceRef.class, "setterResource");
		final var value = new Object();
		final var refer1 = TypeFactory.resolveRawClass(HasResourceRef.class).constructor().exec();
		assertNull(refer1.setterResource);

		final var resource = mock(IuResource.class);
		when(resource.name()).thenReturn("setterResource");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(Object.class));
		when(resource.get()).thenReturn(value);
		ref.bind(resource);
		assertSame(value, refer1.setterResource);

		final var refer2 = TypeFactory.resolveRawClass(HasResourceRef.class).constructor().exec();
		assertSame(value, refer2.setterResource);

		ref.bind(null); // does not clear w/o getter
		assertSame(value, refer1.setterResource);
		assertSame(value, refer2.setterResource);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testBindRequiresNameMatch() {
		final var ref = fieldRef(HasResourceRef.class, "resource");
		final var resource = mock(IuResource.class);
		when(resource.name()).thenReturn("foo");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(Object.class));
		assertEquals("Resource " + resource
				+ " does not apply to ComponentResourceReference [name=resource, type=IuType[Object], attribute=HasResourceRef#resource:Object, boundResource=null]",
				assertThrows(IllegalArgumentException.class, () -> ref.bind(resource)).getMessage());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testBindRequiresSuperType() {
		final var ref = fieldRef(HasTypedResourceRef.class, "resource");
		final var resource = mock(IuResource.class);
		when(resource.name()).thenReturn("resource");
		when(resource.type()).thenReturn((IuType) TypeFactory.resolveRawClass(String.class));
		assertEquals("Resource " + resource
				+ " does not apply to ComponentResourceReference [name=resource, type=IuType[Number], attribute=HasTypedResourceRef#resource:Object, boundResource=null]",
				assertThrows(IllegalArgumentException.class, () -> ref.bind(resource)).getMessage());
	}

}
