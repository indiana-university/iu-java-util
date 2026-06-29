package iu.dao;

import java.beans.PropertyDescriptor;
import java.sql.Timestamp;
import java.time.Instant;

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
 * {@link #selectAlias} is set to the
 * {@link DaoUtils#camelToSnakeUpper(String) UPPER_SNAKE_CASE} form of the
 * property name so it can be aliased in {@code SELECT} lists.</dd>
 * </dl>
 */
class ColumnMetaData {

	/**
	 * The {@link PropertyDescriptor} from bean introspection that this metadata
	 * describes.
	 */
	final PropertyDescriptor property;

	/**
	 * The bean property name derived from the getter, e.g. {@code "myColumn"} for
	 * {@code getMyColumn()}.
	 */
	final String propertyName;

	/**
	 * The physical database column name used in {@code INSERT}, {@code UPDATE}, and
	 * {@code WHERE} clauses. Derived from {@link Column#name()} when non-blank;
	 * otherwise {@link DaoUtils#camelToSnakeUpper(String)} of {@link #propertyName}.
	 * {@code null} for {@link SqlColumn} properties.
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
	 * Constructs metadata for a single bean property.
	 *
	 * <p>
	 * The property's read method must carry either {@link Column} or
	 * {@link SqlColumn}. When {@link Column} is present it takes precedence and the
	 * {@link SqlColumn} branch is not entered.
	 * </p>
	 *
	 * @param property the {@link PropertyDescriptor} to describe; must have a
	 *                 readable getter annotated with {@link Column} or
	 *                 {@link SqlColumn}
	 * @param entity   the owning entity metadata, used to resolve the
	 *                 {@link TableMetaData} and inherit the entity-level
	 *                 {@link SpaceForNull} flag
	 */
	ColumnMetaData(PropertyDescriptor property, EntityMetaData entity) {
		this.property = property;
		this.propertyName = property.getName();
		final var readMethod = property.getReadMethod();
		this.id = readMethod.getAnnotation(Id.class);
		this.column = readMethod.getAnnotation(Column.class);
		this.sqlColumn = readMethod.getAnnotation(SqlColumn.class);
		this.spaceForNull = entity.spaceForNull || readMethod.isAnnotationPresent(SpaceForNull.class);
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
	 * For {@link Column}-mapped properties the result is
	 * {@code alias.columnName}, where {@code alias} is {@code aliasOverride} when
	 * non-{@code null}, or the table's own {@link TableMetaData#alias} otherwise.
	 * For {@link SqlColumn} properties (where {@link #table} is {@code null}) no
	 * alias prefix is emitted; only {@link #sql} is returned regardless of
	 * {@code aliasOverride}.
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
	 * Returns {@code true} when the property is mapped via {@link Column} (i.e.
	 * has a physical column name and table binding).
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
	 * <li>Returns {@code value} unchanged in all other cases.</li>
	 * </ul>
	 *
	 * @param value raw property value; may be {@code null}
	 * @return normalized value suitable for a JDBC bind parameter
	 */
	Object normalizeArgument(Object value) {
		if (value == null && spaceForNull)
			return " ";
		if (value instanceof Instant instant)
			return Timestamp.from(instant);
		return value;
	}

}
