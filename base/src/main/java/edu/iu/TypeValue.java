package edu.iu;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes and caches values associated with reflective types.
 * 
 * <p>
 * This class is intended as a generic equivalent of {@link ClassValue}.
 * </p>
 *
 * <p>
 * Cached values are grouped by the most specific class loader represented by a
 * type, allowing a group to be released when its associated class is unloaded.
 * </p>
 *
 * @param <T> value type
 */
public abstract class TypeValue<T> {

	private final ClassValue<ConcurrentHashMap<Type, T>> classValue = new ClassValue<ConcurrentHashMap<Type, T>>() {
		@Override
		protected ConcurrentHashMap<Type, T> computeValue(Class<?> type) {
			return new ConcurrentHashMap<>();
		}
	};

	/**
	 * Creates a type-value cache.
	 */
	protected TypeValue() {
	}

	/**
	 * Computes the value to cache for a type.
	 *
	 * @param type reflective type
	 * @return value to cache
	 */
	protected abstract T computeValue(Type type);

	/**
	 * Gets the value associated with a reflective type, computing it when it is not
	 * already cached.
	 *
	 * @param type reflective type
	 * @return cached value
	 */
	public T get(Type type) {
		return classValue.get(mostSpecific(type)).computeIfAbsent(type, this::computeValue);
	}

	private Class<?> moreSpecific(Class<?> a, Class<?> b) {
		if (a == null)
			return b;
		if (b == null)
			return a;

		var aLoader = a.getClassLoader();
		var bLoader = b.getClassLoader();

		do {
			if (aLoader == bLoader)
				return a;
			aLoader = IuObject.convert(aLoader, ClassLoader::getParent);
		} while (aLoader != null);

		return b;
	}

	private Class<?> mostSpecific(Type type) {
		if (type instanceof Class c)
			return c;
		else if (type instanceof ParameterizedType pt) {
			var c = mostSpecific(pt.getOwnerType());
			for (final var arg : pt.getActualTypeArguments())
				c = moreSpecific(c, mostSpecific(arg));
			return c;
		} else if (type instanceof TypeVariable v) {
			Class<?> c = Object.class;
			for (final var bound : v.getBounds())
				c = moreSpecific(c, mostSpecific(bound));
			return c;
		} else if (type instanceof WildcardType w) {
			Class<?> c = Object.class;
			for (final var bound : w.getUpperBounds())
				c = moreSpecific(c, mostSpecific(bound));
			for (final var bound : w.getLowerBounds())
				c = moreSpecific(c, mostSpecific(bound));
			return c;
		} else if (type instanceof GenericArrayType ga) {
			return mostSpecific(ga.getGenericComponentType());
		} else if (type == null)
			return null;

		throw new IllegalArgumentException(type.toString());
	}

}
