package iu.type;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import edu.iu.type.IuType;

final class TypeFactory {

	record HierarchyElement(Type type, Class<?> referrer) {
	}

	static class ClassMetadata {
		final List<HierarchyElement> hierarchy;
		final Map<ExecutableKey, Constructor<?>> constructors;
		final Map<String, Field> fields;
		final Map<ExecutableKey, Method> methods;

		private ClassMetadata(Class<?> rawClass) {
			constructors = new LinkedHashMap<>();
			for (var constructor : rawClass.getDeclaredConstructors())
				constructors.put(ExecutableKey.of(constructor), constructor);

			hierarchy = new ArrayList<>();
			methods = new LinkedHashMap<>();
			fields = new LinkedHashMap<>();
			Deque<HierarchyElement> reverse = new ArrayDeque<>();
			Deque<HierarchyElement> todo = new ArrayDeque<>();
			todo.push(new HierarchyElement(rawClass, null));
			while (!todo.isEmpty()) {
				var hierarchyElement = todo.pop();
				if (hierarchyElement.referrer != null)
					hierarchy.add(hierarchyElement);

				var erasedClass = getErasedClass(hierarchyElement.type);
				for (var field : erasedClass.getDeclaredFields()) {
					var fieldName = field.getName();
					if (!fields.containsKey(fieldName))
						fields.put(fieldName, field);
				}

				var superType = erasedClass.getGenericSuperclass();
				if (superType != null)
					reverse.push(new HierarchyElement(superType, erasedClass));

				for (var interfaceToCheck : erasedClass.getGenericInterfaces())
					reverse.push(new HierarchyElement(interfaceToCheck, erasedClass));

				while (!reverse.isEmpty())
					todo.push(reverse.pop());
			}
		}
	}

	private static final TypeFacade<Object> OBJECT = TypeFacade.builder(Object.class).build();
	private static final Map<Class<?>, TypeFacade<?>> RAW_TYPES = new WeakHashMap<>();
	private static final Map<Class<?>, ClassMetadata> CLASS_METADATA = new WeakHashMap<>();

	private static ClassMetadata getClassMetadata(Class<?> rawClass) {
		ClassMetadata classMetadata = CLASS_METADATA.get(rawClass);
		if (classMetadata == null) {
			classMetadata = new ClassMetadata(rawClass);
			synchronized (CLASS_METADATA) {
				CLASS_METADATA.put(rawClass, classMetadata);
			}
		}
		return classMetadata;
	}

	static Class<?> getErasedClass(Type type) {
		if (type instanceof Class)
			return (Class<?>) type;

		record Node(Type type, int array) {
		}

		Deque<Node> todo = new ArrayDeque<>();
		var node = new Node(type, 0);
		todo.push(node);

		while (!todo.isEmpty()) {
			node = todo.pop();

			// JLS 21 4.6: The erasure of an array type T[] is |T|[].
			if (node.type instanceof GenericArrayType)
				todo.push(new Node(((GenericArrayType) node.type).getGenericComponentType(), node.array + 1));

			// JLS 21 4.6: The erasure of a parameterized type (§4.5) G<T1,...,Tn> is |G|.
			else if (node.type instanceof ParameterizedType)
				todo.push(new Node(((ParameterizedType) node.type).getRawType(), node.array));

			// JLS 21 4.6: The erasure of a type variable (§4.4) is the erasure of its
			// leftmost bound
			else if (node.type instanceof TypeVariable)
				/*
				 * This method implements the logic for a **reference** type variable. That is,
				 * The type variable at the end of a reference chain. For example, The type
				 * variable E in the parameterized type ArrayList<E> refers to the variable E in
				 * List<E>, which in turn refers to the variable T in Iterable<T>. If the
				 * introspected instance is an ArrayList, but the type of the element being
				 * introspected is Iterable<?> and the purpose of introspection is to determine
				 * the item type for elements in the iteration, this method is only responsible
				 * for resolving the type variable E on ArrayList<String> to the class String,
				 * not for derefencing the two type variable references it takes to reach that
				 * reference variable.
				 */
				todo.push(new Node(((TypeVariable<?>) node.type).getBounds()[0], node.array));

			/*
			 * During capture conversion, a wildcard type arguments translates to a type
			 * variable with bounds equivalent to the upper bound of upper bound of the
			 * wildcard. So, may be erased to its left-most upper bound for determining the
			 * equivalent raw type of a type argument.
			 */
			// JLS 21 5.1.10:
			// If Ti is a wildcard type argument of the form ? extends Bi, then Si is a
			// fresh type variable whose upper bound is glb(Bi, Ui[A1:=S1,...,An:=Sn]) and
			// whose lower bound is the null type.
			// glb(V1,...,Vm) is defined as V1 & ... & Vm
			else if (node.type instanceof WildcardType)
				todo.push(new Node(((WildcardType) node.type).getUpperBounds()[0], node.array));

			else if (!(node.type instanceof Class))
				// java.lang.reflect.Type should be considered effectively sealed to the only
				// the types handled above. Custom implementations are not covered by JLS and
				// cannot be generated by the compiler.
				throw new IllegalArgumentException(
						"Invalid generic type, must be ParameterizedType, GenericArrayType, TypeVariable, or WildcardType");
		}

		var erasure = (Class<?>) node.type;
		for (var i = 0; i < node.array; i++)
			erasure = Array.newInstance(erasure, 0).getClass();
		return erasure;
	}

	@SuppressWarnings("unchecked")
	static <T> TypeFacade<T> resolveRawClass(Class<T> rawClass) {
		if (rawClass == Object.class)
			return (TypeFacade<T>) OBJECT;

		var resolved = RAW_TYPES.get(rawClass);

		if (resolved == null) {
			var metadata = getClassMetadata(rawClass);

			TypeFacade<? super T>[] hierarchy = new TypeFacade[metadata.hierarchy.size()];
			for (int i = metadata.hierarchy.size() - 1; i >= 0; i--) {
				var e = metadata.hierarchy.get(i);
				var referrer = e.referrer;
				if (referrer == rawClass)
					hierarchy[i] = (TypeFacade<? super T>) resolveType(e.type);
				else {
					var j = i;
					Type referrerType = null;
					while (j >= 0)
						if (getErasedClass(metadata.hierarchy.get(--j).type) == referrer)
							break;
					hierarchy[i] = (TypeFacade<? super T>) //
					resolveType(Objects.requireNonNull(referrerType)).referTo(e.type);
				}
			}
			resolved = TypeFacade.builder(rawClass).hierarchy(List.of(hierarchy)).build();

			synchronized (RAW_TYPES) {
				RAW_TYPES.put(rawClass, resolved);
			}
		}

		return (TypeFacade<T>) resolved;

	}

	static IuType<?> resolveType(Type type) {
		if (type instanceof Class)
			return resolveRawClass((Class<?>) type);
		else
			return TypeFacade.builder(type, resolveRawClass(getErasedClass(type))).build();
	}

	private TypeFactory() {
	}

//	@Override
//	@SuppressWarnings({ "unchecked", "rawtypes" })
//	public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
//			ExtensionContext extensionContext) throws Throwable {
//		try (var mockType = mockStatic(IuType.class)) {
//			var mockTypes = new HashMap<Class<?>, IuType<?>>();
//			Answer ofAnswer = a -> {
//				var t = a.getArgument(0);
//				
//				Class c;
//				if (t instanceof ParameterizedType)
//					c = (Class) ((ParameterizedType) t).getRawType();
//				else
//					c = (Class) t;
//				
//				var type = mockTypes.get(c);
//				if (type == null) {
//					type = mock(IuType.class);
//					when(type.name()).thenReturn(c.getName());
//					when(type.baseClass()).thenReturn(c);
//					if (c == boolean.class)
//						when(type.autoboxClass()).thenReturn((Class) Boolean.class);
//					else
//						when(type.autoboxClass()).thenReturn(c);
//					var constructor = mock(IuConstructor.class);
//					assertDoesNotThrow(() -> when(constructor.exec()).then(b -> {
//						try {
//							try {
//								return c.getDeclaredConstructor().newInstance();
//							} catch (NoSuchMethodException e) {
//								return c.getDeclaredConstructor(invocationContext.getTargetClass())
//										.newInstance(invocationContext.getTarget().get());
//							}
//						} catch (InvocationTargetException e) {
//							throw e.getCause();
//						}
//
//					}));
//					when(type.constructor()).thenReturn(constructor);
//					mockTypes.put(c, type);
//				}
//				return type;
//			};
//			mockType.when(() -> IuType.of(any(Class.class))).then(ofAnswer);
//			mockType.when(() -> IuType.of(any(Type.class))).then(ofAnswer);
//			invocation.proceed();
//		}
//}
}
