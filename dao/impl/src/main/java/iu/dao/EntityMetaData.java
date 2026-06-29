package iu.dao;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.WeakHashMap;

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

	private static final WeakHashMap<Class<?>, EntityMetaData> CACHE = new WeakHashMap<>();

	/**
	 * Returns the cached {@link EntityMetaData} for the given entity class,
	 * constructing it on the first call.
	 *
	 * @param entityClass entity class to look up; must not be {@code null}
	 * @return singleton metadata instance for that class
	 * @throws NullPointerException if {@code entityClass} is {@code null}
	 */
	static EntityMetaData of(Class<?> entityClass) {
		Objects.requireNonNull(entityClass, "entityClass");
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(entityClass, EntityMetaData::new);
		}
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
	final Map<Long, String> selectAndFromCache = new HashMap<>();

	/**
	 * Cache for complete {@code SELECT} statement strings, keyed by fingerprint.
	 */
	final Map<Long, String> selectStatementCache = new HashMap<>();

	/** Cache for {@code UPDATE} statement strings, keyed by fingerprint. */
	final Map<Long, String> updateStatementCache = new HashMap<>();

	/**
	 * Lazily initialized {@code INSERT} statement; {@code null} until first use.
	 */
	private String insertStatement;

	/**
	 * Lazily initialized {@code DELETE} statement; {@code null} until first use.
	 */
	private String deleteStatement;

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

		for (final var property : DaoUtils.getAllBeanProperties(entityClass)) {
			if (DaoUtils.isTransient(property))
				continue;

			final var readMethod = property.getReadMethod();
			if (!readMethod.isAnnotationPresent(Column.class) //
					&& !readMethod.isAnnotationPresent(SqlColumn.class))
				continue;

			final var columnMetaData = new ColumnMetaData(property, this);
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
	 *         FROM primaryTable sub_ed
	 *        WHERE sub_ed.id = outerAlias.id
	 *          AND sub_ed.effectiveColumn &lt;= / &gt; asOfDate)
	 * </pre>
	 *
	 * @param outerAlias      alias of the outer query table
	 * @param effectiveColumn column that holds the effective date
	 * @param asOfDate        SQL expression for the as-of date (e.g.
	 *                        {@code "CURRENT_DATE"})
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

		final var sb = new StringBuilder();
		sb.append(outerAlias).append('.').append(effectiveColumn).append(" = (SELECT ").append(aggregate).append('(')
				.append(subAlias).append('.').append(effectiveColumn).append(")\n        FROM ")
				.append(primaryTable.fullName).append(' ').append(subAlias).append("\n       WHERE ");

		if (DaoUtils.appendCorrelation(sb, outerAlias, subAlias, idColumnNames))
			sb.append("\n         AND ");

		sb.append(subAlias).append('.').append(effectiveColumn).append(comparator).append(asOfDate).append(')');
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
	 *         FROM primaryTable sub_md
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
				.append(maxDateColumn).append(")\n        FROM ").append(primaryTable.fullName).append(' ')
				.append(subAlias);

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
		sb.append("\n  AND ").append(outerAlias).append('.').append(sequenceColumn).append(" = (SELECT MAX(")
				.append(subAlias).append('.').append(sequenceColumn).append(")\n        FROM ")
				.append(primaryTable.fullName).append(' ').append(subAlias).append("\n       WHERE ");

		if (DaoUtils.appendCorrelation(sb, outerAlias, subAlias, idColumnNames))
			sb.append("\n         AND ");

		sb.append(subAlias).append('.').append(effectiveColumn).append(" = ").append(outerAlias).append('.')
				.append(effectiveColumn).append(')');
		return sb.toString();
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
	 * Returns a {@code alias.column IN (v1, v2, ...)} SQL predicate for the given
	 * column and match values.
	 *
	 * @param col       property name or physical column name
	 * @param matchList values to include in the {@code IN} list
	 * @return SQL {@code IN} predicate string
	 */
	String getColumnMatchCriteria(String col, Iterable<String> matchList) {
		return DaoUtils.getInCriteria(columnReference(null, col), matchList);
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
	 * @param tab       table name, alias, or {@code null} for the primary table
	 * @param col       property name or physical column name
	 * @param comp      SQL comparison operator
	 * @param matchList right-hand-side values
	 * @return SQL predicate string, or {@code null} when {@code matchList} is empty
	 */
	String getJoinedColumnCompareCriteria(String tab, String col, String comp, Iterable<String> matchList) {
		final var i = matchList.iterator();
		if (!i.hasNext())
			return null;

		final var reference = columnReference(tab, col);
		final var firstMatch = i.next();
		if (!i.hasNext())
			return reference + " " + comp + " " + firstMatch;

		final var prefix = reference + " " + comp + " ";
		final var criteria = new StringBuilder("(").append(prefix).append(firstMatch);
		while (i.hasNext())
			criteria.append(" OR ").append(prefix).append(i.next());
		return criteria.append(")").toString();
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
	synchronized String getSelectAndFromClause(Iterable<String> props) {
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
	synchronized String getSelectStatement(Iterable<String> props, Iterable<String> where, Iterable<String> order,
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
	 * Returns an {@code UPDATE … SET … WHERE} statement for the given primary-table
	 * properties.
	 *
	 * <p>
	 * Results are cached by fingerprint of {@code properties}. Throws
	 * {@link edu.iu.dao.IuSqlUnchangedException} when {@code properties} is empty.
	 * </p>
	 *
	 * @param properties property names to include in the {@code SET} clause; must
	 *                   not be empty
	 * @return SQL {@code UPDATE} statement
	 * @throws edu.iu.dao.IuSqlUnchangedException if {@code properties} is empty
	 * @throws IllegalArgumentException           if any property is unknown or maps
	 *                                            to a secondary table
	 */
	synchronized String getUpdateStatement(Iterable<String> properties) {
		final var i = properties.iterator();
		if (!i.hasNext())
			throw new IuSqlUnchangedException();

		final var fingerprint = DaoUtils.getFingerprint(properties);
		final var cached = updateStatementCache.get(fingerprint);
		if (cached != null)
			return cached;

		final var sb = new StringBuilder("UPDATE ").append(primaryTable.fullName).append("\nSET");
		while (i.hasNext()) {
			final var column = requirePrimaryColumn(i.next());
			sb.append("\n    ").append(column.columnName).append(" = ?,");
		}
		sb.setLength(sb.length() - 1);
		sb.append(DaoUtils.buildWhere(DaoUtils.idCriteria(idColumns, "?")));

		final var sql = sb.toString();
		updateStatementCache.put(fingerprint, sql);
		return sql;
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
		if (deleteStatement == null)
			deleteStatement = new StringBuilder("DELETE FROM ").append(primaryTable.fullName)
					.append(DaoUtils.buildWhere(DaoUtils.idCriteria(idColumns, "?"))).toString();
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
	 * Returns {@link IuIterable#empty()} when no {@link EffectiveDated} annotation
	 * is present or when {@link EffectiveDated#currentOnly()} is {@code false}.
	 * Otherwise produces one predicate per
	 * {@link EffectiveDated#effectiveDatedColumns()} entry.
	 * </p>
	 *
	 * @return lazy iterable of SQL effective-date predicates
	 */
	Iterable<String> resolveEffectiveDatedCriteria() {
		if (effectiveDated == null //
				|| !effectiveDated.currentOnly())
			return IuIterable.empty();

		return IuIterable.map(IuIterable.iter(effectiveDated.effectiveDatedColumns()),
				effectiveColumn -> buildEffectiveDateCriteria(primaryTable.alias, effectiveColumn,
						effectiveDated.asOfDate(), false, effectiveDateKeyColumns(effectiveColumn,
								effectiveDated.additionalKeyColumns(), effectiveDated.unmappedColumns())));
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
