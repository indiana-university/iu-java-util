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
package iu.type.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class CompoundResourceTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testRequiresIterable() {
		final var type = mock(IuType.class);
		assertThrows(IllegalArgumentException.class, () -> new CompoundResource<>(null, type, null));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInvalidType() {
		final var name = IdGenerator.generateId();

		class ItemType {
		}
		final var itemType = mock(IuType.class);
		when(itemType.erasedClass()).thenReturn(ItemType.class);

		final var type = mock(IuType.class);
		when(type.typeParameter("T")).thenReturn(itemType);
		when(type.erasedClass()).thenReturn(Iterable.class);

		class ResourceType {
		}
		final var resourceType = mock(IuType.class);
		when(resourceType.erasedClass()).thenReturn(ResourceType.class);
		final var resource = mock(IuResource.class);
		when(resource.shared()).thenReturn(true);
		when(resource.type()).thenReturn(resourceType);

		final var resourceInstance = new ResourceType();
		when(resource.get()).thenReturn(resourceInstance);

		final var error = assertThrows(IllegalArgumentException.class,
				() -> new CompoundResource(name, type, IuIterable.iter(resource)));
		assertEquals("resource type mismatch " + resource + ", expected " + itemType, error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testSharedResource() {
		final var name = IdGenerator.generateId();

		class ItemType {
		}
		final var itemType = mock(IuType.class);
		when(itemType.erasedClass()).thenReturn(ItemType.class);

		final var type = mock(IuType.class);
		when(type.typeParameter("T")).thenReturn(itemType);
		when(type.erasedClass()).thenReturn(Iterable.class);

		class ResourceType extends ItemType {
		}
		final var resourceType = mock(IuType.class);
		when(resourceType.erasedClass()).thenReturn(ResourceType.class);
		final var resource = mock(IuResource.class);
		when(resource.shared()).thenReturn(true);
		when(resource.type()).thenReturn(resourceType);

		final var resourceInstance = new ResourceType();
		when(resource.get()).thenReturn(resourceInstance);

		final var compoundResource = new CompoundResource(name, type, IuIterable.iter(resource));
		assertEquals(name, compoundResource.name());
		assertEquals(type, compoundResource.type());
		assertTrue(compoundResource.shared());
		assertFalse(compoundResource.needsAuthentication());
		assertEquals(0, compoundResource.priority());
		assertThrows(UnsupportedOperationException.class, () -> compoundResource.factory(null));

		final var resourceIterator = ((Iterable) compoundResource.factory().get()).iterator();
		assertTrue(resourceIterator.hasNext());
		assertSame(resourceInstance, resourceIterator.next());
		assertFalse(resourceIterator.hasNext());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testServerResource() {
		final var name = IdGenerator.generateId();

		class ItemType {
		}
		final var itemType = mock(IuType.class);
		when(itemType.erasedClass()).thenReturn(ItemType.class);

		final var type = mock(IuType.class);
		when(type.typeParameter("T")).thenReturn(itemType);
		when(type.erasedClass()).thenReturn(Iterable.class);

		class ResourceType extends ItemType {
		}
		final var resourceType = mock(IuType.class);
		when(resourceType.erasedClass()).thenReturn(ResourceType.class);
		final var resource = mock(IuResource.class);
		when(resource.type()).thenReturn(resourceType);
		when(resource.needsAuthentication()).thenReturn(true);
		when(resource.priority()).thenReturn(-5000);

		final var resourceInstance = new ResourceType();
		when(resource.get()).thenReturn(resourceInstance);

		final var compoundResource = new CompoundResource(name, type, IuIterable.iter(resource));
		assertEquals(name, compoundResource.name());
		assertEquals(type, compoundResource.type());
		assertFalse(compoundResource.shared());
		assertTrue(compoundResource.needsAuthentication());
		assertEquals(-5000, compoundResource.priority());
		assertThrows(UnsupportedOperationException.class, () -> compoundResource.factory(null));

		final var resourceIterator = ((Iterable) compoundResource.factory().get()).iterator();
		assertTrue(resourceIterator.hasNext());
		assertSame(resourceInstance, resourceIterator.next());
		assertFalse(resourceIterator.hasNext());
	}

}
