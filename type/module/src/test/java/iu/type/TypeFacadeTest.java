package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class TypeFacadeTest {

	private void assertRaw(Class<?> baseClass, TypeFacade<?> baseFacade) {
		assertNull(baseFacade.reference());
		assertSame(baseClass, baseFacade.deref());
		assertSame(baseFacade, baseFacade.erase());
		assertSame(baseClass, baseFacade.erasedClass());
		assertSame(baseClass.getName(), baseFacade.name());
		assertEquals("IuType[" + baseClass + ']', baseFacade.toString());
	}

	private void assertGeneric(Type genericType, TypeFacade<?> genericFacade) {
		assertNull(genericFacade.reference());
		assertSame(genericType, genericFacade.deref());
		assertNotSame(genericFacade, genericFacade.erase());
		assertSame(genericFacade.erasedClass().getName(), genericFacade.name());
		assertEquals("IuType[" + genericType + ']', genericFacade.toString());

		var erasedClass = genericFacade.erasedClass();
		var erasedFacade = genericFacade.erase();
		assertEquals(IuReferenceKind.ERASURE, erasedFacade.reference().kind());
		assertSame(erasedFacade, erasedFacade.reference().referent());
		assertSame(genericFacade, erasedFacade.reference().referrer());
		assertSame(erasedClass, erasedFacade.deref());
		assertNotSame(erasedFacade, erasedFacade.erase());
		assertSame(erasedClass, erasedFacade.erasedClass());
		assertSame(erasedClass.getName(), erasedFacade.name());
		assertEquals("IuType[" + erasedClass + " ERASURE " + genericType + ']', erasedFacade.toString());
	}

	@Test
	public void testRawBuilderIsValid() {
		var raw = TypeFacade.builder(Object.class).build();
		assertRaw(Object.class, raw);

		var raw2 = TypeFacade.builder(Object.class).build();
		assertTrue(Set.of(raw).contains(raw2));
		assertEquals(raw, raw2);
	}

	@Test
	public void testGenericBuilderIsValid() throws NoSuchFieldException {
		@SuppressWarnings("unused")
		class HasAFieldWithAParameterizedType {
			Optional<?> fieldWithAParameterizedType;
		}
		var type = HasAFieldWithAParameterizedType.class.getDeclaredField("fieldWithAParameterizedType")
				.getGenericType();
		assertGeneric(type, TypeFacade.builder(type, TypeFacade.builder(Optional.class).build()).build());
	}

	@Test
	public void testGenericBuilderAssertsNotClass() {
		assertThrows(AssertionError.class, () -> TypeFacade.builder(Object.class, null));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGenericBuilderAssertsErasureIsClass() {
		var mockType = mock(Type.class);
		var mockFacade = mock(TypeFacade.class);
		when(mockFacade.deref()).thenReturn(mockType);
		assertThrows(AssertionError.class, () -> TypeFacade.builder(mockType, mockFacade));
	}

	@Test
	public void testBuildsHierarchy() {
		interface ExtendsObject {
		}
		var object = TypeFacade.builder(Object.class).build();
		assertTrue(object.hierarchy().isEmpty());

		var extendsObject = TypeFacade.builder(ExtendsObject.class).hierarchy(List.of(object)).build();
		assertRaw(ExtendsObject.class, extendsObject);
		assertEquals(1, extendsObject.hierarchy().size());

		var superObject = extendsObject.hierarchy().get(0);
		assertNotEquals(object, superObject);
		assertFalse(Set.of(object).contains(superObject));
		assertSame(Object.class, superObject.erasedClass());
	}

	@Test
	public void testAssertsBuilderAcceptsHierarchyOnlyOnce() {
		assertThrows(AssertionError.class,
				() -> TypeFacade.builder(Object.class).hierarchy(List.of()).hierarchy(List.of()));
	}

	@Test
	public void testEqualsTypeChecks() {
		var t1 = TypeFacade.builder(Object.class).build();
		var t2 = mock(IuType.class);
		assertNotEquals(t1, t2);
	}

	@Test
	public void testSameRawAreEquals() {
		var t1 = TypeFacade.builder(Object.class).build();
		var t2 = TypeFacade.builder(Object.class).build();
		assertEquals(t1, t2);
		assertEquals(t2, t1);
	}

	@Test
	public void testDifferentRawNotEquals() {
		var t1 = TypeFacade.builder(Object.class).build();
		var t2 = TypeFacade.builder(Number.class).build();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

	@Test
	public void testSetsOfRaw() {
		Set<IuType<?>> set = new HashSet<>();
		assertTrue(set.add(TypeFacade.builder(Object.class).build()));
		assertFalse(set.add(TypeFacade.builder(Object.class).build()));
		assertTrue(set.add(TypeFacade.builder(Number.class).build()));
		assertTrue(set.contains(TypeFacade.builder(Object.class).build()));
		assertTrue(set.contains(TypeFacade.builder(Number.class).build()));
	}

	@Test
	public void testSetsOfGeneric() {
		interface HasTypeParam<T> {
		}
		var e = TypeFacade.builder(Object.class).build();
		Set<IuType<?>> set = new HashSet<>();
		assertTrue(set.add(TypeFacade.builder(HasTypeParam.class.getTypeParameters()[0], e).build()));
		assertTrue(set.add(TypeFacade.builder(HasTypeParam.class.getTypeParameters()[0], e).build()));
	}

	@Test
	public void testGenericNotEqualsRaw() {
		interface HasTypeParam<T> {
		}
		var t1 = TypeFacade.builder(Object.class).build();
		var t2 = TypeFacade.builder(HasTypeParam.class.getTypeParameters()[0], t1).build();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

	@Test
	public void testErasureNotEqualsRaw() {
		interface HasTypeParam<T> {
		}
		var t1 = TypeFacade.builder(Object.class).build();
		var t2 = TypeFacade.builder(HasTypeParam.class.getTypeParameters()[0], t1).build().erase();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

	@Test
	public void testSameGenericsIsEquals() {
		interface HasTypeParam<T> {
		}
		var t1 = TypeFacade.builder(HasTypeParam.class.getTypeParameters()[0], TypeFacade.builder(Object.class).build())
				.build().erase();
		assertEquals(t1, t1);
	}

	@Test
	public void testEquivalentGenericsAreNotEquals() {
		interface HasTypeParam<T> {
		}
		var t1 = TypeFacade.builder(HasTypeParam.class.getTypeParameters()[0], TypeFacade.builder(Object.class).build())
				.build().erase();
		var t2 = TypeFacade.builder(HasTypeParam.class.getTypeParameters()[0], TypeFacade.builder(Object.class).build())
				.build().erase();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

	@Test
	public void testTypeParamsWithDifferentBoundsNotEqua() {
		interface HasTypeParam<T> {
		}
		interface HasBoundedTypeParam<T extends Number> {
		}
		var t1 = TypeFacade.builder(HasTypeParam.class.getTypeParameters()[0], TypeFacade.builder(Object.class).build())
				.build().erase();
		var t2 = TypeFacade
				.builder(HasBoundedTypeParam.class.getTypeParameters()[0], TypeFacade.builder(Number.class).build())
				.build().erase();
		assertNotEquals(t1, t2);
		assertNotEquals(t2, t1);
	}

}
