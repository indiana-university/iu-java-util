package edu.iu.type;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

import iu.type.TypeFactory;

/**
 * Facade interface for a generic type.
 * 
 * <p>
 * Provides uniform, optimized, access to the runtime metadata describing a Java
 * type available via the following APIs:
 * </p>
 * 
 * <ul>
 * <li><a href=
 * "https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/package-summary.html">Java
 * Reflection</a></li>
 * <li><a href=
 * "https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/beans/package-summary.html">Java
 * Beans</a></li>
 * <li><a href= "https://jakarta.ee/specifications/interceptors/2.1/">Jakarta
 * Interceptors 2.1</a></li>
 * <li><a href="https://jakarta.ee/specifications/annotations/2.1/">Jakarta
 * Annotations 2.1</a></li>
 * </ul>
 * 
 * @param <T> described generic type
 */
public interface IuType<T> extends IuNamedElement, IuParameterizedElement {

	/**
	 * Resolves a type introspection facade for a generic type.
	 * 
	 * @param type generic type
	 * @return type introspection facade
	 */
	static IuType<?> of(Type type) {
		return TypeFactory.resolve(type);
	}

	/**
	 * Resolves a type introspection facade for a class.
	 * 
	 * @param <T>  type
	 * @param type type
	 * @return type introspection facade
	 */
	@SuppressWarnings("unchecked")
	static <T> IuType<T> of(Class<?> type) {
		return (IuType<T>) of((Type) type);
	}

	/**
	 * Gets the reference used to obtain this type.
	 * 
	 * @return type reference
	 */
	IuTypeReference<T> getReference();

	/**
	 * Gets the generic type.
	 * 
	 * @return generic type
	 */
	Type deref();

	/**
	 * Gets the {@link IuReferenceKind#BASE base} facade describing the
	 * {@link Class} associated with the generic type.
	 * 
	 * <p>
	 * The {@link #deref()} of the base facade <em>must</em> return a {@link Class}.
	 * </p>
	 * 
	 * @return base facade
	 */
	IuType<T> base();

	/**
	 * Iterates the type hierarchy, from most specific to least specific.
	 * 
	 * <ol>
	 * <li>All {@link Class#getGenericInterfaces()}</li>
	 * <li>{@link Class#getGenericSuperclass()}</li>
	 * <li>Iterate {@link IuType#hierarchy()} until {@link Object} is reached</li>
	 * </ol>
	 * 
	 * <p>
	 * This type described by this facade is not included. {@link Object} is always
	 * the last element.
	 * </p>
	 * 
	 * @return enclosed types
	 */
	Iterable<IuType<?>> hierarchy();

	/**
	 * Refers to a type in the the described type's hierarchy.
	 * 
	 * <p>
	 * When the referent type declares type parameters, the resolved generic types
	 * associated with those parameters are described by the returned facade.
	 * </p>
	 * 
	 * @param referentType type to refer to
	 * @return referent facade
	 */
	IuType<?> referTo(Type referentType);

	/**
	 * Gets all types enclosed by this type.
	 * 
	 * @return enclosed types
	 */
	Set<IuType<?>> enclosedTypes();

	/**
	 * Gets all constructors defined by this type.
	 * 
	 * @return constructors
	 */
	Set<IuConstructor> constructors();

	/**
	 * Gets a constructor defined by this type.
	 * 
	 * @param parameterTypes parameter types
	 * @return constructor
	 */
	IuConstructor constructors(Type... parameterTypes);

	/**
	 * Gets a constructor declared by this type.
	 * 
	 * @param parameterTypes parameter types
	 * @return constructor
	 */
	IuConstructor constructor(IuType<?>... parameterTypes);

	/**
	 * Gets all fields defined by this type.
	 * 
	 * @return fields
	 */
	Map<String, IuField<T>> fields();

	/**
	 * Gets a field declared by this type.
	 * 
	 * @param name field name
	 * @return field
	 */
	IuField<?> field(String name);

	/**
	 * Gets all methods defined by this type.
	 * 
	 * @return methods
	 */
	Set<IuMethod> methods();

	/**
	 * Gets a method defined by this type.
	 * 
	 * @param name           method name
	 * @param parameterTypes parameter types
	 * @return method
	 */
	IuMethod methods(String name, Type... parameterTypes);

	/**
	 * Gets a method declared by this type.
	 * 
	 * @param name           method name
	 * @param parameterTypes parameter types
	 * @return method
	 */
	IuMethod method(String name, IuType<?>... parameterTypes);

	/**
	 * Get the resolved base class.
	 * 
	 * <p>
	 * Shorthand for {@link #base()}.{@link #deref()}
	 * </p>
	 * 
	 * @return base class
	 * @see #base()
	 */
	@SuppressWarnings("unchecked")
	default Class<T> baseClass() {
		return (Class<T>) base().deref();
	}

	/**
	 * Gets a type-enforced facade for a specific sub-type of the described type.
	 * 
	 * @param subclass subclass of the described type
	 * @param <S>      sub-type
	 * @return this
	 * @throws ClassCastException If the base type does not describe a subclass
	 */
	@SuppressWarnings("unchecked")
	default <S> IuType<? extends S> sub(Class<S> subclass) throws ClassCastException {
		baseClass().asSubclass(subclass);
		return (IuType<? extends S>) this;
	}

	/**
	 * Returns the <a href=
	 * "https://docs.oracle.com/javase/tutorial/java/data/autoboxing.html">autobox</a>
	 * equivalent
	 * 
	 * @return the object version related to a primitive type, or the class passed
	 *         in as-is if not primitive
	 */
	@SuppressWarnings("unchecked")
	default Class<T> autoboxClass() {
		var potentiallyPrimitive = baseClass();
		if (Boolean.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Boolean.class;
		else if (Character.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Character.class;
		else if (Byte.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Byte.class;
		else if (Short.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Short.class;
		else if (Integer.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Integer.class;
		else if (Long.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Long.class;
		else if (Float.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Float.class;
		else if (Double.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Double.class;
		else if (Void.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Void.class;
		else
			return potentiallyPrimitive;
	}

	/**
	 * Returns the default value for an object or primitive type.
	 * 
	 * @return The default value that would be assigned to a field of described
	 *         primitive type if declared without an initializer; null if the
	 *         described time is not primitive.
	 */
	@SuppressWarnings("unchecked")
	default T autoboxDefault() {
		var potentiallyPrimitive = baseClass();
		if (Boolean.TYPE.equals(potentiallyPrimitive))
			return (T) Boolean.FALSE;
		else if (Character.TYPE.equals(potentiallyPrimitive))
			return (T) Character.valueOf('\0');
		else if (Byte.TYPE.equals(potentiallyPrimitive))
			return (T) Byte.valueOf((byte) 0);
		else if (Short.TYPE.equals(potentiallyPrimitive))
			return (T) Short.valueOf((short) 0);
		else if (Integer.TYPE.equals(potentiallyPrimitive))
			return (T) Integer.valueOf(0);
		else if (Long.TYPE.equals(potentiallyPrimitive))
			return (T) Long.valueOf(0L);
		else if (Float.TYPE.equals(potentiallyPrimitive))
			return (T) Float.valueOf(0.0f);
		else if (Double.TYPE.equals(potentiallyPrimitive))
			return (T) Double.valueOf(0.0);
		else
			return null;
	}

}
