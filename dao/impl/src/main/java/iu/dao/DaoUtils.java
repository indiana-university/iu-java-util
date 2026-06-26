package iu.dao;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import edu.iu.IuException;
import jakarta.persistence.Column;

/**
 * Internal utility methods used across the DAO implementation layer.
 */
final class DaoUtils {

	/**
	 * Converts a camelCase identifier to UPPER_SNAKE_CASE.
	 *
	 * <p>
	 * Example: {@code "myColumnName"} → {@code "MY_COLUMN_NAME"}.
	 * </p>
	 *
	 * @param name camelCase name; must not be {@code null}
	 * @return UPPER_SNAKE_CASE equivalent
	 */
	static String camelToSnakeUpper(String name) {
		final var sb = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			final char c = name.charAt(i);
			if (Character.isUpperCase(c) && i > 0)
				sb.append('_');
			sb.append(Character.toUpperCase(c));
		}
		return sb.toString();
	}

	/**
	 * Returns a schema-qualified name when a schema is present, otherwise returns
	 * the bare name.
	 *
	 * @param schema schema name; may be {@code null} or blank
	 * @param name   table or object name; must not be {@code null}
	 * @return {@code schema.name} if schema has a value, otherwise {@code name}
	 */
	static String qualifyName(String schema, String name) {
		return hasValue(schema) ? schema + "." + name : name;
	}

	/**
	 * Normalizes a database object name to a trimmed, uppercase string.
	 *
	 * <p>
	 * Returns an empty string for {@code null} input.
	 * </p>
	 *
	 * @param value raw name; may be {@code null}
	 * @return trimmed uppercase name, or {@code ""} if {@code value} is
	 *         {@code null}
	 */
	static String normalizeName(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	/**
	 * Returns {@code true} if the given string is non-{@code null} and contains at
	 * least one non-whitespace character.
	 *
	 * @param value string to test; may be {@code null}
	 * @return {@code true} when value is present
	 */
	static boolean hasValue(String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * Computes a stable 64-bit fingerprint over one or more ordered sequences of
	 * values.
	 *
	 * <p>
	 * The fingerprint is sensitive to both element identity (via
	 * {@link Object#hashCode()}) and element count within each iterable, making it
	 * suitable for cache-key generation. {@code null} elements contribute
	 * {@code 0}. {@code null} iterables contribute only their zero count.
	 * </p>
	 *
	 * @param values zero or more iterables whose contents form the fingerprint
	 * @return a 64-bit fingerprint value, {@code 0L} when {@code values} is
	 *         {@code null}
	 */
	static long getFingerprint(Iterable<?>... values) {
		long fingerprint = 0L;
		if (values != null)
			for (final var iterable : values) {
				int count = 0;
				if (iterable != null)
					for (final var value : iterable) {
						count++;
						fingerprint = 43L * fingerprint + (value == null ? 0L : value.hashCode());
					}
				fingerprint = 23L * fingerprint + count;
			}
		return fingerprint;
	}

	/**
	 * Generates a short alphabetic SQL table alias for a zero-based index.
	 *
	 * <p>
	 * Index {@code 0} → {@code "a"}, {@code 1} → {@code "b"}, …, {@code 25} →
	 * {@code "z"}, {@code 26} → {@code "ba"}, etc.
	 * </p>
	 *
	 * @param i zero-based alias index
	 * @return one or more lowercase letters identifying the alias
	 */
	static String getAlias(int i) {
		final var alias = new StringBuilder();
		do {
			alias.insert(0, (char) ('a' + i % 26));
			i /= 26;
		} while (i > 0);
		return alias.toString();
	}

	/**
	 * Returns all readable bean properties for an entity class and its full
	 * superclass hierarchy (excluding {@link Object}).
	 *
	 * <p>
	 * Properties are collected from the top of the hierarchy downward so that
	 * subclass overrides shadow superclass definitions. The {@code class} property
	 * is always excluded. Only properties with a getter are included.
	 * </p>
	 *
	 * @param entityClass entity class; must not be {@code null}
	 * @return ordered list of readable property descriptors
	 */
	static List<PropertyDescriptor> getAllBeanProperties(Class<?> entityClass) {
		final var properties = new LinkedHashMap<String, PropertyDescriptor>();

		final var hierarchy = new ArrayList<Class<?>>();
		for (var current = entityClass; //
				current != Object.class; //
				current = current.getSuperclass())
			hierarchy.add(0, current);

		IuException.unchecked(() -> {
			for (final var type : hierarchy)
				for (final var property : Introspector.getBeanInfo(type).getPropertyDescriptors())
					if (!"class".equals(property.getName()) //
							&& property.getReadMethod() != null)
						properties.put(property.getName(), property);
		});

		return new ArrayList<>(properties.values());
	}

	/**
	 * Searches {@code c} and each of its superclasses for the given annotation,
	 * returning the first match found.
	 *
	 * @param <A> annotation type
	 * @param c   class to search; must not be {@code null}
	 * @param ann annotation type to look for; must not be {@code null}
	 * @return the annotation instance, or {@code null} if not found in the
	 *         hierarchy
	 */
	static <A extends Annotation> A getAnnotationFromHierarchy(Class<?> c, Class<A> ann) {
		for (var clazz = c; clazz != null; clazz = clazz.getSuperclass())
			if (clazz.isAnnotationPresent(ann))
				return clazz.getAnnotation(ann);
		return null;
	}

	/**
	 * Reads the value of a bean property by invoking its getter reflectively.
	 *
	 * @param bean bean instance; must not be {@code null}
	 * @param prop descriptor for the property to read; must have a readable getter
	 * @return the property value, possibly {@code null}
	 * @throws IllegalStateException if the getter throws or is inaccessible
	 */
	static Object getPropertyValue(Object bean, PropertyDescriptor prop) {
		try {
			return IuException.checkedInvocation(() -> prop.getReadMethod().invoke(bean));
		} catch (Throwable e) {
			throw new IllegalStateException("Failed to read property " + prop.getName() + " from " + bean.getClass(),
					e);
		}
	}

	/**
	 * Returns the boxed wrapper type for a primitive class, or the class itself for
	 * non-primitive types.
	 *
	 * <p>
	 * Supported primitives: {@code boolean}, {@code byte}, {@code short},
	 * {@code int}, {@code long}, {@code float}, {@code double}, {@code char}.
	 * </p>
	 *
	 * @param c class to autobox; must not be {@code null}
	 * @return corresponding wrapper type, or {@code c} unchanged if not primitive
	 */
	static Class<?> autobox(Class<?> c) {
		if (!c.isPrimitive())
			return c;
		if (c == boolean.class)
			return Boolean.class;
		if (c == byte.class)
			return Byte.class;
		if (c == short.class)
			return Short.class;
		if (c == int.class)
			return Integer.class;
		if (c == long.class)
			return Long.class;
		if (c == float.class)
			return Float.class;
		if (c == double.class)
			return Double.class;
		if (c == char.class)
			return Character.class;
		return c;
	}

	/**
	 * Finds a named bean property on an entity class (or its superclasses).
	 *
	 * @param entityClass  class to search; must not be {@code null}
	 * @param propertyName exact property name to locate
	 * @return the matching {@link PropertyDescriptor}
	 * @throws NoSuchElementException if no readable property with that name exists
	 */
	static PropertyDescriptor findProperty(Class<?> entityClass, String propertyName) {
		for (final var property : getAllBeanProperties(entityClass))
			if (property.getName().equals(propertyName))
				return property;
		throw new NoSuchElementException("Unknown property " + propertyName + " on " + entityClass);
	}

	/**
	 * Determines the JDBC-compatible Java type to use for a named property when
	 * binding SQL parameters or reading result sets.
	 *
	 * <p>
	 * When the property's getter carries a {@link Column} annotation with a
	 * non-blank {@code columnDefinition}, the SQL type keyword is inspected:
	 * </p>
	 * <ul>
	 * <li>Contains {@code CHAR} → {@link String}</li>
	 * <li>Starts with {@code INT} → {@link Long}</li>
	 * <li>Starts with {@code NUMBER}, {@code NUMERIC}, or {@code DECIMAL} →
	 * {@link Number}</li>
	 * <li>Equals {@code DATE} → {@link java.sql.Date}</li>
	 * <li>Equals {@code TIME} → {@link java.sql.Time}</li>
	 * <li>Equals or contains {@code TIMESTAMP} or equals {@code DATETIME} →
	 * {@link Timestamp}</li>
	 * <li>Equals {@code CLOB} or {@code TEXT} → {@code char[]}</li>
	 * <li>Anything else → {@link Object}</li>
	 * </ul>
	 * <p>
	 * When no column definition is present, the property type is returned after
	 * autoboxing any primitive via {@link #autobox(Class)}.
	 * </p>
	 *
	 * @param entityClass entity class owning the property; must not be {@code null}
	 * @param prop        property name; must exist on the entity class
	 * @return Java type to use for the SQL column
	 * @throws NoSuchElementException if the property is not found
	 */
	static Class<?> getSqlType(Class<?> entityClass, String prop) {
		final var property = findProperty(entityClass, prop);
		final var column = property.getReadMethod().getAnnotation(Column.class);
		if (column != null //
				&& hasValue(column.columnDefinition())) {
			final var sqlType = column.columnDefinition().toUpperCase(Locale.ROOT);
			if (sqlType.contains("CHAR"))
				return String.class;
			if (sqlType.startsWith("INT"))
				return Long.class;
			if (sqlType.startsWith("NUMBER") //
					|| sqlType.startsWith("NUMERIC") //
					|| sqlType.startsWith("DECIMAL"))
				return Number.class;
			if (sqlType.equals("DATE"))
				return Date.class;
			if (sqlType.equals("TIME"))
				return java.sql.Time.class;
			if (sqlType.equals("DATETIME") //
					|| sqlType.contains("TIMESTAMP"))
				return Timestamp.class;
			if (sqlType.equals("CLOB") || sqlType.equals("TEXT"))
				return char[].class;
			return Object.class;
		}
		return autobox(property.getPropertyType());
	}

	/**
	 * Converts an {@link Iterable} to a {@link List}, returning an empty list for
	 * {@code null} input.
	 *
	 * @param <T>    element type
	 * @param values source iterable; may be {@code null}
	 * @return mutable list containing all elements in iteration order; never
	 *         {@code null}
	 */
	static <T> List<T> toList(Iterable<T> values) {
		if (values == null)
			return List.of();
		final var list = new ArrayList<T>();
		for (final var value : values)
			list.add(value);
		return list;
	}

	/**
	 * Appends all non-{@code null} elements from {@code values} to {@code target},
	 * silently ignoring a {@code null} source iterable.
	 *
	 * @param <T>    element type
	 * @param target destination list; must not be {@code null}
	 * @param values source iterable; may be {@code null}
	 */
	static <T> void appendAll(List<T> target, Iterable<T> values) {
		if (values == null)
			return;
		for (final var value : values)
			if (value != null)
				target.add(value);
	}

	private DaoUtils() {
	}

}
