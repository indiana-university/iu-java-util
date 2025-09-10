package iu.type;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class TypeUtilsTest {

	@SuppressWarnings("unused")
	@Test
	public void testGetContext() throws Exception {
		class C {
			Object a;

			void b(Object c) {
			}
		}
		assertSame(C.class.getClassLoader(), TypeUtils.getContext(C.class));
		assertSame(C.class.getClassLoader(), TypeUtils.getContext(C.class.getDeclaredField("a")));
		assertSame(C.class.getClassLoader(), TypeUtils.getContext(C.class.getDeclaredMethod("b", Object.class)));
		assertSame(C.class.getClassLoader(),
				TypeUtils.getContext(C.class.getDeclaredMethod("b", Object.class).getParameters()[0]));

		final var ae = mock(AnnotatedElement.class);
		assertThrows(UnsupportedOperationException.class, () -> TypeUtils.getContext(ae));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testReferTo() {
		class A {
		}
		class B {
		}
		final var type = mock(Type.class);
		final var referrerType = mock(IuType.class);
		when(referrerType.erasedClass()).thenReturn(B.class);
		try (final var mockTypeFactory = mockStatic(TypeFactory.class)) {
			mockTypeFactory.when(() -> TypeFactory.getErasedClass(type)).thenReturn(A.class);
			assertThrows(IllegalArgumentException.class,
					() -> TypeUtils.referTo(referrerType, Set.of(), type));
		}
	}

}
