package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuReferenceKind;

@SuppressWarnings("javadoc")
public class TypeFacadeTest {

	private void assertBase(Class<?> baseClass, TypeFacade<?> baseFacade) {
		assertNull(baseFacade.reference());
		assertSame(baseClass, baseFacade.deref());
		assertSame(baseFacade, baseFacade.erase());
		assertSame(baseClass, baseFacade.erasedClass());
		assertSame(baseClass.getName(), baseFacade.name());
		assertEquals(baseClass.toString(), baseFacade.toString());
	}

	private void assertGeneric(Type genericType, TypeFacade<?> genericFacade) {
		assertNull(genericFacade.reference());
		assertSame(genericType, genericFacade.deref());
		assertNotSame(genericFacade, genericFacade.erase());
		assertSame(genericFacade.erasedClass().getName(), genericFacade.name());
		assertEquals(genericType.toString(), genericFacade.toString());

		var erasedClass = genericFacade.erasedClass();
		var erasedFacade = genericFacade.erase();
		assertEquals(IuReferenceKind.ERASURE, erasedFacade.reference().kind());
		assertSame(erasedFacade, erasedFacade.reference().referent());
		assertSame(genericFacade, erasedFacade.reference().referrer());
		assertSame(erasedClass, erasedFacade.deref());
		assertNotSame(erasedFacade, erasedFacade.erase());
		assertSame(erasedClass, erasedFacade.erasedClass());
		assertSame(erasedClass.getName(), erasedFacade.name());
		assertEquals(erasedClass.toString(), erasedFacade.toString());
	}

	@Test
	public void testBaseConstructorIsValid() {
		assertBase(Object.class, new TypeFacade<>(Object.class));
	}

	@Test
	public void testGenericConstructorIsValid() throws NoSuchFieldException {
		@SuppressWarnings("unused")
		class HasAFieldWithAParameterizedType {
			Optional<?> fieldWithAParameterizedType;
		}
		var type = HasAFieldWithAParameterizedType.class.getDeclaredField("fieldWithAParameterizedType")
				.getGenericType();
		assertGeneric(type, new TypeFacade<>(type, new TypeFacade<>(Optional.class)));
	}

	@Test
	public void testGenericConstructorAssertsNotClass() {
		assertThrows(AssertionError.class, () -> new TypeFacade<>(Object.class, null));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGenericConstructorAssertsErasureIsClass() {
		var mockType = mock(Type.class);
		var mockFacade = mock(TypeFacade.class);
		when(mockFacade.deref()).thenReturn(mockType);
		assertThrows(AssertionError.class, () -> new TypeFacade<>(mockType, mockFacade));
	}

}
