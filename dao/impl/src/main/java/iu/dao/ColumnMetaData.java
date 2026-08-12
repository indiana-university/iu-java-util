/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.dao;

import java.beans.PropertyDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;

import edu.iu.IuException;
import edu.iu.dao.SpaceForNull;
import edu.iu.dao.SqlColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Id;

/**
 * Holds reflection- and annotation-derived metadata for a single mapped bean
 * property of a JPA entity.
 *
 * <p>
 * A property is mapped in one of two exclusive modes:
 * </p>
 * <dl>
 * <dt>{@link Column} mode</dt>
 * <dd>The getter carries {@code @Column}. {@link #columnName} is set to the
 * annotation's {@code name()} value when non-blank, or to the
 * {@link DaoUtils#camelToSnakeUpper(String) UPPER_SNAKE_CASE} conversion of the
 * property name otherwise. {@link #sql} equals {@link #columnName},
 * {@link #selectAlias} is {@code null}, and {@link #table} is resolved from
 * {@code @Column.table()}.</dd>
 * <dt>{@link SqlColumn} mode</dt>
 * <dd>The getter carries {@code @SqlColumn} but not {@code @Column}.
 * {@link #columnName} and {@link #table} are {@code null}. {@link #sql} holds
 * the raw SQL expression from {@link SqlColumn#value()}, and
 * {@link #selectAlias} is set to the {@link DaoUtils#camelToSnakeUpper(String)
 * UPPER_SNAKE_CASE} form of the property name so it can be aliased in
 * {@code SELECT} lists.</dd>
 * </dl>
 */
class ColumnMetaData {

	/**
	 * The {@link PropertyDescriptor} from bean introspection that this metadata
	 * describes, or {@code null} when the column is mapped on a field that has no
	 * bean property.
	 */
	final PropertyDescriptor property;

	/**
	 * The field backing this column, or {@code null} when the property is computed
	 * rather than stored. Present for both field-mapped and property-mapped
	 * columns, since a property-mapped column may still need its field to write
	 * through.
	 */
	final Field field;

	/**
	 * The name this column is known by: the bean property name derived from the
	 * getter, e.g. {@code "myColumn"} for {@code getMyColumn()}, or the field name
	 * for a column mapped on a field alone.
	 */
	final String propertyName;

	/**
	 * The declared Java type of the mapped property or field.
	 */
	final Class<?> javaType;

	/**
	 * The physical database column name used in {@code INSERT}, {@code UPDATE}, and
	 * {@code WHERE} clauses. Derived from {@link Column#name()} when non-blank;
	 * otherwise {@link DaoUtils#camelToSnakeUpper(String)} of
	 * {@link #propertyName}. {@code null} for {@link SqlColumn} properties.
	 */
	final String columnName;

	/**
	 * The SQL fragment used to reference this property in a {@code SELECT} list.
	 * For {@link Column}-mapped properties this equals {@link #columnName}. For
	 * {@link SqlColumn} properties this is the raw expression from
	 * {@link SqlColumn#value()}.
	 */
	final String sql;

	/**
	 * The {@code AS} alias emitted in a {@code SELECT} list for
	 * {@link SqlColumn}-mapped properties. Set to the
	 * {@link DaoUtils#camelToSnakeUpper(String) UPPER_SNAKE_CASE} form of
	 * {@link #propertyName}. {@code null} for {@link Column}-mapped properties,
	 * which are identified by their physical column name.
	 */
	final String selectAlias;

	/**
	 * The {@link Id} annotation on the getter, or {@code null} when the property is
	 * not part of the entity's primary key.
	 */
	final Id id;

	/**
	 * The {@link Column} annotation on the getter, or {@code null} for
	 * {@link SqlColumn} properties.
	 */
	final Column column;

	/**
	 * The {@link SqlColumn} annotation on the getter, or {@code null} for
	 * {@link Column}-mapped properties.
	 */
	final SqlColumn sqlColumn;

	/**
	 * {@code true} when {@code null} bound values for this property should be
	 * replaced with a single space character. Inherits the entity-level flag from
	 * {@link EntityMetaData#spaceForNull}, or is set independently when the getter
	 * carries {@link SpaceForNull}.
	 */
	final boolean spaceForNull;

	/**
	 * The {@link TableMetaData} that owns this column's physical table, resolved
	 * from {@link Column#table()}. {@code null} for {@link SqlColumn} properties,
	 * which have no physical table binding.
	 */
	final TableMetaData table;

	/**
	 * The resolved SQL type
	 */
	final Class<?> sqlType;

	/**
	 * Reads this column's value from an entity, through the getter when there is
	 * one and through the field otherwise.
	 */
	final MethodHandle getter;

	/**
	 * Constructs metadata for a single mapped column.
	 *
	 * <p>
	 * The column must carry either {@link Column} or {@link SqlColumn}, on the
	 * getter or on the field; the getter is consulted first, so a property
	 * annotated in both places is described by its getter. When {@link Column} is
	 * present it takes precedence and the {@link SqlColumn} branch is not entered.
	 * </p>
	 *
	 * <p>
	 * Access is resolved independently of the annotation: values are read through
	 * the getter when one exists and through the field otherwise.
	 * </p>
	 *
	 * @param entity   the owning entity metadata, used to resolve the
	 *                 {@link TableMetaData} and inherit the entity-level
	 *                 {@link SpaceForNull} flag
	 * @param property the bean property to describe, or {@code null} for a column
	 *                 mapped on a field with no bean property
	 * @param field    the field backing the column; must be non-{@code null} when
	 *                 {@code property} is {@code null}
	 */
	ColumnMetaData(EntityMetaData entity, PropertyDescriptor property, Field field) {
		this.property = property;
		this.field = field;

		final var readMethod = property == null ? null : property.getReadMethod();

		this.propertyName = property == null ? field.getName() : property.getName();
		this.javaType = property == null ? field.getType() : property.getPropertyType();

		this.id = DaoUtils.getAnnotation(Id.class, readMethod, field);
		this.column = DaoUtils.getAnnotation(Column.class, readMethod, field);
		this.sqlColumn = DaoUtils.getAnnotation(SqlColumn.class, readMethod, field);
		this.spaceForNull = entity.spaceForNull //
				|| DaoUtils.getAnnotation(SpaceForNull.class, readMethod, field) != null;

		if (column == null) {
			this.table = null;
			this.columnName = null;
			this.sql = sqlColumn.value();
			this.selectAlias = DaoUtils.camelToSnakeUpper(propertyName);
		} else {
			this.table = entity.resolveTable(column.table());
			this.columnName = DaoUtils.hasValue(column.name()) ? column.name()
					: DaoUtils.camelToSnakeUpper(propertyName);
			this.sql = this.columnName;
			this.selectAlias = null;
		}

		this.sqlType = DaoUtils.resolveSqlType(javaType, column);

		if (readMethod == null)
			this.getter = IuException
					.unchecked(() -> MethodHandles.lookup().unreflectGetter(DaoUtils.accessible(field)));
		else {
			readMethod.setAccessible(true);
			this.getter = IuException.unchecked(() -> MethodHandles.lookup().unreflect(readMethod));
		}
	}

	/**
	 * Returns the SQL reference for this column using the table's own alias.
	 *
	 * <p>
	 * Equivalent to {@link #reference(String) reference(null)}.
	 * </p>
	 *
	 * @return SQL reference string
	 */
	String reference() {
		return reference(null);
	}

	/**
	 * Returns the SQL reference for this column, optionally overriding the table
	 * alias.
	 *
	 * <p>
	 * For {@link Column}-mapped properties the result is {@code alias.columnName},
	 * where {@code alias} is {@code aliasOverride} when non-{@code null}, or the
	 * table's own {@link TableMetaData#alias} otherwise. For {@link SqlColumn}
	 * properties (where {@link #table} is {@code null}) no alias prefix is emitted;
	 * only {@link #sql} is returned regardless of {@code aliasOverride}.
	 * </p>
	 *
	 * @param aliasOverride table alias to use instead of the table's own alias, or
	 *                      {@code null} to use the default
	 * @return SQL reference string
	 */
	String reference(String aliasOverride) {
		final var sb = new StringBuilder();
		if (table != null)
			sb.append(aliasOverride == null ? table.alias : aliasOverride).append('.');
		sb.append(sql);
		return sb.toString();
	}

	/**
	 * Returns {@code true} when the property is mapped via {@link Column} (i.e. has
	 * a physical column name and table binding).
	 *
	 * @return {@code true} for {@link Column}-mapped properties
	 */
	boolean isMappedColumn() {
		return column != null;
	}

	/**
	 * Returns {@code true} when the property is mapped via {@link Column} and
	 * belongs to the entity's primary table.
	 *
	 * @return {@code true} for primary-table {@link Column}-mapped properties
	 */
	boolean isPrimaryColumn() {
		return isMappedColumn() && table.primary;
	}

	/**
	 * Normalizes a bind-parameter value before it is passed to a JDBC statement.
	 *
	 * <ul>
	 * <li>Returns a single space ({@code " "}) when {@code value} is {@code null}
	 * and {@link #spaceForNull} is {@code true}.</li>
	 * <li>Converts {@link java.time.Instant} to {@link java.sql.Timestamp} so that
	 * JDBC drivers receive a type they understand.</li>
	 * <li>Converts {@link Boolean} to {@code "Y"} or {@code "N"} when the column's
	 * {@link #sqlType} is {@link String}, which is how a flag declared as a
	 * character column is stored.</li>
	 * <li>Converts {@link java.util.Date} to {@link java.sql.Timestamp} when the
	 * column's {@link #sqlType} is {@link java.sql.Timestamp}, since a driver given
	 * a bare {@code java.util.Date} binds it without its time component.</li>
	 * <li>Returns {@code value} unchanged in all other cases.</li>
	 * </ul>
	 *
	 * <p>
	 * Both conversions are driven by {@link #sqlType}, so they apply only where
	 * {@link Column#columnDefinition()} declares a type that calls for them. A
	 * {@code boolean} column left to its natural type is bound as a boolean, not as
	 * {@code "Y"}.
	 * </p>
	 *
	 * @param value raw property value; may be {@code null}
	 * @return normalized value suitable for a JDBC bind parameter
	 */
	Object normalizeArgument(Object value) {
		if (value == null && spaceForNull)
			return " ";
		if (value instanceof Instant instant)
			return Timestamp.from(instant);
		if (sqlType == String.class //
				&& value instanceof Boolean flag)
			return flag ? "Y" : "N";
		// java.sql.Timestamp is itself a java.util.Date, so only a value that is not
		// already one needs converting.
		if (sqlType == Timestamp.class //
				&& !(value instanceof Timestamp) //
				&& value instanceof java.util.Date date)
			return new Timestamp(date.getTime());
		return value;
	}

	/**
	 * Converts a value read from the database back to the type this column's member
	 * expects, reversing {@link #normalizeArgument(Object)}.
	 *
	 * <ul>
	 * <li>Returns {@code null} for a single space ({@code " "}) when
	 * {@link #spaceForNull} is {@code true}.</li>
	 * <li>Converts {@link java.sql.Timestamp} to {@link java.time.Instant} for a
	 * member declared as an {@code Instant}.</li>
	 * <li>Converts {@code "Y"} to {@code true} and any other text to {@code false}
	 * for a member declared as a boolean, which is how a flag stored in a character
	 * column reads back.</li>
	 * <li>Narrows anything else to {@link #javaType} as
	 * {@link DaoUtils#coerce(Class, Object)} does, so that a value read as the
	 * column's declared SQL type still reaches the member's own type — a
	 * {@code NUMBER} column read as the driver's {@link java.math.BigDecimal}
	 * reaching an {@link Integer} member, or a {@code CHAR} column read as text
	 * reaching a {@code char} one.</li>
	 * <li>Returns {@code value} unchanged whenever it already is an instance of
	 * {@link #javaType}.</li>
	 * </ul>
	 *
	 * <p>
	 * The value must have been read as {@link #sqlType}, the type the column
	 * actually holds, rather than as {@link #javaType}. Reading a character column
	 * as a boolean would have parsed {@code "Y"} as {@code false} long before this
	 * had anything to reverse.
	 * </p>
	 *
	 * @param value value read from the database; may be {@code null}
	 * @return value converted to {@link #javaType}
	 */
	Object normalizeResult(Object value) {
		if (spaceForNull //
				&& " ".equals(value))
			return null;

		// A primitive javaType is never an instance of its own boxed value, so this
		// deliberately does not short-circuit the conversions below for primitives.
		if (value == null //
				|| javaType.isInstance(value))
			return value;

		if (javaType == Instant.class //
				&& value instanceof Timestamp timestamp)
			return timestamp.toInstant();

		if ((javaType == Boolean.class || javaType == boolean.class) //
				&& value instanceof String flag)
			return "Y".equalsIgnoreCase(flag);

		// Reading as the declared SQL type leaves whatever it produced still to be
		// narrowed to the member's own type: a NUMBER column read as the driver's
		// BigDecimal has to reach an Integer member, and a CHAR column read as text
		// has to reach a char one.
		return DaoUtils.coerce(javaType, value);
	}

}
