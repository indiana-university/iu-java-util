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
