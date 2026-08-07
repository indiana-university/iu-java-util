package iu.dao;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import edu.iu.IuCacheMap;
import edu.iu.IuIterable;
import edu.iu.dao.Distinct;
import edu.iu.dao.EffectiveDated;
import edu.iu.dao.Filtered;
import edu.iu.dao.IuSqlUnchangedException;
import edu.iu.dao.SpaceForNull;
import edu.iu.dao.SqlColumn;
import edu.iu.dao.SqlFilter;
import edu.iu.dao.SqlJoinType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.SecondaryTables;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Holds all reflection- and annotation-derived metadata for a JPA entity class.
 *
 * <p>
 * An instance is created once per entity class and cached by the SQL builder.
 * Construction eagerly resolves table names, column mappings, feature flags,
 * and secondary-table join conditions so that later SQL generation is
 * allocation-free.
 * </p>
 *
 * <p>
 * Table name resolution follows this priority order:
 * </p>
 * <ol>
 * <li>{@link Table#name()} if {@code @Table} is present and non-blank</li>
 * <li>{@link Entity#name()} if {@code @Entity} is present and non-blank</li>
 * <li>{@link Class#getSimpleName()} as a final fallback</li>
 * </ol>
 *
 * <p>
 * Only properties whose getter is annotated with {@link Column} or
 * {@link SqlColumn} (and not {@link jakarta.persistence.Transient}) are
 * included in the column maps. Properties in the primary table are further
 * partitioned into {@link #idColumns}, {@link #primaryColumns}, and
 * {@link #primaryNonIdColumns}.
 * </p>
 */
class EntityMetaData {

	private static final ClassValue<EntityMetaData> CACHE = new ClassValue<>() {
		@Override
		protected EntityMetaData computeValue(Class<?> type) {
			return new EntityMetaData(type);
		}
	};

	private static final Set<String> ALLOWED_COMPARATORS = Set.of("=", "<>", "!=", "<", ">", "<=", ">=", "LIKE",
			"NOT LIKE", "IS", "IS NOT");

	/**
	 * Returns the cached {@link EntityMetaData} for the given entity class,
	 * constructing it on the first call.
	 *
	 * <p>
	 * An entity read as an interface is a {@link Proxy}, whose class declares the
	 * interface's methods but carries none of its annotations. Such a class resolves
	 * to the first interface it implements, so that metadata is the same whether an
	 * entity is described by its type or by an instance read from a query.
	 * </p>
	 *
	 * @param entityClass entity class or interface to look up; must not be
	 *                    {@code null}
	 * @return singleton metadata instance for that class
	 * @throws NullPointerException if {@code entityClass} is {@code null}
	 */
	static EntityMetaData of(Class<?> entityClass) {
		Objects.requireNonNull(entityClass, "entityClass");
		if (Proxy.isProxyClass(entityClass))
			return CACHE.get(entityClass.getInterfaces()[0]);
		return CACHE.get(entityClass);
	}

	/**
	 * Registers a table under its short name, fully-qualified name, and alias so
	 * that all three forms can be used as lookup keys in
	 * {@link #tablesByReference}.
	 *
	 * @param tablesByReference mutable map being built during construction
	 * @param table             table to register
	 */
	private static void registerTable(Map<String, TableMetaData> tablesByReference, TableMetaData table) {
		tablesByReference.put(DaoUtils.normalizeName(table.name), table);
		tablesByReference.put(DaoUtils.normalizeName(table.fullName), table);
		tablesByReference.put(DaoUtils.normalizeName(table.alias), table);
	}

	/** The entity class this metadata was built from. */
	final Class<?> entityClass;

	/**
	 * Metadata for the primary table; always assigned the alias {@code "a"}.
	 */
	final TableMetaData primaryTable;

	/**
	 * Ordered list of secondary-table metadata; empty when no
	 * {@link SecondaryTable} or {@link SecondaryTables} annotation is present.
	 * Secondary tables receive aliases {@code "b"}, {@code "c"}, etc. in the order
	 * they are declared.
	 */
	final Iterable<TableMetaData> secondaryTables;

	/**
	 * Lookup map for all tables (primary and secondary) keyed by each of the
	 * table's short name, fully-qualified name, and alias — all normalized via
	 * {@link DaoUtils#normalizeName(String)}.
	 */
	final Map<String, TableMetaData> tablesByReference;

	/**
	 * All mapped columns keyed by {@link ColumnMetaData#propertyName} in
	 * bean-introspection order. Includes both primary-table and secondary-table
	 * columns, as well as raw-SQL {@link SqlColumn} properties.
	 */
	final Map<String, ColumnMetaData> columns;

	/**
	 * {@link Column}-mapped columns keyed by their normalized column name
	 * ({@link ColumnMetaData#columnName} uppercased and trimmed). Excludes
	 * {@link SqlColumn} properties, which have no physical column name.
	 */
	final Map<String, ColumnMetaData> columnsByNormalizedColumn;

	/**
	 * Maps each normalized column name to the property name of its
	 * {@link ColumnMetaData}. Mirrors {@link #columnsByNormalizedColumn} but stores
	 * the property name rather than the full metadata object.
	 */
	final Map<String, String> columnToPropertyNames;

	/**
	 * Columns whose getter carries {@link jakarta.persistence.Id}, in
	 * bean-introspection order.
	 */
	final Iterable<ColumnMetaData> idColumns;

	/**
	 * All {@link Column}-mapped columns belonging to the primary table, in
	 * bean-introspection order. Includes both {@code @Id} and non-{@code @Id}
	 * columns.
	 */
	final Iterable<ColumnMetaData> primaryColumns;

	/**
	 * Primary-table columns that are not annotated with
	 * {@link jakarta.persistence.Id}. Used to build {@code SET} clauses in
	 * {@code UPDATE} statements.
	 */
	final Iterable<ColumnMetaData> primaryNonIdColumns;

	/**
	 * The join type used when joining secondary tables. Defaults to
	 * {@link SqlJoinType.Type#INNER} when {@link SqlJoinType} is absent.
	 */
	final SqlJoinType.Type joinType;

	/**
	 * {@code true} when the entity class (or a superclass) carries the
	 * {@link Distinct} annotation, causing {@code SELECT DISTINCT} to be emitted.
	 */
	final boolean distinct;

	/**
	 * {@code true} when the entity class (or a superclass) carries the
	 * {@link SpaceForNull} annotation at the class level, causing {@code null}
	 * bound values to be replaced with a single space for all columns unless
	 * overridden at the property level.
	 */
	final boolean spaceForNull;

	/**
	 * The {@link Filtered} annotation from the entity hierarchy, or {@code null}
	 * when absent. When present, its {@link Filtered#filters()} are resolved into
	 * additional {@code WHERE} predicates.
	 */
	final Filtered filtered;

	/**
	 * The {@link EffectiveDated} annotation from the entity hierarchy, or
	 * {@code null} when absent. When present, effective-date subquery criteria can
	 * be generated.
	 */
	final EffectiveDated effectiveDated;

	/** Cache for {@code SELECT … FROM} clause fragments, keyed by fingerprint. */
	final Map<List<?>, String> selectAndFromCache = new IuCacheMap<>(Duration.ofMinutes(10L));

	/**
	 * Cache for complete {@code SELECT} statement strings, keyed by fingerprint.
	 */
	final Map<List<?>, String> selectStatementCache = new IuCacheMap<>(Duration.ofMinutes(10L));

	/** Cache for {@code UPDATE} statement strings, keyed by fingerprint. */
	final Map<List<?>, String> updateStatementCache = new IuCacheMap<>(Duration.ofMinutes(10L));

	/**
	 * Lazily initialized {@code INSERT} statement; {@code null} until first use.
	 */
	private volatile String insertStatement;

	/**
	 * Lazily initialized {@code DELETE} statement; {@code null} until first use.
	 */
	private volatile String deleteStatement;

	/**
	 * Constructs metadata for the given entity class.
	 *
	 * <p>
	 * The constructor performs the following steps:
	 * </p>
	 * <ol>
	 * <li>Resolves the primary table name and schema from {@link Table},
	 * {@link Entity}, or the simple class name.</li>
	 * <li>Resolves optional feature annotations: {@link SqlJoinType},
	 * {@link Distinct}, {@link SpaceForNull}, {@link Filtered},
	 * {@link EffectiveDated}.</li>
	 * <li>Builds {@link #secondaryTables} from {@link SecondaryTable} and/or
	 * {@link SecondaryTables} annotations, assigning sequential aliases starting at
	 * {@code "b"}.</li>
	 * <li>Scans all bean properties for {@link Column} and {@link SqlColumn}
	 * annotations, skipping {@link jakarta.persistence.Transient} properties and
	 * those with neither annotation.</li>
	 * <li>Initializes join conditions on each secondary table.</li>
	 * </ol>
	 *
	 * @param entityClass entity class to introspect; must not be {@code null}
	 */
	EntityMetaData(Class<?> entityClass) {
		this.entityClass = entityClass;

		final var table = DaoUtils.getAnnotationFromHierarchy(entityClass, Table.class);
		final var entity = DaoUtils.getAnnotationFromHierarchy(entityClass, Entity.class);
		final var tableName = table != null //
				&& DaoUtils.hasValue(table.name()) //
						? table.name()
						: entity != null //
								&& DaoUtils.hasValue(entity.name()) //
										? entity.name()
										: entityClass.getSimpleName();

		this.primaryTable = new TableMetaData(tableName, //
				table == null //
						? ""
						: table.schema(),
				"a", true, null);

		final var joinTypeAnnotation = DaoUtils.getAnnotationFromHierarchy(entityClass, SqlJoinType.class);
		this.joinType = joinTypeAnnotation == null //
				? SqlJoinType.Type.INNER
				: joinTypeAnnotation.value();
		this.distinct = DaoUtils.getAnnotationFromHierarchy(entityClass, Distinct.class) != null;
		this.spaceForNull = DaoUtils.getAnnotationFromHierarchy(entityClass, SpaceForNull.class) != null;
		this.filtered = DaoUtils.getAnnotationFromHierarchy(entityClass, Filtered.class);
		this.effectiveDated = DaoUtils.getAnnotationFromHierarchy(entityClass, EffectiveDated.class);

		final Map<String, TableMetaData> tablesByReference = new LinkedHashMap<>();
		registerTable(tablesByReference, primaryTable);

		final Queue<TableMetaData> secondaryTables = new ArrayDeque<>();
		final var secondary = DaoUtils.getAnnotationFromHierarchy(entityClass, SecondaryTable.class);
		if (secondary != null) {
			final var secondaryMeta = new TableMetaData(secondary.name(), secondary.schema(), DaoUtils.getAlias(1),
					false, secondary);
			secondaryTables.offer(secondaryMeta);
			registerTable(tablesByReference, secondaryMeta);
		}

		final var secondaries = DaoUtils.getAnnotationFromHierarchy(entityClass, SecondaryTables.class);
		if (secondaries != null)
			for (int i = 0; i < secondaries.value().length; i++) {
				final var secondaryTable = secondaries.value()[i];
				final var secondaryMeta = new TableMetaData(secondaryTable.name(), secondaryTable.schema(),
						DaoUtils.getAlias(secondaryTables.size() + 1), false, secondaryTable);
				secondaryTables.add(secondaryMeta);
				registerTable(tablesByReference, secondaryMeta);
			}

		this.secondaryTables = secondaryTables::iterator;
		this.tablesByReference = Collections.unmodifiableMap(tablesByReference);

		final Map<String, ColumnMetaData> columns = new LinkedHashMap<>();
		final Map<String, ColumnMetaData> columnsByNormalizedColumn = new LinkedHashMap<>();
		final Map<String, String> columnToPropertyNames = new LinkedHashMap<>();
		final Queue<ColumnMetaData> idColumns = new ArrayDeque<>();
		final Queue<ColumnMetaData> primaryColumns = new ArrayDeque<>();
		final Queue<ColumnMetaData> primaryNonIdColumns = new ArrayDeque<>();

		final Queue<ColumnMetaData> mappedColumns = new ArrayDeque<>();
		final Set<String> propertyNames = new LinkedHashSet<>();

		for (final var property : DaoUtils.getAllBeanProperties(entityClass)) {
			// Recorded before any skip, so that a field of the same name cannot revive a
			// property this entity deliberately leaves unmapped.
			propertyNames.add(property.getName());

			if (DaoUtils.isTransient(entityClass, property))
				continue;

			// Mapped either way round: the annotation may sit on the getter or on the
			// field behind it.
			if (DaoUtils.getPropertyAnnotation(entityClass, property, Column.class) == null //
					&& DaoUtils.getPropertyAnnotation(entityClass, property, SqlColumn.class) == null)
				continue;

			mappedColumns.add(new ColumnMetaData(this, property, //
					DaoUtils.getPropertyField(entityClass, property.getName())));
		}

		// A field with no bean property is mapped on its own. One that does have a bean
		// property was already decided by the loop above, whose annotations win.
		for (final var field : DaoUtils.getAllDeclaredFields(entityClass)) {
			if (propertyNames.contains(field.getName()) //
					|| field.isAnnotationPresent(Transient.class))
				continue;

			if (!field.isAnnotationPresent(Column.class) //
					&& !field.isAnnotationPresent(SqlColumn.class))
				continue;

			mappedColumns.add(new ColumnMetaData(this, null, field));
		}

		for (final var columnMetaData : mappedColumns) {
			columns.put(columnMetaData.propertyName, columnMetaData);
			if (columnMetaData.columnName != null) {
				columnsByNormalizedColumn.put(DaoUtils.normalizeName(columnMetaData.columnName), columnMetaData);
				columnToPropertyNames.put(DaoUtils.normalizeName(columnMetaData.columnName),
						columnMetaData.propertyName);
			}

			if (columnMetaData.id != null)
				idColumns.add(columnMetaData);

			if (columnMetaData.isPrimaryColumn()) {
				primaryColumns.add(columnMetaData);
				if (columnMetaData.id == null)
					primaryNonIdColumns.add(columnMetaData);
			}
		}

		this.columns = Collections.unmodifiableMap(columns);
		this.columnsByNormalizedColumn = Collections.unmodifiableMap(columnsByNormalizedColumn);
		this.columnToPropertyNames = Collections.unmodifiableMap(columnToPropertyNames);
		this.idColumns = idColumns::iterator;
		this.primaryColumns = primaryColumns::iterator;
		this.primaryNonIdColumns = primaryNonIdColumns::iterator;

		for (final var secondaryTable : secondaryTables)
			secondaryTable.initializeJoinConditions(this);
	}

	/**
	 * Resolves a table reference to its {@link TableMetaData}.
	 *
	 * <p>
	 * If {@code tableName} is blank or {@code null}, the primary table is returned.
	 * Otherwise the name is normalized and looked up in {@link #tablesByReference}.
	 * </p>
	 *
	 * @param tableName table short name, fully-qualified name, or alias; may be
	 *                  {@code null} or blank
	 * @return matching {@link TableMetaData}
	 * @throws IllegalArgumentException if {@code tableName} is non-blank but does
	 *                                  not match any registered table
	 */
	TableMetaData resolveTable(String tableName) {
		if (!DaoUtils.hasValue(tableName))
			return primaryTable;
		final var table = tablesByReference.get(DaoUtils.normalizeName(tableName));
		if (table == null)
			throw new IllegalArgumentException("Unknown table reference " + tableName + " for " + entityClass);
		return table;
	}

	/**
	 * Resolves a table name or alias to the canonical alias used in SQL.
	 *
	 * <p>
	 * Returns the primary table alias ({@code "a"}) when {@code tableOrAlias} is
	 * blank or {@code null}. Otherwise looks up the value in
	 * {@link #tablesByReference} and returns the matched table's alias. If no match
	 * is found the input is returned unchanged, allowing callers to pass through
	 * literal alias strings.
	 * </p>
	 *
	 * @param tableOrAlias table name, fully-qualified name, or existing alias; may
	 *                     be {@code null} or blank
	 * @return resolved alias, or {@code tableOrAlias} unchanged when not found
	 */
	String resolveAlias(String tableOrAlias) {
		if (!DaoUtils.hasValue(tableOrAlias))
			return primaryTable.alias;
		final var table = tablesByReference.get(DaoUtils.normalizeName(tableOrAlias));
		return table == null ? tableOrAlias : table.alias;
	}

	/**
	 * Looks up a column by property name or by normalized column name.
	 *
	 * <p>
	 * The lookup order is:
	 * </p>
	 * <ol>
	 * <li>Exact match against {@link ColumnMetaData#propertyName} in
	 * {@link #columns}.</li>
	 * <li>Normalized match ({@link DaoUtils#normalizeName(String)}) against
	 * {@link #columnsByNormalizedColumn}.</li>
	 * </ol>
	 *
	 * @param propertyOrColumn property name or physical column name (case
	 *                         insensitive for the latter)
	 * @return matching {@link ColumnMetaData}, or {@code null} when not found
	 */
	ColumnMetaData resolveColumn(String propertyOrColumn) {
		final var byProperty = columns.get(propertyOrColumn);
		if (byProperty != null)
			return byProperty;
		return columnsByNormalizedColumn.get(DaoUtils.normalizeName(propertyOrColumn));
	}

	/**
	 * Returns all mapped property names in the order they were discovered during
	 * bean introspection (superclass properties first, then subclass properties;
	 * within each class in the order returned by {@link java.beans.Introspector}).
	 *
	 * @return property name iterable; never {@code null}
	 */
	Iterable<String> defaultPropertyNames() {
		return columns.keySet();
	}

	/**
	 * Builds a correlated subquery predicate that constrains a date column to its
	 * maximum (past) or minimum (future) effective value as of the given date.
	 *
	 * <p>
	 * The generated SQL has the form:
	 * </p>
	 *
	 * <pre>
	 * outerAlias.effectiveColumn = (SELECT MAX/MIN(sub_ed.effectiveColumn)
	 *         FROM outerTable sub_ed
	 *        WHERE sub_ed.id = outerAlias.id
	 *          AND sub_ed.effectiveColumn &lt;= / &gt; asOfDate)
	 * </pre>
	 *
	 * <p>
	 * A {@code null} {@code asOfDate} leaves the subquery unbounded, selecting the
	 * outright latest (or earliest) row rather than the one effective on a given
	 * date. The {@code WHERE} keyword is emitted only when the subquery has at
	 * least one condition to carry.
	 * </p>
	 *
	 * <p>
	 * Over a {@linkplain #isNullExtended(TableMetaData) null-extended} table the predicate
	 * is widened to admit an absent row:
	 * </p>
	 *
	 * <pre>
	 * (outerAlias.effectiveColumn IS NULL
	 *    OR outerAlias.effectiveColumn = (SELECT …))
	 * </pre>
	 *
	 * @param outerAlias      alias of the outer query table
	 * @param effectiveColumn column that holds the effective date
	 * @param asOfDate        SQL expression for the as-of date (e.g.
	 *                        {@code "CURRENT_DATE"}), or {@code null} to leave the
	 *                        subquery unbounded
	 * @param future          {@code true} to select the minimum future date;
	 *                        {@code false} for the maximum past date
	 * @param idColumnNames   column names used to correlate the subquery
	 * @return SQL predicate string
	 */
	String buildEffectiveDateCriteria(String outerAlias, String effectiveColumn, String asOfDate, boolean future,
			Iterable<String> idColumnNames) {
		final var aggregate = future //
				? "MIN"
				: "MAX";

		final var comparator = future //
				? " > "
				: " <= ";

		final var subAlias = outerAlias + "_ed";
		// The subquery reads the table the criterion refers to, which is not the
		// primary table when the criterion is written against a joined one.
		final var table = resolveTableOrPrimary(outerAlias);
		final var nullExtended = isNullExtended(table);

		final var sb = new StringBuilder();
		if (nullExtended)
			sb.append('(').append(outerAlias).append('.').append(effectiveColumn).append(" IS NULL\n   OR ");

		sb.append(outerAlias).append('.').append(effectiveColumn).append(" = (SELECT ").append(aggregate).append('(')
				.append(subAlias).append('.').append(effectiveColumn).append(")\n        FROM ")
				.append(table.fullName).append(' ').append(subAlias);

		final var beforeConditions = sb.length();
		final var correlated = DaoUtils.appendCorrelation(sb, outerAlias, subAlias, idColumnNames);

		if (asOfDate != null) {
			if (correlated)
				sb.append("\n         AND ");
			sb.append(subAlias).append('.').append(effectiveColumn).append(comparator).append(asOfDate);
		}

		if (correlated || asOfDate != null)
			sb.insert(beforeConditions, "\n       WHERE ");

		sb.append(')');
		if (nullExtended)
			sb.append(')');
		return sb.toString();
	}

	/**
	 * Builds a correlated subquery predicate that constrains a date column to the
	 * maximum value across all rows with the same ID columns.
	 *
	 * <p>
	 * The generated SQL has the form:
	 * </p>
	 *
	 * <pre>
	 * outerAlias.maxDateColumn = (SELECT MAX(sub_md.maxDateColumn)
	 *         FROM outerTable sub_md
	 *        WHERE sub_md.id = outerAlias.id)
	 * </pre>
	 *
	 * @param outerAlias    alias of the outer query table
	 * @param maxDateColumn column that holds the maximum date
	 * @param idColumnNames column names used to correlate the subquery
	 * @return SQL predicate string
	 */
	String buildMaxDateCriteria(String outerAlias, String maxDateColumn, Iterable<String> idColumnNames) {
		final var subAlias = outerAlias + "_md";
		final var sb = new StringBuilder();
		sb.append(outerAlias).append('.').append(maxDateColumn).append(" = (SELECT MAX(").append(subAlias).append('.')
				.append(maxDateColumn).append(")\n        FROM ").append(resolveTableOrPrimary(outerAlias).fullName)
				.append(' ').append(subAlias);

		final var preIdCrit = sb.length();
		if (DaoUtils.appendCorrelation(sb, outerAlias, subAlias, idColumnNames))
			sb.insert(preIdCrit, "\n       WHERE ");

		sb.append(')');
		return sb.toString();
	}

	/**
	 * Builds a pair of correlated subquery predicates that together select the row
	 * with the maximum effective date as of the given date AND the maximum sequence
	 * number for that effective date.
	 *
	 * <p>
	 * Combines an effective-date subquery (via
	 * {@link #buildEffectiveDateCriteria(String, String, String, boolean, Iterable)})
	 * with a sequence subquery that further narrows the result to the single row
	 * with the highest sequence number on the chosen effective date.
	 * </p>
	 *
	 * <p>
	 * Over a {@linkplain #isNullExtended(TableMetaData) null-extended} table each of the two
	 * conditions is separately widened to admit an absent row. The sequence condition
	 * is parenthesized in its own right, so that the {@code OR} admitting {@code NULL}
	 * cannot capture the {@code AND} joining it to the effective-date condition.
	 * </p>
	 *
	 * @param outerAlias      alias of the outer query table
	 * @param effectiveColumn column holding the effective date
	 * @param sequenceColumn  column holding the sequence number
	 * @param asOfDate        SQL expression for the as-of date
	 * @param idColumnNames   column names used to correlate the subquery
	 * @return SQL predicate string containing two correlated subquery conditions
	 */
	String buildEffectiveDateSeqCriteria(String outerAlias, String effectiveColumn, String sequenceColumn,
			String asOfDate, Iterable<String> idColumnNames) {
		final var sb = new StringBuilder(
				buildEffectiveDateCriteria(outerAlias, effectiveColumn, asOfDate, false, idColumnNames));

		final var subAlias = outerAlias + "_seq";
		final var table = resolveTableOrPrimary(outerAlias);
		final var nullExtended = isNullExtended(table);

		sb.append("\n  AND ");
		if (nullExtended)
			sb.append('(').append(outerAlias).append('.').append(sequenceColumn).append(" IS NULL\n   OR ");

		sb.append(outerAlias).append('.').append(sequenceColumn).append(" = (SELECT MAX(").append(subAlias).append('.')
				.append(sequenceColumn).append(")\n        FROM ").append(table.fullName).append(' ').append(subAlias)
				.append("\n       WHERE ");

		if (DaoUtils.appendCorrelation(sb, outerAlias, subAlias, idColumnNames))
			sb.append("\n         AND ");

		sb.append(subAlias).append('.').append(effectiveColumn).append(" = ").append(outerAlias).append('.')
				.append(effectiveColumn).append(')');
		if (nullExtended)
			sb.append(')');
		return sb.toString();
	}

	/**
	 * Resolves the table a criterion refers to, falling back to the primary table
	 * for a blank reference or one that names no table this entity maps.
	 *
	 * @param tableOrAlias table name, qualified name, or alias; {@code null} or blank
	 *                     for the primary table
	 * @return the referenced table, or {@link #primaryTable} when the reference does
	 *         not resolve
	 */
	private TableMetaData resolveTableOrPrimary(String tableOrAlias) {
		if (!DaoUtils.hasValue(tableOrAlias))
			return primaryTable;

		final var table = tablesByReference.get(DaoUtils.normalizeName(tableOrAlias));
		return table == null ? primaryTable : table;
	}

	/**
	 * Determines whether a table can contribute {@code NULL} values to the result
	 * because it is outer-joined into the query.
	 *
	 * <p>
	 * True only for a secondary table, and only when the entity declares a
	 * {@link SqlJoinType} other than {@link SqlJoinType.Type#INNER}: the primary
	 * table is never null-extended, and an inner join discards the unmatched rows
	 * that would produce nulls. A predicate over a table that is null-extended has to
	 * admit {@code NULL} explicitly, since a comparison against {@code NULL} is
	 * unknown rather than true and would silently drop the very rows the outer join
	 * was written to keep.
	 * </p>
	 *
	 * @param table table a criterion refers to
	 * @return {@code true} when predicates over the table must also admit
	 *         {@code NULL}
	 */
	private boolean isNullExtended(TableMetaData table) {
		return joinType != SqlJoinType.Type.INNER //
				&& !table.primary;
	}

	/**
	 * Looks up a primary-table column by property or column name, throwing if it is
	 * not found or does not belong to the primary table.
	 *
	 * @param property property name or physical column name to look up
	 * @return matching primary-table {@link ColumnMetaData}
	 * @throws IllegalArgumentException if the property is unknown or maps to a
	 *                                  secondary table
	 */
	ColumnMetaData requirePrimaryColumn(String property) {
		final var column = resolveColumn(property);
		if (column == null || !column.isPrimaryColumn())
			throw new IllegalArgumentException("Unknown primary-table property " + property + " for " + entityClass);
		return column;
	}

	/**
	 * Returns a {@code alias.column} SQL reference for a property or column name.
	 *
	 * <p>
	 * If {@code columnOrProperty} resolves to a known {@link ColumnMetaData}, the
	 * column's own reference is returned (with the given table alias override if
	 * non-null). Otherwise {@link #resolveAlias(String)} is applied to
	 * {@code table} and the raw {@code columnOrProperty} string is appended.
	 * </p>
	 *
	 * @param table            table name, alias, or {@code null} for the primary
	 *                         table
	 * @param columnOrProperty property name or physical column name
	 * @return SQL column reference string (e.g. {@code "a.MY_COLUMN"})
	 */
	String columnReference(String table, String columnOrProperty) {
		final var column = resolveColumn(columnOrProperty);
		if (column != null)
			return column.reference(table == null ? null : resolveAlias(table));
		return resolveAlias(table) + "." + columnOrProperty;
	}

	/**
	 * Returns a {@code alias.column IN (v1, v2, ...)} SQL predicate for a
	 * primary-table column. Delegates to
	 * {@link #getJoinedColumnMatchCriteria(String, String, Iterable)} with
	 * {@code tab=null}.
	 *
	 * @param col       property name or physical column name
	 * @param matchList values to include in the {@code IN} list
	 * @return SQL {@code IN} predicate string
	 */
	String getColumnMatchCriteria(String col, Iterable<String> matchList) {
		return getJoinedColumnMatchCriteria(null, col, matchList);
	}

	/**
	 * Returns a {@code alias.column IN (v1, v2, ...)} SQL predicate for a column in
	 * the given table.
	 *
	 * <p>
	 * Over a {@linkplain #isNullExtended(TableMetaData) null-extended} table the
	 * result is wrapped as {@code "(alias.COL IS NULL OR ...)"}, so that rows the
	 * outer join kept without a match are not dropped by the {@code IN} test.
	 * </p>
	 *
	 * @param tab       table name, alias, or {@code null} for the primary table
	 * @param col       property name or physical column name
	 * @param matchList values to include in the {@code IN} list
	 * @return SQL {@code IN} predicate string
	 */
	String getJoinedColumnMatchCriteria(String tab, String col, Iterable<String> matchList) {
		final var reference = columnReference(tab, col);
		final var criteria = DaoUtils.getInCriteria(reference, matchList);

		if (isNullExtended(resolveTableOrPrimary(tab)))
			return "(" + reference + " IS NULL OR " + criteria + ")";
		return criteria;
	}

	/**
	 * Returns a SQL comparison predicate for a primary-table column against a list
	 * of values. Delegates to
	 * {@link #getJoinedColumnCompareCriteria(String, String, String, Iterable)}
	 * with {@code tab=null}.
	 *
	 * @param col       property name or physical column name
	 * @param comp      SQL comparison operator (e.g. {@code "="}, {@code "LIKE"})
	 * @param matchList right-hand-side values
	 * @return SQL predicate string, or {@code null} when {@code matchList} is empty
	 */
	String getColumnCompareCriteria(String col, String comp, Iterable<String> matchList) {
		return getJoinedColumnCompareCriteria(null, col, comp, matchList);
	}

	/**
	 * Returns a SQL comparison predicate for a column in the given table against a
	 * list of values.
	 *
	 * <ul>
	 * <li>Returns {@code null} when {@code matchList} is empty.</li>
	 * <li>Returns {@code "alias.COL comp value"} when exactly one value is
	 * present.</li>
	 * <li>Returns {@code "(alias.COL comp v1 OR alias.COL comp v2 OR ...)"} when
	 * multiple values are present.</li>
	 * </ul>
	 *
	 * <p>
	 * Over a {@linkplain #isNullExtended(TableMetaData) null-extended} table the result is
	 * wrapped as {@code "(alias.COL IS NULL OR ...)"}, so that rows the outer join
	 * kept without a match are not dropped by the comparison.
	 * </p>
	 *
	 * @param tab       table name, alias, or {@code null} for the primary table
	 * @param col       property name or physical column name
	 * @param comp      SQL comparison operator
	 * @param matchList right-hand-side values
	 * @return SQL predicate string, or {@code null} when {@code matchList} is empty
	 */
	String getJoinedColumnCompareCriteria(String tab, String col, String comp, Iterable<String> matchList) {
		if (comp == null || !ALLOWED_COMPARATORS.contains(comp.trim().toUpperCase(Locale.ROOT)))
			throw new IllegalArgumentException("Invalid SQL comparator: " + comp);
		final var i = matchList.iterator();

		if (!i.hasNext())
			return null;

		final var reference = columnReference(tab, col);
		final var firstMatch = i.next();
		final String criteria;
		if (i.hasNext()) {
			final var prefix = reference + " " + comp + " ";
			final var sb = new StringBuilder("(").append(prefix).append(firstMatch);
			while (i.hasNext())
				sb.append(" OR ").append(prefix).append(i.next());
			criteria = sb.append(")").toString();
		} else
			criteria = reference + " " + comp + " " + firstMatch;

		if (isNullExtended(resolveTableOrPrimary(tab)))
			return "(" + reference + " IS NULL OR " + criteria + ")";
		return criteria;
	}

	/**
	 * Returns a {@code SELECT … FROM …} SQL clause with {@code JOIN} expressions
	 * for each secondary table, optionally restricted to a subset of columns.
	 *
	 * <p>
	 * Results are cached by fingerprint of the effective property list. The
	 * {@code synchronized} keyword protects the shared {@link #selectAndFromCache}.
	 * </p>
	 *
	 * @param props property names to include in the {@code SELECT} list; pass
	 *              {@code null} to include {@link #defaultPropertyNames()}
	 * @return SQL string starting with {@code "SELECT"} and ending after the last
	 *         {@code JOIN … ON} clause
	 * @throws IllegalArgumentException if {@code props} resolves to an empty
	 *                                  sequence
	 */
	String getSelectAndFromClause(Iterable<String> props) {
		final var effectiveProps = props == null ? defaultPropertyNames() : props;
		final var fingerprint = DaoUtils.getFingerprint(effectiveProps);
		final var cached = selectAndFromCache.get(fingerprint);
		if (cached != null)
			return cached;

		final var sb = new StringBuilder(distinct ? "SELECT DISTINCT" : "SELECT");
		boolean found = false;
		for (final var prop : effectiveProps) {
			found = true;
			sb.append("\n    ");
			final var column = resolveColumn(prop);
			if (column == null) {
				sb.append(prop);
			} else {
				sb.append(column.reference());
				if (column.selectAlias != null)
					sb.append(" AS ").append(column.selectAlias);
			}
			sb.append(',');
		}
		if (!found)
			throw new IllegalArgumentException("At least one property is required");
		sb.setLength(sb.length() - 1);
		sb.append("\nFROM ").append(primaryTable.fullName).append(' ').append(primaryTable.alias);
		for (final var table : secondaryTables) {
			sb.append("\n  ").append(DaoUtils.joinKeyword(joinType)).append(' ').append(table.fullName).append(' ')
					.append(table.alias).append("\n    ON ");
			final var i = table.joinConditions.iterator();
			var first = true;
			while (i.hasNext()) {
				if (first)
					first = false;
				else
					sb.append(" AND ");
				i.next().appendTo(sb);
			}
		}

		final var sql = sb.toString();
		selectAndFromCache.put(fingerprint, sql);
		return sql;
	}

	/**
	 * Returns a complete {@code SELECT} statement including {@code WHERE},
	 * {@code ORDER BY}, and optionally {@code FOR UPDATE} clauses.
	 *
	 * <p>
	 * The effective {@code WHERE} criteria are the union of {@code where},
	 * {@link #resolveFilters()}, and {@link #resolveEffectiveDatedCriteria()}.
	 * Results are cached by fingerprint of all inputs.
	 * </p>
	 *
	 * @param props     property names for the {@code SELECT} list; {@code null}
	 *                  uses {@link #defaultPropertyNames()}
	 * @param where     additional {@code WHERE} predicates; may be empty
	 * @param order     {@code ORDER BY} expressions; may be empty
	 * @param forUpdate {@code true} to append {@code FOR UPDATE}
	 * @return complete SQL {@code SELECT} statement
	 */
	String getSelectStatement(Iterable<String> props, Iterable<String> where, Iterable<String> order,
			boolean forUpdate) {
		final var criteria = IuIterable.cat(where, resolveFilters(), resolveEffectiveDatedCriteria());
		final var effectiveProps = props == null ? defaultPropertyNames() : props;
		final var fingerprint = DaoUtils.getFingerprint(effectiveProps, criteria, order,
				IuIterable.iter(Boolean.valueOf(forUpdate)));
		final var cached = selectStatementCache.get(fingerprint);
		if (cached != null)
			return cached;

		final var sb = new StringBuilder(getSelectAndFromClause(effectiveProps));
		sb.append(DaoUtils.buildWhere(criteria));
		DaoUtils.appendOrderBy(sb, order);
		if (forUpdate)
			sb.append("\nFOR UPDATE");

		final var sql = sb.toString();
		selectStatementCache.put(fingerprint, sql);
		return sql;
	}

	/**
	 * Returns the statement that applies a change to the given primary-table
	 * properties.
	 *
	 * <p>
	 * An ordinary entity yields an {@code UPDATE … SET … WHERE} statement. An
	 * {@link EffectiveDated} entity is never modified in place — its rows are
	 * history — so it yields an {@code INSERT INTO … SELECT … FROM … WHERE}
	 * statement that supersedes the current row instead; see
	 * {@link #buildEffectiveDatedUpdateStatement(Iterable)}. Both forms bind the
	 * changed values first and the {@code @Id} values last, so
	 * {@link IuSqlBuilderImpl#getUpdateArguments(Object, Iterable)} supplies
	 * arguments for either without knowing which was generated.
	 * </p>
	 *
	 * <p>
	 * Results are cached by fingerprint of {@code properties}.
	 * </p>
	 *
	 * @param properties property names being changed; must not be empty
	 * @return SQL {@code UPDATE} statement, or {@code INSERT INTO … SELECT}
	 *         statement for an {@link EffectiveDated} entity
	 * @throws edu.iu.dao.IuSqlUnchangedException if {@code properties} is empty
	 * @throws IllegalArgumentException           if any property is unknown or maps
	 *                                            to a secondary table, or if the
	 *                                            entity declares no {@code @Id}
	 *                                            columns
	 */
	String getUpdateStatement(Iterable<String> properties) {
		if (!properties.iterator().hasNext())
			throw new IuSqlUnchangedException();

		final var fingerprint = DaoUtils.getFingerprint(properties);
		final var cached = updateStatementCache.get(fingerprint);
		if (cached != null)
			return cached;

		final var sql = effectiveDated == null //
				? buildUpdateStatement(properties)
				: buildEffectiveDatedUpdateStatement(properties);

		updateStatementCache.put(fingerprint, sql);
		return sql;
	}

	/**
	 * Builds the {@code UPDATE … SET … WHERE} statement that modifies the current
	 * row in place.
	 *
	 * @param properties property names to include in the {@code SET} clause
	 * @return SQL {@code UPDATE} statement
	 * @throws IllegalArgumentException if any property is unknown or maps to a
	 *                                  secondary table, or if the entity declares
	 *                                  no {@code @Id} columns
	 */
	private String buildUpdateStatement(Iterable<String> properties) {
		final var sb = new StringBuilder("UPDATE ").append(primaryTable.fullName).append("\nSET");
		for (final var property : properties) {
			final var column = requirePrimaryColumn(property);
			sb.append("\n    ").append(column.columnName).append(" = ?,");
		}
		sb.setLength(sb.length() - 1); // trim last comma

		final var where = DaoUtils.buildWhere(DaoUtils.idCriteria(idColumns, "?"));
		if (where.isEmpty())
			throw new IllegalArgumentException("No @Id criteria defined for " + entityClass);

		return sb.append(where).toString();
	}

	/**
	 * Builds the {@code INSERT INTO … SELECT … FROM … WHERE} statement that
	 * supersedes an {@link EffectiveDated} entity's current row with a new one.
	 *
	 * <p>
	 * The inserted row takes its changed values from bind parameters and every
	 * other value from the row being superseded, so the new row is complete without
	 * the caller having to supply columns it did not change. Each effective-dated
	 * column not among the changed properties takes the matching
	 * {@link EffectiveDated#initialValues() initial value} expression, which is
	 * what dates the new row.
	 * </p>
	 *
	 * <p>
	 * The generated SQL has the form:
	 * </p>
	 *
	 * <pre>
	 * INSERT INTO schema.table (CHANGED, CARRIED, EFFDT)
	 * SELECT
	 *     ?, -- CHANGED
	 *     a.CARRIED, -- CARRIED
	 *     CURRENT_DATE -- EFFDT
	 * FROM schema.table a
	 * WHERE a.ID = ?
	 *   AND a.EFFDT = (SELECT MAX(a_ed.EFFDT)
	 *         FROM schema.table a_ed
	 *        WHERE a_ed.ID = a.ID)
	 * </pre>
	 *
	 * <p>
	 * The effective-date subquery is deliberately unbounded, so the row copied from
	 * is the outright latest rather than the one effective today. Where more than
	 * one effective-dated column is declared, each subquery is additionally
	 * correlated on the columns preceding it, narrowing to a single source row.
	 * {@link EffectiveDated#unmappedColumns() Unmapped columns} are carried forward
	 * here rather than correlated on, since in this statement they name values the
	 * new row must keep rather than the key it is selected by.
	 * </p>
	 *
	 * @param properties property names carrying changed values
	 * @return SQL {@code INSERT INTO … SELECT} statement
	 * @throws IllegalArgumentException if any property is unknown or maps to a
	 *                                  secondary table, if the entity declares no
	 *                                  {@code @Id} columns, or if an
	 *                                  effective-dated column has no corresponding
	 *                                  initial value
	 */
	private String buildEffectiveDatedUpdateStatement(Iterable<String> properties) {
		final var datedColumns = effectiveDated.effectiveDatedColumns();
		final var initialValues = effectiveDated.initialValues();

		// All keyed by normalized column name, so that a column the annotation spells
		// differently from its mapping cannot be emitted twice.
		final Map<String, String> primaryColumnNames = new LinkedHashMap<>();
		for (final var column : primaryColumns)
			primaryColumnNames.put(DaoUtils.normalizeName(column.columnName), column.columnName);

		final var columns = new LinkedHashMap<String, InsertColumn>();
		for (final var property : properties) {
			final var column = requirePrimaryColumn(property);
			columns.put(DaoUtils.normalizeName(column.columnName), new InsertColumn(column.columnName, "?"));
		}

		// An effective-dated column the caller did not change is what dates the new
		// row, so it takes its initial value. Carrying the old value forward instead
		// would insert a duplicate of the row being superseded.
		final var dated = new LinkedHashMap<String, InsertColumn>();
		for (int i = 0; i < datedColumns.length; i++) {
			final var normalized = DaoUtils.normalizeName(datedColumns[i]);
			if (columns.containsKey(normalized))
				continue; // the caller is dating the new row itself
			if (i >= initialValues.length)
				throw new IllegalArgumentException(
						"No @EffectiveDated initialValues entry for column " + datedColumns[i] + " on " + entityClass);
			dated.put(normalized,
					new InsertColumn(primaryColumnNames.getOrDefault(normalized, datedColumns[i]), initialValues[i]));
		}

		// Everything the caller did not change and that does not date the row is
		// copied from the row being superseded, so the new row is complete.
		for (final var column : primaryColumns) {
			final var normalized = DaoUtils.normalizeName(column.columnName);
			if (!dated.containsKey(normalized))
				columns.putIfAbsent(normalized, new InsertColumn(column.columnName, column.reference()));
		}
		for (final var unmappedColumn : effectiveDated.unmappedColumns())
			columns.putIfAbsent(DaoUtils.normalizeName(unmappedColumn),
					new InsertColumn(unmappedColumn, primaryTable.alias + '.' + unmappedColumn));
		columns.putAll(dated);

		final var criteria = new ArrayList<String>();
		DaoUtils.idCriteria(primaryTable.alias, idColumns, "?").forEach(criteria::add);
		if (criteria.isEmpty())
			throw new IllegalArgumentException("No @Id criteria defined for " + entityClass);
		// Unbounded, so the row superseded is the outright latest, and with no unmapped
		// key columns, since here those name values to carry forward rather than keys.
		criteria.addAll(effectiveDatedCriteria(null, new String[0]));

		final var sb = new StringBuilder("INSERT INTO ").append(primaryTable.fullName).append(" (");
		var first = true;
		for (final var column : columns.values()) {
			if (first)
				first = false;
			else
				sb.append(", ");
			sb.append(column.columnName());
		}

		// Each value is labelled with the column it fills, because the bind
		// placeholders are otherwise indistinguishable in a generated statement.
		sb.append(")\nSELECT");
		final var i = columns.values().iterator();
		while (i.hasNext()) {
			final var column = i.next();
			sb.append("\n    ").append(column.expression());
			if (i.hasNext())
				sb.append(',');
			sb.append(" -- ").append(column.columnName());
		}

		sb.append("\nFROM ").append(primaryTable.fullName).append(' ').append(primaryTable.alias);
		return sb.append(DaoUtils.buildWhere(criteria)).toString();
	}

	/**
	 * One column of a generated {@code INSERT INTO … SELECT} statement.
	 *
	 * @param columnName column being inserted into
	 * @param expression SQL that supplies its value, either a bind placeholder, a
	 *                   reference to the row being superseded, or a literal
	 *                   expression
	 */
	private record InsertColumn(String columnName, String expression) {
	}

	/**
	 * Returns the {@code INSERT INTO … (cols) VALUES (?, …)} statement for the
	 * primary table.
	 *
	 * <p>
	 * Lazily built on the first call and then cached for the lifetime of this
	 * metadata instance.
	 * </p>
	 *
	 * @return SQL {@code INSERT} statement
	 */
	synchronized String getInsertStatement() {
		if (insertStatement != null)
			return insertStatement;

		final var insertClause = new StringBuilder("INSERT INTO ").append(primaryTable.fullName).append(" (\n");
		final var valuesClause = new StringBuilder(")\nVALUES (\n");

		final var i = primaryColumns.iterator();
		while (i.hasNext()) {
			final var primaryColumn = i.next();
			insertClause.append("    ").append(primaryColumn.columnName);
			valuesClause.append("    ?");
			if (i.hasNext()) {
				insertClause.append(',');
				valuesClause.append(',');
			}
			insertClause.append('\n');
			valuesClause.append('\n');
		}

		insertStatement = insertClause.append(valuesClause).append(")").toString();
		return insertStatement;
	}

	/**
	 * Returns the {@code DELETE FROM … WHERE id = ?} statement for the primary
	 * table.
	 *
	 * <p>
	 * Lazily built on the first call and then cached. Throws
	 * {@link UnsupportedOperationException} when the entity carries
	 * {@link EffectiveDated}.
	 * </p>
	 *
	 * @return SQL {@code DELETE} statement
	 * @throws UnsupportedOperationException if the entity is effective-dated
	 */
	synchronized String getDeleteStatement() {
		if (effectiveDated != null)
			throw new UnsupportedOperationException("Delete is not supported for effective-dated entities");
		if (deleteStatement == null) {
			final var sb = new StringBuilder("DELETE FROM ").append(primaryTable.fullName);
			final var where = DaoUtils.buildWhere(DaoUtils.idCriteria(idColumns, "?"));
			if (where.isEmpty())
				throw new IllegalArgumentException("No @Id criteria defined for " + entityClass);
			else
				sb.append(where);

			deleteStatement = sb.toString();
		}
		return deleteStatement;
	}

	/**
	 * Resolves the {@link Filtered} annotation's filter entries into SQL predicate
	 * strings.
	 *
	 * <p>
	 * Returns {@link IuIterable#empty()} when no {@link Filtered} annotation is
	 * present. {@code null} values returned by {@link #resolveFilter(SqlFilter)}
	 * are filtered out.
	 * </p>
	 *
	 * @return lazy iterable of resolved SQL predicates
	 */
	Iterable<String> resolveFilters() {
		if (filtered == null)
			return IuIterable.empty();
		else
			return IuIterable.filter(
					IuIterable.map(IuIterable.iter(filtered.filters()), filter -> resolveFilter(filter)),
					Objects::nonNull);
	}

	/**
	 * Converts a single {@link SqlFilter} annotation into a SQL predicate string.
	 *
	 * <p>
	 * If {@link SqlFilter#sql()} is non-blank it is returned directly. Otherwise
	 * the filter is dispatched by {@link SqlFilter#name()}:
	 * </p>
	 * <ul>
	 * <li>{@code "effectiveDate"} — effective-date past subquery</li>
	 * <li>{@code "maxDate"} — max-date subquery</li>
	 * <li>{@code "columnMatch"} — {@code IN} predicate</li>
	 * <li>{@code "columnCompare"} — comparison predicate</li>
	 * </ul>
	 *
	 * @param filter filter to resolve; must not be {@code null}
	 * @return SQL predicate string
	 * @throws IllegalArgumentException if the filter name is not supported
	 */
	String resolveFilter(SqlFilter filter) {
		if (DaoUtils.hasValue(filter.sql()))
			return filter.sql();
		final var params = filter.params();
		return switch (filter.name()) {
		case "effectiveDate" ->
			buildEffectiveDateCriteria(primaryTable.alias, params[0], params.length > 1 ? params[1] : "CURRENT_DATE",
					false, effectiveDateKeyColumns(params[0], new String[0], new String[0]));
		case "maxDate" -> buildMaxDateCriteria(primaryTable.alias, params[0],
				effectiveDateKeyColumns(params[0], new String[0], new String[0]));
		case "columnMatch" -> getColumnMatchCriteria(params[0], Arrays.asList(params).subList(1, params.length));
		case "columnCompare" ->
			getColumnCompareCriteria(params[0], params[1], Arrays.asList(params).subList(2, params.length));
		default ->
			throw new IllegalArgumentException("Unsupported filter name " + filter.name() + " for " + entityClass);
		};
	}

	/**
	 * Resolves effective-date subquery predicates from the {@link EffectiveDated}
	 * annotation.
	 *
	 * <p>
	 * An effective-dated entity always resolves to one row per key, so a predicate is
	 * produced for every {@link EffectiveDated#effectiveDatedColumns()} entry whenever
	 * the annotation is present. {@link EffectiveDated#currentOnly()} decides which
	 * row that is: bounded to {@link EffectiveDated#asOfDate()} when {@code true}, or
	 * the outright latest row when {@code false}. Returns
	 * {@link IuIterable#empty()} only when the annotation is absent.
	 * </p>
	 *
	 * @return SQL effective-date predicates, one per declared column
	 */
	Iterable<String> resolveEffectiveDatedCriteria() {
		if (effectiveDated == null)
			return IuIterable.empty();

		// currentOnly bounds the subquery to the row effective on the as-of date;
		// otherwise it is unbounded and selects the outright latest row.
		return effectiveDatedCriteria(effectiveDated.currentOnly() ? effectiveDated.asOfDate() : null,
				effectiveDated.unmappedColumns());
	}

	/**
	 * Builds one correlated effective-date predicate per column declared by
	 * {@link EffectiveDated#effectiveDatedColumns()}.
	 *
	 * <p>
	 * Each predicate after the first is additionally correlated on the columns
	 * preceding it, so that a multi-column declaration such as
	 * {@code {"EFFDT", "EFFSEQ"}} narrows to one row — the highest sequence within
	 * the latest date — rather than to one row per column independently.
	 * </p>
	 *
	 * @param asOfDate           SQL expression bounding each subquery, or
	 *                           {@code null} to leave it unbounded
	 * @param unmappedKeyColumns unmapped column names to correlate on
	 * @return one predicate per effective-dated column, in declaration order
	 */
	private List<String> effectiveDatedCriteria(String asOfDate, String[] unmappedKeyColumns) {
		final var datedColumns = effectiveDated.effectiveDatedColumns();
		final var criteria = new ArrayList<String>(datedColumns.length);
		for (int i = 0; i < datedColumns.length; i++)
			criteria.add(buildEffectiveDateCriteria(primaryTable.alias, datedColumns[i], asOfDate, false,
					IuIterable.cat(
							effectiveDateKeyColumns(datedColumns[i], effectiveDated.additionalKeyColumns(),
									unmappedKeyColumns),
							Arrays.asList(datedColumns).subList(0, i))));
		return criteria;
	}

	/**
	 * Returns the column names used to correlate effective-date subqueries for the
	 * given date column.
	 *
	 * <p>
	 * The result contains, in order:
	 * </p>
	 * <ol>
	 * <li>All {@link #idColumns} whose normalized column name differs from
	 * {@code effectiveColumn}.</li>
	 * <li>Additional column names from {@code additional}.</li>
	 * <li>Unmapped column names from {@code unmapped}.</li>
	 * </ol>
	 *
	 * @param effectiveColumn effective-date column name to exclude from the key set
	 * @param additional      additional mapped column names to include
	 * @param unmapped        raw column names with no entity mapping to include
	 * @return ordered set of column names (duplicates preserved per set semantics)
	 */
	Iterable<String> effectiveDateKeyColumns(String effectiveColumn, String[] additional, String[] unmapped) {
		final var keys = new LinkedHashSet<String>();
		final var normalized = DaoUtils.normalizeName(effectiveColumn);
		for (final var idColumn : idColumns)
			if (!DaoUtils.normalizeName(idColumn.columnName).equals(normalized))
				keys.add(idColumn.columnName);
		keys.addAll(Arrays.asList(additional));
		keys.addAll(Arrays.asList(unmapped));
		return keys::iterator;
	}

}
