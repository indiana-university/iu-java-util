package iu.dao;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.dao.SqlJoinType;
import jakarta.persistence.Column;
import jakarta.persistence.Transient;

/**
 * Internal utility methods used across the DAO implementation layer.
 */
final class DaoUtils {

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS",
			Locale.ROOT);

	/**
	 * Wraps a string value in SQL single quotes, escaping any embedded single
	 * quotes by doubling them.
	 *
	 * <p>
	 * Example: {@code singleQuoted("it's")} → {@code "'it''s'"}.
	 * </p>
	 *
	 * @param value string to quote; must not be {@code null}
	 * @return SQL single-quoted string literal
	 */
	static String singleQuoted(String value) {
		// Defence-in-depth: reject null bytes and backslashes that can
		// bypass quote-doubling on non-ANSI-mode MySQL or multi-byte charsets.
		if (value.indexOf('\0') >= 0 || value.indexOf('\u001a') >= 0)
			throw new IllegalArgumentException("String literal contains characters unsafe for SQL embedding");
		return "'" + value.replace("'", "''") + "'";
	}

	/**
	 * Formats a {@link java.util.Date} value as a SQL {@code TIMESTAMP} literal.
	 *
	 * <p>
	 * The returned string has the form {@code TIMESTAMP 'yyyy-MM-dd HH:mm:ss.SSS'}
	 * using {@link Locale#ROOT} so that the output is locale-independent.
	 * </p>
	 *
	 * @param date date/time value; must not be {@code null}
	 * @return SQL {@code TIMESTAMP} literal string
	 */
	static String literalFromDate(java.util.Date date) {
		synchronized (TIMESTAMP_FORMAT) {
			return "TIMESTAMP '" + TIMESTAMP_FORMAT.format(date.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime())
					+ "'";
		}
	}

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
	 * Copies one or more ordered sequences of values into a stable List suitable
	 * for use as a hash key.
	 *
	 * @param values zero or more iterables whose contents form the fingerprint
	 * @return a flattened list of all iterated values
	 */
	static List<?> getFingerprint(Iterable<?>... values) {
		final var fingerprint = new ArrayList<>();
		if (values != null)
			for (final var iterable : values)
				if (iterable != null)
					for (final var value : iterable)
						fingerprint.add(value);
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
	 * Returns all readable bean properties for an entity type and its full
	 * superclass hierarchy (excluding {@link Object}).
	 *
	 * <p>
	 * Properties are collected from the top of the hierarchy downward so that
	 * subclass overrides shadow superclass definitions. The {@code class} property
	 * is always excluded. Only properties with a getter are included.
	 * </p>
	 *
	 * <p>
	 * An interface has no superclass, so its hierarchy is the interface alone;
	 * inherited properties are still reported, because bean introspection walks an
	 * interface's extended interfaces itself.
	 * </p>
	 *
	 * @param entityClass entity class or interface; must not be {@code null}
	 * @return ordered list of readable property descriptors
	 */
	static Iterable<PropertyDescriptor> getAllBeanProperties(Class<?> entityClass) {
		final var properties = new LinkedHashMap<String, PropertyDescriptor>();

		final Deque<Class<?>> hierarchy = new ArrayDeque<>();
		for (var current = entityClass; //
				current != null && current != Object.class; //
				current = current.getSuperclass())
			hierarchy.push(current);

		IuException.unchecked(() -> {
			for (final var type : hierarchy)
				for (final var property : Introspector.getBeanInfo(type).getPropertyDescriptors())
					if (!"class".equals(property.getName()) //
							&& property.getReadMethod() != null)
						properties.put(property.getName(), property);
		});

		return Collections.unmodifiableCollection(properties.values());
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
	 * @param bean   bean instance; must not be {@code null}
	 * @param column annotated property metadata
	 * @return the property value, possibly {@code null}
	 * @throws IllegalStateException if the getter throws or is inaccessible
	 */
	static Object getPropertyValue(Object bean, ColumnMetaData column) {
		try {
			return column.getter.invoke(bean);
		} catch (Throwable e) {
			throw new IllegalStateException("Failed to read " + column.propertyName + " from " + bean.getClass(), e);
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
	 * @param javaType declared type of the mapped property or field
	 * @param column   column
	 * @return Java type to use for the SQL column
	 */
	static Class<?> resolveSqlType(Class<?> javaType, Column column) {
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
		return autobox(javaType);
	}

	/**
	 * Returns every instance field declared by an entity type and its superclasses.
	 *
	 * <p>
	 * Collected from the top of the hierarchy downward, so that a subclass field
	 * shadows one of the same name it hides. Static fields are constants rather than
	 * state, and synthetic fields are compiler bookkeeping, so neither is included.
	 * An interface declares no instance fields at all.
	 * </p>
	 *
	 * @param entityClass entity class or interface; must not be {@code null}
	 * @return ordered instance fields, superclass declarations first
	 */
	static Iterable<Field> getAllDeclaredFields(Class<?> entityClass) {
		final var fields = new LinkedHashMap<String, Field>();

		final Deque<Class<?>> hierarchy = new ArrayDeque<>();
		for (var current = entityClass; //
				current != null && current != Object.class; //
				current = current.getSuperclass())
			hierarchy.push(current);

		for (final var type : hierarchy)
			for (final var field : type.getDeclaredFields())
				if (!Modifier.isStatic(field.getModifiers()) //
						&& !field.isSynthetic())
					fields.put(field.getName(), field);

		return Collections.unmodifiableCollection(fields.values());
	}

	/**
	 * Suppresses access checks on a field so that it can be read and written
	 * regardless of its declared visibility.
	 *
	 * <p>
	 * A field-mapped column is normally private with no accessor, which is the point
	 * of mapping it directly; reaching it requires the same suppression a JPA
	 * provider performs, and the same {@code opens} directive from a modular
	 * application.
	 * </p>
	 *
	 * @param field field to make accessible
	 * @return the same field
	 */
	static Field accessible(Field field) {
		field.setAccessible(true);
		return field;
	}

	/**
	 * Finds the field that backs a bean property.
	 *
	 * @param entityClass  entity class or interface to search; must not be
	 *                     {@code null}
	 * @param propertyName property name to match
	 * @return the backing field, or {@code null} when the property has none
	 */
	static Field getPropertyField(Class<?> entityClass, String propertyName) {
		for (final var field : getAllDeclaredFields(entityClass))
			if (field.getName().equals(propertyName))
				return field;
		return null;
	}

	/**
	 * Reads an annotation from the first of several elements that carries it.
	 *
	 * <p>
	 * Lets a mapping be declared on a getter or on the field behind it, resolved in
	 * the order the caller considers authoritative. {@code null} elements are
	 * skipped, so a caller need not know which of them exist.
	 * </p>
	 *
	 * @param <A>            annotation type
	 * @param annotationType annotation to look for
	 * @param elements       elements to search, in precedence order; entries may be
	 *                       {@code null}
	 * @return the annotation from the first element carrying it, or {@code null}
	 */
	@SafeVarargs
	static <A extends Annotation> A getAnnotation(Class<A> annotationType, AnnotatedElement... elements) {
		for (final var element : elements)
			if (element != null) {
				final var annotation = element.getAnnotation(annotationType);
				if (annotation != null)
					return annotation;
			}
		return null;
	}

	/**
	 * Reads a mapping annotation from a property, accepting it on either the getter
	 * or the backing field.
	 *
	 * <p>
	 * The getter is consulted first, so that a property whose field and getter are
	 * both annotated is described by the one nearer the accessor this DAO reads
	 * values through. Annotating the field is the more common style: it keeps the
	 * mapping beside the state it describes and leaves plain accessors uncluttered.
	 * </p>
	 *
	 * @param <A>            annotation type
	 * @param entityClass    entity class or interface declaring the property
	 * @param property       property to read the annotation from; must have a getter
	 * @param annotationType annotation to look for
	 * @return the annotation from the getter, else from the backing field, else
	 *         {@code null}
	 */
	static <A extends Annotation> A getPropertyAnnotation(Class<?> entityClass, PropertyDescriptor property,
			Class<A> annotationType) {
		return getAnnotation(annotationType, property.getReadMethod(),
				getPropertyField(entityClass, property.getName()));
	}

	/**
	 * Determines if {@link Transient} is present on a property's getter or backing
	 * field.
	 *
	 * @param entityClass entity class or interface declaring the property
	 * @param property    {@link PropertyDescriptor}
	 * @return true if the property has no read method, or {@link Transient} is
	 *         present on its getter or backing field
	 */
	static boolean isTransient(Class<?> entityClass, PropertyDescriptor property) {
		return property.getReadMethod() == null //
				|| getPropertyAnnotation(entityClass, property, Transient.class) != null;
	}

	/**
	 * Returns the SQL {@code JOIN} keyword for the given join type.
	 *
	 * @param joinType join type; must not be {@code null}
	 * @return SQL join keyword string (e.g. {@code "JOIN"},
	 *         {@code "LEFT OUTER JOIN"})
	 */
	static String joinKeyword(SqlJoinType.Type joinType) {
		return switch (joinType) {
		case LEFT -> "LEFT OUTER JOIN";
		case RIGHT -> "RIGHT OUTER JOIN";
		case FULL -> "FULL OUTER JOIN";
		case INNER -> "JOIN";
		};
	}

	/**
	 * Builds a {@code WHERE} clause from an ordered sequence of SQL predicates.
	 *
	 * <p>
	 * {@code null} elements in {@code criteria} are silently skipped. Returns an
	 * empty string when all predicates are {@code null} or the iterable is empty.
	 * </p>
	 *
	 * @param criteria SQL predicate strings; {@code null} elements are skipped
	 * @return {@code "\nWHERE pred1\n  AND pred2 ..."}, or {@code ""} if empty
	 */
	static String buildWhere(Iterable<String> criteria) {
		final var i = criteria.iterator();
		if (!i.hasNext())
			return "";
		final var sb = new StringBuilder();
		while (i.hasNext()) {
			final var crit = i.next();
			if (crit != null) {
				if (sb.isEmpty())
					sb.append("\nWHERE ");
				else
					sb.append("\n  AND ");
				sb.append(crit);
			}
		}
		return sb.toString();
	}

	/**
	 * Appends an {@code ORDER BY} clause to an existing SQL {@link StringBuilder}.
	 *
	 * <p>
	 * Does nothing when {@code order} is empty.
	 * </p>
	 *
	 * @param sb    target {@link StringBuilder} containing the SQL built so far
	 * @param order order expressions to append; no-op when empty
	 */
	static void appendOrderBy(StringBuilder sb, Iterable<String> order) {
		boolean first = true;
		if (order != null)
			for (final var item : order) {
				if (first) {
					sb.append("\nORDER BY ");
					first = false;
				} else {
					sb.append(", ");
				}
				sb.append(item);
			}
	}

	/**
	 * Builds a lazy iterable of {@code alias.columnName = placeholder} predicates
	 * for each ID column, for use in a {@code WHERE} clause with a table alias.
	 *
	 * @param alias       table alias prefix for the column reference
	 * @param idColumns   ID column metadata
	 * @param placeholder SQL parameter placeholder (e.g. {@code "?"})
	 * @return lazy iterable of SQL equality predicates
	 */
	static Iterable<String> idCriteria(String alias, Iterable<ColumnMetaData> idColumns, String placeholder) {
		return IuIterable.map(idColumns, idColumn -> alias + "." + idColumn.columnName + " = " + placeholder);
	}

	/**
	 * Builds a lazy iterable of {@code columnName = placeholder} predicates for
	 * each ID column (no alias prefix), for use in an {@code UPDATE … WHERE}
	 * clause.
	 *
	 * @param idColumns   ID column metadata
	 * @param placeholder SQL parameter placeholder (e.g. {@code "?"})
	 * @return lazy iterable of SQL equality predicates
	 */
	static Iterable<String> idCriteria(Iterable<ColumnMetaData> idColumns, String placeholder) {
		return IuIterable.map(idColumns, idColumn -> idColumn.columnName + " = " + placeholder);
	}

	/**
	 * Builds a {@code leftHandSide IN (v1, v2, ...)} SQL expression.
	 *
	 * @param leftHandSide  left-hand side of the {@code IN} expression (e.g.
	 *                      {@code "a.COLUMN_NAME"})
	 * @param matchCriteria values to include in the {@code IN} list
	 * @return the complete {@code IN} expression as a string
	 */
	static String getInCriteria(String leftHandSide, Iterable<String> matchCriteria) {
		return leftHandSide + " IN (" + String.join(", ", matchCriteria) + ")";
	}

	/**
	 * Appends correlated {@code subAlias.col = outerAlias.col} predicates for each
	 * column name, joined by {@code AND}.
	 *
	 * <p>
	 * When {@code idColumnNames} is empty the buffer is unchanged and {@code false}
	 * is returned.
	 * </p>
	 *
	 * @param sb            target {@link StringBuilder}
	 * @param outerAlias    alias of the outer query's table reference
	 * @param subAlias      alias of the correlated subquery's table reference
	 * @param idColumnNames column names to correlate
	 * @return {@code true} if at least one predicate was appended
	 */
	static boolean appendCorrelation(StringBuilder sb, String outerAlias, String subAlias,
			Iterable<String> idColumnNames) {
		boolean added = false;
		for (final var idColumnName : idColumnNames) {
			if (added)
				sb.append("\n         AND ");
			sb.append(subAlias).append('.').append(idColumnName).append(" = ").append(outerAlias).append('.')
					.append(idColumnName);
			added = true;
		}
		return added;
	}

	private DaoUtils() {
	}

}
