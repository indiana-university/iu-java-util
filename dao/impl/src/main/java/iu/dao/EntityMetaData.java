package iu.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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

class EntityMetaData {
	
	final Class<?> entityClass;
	final TableMetaData primaryTable;
	final List<TableMetaData> secondaryTables;
	final Map<String, TableMetaData> tablesByReference;
	final Map<String, ColumnMetaData> columns;
	final Map<String, ColumnMetaData> columnsByNormalizedColumn;
	final Map<String, String> columnToPropertyNames;
	final List<ColumnMetaData> idColumns;
	final List<ColumnMetaData> primaryColumns;
	final List<ColumnMetaData> primaryNonIdColumns;
	final SqlJoinType.Type joinType;
	final boolean distinct;
	final boolean spaceForNull;
	final Filtered filtered;
	final EffectiveDated effectiveDated;
	final Map<Long, String> selectAndFromCache = new HashMap<>();
	final Map<Long, String> selectStatementCache = new HashMap<>();
	final Map<Long, String> updateStatementCache = new HashMap<>();
	private String insertStatement;
	private String deleteStatement;

	EntityMetaData(Class<?> entityClass) {
		this.entityClass = entityClass;
		final var table = getAnnotationFromHierarchy(entityClass, Table.class);
		final var entity = getAnnotationFromHierarchy(entityClass, Entity.class);
		final var tableName = table != null && hasValue(table.name()) ? table.name()
				: entity != null && hasValue(entity.name()) ? entity.name() : entityClass.getSimpleName();
		this.primaryTable = new TableMetaData(tableName, table == null ? "" : table.schema(), "a", true, null);
		final var joinTypeAnnotation = getAnnotationFromHierarchy(entityClass, SqlJoinType.class);
		this.joinType = joinTypeAnnotation == null ? SqlJoinType.Type.INNER : joinTypeAnnotation.value();
		this.distinct = getAnnotationFromHierarchy(entityClass, Distinct.class) != null;
		this.spaceForNull = getAnnotationFromHierarchy(entityClass, SpaceForNull.class) != null;
		this.filtered = getAnnotationFromHierarchy(entityClass, Filtered.class);
		this.effectiveDated = getAnnotationFromHierarchy(entityClass, EffectiveDated.class);

		final var tablesByReference = new LinkedHashMap<String, TableMetaData>();
		final var secondaryTables = new ArrayList<TableMetaData>();
		registerTable(tablesByReference, primaryTable);
		final var secondary = getAnnotationFromHierarchy(entityClass, SecondaryTable.class);
		if (secondary != null) {
			final var secondaryMeta = new TableMetaData(secondary.name(), secondary.schema(), getAlias(1), false,
					secondary);
			secondaryTables.add(secondaryMeta);
			registerTable(tablesByReference, secondaryMeta);
		}
		final var secondaries = getAnnotationFromHierarchy(entityClass, SecondaryTables.class);
		if (secondaries != null) {
			for (int i = 0; i < secondaries.value().length; i++) {
				final var secondaryTable = secondaries.value()[i];
				final var secondaryMeta = new TableMetaData(secondaryTable.name(), secondaryTable.schema(),
						getAlias(secondaryTables.size() + 1), false, secondaryTable);
				secondaryTables.add(secondaryMeta);
				registerTable(tablesByReference, secondaryMeta);
			}
		}
		this.secondaryTables = List.copyOf(secondaryTables);
		this.tablesByReference = Collections.unmodifiableMap(tablesByReference);

		final var columns = new LinkedHashMap<String, ColumnMetaData>();
		final var columnsByNormalizedColumn = new LinkedHashMap<String, ColumnMetaData>();
		final var columnToPropertyNames = new LinkedHashMap<String, String>();
		final var idColumns = new ArrayList<ColumnMetaData>();
		final var primaryColumns = new ArrayList<ColumnMetaData>();
		final var primaryNonIdColumns = new ArrayList<ColumnMetaData>();

		for (final var property : getAllBeanProperties(entityClass)) {
			if (isTransient(property))
				continue;
			final var readMethod = property.getReadMethod();
			if (!readMethod.isAnnotationPresent(Column.class) && !readMethod.isAnnotationPresent(SqlColumn.class))
				continue;
			final var columnMetaData = new ColumnMetaData(property, this);
			columns.put(columnMetaData.propertyName, columnMetaData);
			if (columnMetaData.columnName != null) {
				columnsByNormalizedColumn.put(normalizeName(columnMetaData.columnName), columnMetaData);
				columnToPropertyNames.put(normalizeName(columnMetaData.columnName), columnMetaData.propertyName);
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
		this.idColumns = List.copyOf(idColumns);
		this.primaryColumns = List.copyOf(primaryColumns);
		this.primaryNonIdColumns = List.copyOf(primaryNonIdColumns);

		for (final var secondaryTable : secondaryTables)
			secondaryTable.initializeJoinConditions(this);
	}

	TableMetaData resolveTable(String tableName) {
		if (!hasValue(tableName))
			return primaryTable;
		final var table = tablesByReference.get(normalizeName(tableName));
		if (table == null)
			throw new IllegalArgumentException("Unknown table reference " + tableName + " for " + entityClass);
		return table;
	}

	String resolveAlias(String tableOrAlias) {
		if (!hasValue(tableOrAlias))
			return primaryTable.alias;
		final var table = tablesByReference.get(normalizeName(tableOrAlias));
		return table == null ? tableOrAlias : table.alias;
	}

	ColumnMetaData resolveColumn(String propertyOrColumn) {
		final var byProperty = columns.get(propertyOrColumn);
		if (byProperty != null)
			return byProperty;
		return columnsByNormalizedColumn.get(normalizeName(propertyOrColumn));
	}

	Iterable<String> defaultPropertyNames() {
		return columns.keySet();
	}

	synchronized String getSelectAndFromClause(Iterable<String> props) {
		final var effectiveProps = props == null ? defaultPropertyNames() : props;
		final var fingerprint = getFingerprint(effectiveProps);
		final var cached = selectAndFromCache.get(fingerprint);
		if (cached != null)
			return cached;

		final var sb = new StringBuilder(distinct ? "SELECT DISTINCT" : "SELECT");
		boolean found = false;
		for (final var prop : effectiveProps) {
			if (prop == null)
				continue;
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
			sb.append("\n  ").append(joinKeyword(joinType)).append(' ').append(table.fullName).append(' ')
					.append(table.alias).append("\n    ON ");
			for (int i = 0; i < table.joinConditions.size(); i++) {
				if (i > 0)
					sb.append(" AND ");
				table.joinConditions.get(i).appendTo(sb);
			}
		}

		final var sql = sb.toString();
		selectAndFromCache.put(fingerprint, sql);
		return sql;
	}

	synchronized String getSelectStatement(Iterable<String> props, Iterable<String> where,
			Iterable<String> order, boolean forUpdate) {
		final var criteria = new ArrayList<String>();
		appendAll(criteria, where);
		appendAll(criteria, resolveFilters());
		appendAll(criteria, resolveEffectiveDatedCriteria());
		final var effectiveProps = props == null ? defaultPropertyNames() : props;
		final var fingerprint = getFingerprint(effectiveProps, criteria, order,
				List.of(Boolean.valueOf(forUpdate)));
		final var cached = selectStatementCache.get(fingerprint);
		if (cached != null)
			return cached;

		final var sb = new StringBuilder(getSelectAndFromClause(effectiveProps));
		sb.append(buildWhere(criteria));
		appendOrderBy(sb, order);
		if (forUpdate)
			sb.append("\nFOR UPDATE");

		final var sql = sb.toString();
		selectStatementCache.put(fingerprint, sql);
		return sql;
	}

	synchronized String getUpdateStatement(Iterable<String> properties) {
		final var propertyList = toList(properties);
		if (propertyList.isEmpty())
			throw new IuSqlUnchangedException();

		final var fingerprint = getFingerprint(propertyList);
		final var cached = updateStatementCache.get(fingerprint);
		if (cached != null)
			return cached;

		final var sb = new StringBuilder("UPDATE ").append(primaryTable.fullName).append("\nSET");
		for (final var property : propertyList) {
			final var column = requirePrimaryColumn(property);
			sb.append("\n    ").append(column.columnName).append(" = ?,");
		}
		sb.setLength(sb.length() - 1);
		sb.append(buildWhere(idCriteria(idColumns, "?")));

		final var sql = sb.toString();
		updateStatementCache.put(fingerprint, sql);
		return sql;
	}

	synchronized String getInsertStatement() {
		if (insertStatement != null)
			return insertStatement;
		final var sb = new StringBuilder("INSERT INTO ").append(primaryTable.fullName).append(" (\n");
		for (int i = 0; i < primaryColumns.size(); i++) {
			sb.append("    ").append(primaryColumns.get(i).columnName);
			if (i + 1 < primaryColumns.size())
				sb.append(',');
			sb.append('\n');
		}
		sb.append(")\nVALUES (\n");
		for (int i = 0; i < primaryColumns.size(); i++) {
			sb.append("    ?");
			if (i + 1 < primaryColumns.size())
				sb.append(',');
			sb.append('\n');
		}
		sb.append(')');
		insertStatement = sb.toString();
		return insertStatement;
	}

	synchronized String getDeleteStatement() {
		if (effectiveDated != null)
			throw new UnsupportedOperationException("Delete is not supported for effective-dated entities");
		if (deleteStatement == null)
			deleteStatement = new StringBuilder("DELETE FROM ").append(primaryTable.fullName)
					.append(buildWhere(idCriteria(idColumns, "?"))).toString();
		return deleteStatement;
	}

	ColumnMetaData requirePrimaryColumn(String property) {
		final var column = resolveColumn(property);
		if (column == null || !column.isPrimaryColumn())
			throw new IllegalArgumentException(
					"Unknown primary-table property " + property + " for " + entityClass);
		return column;
	}

	List<String> resolveFilters() {
		if (filtered == null)
			return List.of();
		final var filters = new ArrayList<String>();
		for (final var filter : filtered.filters())
			filters.add(resolveFilter(filter));
		return filters;
	}

	String resolveFilter(SqlFilter filter) {
		if (hasValue(filter.sql()))
			return filter.sql();
		final var params = filter.params();
		return switch (filter.name()) {
		case "effectiveDate" -> buildEffectiveDateCriteria(primaryTable.alias, params[0],
				params.length > 1 ? params[1] : "CURRENT_DATE", false,
				effectiveDateKeyColumns(params[0], new String[0], new String[0]));
		case "maxDate" -> buildMaxDateCriteria(primaryTable.alias, params[0],
				effectiveDateKeyColumns(params[0], new String[0], new String[0]));
		case "columnMatch" -> IuSqlBuilderImpl.this.getColumnMatchCriteria(entityClass, params[0],
				Arrays.asList(params).subList(1, params.length));
		case "columnCompare" -> IuSqlBuilderImpl.this.getColumnCompareCriteria(entityClass, params[0], params[1],
				Arrays.asList(params).subList(2, params.length));
		default ->
			throw new IllegalArgumentException("Unsupported filter name " + filter.name() + " for " + entityClass);
		};
	}

	List<String> resolveEffectiveDatedCriteria() {
		if (effectiveDated == null || !effectiveDated.currentOnly())
			return List.of();
		final var criteria = new ArrayList<String>();
		for (final var effectiveColumn : effectiveDated.effectiveDatedColumns())
			criteria.add(buildEffectiveDateCriteria(primaryTable.alias, effectiveColumn, effectiveDated.asOfDate(),
					false, effectiveDateKeyColumns(effectiveColumn, effectiveDated.additionalKeyColumns(),
							effectiveDated.unmappedColumns())));
		return criteria;
	}

	List<String> effectiveDateKeyColumns(String effectiveColumn, String[] additional, String[] unmapped) {
		final var keys = new LinkedHashSet<String>();
		final var normalized = normalizeName(effectiveColumn);
		for (final var idColumn : idColumns)
			if (!normalizeName(idColumn.columnName).equals(normalized))
				keys.add(idColumn.columnName);
		keys.addAll(Arrays.asList(additional));
		keys.addAll(Arrays.asList(unmapped));
		return List.copyOf(keys);
	}

	private String buildEffectiveDateCriteria(String outerAlias, String effectiveColumn, String asOfDate,
			boolean future, Iterable<String> idColumnNames) {
		final var aggregate = future ? "MIN" : "MAX";
		final var comparator = future ? " > " : " <= ";
		final var subAlias = outerAlias + "_ed";
		final var sb = new StringBuilder();
		sb.append(outerAlias).append('.').append(effectiveColumn).append(" = (SELECT ").append(aggregate)
				.append('(').append(subAlias).append('.').append(effectiveColumn).append(")\n        FROM ")
				.append(primaryTable.fullName).append(' ').append(subAlias).append("\n       WHERE ");
		appendCorrelation(sb, outerAlias, subAlias, idColumnNames);
		if (!sb.toString().endsWith("WHERE 1 = 1"))
			sb.append("\n         AND ");
		else
			sb.setLength(sb.length() - "1 = 1".length());
		sb.append(subAlias).append('.').append(effectiveColumn).append(comparator).append(asOfDate).append(')');
		return sb.toString();
	}

	private String buildMaxDateCriteria(String outerAlias, String maxDateColumn, Iterable<String> idColumnNames) {
		final var subAlias = outerAlias + "_md";
		final var sb = new StringBuilder();
		sb.append(outerAlias).append('.').append(maxDateColumn).append(" = (SELECT MAX(").append(subAlias)
				.append('.').append(maxDateColumn).append(")\n        FROM ").append(primaryTable.fullName)
				.append(' ').append(subAlias).append("\n       WHERE ");
		appendCorrelation(sb, outerAlias, subAlias, idColumnNames);
		sb.append(')');
		return sb.toString();
	}

	private String buildEffectiveDateSeqCriteria(String outerAlias, String effectiveColumn, String sequenceColumn,
			String asOfDate, Iterable<String> idColumnNames) {
		final var sb = new StringBuilder(
				buildEffectiveDateCriteria(outerAlias, effectiveColumn, asOfDate, false, idColumnNames));
		final var subAlias = outerAlias + "_seq";
		sb.append("\n  AND ").append(outerAlias).append('.').append(sequenceColumn).append(" = (SELECT MAX(")
				.append(subAlias).append('.').append(sequenceColumn).append(")\n        FROM ")
				.append(primaryTable.fullName).append(' ').append(subAlias).append("\n       WHERE ");
		appendCorrelation(sb, outerAlias, subAlias, idColumnNames);
		if (!sb.toString().endsWith("WHERE 1 = 1"))
			sb.append("\n         AND ");
		else
			sb.setLength(sb.length() - "1 = 1".length());
		sb.append(subAlias).append('.').append(effectiveColumn).append(" = ").append(outerAlias).append('.')
				.append(effectiveColumn).append(')');
		return sb.toString();
	}

	private void appendCorrelation(StringBuilder sb, String outerAlias, String subAlias,
			Iterable<String> idColumnNames) {
		boolean added = false;
		for (final var idColumnName : idColumnNames) {
			if (added)
				sb.append("\n         AND ");
			sb.append(subAlias).append('.').append(idColumnName).append(" = ").append(outerAlias).append('.')
					.append(idColumnName);
			added = true;
		}
		if (!added)
			sb.append("1 = 1");
	}
	
}
