package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class TypeFactoryTest {

	private void assertErased(Class<?> erasedClass, IuType<?> shouldBeErased) {
		assertSame(erasedClass, shouldBeErased.deref());
		assertEquals(IuReferenceKind.ERASURE, shouldBeErased.reference().kind());
		assertSame(shouldBeErased, ((IuType<?>) shouldBeErased.reference().referrer()).erase());
		assertSame(shouldBeErased, ((IuType<?>) shouldBeErased.reference().referent()));
	}

	@Test
	public void testSameSame() {
		assertSame(TypeFactory.resolveType(Object.class), TypeFactory.resolveType(Object.class));
	}

	@Test
	public void testErasesRawTypeFromParameterizedType() throws NoSuchMethodException {
		interface HasAMethodWithParameterizedReturnType {
			Optional<Object> methodWithParameterizedReturnType();
		}
		var type = HasAMethodWithParameterizedReturnType.class.getDeclaredMethod("methodWithParameterizedReturnType")
				.getGenericReturnType();
		var facade = TypeFactory.resolveType(type);
		assertErased(Optional.class, facade.erase());
	}

	@Test
	public void testErasesUpperBoundsFromBoundedWilcardType() throws NoSuchMethodException {
		interface HasAMethodWithBoundedWildCardType {
			Optional<? extends Number> methodWithBoundedWildcardType();
		}
		var type = ((ParameterizedType) HasAMethodWithBoundedWildCardType.class
				.getDeclaredMethod("methodWithBoundedWildcardType").getGenericReturnType()).getActualTypeArguments()[0];
		var facade = TypeFactory.resolveType(type);
		assertErased(Number.class, facade.erase());
	}

	@Test
	public void testErasesUpperBoundFromUnboundedWilcardType() throws NoSuchMethodException {
		interface HasAMethodWithWildCardType {
			Optional<?> methodWithWildcardType();
		}
		var type = ((ParameterizedType) HasAMethodWithWildCardType.class.getDeclaredMethod("methodWithWildcardType")
				.getGenericReturnType()).getActualTypeArguments()[0];
		var facade = TypeFactory.resolveType(type);
		assertErased(Object.class, facade.erase());
	}

	@Test
	public void testErasesBoundFromTypeVariable() throws NoSuchMethodException {
		interface HasATypeVariable<N extends Number> {
		}
		var type = HasATypeVariable.class.getTypeParameters()[0];
		var facade = TypeFactory.resolveType(type);
		assertErased(Number.class, facade.erase());
	}

	@Test
	public void testErasesGenericArrayTypeToArrayOfErasedComponents() throws NoSuchMethodException {
		interface HasMethodThatReturnsAGenericArray<N extends Number> {
			N[] methodThatReturnsAGenericArray();
		}
		var type = ((GenericArrayType) HasMethodThatReturnsAGenericArray.class
				.getDeclaredMethod("methodThatReturnsAGenericArray").getGenericReturnType());
		var facade = TypeFactory.resolveType(type);
		assertErased(Number[].class, facade.erase());
	}

	@Test
	public void testErasesGenericMultiDimensionalArrayTypeToMultiDimensionalArrayOfErasedComponents()
			throws NoSuchMethodException {
		interface HasMethodThatReturnsAMultiDimensionalGenericArray<N extends Number> {
			N[][] methodThatReturnsAMultiDimensionalGenericArray();
		}
		var type = ((GenericArrayType) HasMethodThatReturnsAMultiDimensionalGenericArray.class
				.getDeclaredMethod("methodThatReturnsAMultiDimensionalGenericArray").getGenericReturnType());
		var facade = TypeFactory.resolveType(type);
		assertErased(Number[][].class, facade.erase());
	}

	@Test
	public void testIllegalArgumentUnlessErasesConformsToJls() throws NoSuchMethodException {
		var type = new Type() {
		};
		assertSame("Invalid generic type, must be ParameterizedType, GenericArrayType, TypeVariable, or WildcardType",
				assertThrows(IllegalArgumentException.class, () -> TypeFactory.resolveType(type)).getMessage());
	}

	@Test
	public void testHierarchy() {
		interface AnInterface<T> {
		}
		abstract class AnAbstractClass<A extends AnAbstractClass<A>> implements AnInterface<Number> {
		}
		class AClass extends AnAbstractClass<AClass> implements Serializable {
			private static final long serialVersionUID = 1L;
		}
		var type = TypeFactory.resolveRawClass(AClass.class);
		var hierarchy = type.hierarchy();
		assertEquals(4, hierarchy.size(), hierarchy::toString);
		assertSame(Serializable.class, hierarchy.get(0).erasedClass());
		assertSame(type, hierarchy.get(0).reference().referrer());
		assertSame(AnAbstractClass.class, hierarchy.get(1).erasedClass());
		assertSame(type, hierarchy.get(1).reference().referrer());
		assertSame(AnInterface.class, hierarchy.get(2).erasedClass());
		assertSame(hierarchy.get(1), hierarchy.get(2).reference().referrer());
		assertSame(Object.class, hierarchy.get(3).erasedClass());
		assertSame(hierarchy.get(1), hierarchy.get(3).reference().referrer());
	}

}
