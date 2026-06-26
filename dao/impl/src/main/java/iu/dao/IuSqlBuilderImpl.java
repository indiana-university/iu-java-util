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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.dao.Distinct;
import edu.iu.dao.EffectiveDated;
import edu.iu.dao.Filtered;
import edu.iu.dao.IuSqlBuilder;
import edu.iu.dao.IuSqlUnchangedException;
import edu.iu.dao.SpaceForNull;
import edu.iu.dao.SqlColumn;
import edu.iu.dao.SqlFilter;
import edu.iu.dao.SqlJoinType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.SecondaryTables;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Default {@link IuSqlBuilder} implementation.
 */
public class IuSqlBuilderImpl implements IuSqlBuilder {

	private static final WeakHashMap<Class<?>, EntityMetaData> ENTITY_CACHE = new WeakHashMap<>();
	private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
			Locale.ROOT);

//	private static final class JoinCondition {
//		private final String primaryAlias;
//		private final String primaryColumn;
//		private final String secondaryAlias;
//		private final String secondaryColumn;
//
//		private JoinCondition(String secondaryAlias, ColumnMetaData column) {
//			this.primaryAlias = column.table.alias;
//			this.primaryColumn = column.columnName;
//			this.secondaryAlias = secondaryAlias;
//			this.secondaryColumn = column.columnName;
//		}
//
//		private JoinCondition(String secondaryAlias, PrimaryKeyJoinColumn pkJoinColumn, EntityMetaData entity) {
//			final var primaryLookup = hasValue(pkJoinColumn.referencedColumnName())
//					? pkJoinColumn.referencedColumnName()
//					: entity.idColumns.size() == 1 ? entity.idColumns.get(0).columnName : pkJoinColumn.name();
//			final var column = entity.columnsByNormalizedColumn.get(normalizeName(primaryLookup));
//			if (column == null) {
//				this.primaryAlias = entity.primaryTable.alias;
//				this.primaryColumn = primaryLookup;
//			} else {
//				this.primaryAlias = column.table.alias;
//				this.primaryColumn = column.columnName;
//			}
//			this.secondaryAlias = secondaryAlias;
//			this.secondaryColumn = pkJoinColumn.name();
//		}
//
//		private void appendTo(StringBuilder sb) {
//			sb.append(primaryAlias).append('.').append(primaryColumn).append(" = ").append(secondaryAlias).append('.')
//					.append(secondaryColumn);
//		}
//	}

//	private static final class TableMetaData {
//		private final String name;
//		private final String fullName;
//		private final String alias;
//		private final boolean primary;
//		private final SecondaryTable secondaryTable;
//		private List<JoinCondition> joinConditions = List.of();
//
//		private TableMetaData(String name, String schema, String alias, boolean primary,
//				SecondaryTable secondaryTable) {
//			this.name = name;
//			this.fullName = qualifyName(schema, name);
//			this.alias = alias;
//			this.primary = primary;
//			this.secondaryTable = secondaryTable;
//		}
//
//		private void initializeJoinConditions(EntityMetaData entity) {
//			if (primary) {
//				joinConditions = List.of();
//				return;
//			}
//
//			final var conditions = new ArrayList<JoinCondition>();
//			if (secondaryTable.pkJoinColumns().length == 0) {
//				for (final var column : entity.idColumns)
//					conditions.add(new JoinCondition(alias, column));
//			} else {
//				for (final var pkJoinColumn : secondaryTable.pkJoinColumns())
//					conditions.add(new JoinCondition(alias, pkJoinColumn, entity));
//			}
//			joinConditions = List.copyOf(conditions);
//		}
//	}

//	private static final class ColumnMetaData {
//		private final PropertyDescriptor property;
//		private final String propertyName;
//		private final String columnName;
//		private final String sql;
//		private final String selectAlias;
//		private final Id id;
//		private final Column column;
//		private final SqlColumn sqlColumn;
//		private final boolean spaceForNull;
//		private final TableMetaData table;
//
//		private ColumnMetaData(PropertyDescriptor property, EntityMetaData entity) {
//			this.property = property;
//			this.propertyName = property.getName();
//			final var readMethod = property.getReadMethod();
//			this.id = readMethod.getAnnotation(Id.class);
//			this.column = readMethod.getAnnotation(Column.class);
//			this.sqlColumn = readMethod.getAnnotation(SqlColumn.class);
//			this.spaceForNull = entity.spaceForNull || readMethod.isAnnotationPresent(SpaceForNull.class);
//			if (column == null) {
//				this.table = null;
//				this.columnName = null;
//				this.sql = sqlColumn.value();
//				this.selectAlias = camelToSnakeUpper(propertyName);
//			} else {
//				this.table = entity.resolveTable(column.table());
//				this.columnName = hasValue(column.name()) ? column.name() : camelToSnakeUpper(propertyName);
//				this.sql = this.columnName;
//				this.selectAlias = null;
//			}
//		}
//
//		private String reference() {
//			return reference(null);
//		}
//
//		private String reference(String aliasOverride) {
//			final var sb = new StringBuilder();
//			if (table != null)
//				sb.append(aliasOverride == null ? table.alias : aliasOverride).append('.');
//			sb.append(sql);
//			return sb.toString();
//		}
//
//		private boolean isMappedColumn() {
//			return column != null;
//		}
//
//		private boolean isPrimaryColumn() {
//			return isMappedColumn() && table.primary;
//		}
//	}

//	private final class EntityMetaData {
//		private final Class<?> entityClass;
//		private final TableMetaData primaryTable;
//		private final List<TableMetaData> secondaryTables;
//		private final Map<String, TableMetaData> tablesByReference;
//		private final Map<String, ColumnMetaData> columns;
//		private final Map<String, ColumnMetaData> columnsByNormalizedColumn;
//		private final Map<String, String> columnToPropertyNames;
//		private final List<ColumnMetaData> idColumns;
//		private final List<ColumnMetaData> primaryColumns;
//		private final List<ColumnMetaData> primaryNonIdColumns;
//		private final SqlJoinType.Type joinType;
//		private final boolean distinct;
//		private final boolean spaceForNull;
//		private final Filtered filtered;
//		private final EffectiveDated effectiveDated;
//		private final Map<Long, String> selectAndFromCache = new HashMap<>();
//		private final Map<Long, String> selectStatementCache = new HashMap<>();
//		private final Map<Long, String> updateStatementCache = new HashMap<>();
//		private String insertStatement;
//		private String deleteStatement;
//
//		private EntityMetaData(Class<?> entityClass) {
//			this.entityClass = entityClass;
//			final var table = getAnnotationFromHierarchy(entityClass, Table.class);
//			final var entity = getAnnotationFromHierarchy(entityClass, Entity.class);
//			final var tableName = table != null && hasValue(table.name()) ? table.name()
//					: entity != null && hasValue(entity.name()) ? entity.name() : entityClass.getSimpleName();
//			this.primaryTable = new TableMetaData(tableName, table == null ? "" : table.schema(), "a", true, null);
//			final var joinTypeAnnotation = getAnnotationFromHierarchy(entityClass, SqlJoinType.class);
//			this.joinType = joinTypeAnnotation == null ? SqlJoinType.Type.INNER : joinTypeAnnotation.value();
//			this.distinct = getAnnotationFromHierarchy(entityClass, Distinct.class) != null;
//			this.spaceForNull = getAnnotationFromHierarchy(entityClass, SpaceForNull.class) != null;
//			this.filtered = getAnnotationFromHierarchy(entityClass, Filtered.class);
//			this.effectiveDated = getAnnotationFromHierarchy(entityClass, EffectiveDated.class);
//
//			final var tablesByReference = new LinkedHashMap<String, TableMetaData>();
//			final var secondaryTables = new ArrayList<TableMetaData>();
//			registerTable(tablesByReference, primaryTable);
//			final var secondary = getAnnotationFromHierarchy(entityClass, SecondaryTable.class);
//			if (secondary != null) {
//				final var secondaryMeta = new TableMetaData(secondary.name(), secondary.schema(), getAlias(1), false,
//						secondary);
//				secondaryTables.add(secondaryMeta);
//				registerTable(tablesByReference, secondaryMeta);
//			}
//			final var secondaries = getAnnotationFromHierarchy(entityClass, SecondaryTables.class);
//			if (secondaries != null) {
//				for (int i = 0; i < secondaries.value().length; i++) {
//					final var secondaryTable = secondaries.value()[i];
//					final var secondaryMeta = new TableMetaData(secondaryTable.name(), secondaryTable.schema(),
//							getAlias(secondaryTables.size() + 1), false, secondaryTable);
//					secondaryTables.add(secondaryMeta);
//					registerTable(tablesByReference, secondaryMeta);
//				}
//			}
//			this.secondaryTables = List.copyOf(secondaryTables);
//			this.tablesByReference = Collections.unmodifiableMap(tablesByReference);
//
//			final var columns = new LinkedHashMap<String, ColumnMetaData>();
//			final var columnsByNormalizedColumn = new LinkedHashMap<String, ColumnMetaData>();
//			final var columnToPropertyNames = new LinkedHashMap<String, String>();
//			final var idColumns = new ArrayList<ColumnMetaData>();
//			final var primaryColumns = new ArrayList<ColumnMetaData>();
//			final var primaryNonIdColumns = new ArrayList<ColumnMetaData>();
//
//			for (final var property : getAllBeanProperties(entityClass)) {
//				if (isTransient(property))
//					continue;
//				final var readMethod = property.getReadMethod();
//				if (!readMethod.isAnnotationPresent(Column.class) && !readMethod.isAnnotationPresent(SqlColumn.class))
//					continue;
//				final var columnMetaData = new ColumnMetaData(property, this);
//				columns.put(columnMetaData.propertyName, columnMetaData);
//				if (columnMetaData.columnName != null) {
//					columnsByNormalizedColumn.put(normalizeName(columnMetaData.columnName), columnMetaData);
//					columnToPropertyNames.put(normalizeName(columnMetaData.columnName), columnMetaData.propertyName);
//				}
//				if (columnMetaData.id != null)
//					idColumns.add(columnMetaData);
//				if (columnMetaData.isPrimaryColumn()) {
//					primaryColumns.add(columnMetaData);
//					if (columnMetaData.id == null)
//						primaryNonIdColumns.add(columnMetaData);
//				}
//			}
//
//			this.columns = Collections.unmodifiableMap(columns);
//			this.columnsByNormalizedColumn = Collections.unmodifiableMap(columnsByNormalizedColumn);
//			this.columnToPropertyNames = Collections.unmodifiableMap(columnToPropertyNames);
//			this.idColumns = List.copyOf(idColumns);
//			this.primaryColumns = List.copyOf(primaryColumns);
//			this.primaryNonIdColumns = List.copyOf(primaryNonIdColumns);
//
//			for (final var secondaryTable : secondaryTables)
//				secondaryTable.initializeJoinConditions(this);
//		}
//
//		private TableMetaData resolveTable(String tableName) {
//			if (!hasValue(tableName))
//				return primaryTable;
//			final var table = tablesByReference.get(normalizeName(tableName));
//			if (table == null)
//				throw new IllegalArgumentException("Unknown table reference " + tableName + " for " + entityClass);
//			return table;
//		}
//
//		private String resolveAlias(String tableOrAlias) {
//			if (!hasValue(tableOrAlias))
//				return primaryTable.alias;
//			final var table = tablesByReference.get(normalizeName(tableOrAlias));
//			return table == null ? tableOrAlias : table.alias;
//		}
//
//		private ColumnMetaData resolveColumn(String propertyOrColumn) {
//			final var byProperty = columns.get(propertyOrColumn);
//			if (byProperty != null)
//				return byProperty;
//			return columnsByNormalizedColumn.get(normalizeName(propertyOrColumn));
//		}
//
//		private Iterable<String> defaultPropertyNames() {
//			return columns.keySet();
//		}
//
//		private synchronized String getSelectAndFromClause(Iterable<String> props) {
//			final var effectiveProps = props == null ? defaultPropertyNames() : props;
//			final var fingerprint = getFingerprint(effectiveProps);
//			final var cached = selectAndFromCache.get(fingerprint);
//			if (cached != null)
//				return cached;
//
//			final var sb = new StringBuilder(distinct ? "SELECT DISTINCT" : "SELECT");
//			boolean found = false;
//			for (final var prop : effectiveProps) {
//				if (prop == null)
//					continue;
//				found = true;
//				sb.append("\n    ");
//				final var column = resolveColumn(prop);
//				if (column == null) {
//					sb.append(prop);
//				} else {
//					sb.append(column.reference());
//					if (column.selectAlias != null)
//						sb.append(" AS ").append(column.selectAlias);
//				}
//				sb.append(',');
//			}
//			if (!found)
//				throw new IllegalArgumentException("At least one property is required");
//			sb.setLength(sb.length() - 1);
//			sb.append("\nFROM ").append(primaryTable.fullName).append(' ').append(primaryTable.alias);
//			for (final var table : secondaryTables) {
//				sb.append("\n  ").append(joinKeyword(joinType)).append(' ').append(table.fullName).append(' ')
//						.append(table.alias).append("\n    ON ");
//				for (int i = 0; i < table.joinConditions.size(); i++) {
//					if (i > 0)
//						sb.append(" AND ");
//					table.joinConditions.get(i).appendTo(sb);
//				}
//			}
//
//			final var sql = sb.toString();
//			selectAndFromCache.put(fingerprint, sql);
//			return sql;
//		}
//
//		private synchronized String getSelectStatement(Iterable<String> props, Iterable<String> where,
//				Iterable<String> order, boolean forUpdate) {
//			final var criteria = new ArrayList<String>();
//			appendAll(criteria, where);
//			appendAll(criteria, resolveFilters());
//			appendAll(criteria, resolveEffectiveDatedCriteria());
//			final var effectiveProps = props == null ? defaultPropertyNames() : props;
//			final var fingerprint = getFingerprint(effectiveProps, criteria, order,
//					List.of(Boolean.valueOf(forUpdate)));
//			final var cached = selectStatementCache.get(fingerprint);
//			if (cached != null)
//				return cached;
//
//			final var sb = new StringBuilder(getSelectAndFromClause(effectiveProps));
//			sb.append(buildWhere(criteria));
//			appendOrderBy(sb, order);
//			if (forUpdate)
//				sb.append("\nFOR UPDATE");
//
//			final var sql = sb.toString();
//			selectStatementCache.put(fingerprint, sql);
//			return sql;
//		}
//
//		private synchronized String getUpdateStatement(Iterable<String> properties) {
//			final var propertyList = toList(properties);
//			if (propertyList.isEmpty())
//				throw new IuSqlUnchangedException();
//
//			final var fingerprint = getFingerprint(propertyList);
//			final var cached = updateStatementCache.get(fingerprint);
//			if (cached != null)
//				return cached;
//
//			final var sb = new StringBuilder("UPDATE ").append(primaryTable.fullName).append("\nSET");
//			for (final var property : propertyList) {
//				final var column = requirePrimaryColumn(property);
//				sb.append("\n    ").append(column.columnName).append(" = ?,");
//			}
//			sb.setLength(sb.length() - 1);
//			sb.append(buildWhere(idCriteria(idColumns, "?")));
//
//			final var sql = sb.toString();
//			updateStatementCache.put(fingerprint, sql);
//			return sql;
//		}
//
//		private synchronized String getInsertStatement() {
//			if (insertStatement != null)
//				return insertStatement;
//			final var sb = new StringBuilder("INSERT INTO ").append(primaryTable.fullName).append(" (\n");
//			for (int i = 0; i < primaryColumns.size(); i++) {
//				sb.append("    ").append(primaryColumns.get(i).columnName);
//				if (i + 1 < primaryColumns.size())
//					sb.append(',');
//				sb.append('\n');
//			}
//			sb.append(")\nVALUES (\n");
//			for (int i = 0; i < primaryColumns.size(); i++) {
//				sb.append("    ?");
//				if (i + 1 < primaryColumns.size())
//					sb.append(',');
//				sb.append('\n');
//			}
//			sb.append(')');
//			insertStatement = sb.toString();
//			return insertStatement;
//		}
//
//		private synchronized String getDeleteStatement() {
//			if (effectiveDated != null)
//				throw new UnsupportedOperationException("Delete is not supported for effective-dated entities");
//			if (deleteStatement == null)
//				deleteStatement = new StringBuilder("DELETE FROM ").append(primaryTable.fullName)
//						.append(buildWhere(idCriteria(idColumns, "?"))).toString();
//			return deleteStatement;
//		}
//
//		private ColumnMetaData requirePrimaryColumn(String property) {
//			final var column = resolveColumn(property);
//			if (column == null || !column.isPrimaryColumn())
//				throw new IllegalArgumentException(
//						"Unknown primary-table property " + property + " for " + entityClass);
//			return column;
//		}
//
//		private List<String> resolveFilters() {
//			if (filtered == null)
//				return List.of();
//			final var filters = new ArrayList<String>();
//			for (final var filter : filtered.filters())
//				filters.add(resolveFilter(filter));
//			return filters;
//		}
//
//		private String resolveFilter(SqlFilter filter) {
//			if (hasValue(filter.sql()))
//				return filter.sql();
//			final var params = filter.params();
//			return switch (filter.name()) {
//			case "effectiveDate" -> buildEffectiveDateCriteria(primaryTable.alias, params[0],
//					params.length > 1 ? params[1] : "CURRENT_DATE", false,
//					effectiveDateKeyColumns(params[0], new String[0], new String[0]));
//			case "maxDate" -> buildMaxDateCriteria(primaryTable.alias, params[0],
//					effectiveDateKeyColumns(params[0], new String[0], new String[0]));
//			case "columnMatch" -> IuSqlBuilderImpl.this.getColumnMatchCriteria(entityClass, params[0],
//					Arrays.asList(params).subList(1, params.length));
//			case "columnCompare" -> IuSqlBuilderImpl.this.getColumnCompareCriteria(entityClass, params[0], params[1],
//					Arrays.asList(params).subList(2, params.length));
//			default ->
//				throw new IllegalArgumentException("Unsupported filter name " + filter.name() + " for " + entityClass);
//			};
//		}
//
//		private List<String> resolveEffectiveDatedCriteria() {
//			if (effectiveDated == null || !effectiveDated.currentOnly())
//				return List.of();
//			final var criteria = new ArrayList<String>();
//			for (final var effectiveColumn : effectiveDated.effectiveDatedColumns())
//				criteria.add(buildEffectiveDateCriteria(primaryTable.alias, effectiveColumn, effectiveDated.asOfDate(),
//						false, effectiveDateKeyColumns(effectiveColumn, effectiveDated.additionalKeyColumns(),
//								effectiveDated.unmappedColumns())));
//			return criteria;
//		}
//
//		private List<String> effectiveDateKeyColumns(String effectiveColumn, String[] additional, String[] unmapped) {
//			final var keys = new LinkedHashSet<String>();
//			final var normalized = normalizeName(effectiveColumn);
//			for (final var idColumn : idColumns)
//				if (!normalizeName(idColumn.columnName).equals(normalized))
//					keys.add(idColumn.columnName);
//			keys.addAll(Arrays.asList(additional));
//			keys.addAll(Arrays.asList(unmapped));
//			return List.copyOf(keys);
//		}
//
//		private String buildEffectiveDateCriteria(String outerAlias, String effectiveColumn, String asOfDate,
//				boolean future, Iterable<String> idColumnNames) {
//			final var aggregate = future ? "MIN" : "MAX";
//			final var comparator = future ? " > " : " <= ";
//			final var subAlias = outerAlias + "_ed";
//			final var sb = new StringBuilder();
//			sb.append(outerAlias).append('.').append(effectiveColumn).append(" = (SELECT ").append(aggregate)
//					.append('(').append(subAlias).append('.').append(effectiveColumn).append(")\n        FROM ")
//					.append(primaryTable.fullName).append(' ').append(subAlias).append("\n       WHERE ");
//			appendCorrelation(sb, outerAlias, subAlias, idColumnNames);
//			if (!sb.toString().endsWith("WHERE 1 = 1"))
//				sb.append("\n         AND ");
//			else
//				sb.setLength(sb.length() - "1 = 1".length());
//			sb.append(subAlias).append('.').append(effectiveColumn).append(comparator).append(asOfDate).append(')');
//			return sb.toString();
//		}
//
//		private String buildMaxDateCriteria(String outerAlias, String maxDateColumn, Iterable<String> idColumnNames) {
//			final var subAlias = outerAlias + "_md";
//			final var sb = new StringBuilder();
//			sb.append(outerAlias).append('.').append(maxDateColumn).append(" = (SELECT MAX(").append(subAlias)
//					.append('.').append(maxDateColumn).append(")\n        FROM ").append(primaryTable.fullName)
//					.append(' ').append(subAlias).append("\n       WHERE ");
//			appendCorrelation(sb, outerAlias, subAlias, idColumnNames);
//			sb.append(')');
//			return sb.toString();
//		}
//
//		private String buildEffectiveDateSeqCriteria(String outerAlias, String effectiveColumn, String sequenceColumn,
//				String asOfDate, Iterable<String> idColumnNames) {
//			final var sb = new StringBuilder(
//					buildEffectiveDateCriteria(outerAlias, effectiveColumn, asOfDate, false, idColumnNames));
//			final var subAlias = outerAlias + "_seq";
//			sb.append("\n  AND ").append(outerAlias).append('.').append(sequenceColumn).append(" = (SELECT MAX(")
//					.append(subAlias).append('.').append(sequenceColumn).append(")\n        FROM ")
//					.append(primaryTable.fullName).append(' ').append(subAlias).append("\n       WHERE ");
//			appendCorrelation(sb, outerAlias, subAlias, idColumnNames);
//			if (!sb.toString().endsWith("WHERE 1 = 1"))
//				sb.append("\n         AND ");
//			else
//				sb.setLength(sb.length() - "1 = 1".length());
//			sb.append(subAlias).append('.').append(effectiveColumn).append(" = ").append(outerAlias).append('.')
//					.append(effectiveColumn).append(')');
//			return sb.toString();
//		}
//
//		private void appendCorrelation(StringBuilder sb, String outerAlias, String subAlias,
//				Iterable<String> idColumnNames) {
//			boolean added = false;
//			for (final var idColumnName : idColumnNames) {
//				if (added)
//					sb.append("\n         AND ");
//				sb.append(subAlias).append('.').append(idColumnName).append(" = ").append(outerAlias).append('.')
//						.append(idColumnName);
//				added = true;
//			}
//			if (!added)
//				sb.append("1 = 1");
//		}
//	}

	private static boolean isTransient(PropertyDescriptor property) {
		final var readMethod = property.getReadMethod();
		return readMethod == null || readMethod.isAnnotationPresent(Transient.class);
	}

	private static void registerTable(Map<String, TableMetaData> tablesByReference, TableMetaData table) {
		tablesByReference.put(normalizeName(table.name), table);
		tablesByReference.put(normalizeName(table.fullName), table);
		tablesByReference.put(normalizeName(table.alias), table);
	}

	private static String joinKeyword(SqlJoinType.Type joinType) {
		return switch (joinType) {
		case LEFT -> "LEFT OUTER JOIN";
		case RIGHT -> "RIGHT OUTER JOIN";
		case FULL -> "FULL OUTER JOIN";
		case INNER -> "JOIN";
		};
	}

//	/** Get all PropertyDescriptors from entityClass hierarchy annotated with given annotation */
//	private static List<PropertyDescriptor> getAllAnnotatedProperties(Class<?> entityClass,
//			Class<? extends Annotation> ann) {
//		final var properties = new LinkedHashMap<String, PropertyDescriptor>();
//		for (final var property : getAllBeanProperties(entityClass))
//			if (property.getReadMethod().isAnnotationPresent(ann))
//				properties.put(property.getName(), property);
//		return new ArrayList<>(properties.values());
//	}

	private static String buildWhere(Iterable<String> criteria) {
		final var list = toList(criteria);
		if (list.isEmpty())
			return "";
		final var sb = new StringBuilder("\nWHERE ");
		for (int i = 0; i < list.size(); i++) {
			if (i > 0)
				sb.append("\n  AND ");
			sb.append(list.get(i));
		}
		return sb.toString();
	}

	private static void appendOrderBy(StringBuilder sb, Iterable<String> order) {
		final var list = toList(order);
		if (list.isEmpty())
			return;
		sb.append("\nORDER BY ");
		for (int i = 0; i < list.size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(list.get(i));
		}
	}

//	private static List<String> idCriteria(String alias, Iterable<ColumnMetaData> idColumns, String placeholder) {
//		final var criteria = new ArrayList<String>();
//		for (final var idColumn : idColumns)
//			criteria.add(alias + "." + idColumn.columnName + " = " + placeholder);
//		return criteria;
//	}
//
	/**
	 * Builds id criteria without a table alias, for use in UPDATE/DELETE WHERE
	 * clauses.
	 */
	private static List<String> idCriteria(Iterable<ColumnMetaData> idColumns, String placeholder) {
		final var criteria = new ArrayList<String>();
		for (final var idColumn : idColumns)
			criteria.add(idColumn.columnName + " = " + placeholder);
		return criteria;
	}

	private EntityMetaData getEntityMetaData(Class<?> entityClass) {
		Objects.requireNonNull(entityClass, "entityClass");
		synchronized (ENTITY_CACHE) {
			return ENTITY_CACHE.computeIfAbsent(entityClass, EntityMetaData::new);
		}
	}

	private static Object normalizeArgument(ColumnMetaData column, Object value) {
		if (value == null && column.spaceForNull)
			return " ";
		if (value instanceof Instant instant)
			return Timestamp.from(instant);
		return value;
	}

	private static String singleQuoted(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private static String literalFromDate(java.util.Date date) {
		synchronized (TIMESTAMP_FORMAT) {
			return "TIMESTAMP '" + TIMESTAMP_FORMAT.format(date) + "'";
		}
	}

	private String columnReference(EntityMetaData entity, String table, String columnOrProperty) {
		final var column = entity.resolveColumn(columnOrProperty);
		if (column != null)
			return column.reference(table == null ? null : entity.resolveAlias(table));
		return entity.resolveAlias(table) + "." + columnOrProperty;
	}

	@Override
	public String getTableAlias(Class<?> entityClass, String table) {
		return getEntityMetaData(entityClass).resolveAlias(table);
	}

	@Override
	public Iterable<String> getPrimaryKeyProperties(Class<?> entityClass) {
		return IuIterable.map(getEntityMetaData(entityClass).idColumns, c -> c.propertyName);
	}

	@Override
	public String getSelectAndFromClause(Class<?> entityClass) {
		return getEntityMetaData(entityClass).getSelectAndFromClause(null);
	}

	@Override
	public String getSelectAndFromClause(Class<?> entityClass, Iterable<String> props) {
		return getEntityMetaData(entityClass).getSelectAndFromClause(props);
	}

	@Override
	public String getSelectStatement(Class<?> entityClass, Iterable<String> where) {
		return getEntityMetaData(entityClass).getSelectStatement(null, where, null, false);
	}

	@Override
	public String getOrderedSelectStatement(Class<?> entityClass, Iterable<String> where, Iterable<String> order) {
		return getEntityMetaData(entityClass).getSelectStatement(null, where, order, false);
	}

	@Override
	public String getSelectStatement(Class<?> entityClass, Iterable<String> where, int lockTimeout) {
		return getEntityMetaData(entityClass).getSelectStatement(null, where, null, true);
	}

	@Override
	public String getSelectStatement(Class<?> entityClass, Iterable<String> where, Iterable<String> order,
			int lockTimeout) {
		return getEntityMetaData(entityClass).getSelectStatement(null, where, order, true);
	}

	@Override
	public String getSelectStatement(Class<?> entityClass, Iterable<String> props, Iterable<String> where) {
		return getEntityMetaData(entityClass).getSelectStatement(props, where, null, false);
	}

	@Override
	public Iterable<String> getBeanKeyCriteria(Class<?> entityClass, Map<String, ?> idprops) {
		final var entity = getEntityMetaData(entityClass);
		final var criteria = new ArrayList<String>();
		for (final var idColumn : entity.idColumns) {
			final Object value;
			if (idprops.containsKey(idColumn.propertyName))
				value = idprops.get(idColumn.propertyName);
			else if (idprops.containsKey(idColumn.columnName))
				value = idprops.get(idColumn.columnName);
			else
				continue;
			criteria.add(value == null ? idColumn.reference() + " IS NULL" : idColumn.reference() + " = ?");
		}
		return criteria;
	}

	@Override
	public Iterable<?> getBeanKeyArgs(Class<?> entityClass, Map<String, ?> idprops) {
		final var entity = getEntityMetaData(entityClass);
		final var args = new ArrayList<>();
		for (final var idColumn : entity.idColumns) {
			final Object value;
			if (idprops.containsKey(idColumn.propertyName))
				value = idprops.get(idColumn.propertyName);
			else if (idprops.containsKey(idColumn.columnName))
				value = idprops.get(idColumn.columnName);
			else
				continue;
			if (value != null)
				args.add(value);
		}
		return args;
	}

	@Override
	public Iterable<String> getUpdateProperties(Object entity) {
		Objects.requireNonNull(entity, "entity");
		return IuIterable.map(getEntityMetaData(entity.getClass()).primaryNonIdColumns, c -> c.propertyName);
	}

	@Override
	public Iterable<String> getUpdateProperties(Object entity, Object original) {
		Objects.requireNonNull(entity, "entity");
		if (original == null)
			return getUpdateProperties(entity);
		final var meta = getEntityMetaData(entity.getClass());
		final var properties = new ArrayList<String>();
		for (final var column : meta.primaryNonIdColumns) {
			final var currentValue = getPropertyValue(entity, column.property);
			final var originalValue = getPropertyValue(original, column.property);
			if (!IuObject.equals(currentValue, originalValue))
				properties.add(column.propertyName);
		}
		return properties;
	}

	@Override
	public Iterable<String> getUpdateProperties(Object entity, Supplier<Object> passive) {
		return getUpdateProperties(entity, passive == null ? null : passive.get());
	}

	@Override
	public Iterable<?> getUpdateArguments(Object entity, Iterable<String> properties) {
		Objects.requireNonNull(entity, "entity");
		final var meta = getEntityMetaData(entity.getClass());
		final var args = new ArrayList<>();
		for (final var property : properties) {
			final var column = meta.requirePrimaryColumn(property);
			args.add(normalizeArgument(column, getPropertyValue(entity, column.property)));
		}
		for (final var idColumn : meta.idColumns)
			args.add(normalizeArgument(idColumn, getPropertyValue(entity, idColumn.property)));
		return args;
	}

	@Override
	public String getUpdateStatement(Class<?> entityClass, Iterable<String> properties) {
		return getEntityMetaData(entityClass).getUpdateStatement(properties);
	}

	@Override
	public Iterable<?> getDeleteArguments(Object entity) {
		Objects.requireNonNull(entity, "entity");
		final var meta = getEntityMetaData(entity.getClass());
		final var args = new ArrayList<>();
		for (final var idColumn : meta.idColumns)
			args.add(normalizeArgument(idColumn, getPropertyValue(entity, idColumn.property)));
		return args;
	}

	@Override
	public String getDeleteStatement(Class<?> entityClass) {
		return getEntityMetaData(entityClass).getDeleteStatement();
	}

	@Override
	public String getInsertStatement(Class<?> entityClass) {
		return getEntityMetaData(entityClass).getInsertStatement();
	}

	@Override
	public Iterable<?> getInsertArguments(Object entity) {
		Objects.requireNonNull(entity, "entity");
		final var meta = getEntityMetaData(entity.getClass());
		final var args = new ArrayList<>();
		for (final var column : meta.primaryColumns)
			args.add(normalizeArgument(column, getPropertyValue(entity, column.property)));
		return args;
	}

	@Override
	public Object getForSql(Object entity, String propertyName) {
		Objects.requireNonNull(entity, "entity");
		final var meta = getEntityMetaData(entity.getClass());
		final var column = meta.resolveColumn(propertyName);
		if (column == null)
			throw new IllegalArgumentException("Unknown property " + propertyName + " for " + entity.getClass());
		return normalizeArgument(column, getPropertyValue(entity, column.property));
	}

	@Override
	public String getLiteral(Object o) {
		if (o == null)
			return "NULL";
		if (o instanceof String s)
			return singleQuoted(s);
		if (o instanceof Date date)
			return "DATE '" + date.toLocalDate() + "'";
		if (o instanceof Timestamp timestamp)
			return literalFromDate(timestamp);
		if (o instanceof java.util.Date date)
			return literalFromDate(date);
		if (o instanceof Boolean b)
			return b.booleanValue() ? "'Y'" : "'N'";
		if (o instanceof Number n)
			return n.toString();
		throw new UnsupportedOperationException("Unsupported literal type " + o.getClass());
	}

	@Override
	public Iterable<String> getLiterals(Iterable<Object> o) {
		return o == null ? IuIterable.empty() : IuIterable.map(o, this::getLiteral);
	}

	@Override
	public String getWhereClause(Iterable<String> whereClause) {
		return buildWhere(whereClause);
	}

	@Override
	public String getInCriteria(String leftHandSide, Iterable<String> matchCriteria) {
		final var matches = toList(matchCriteria);
		if (matches.isEmpty())
			return "1 = 0";
		return leftHandSide + " IN (" + String.join(", ", matches) + ")";
	}

	@Override
	public String getMultipartKeyListMatch(Iterable<String> leftHandSide, Iterable<Iterable<String>> matchCriteria) {
		final var left = toList(leftHandSide);
		final var matches = new ArrayList<String>();
		for (final var match : matchCriteria)
			matches.add("(" + String.join(", ", toList(match)) + ")");
		if (left.isEmpty() || matches.isEmpty())
			return "1 = 0";
		return "(" + String.join(", ", left) + ") IN (" + String.join(", ", matches) + ")";
	}

	@Override
	public String getColumnMatchCriteria(Class<?> entityClass, String col, Iterable<String> matchList) {
		return getInCriteria(columnReference(getEntityMetaData(entityClass), null, col), matchList);
	}

	@Override
	public String getJoinedColumnMatchCriteria(Class<?> entityClass, String tab, String col,
			Iterable<String> matchList) {
		return getInCriteria(columnReference(getEntityMetaData(entityClass), tab, col), matchList);
	}

	@Override
	public String getColumnCompareCriteria(Class<?> entityClass, String col, String comp, Iterable<String> matchList) {
		return getJoinedColumnCompareCriteria(entityClass, null, col, comp, matchList);
	}

	@Override
	public String getJoinedColumnCompareCriteria(Class<?> entityClass, String tab, String col, String comp,
			Iterable<String> matchList) {
		final var matches = toList(matchList);
		if (matches.isEmpty())
			return "1 = 0";
		final var reference = columnReference(getEntityMetaData(entityClass), tab, col);
		if (matches.size() == 1)
			return reference + " " + comp + " " + matches.get(0);
		final var criteria = new ArrayList<String>();
		for (final var match : matches)
			criteria.add(reference + " " + comp + " " + match);
		return "(" + String.join(" OR ", criteria) + ")";
	}

	@Override
	public String getEffectiveDateCriteria(Class<?> entityClass, String effectiveDatedColumnName) {
		return getAsOfEffectiveDateCriteria(entityClass, effectiveDatedColumnName, "CURRENT_DATE");
	}

	@Override
	public String getAsOfEffectiveDateCriteria(Class<?> entityClass, String effectiveDatedColumnName, String asOfDate) {
		final var entity = getEntityMetaData(entityClass);
		return entity.buildEffectiveDateCriteria(entity.primaryTable.alias, effectiveDatedColumnName, asOfDate, false,
				entity.effectiveDateKeyColumns(effectiveDatedColumnName, new String[0], new String[0]));
	}

	@Override
	public String getEffectiveDateSeqCriteria(Class<?> entityClass, String effectiveDatedColumnName, String seqcol) {
		return getAsOfEffectiveDateSeqCriteria(entityClass, effectiveDatedColumnName, seqcol, "CURRENT_DATE");
	}

	@Override
	public String getAsOfEffectiveDateSeqCriteria(Class<?> entityClass, String effectiveDatedColumnName,
			String sequenceColumnName, String asOfDate) {
		final var entity = getEntityMetaData(entityClass);
		return entity.buildEffectiveDateSeqCriteria(entity.primaryTable.alias, effectiveDatedColumnName,
				sequenceColumnName, asOfDate,
				entity.effectiveDateKeyColumns(effectiveDatedColumnName, new String[0], new String[0]));
	}

	@Override
	public String getMaxDateCriteria(Class<?> entityClass, String maxDateColumn) {
		final var entity = getEntityMetaData(entityClass);
		return entity.buildMaxDateCriteria(entity.primaryTable.alias, maxDateColumn,
				entity.effectiveDateKeyColumns(maxDateColumn, new String[0], new String[0]));
	}

	@Override
	public String getFutureEffectiveDateCriteria(Class<?> entityClass, String effectiveDatedColumnName,
			String asOfDate) {
		final var entity = getEntityMetaData(entityClass);
		return entity.buildEffectiveDateCriteria(entity.primaryTable.alias, effectiveDatedColumnName, asOfDate, true,
				entity.effectiveDateKeyColumns(effectiveDatedColumnName, new String[0], new String[0]));
	}

	@Override
	public String getJoinedEffectiveDateCriteria(Class<?> entityClass, String table, String effectiveDatedColumnName,
			Iterable<String> idColumnNames) {
		return getJoinedAsOfEffectiveDateCriteria(entityClass, table, effectiveDatedColumnName, "CURRENT_DATE",
				idColumnNames);
	}

	@Override
	public String getJoinedFutureEffectiveDateCriteria(Class<?> entityClass, String table,
			String effectiveDatedColumnName, Iterable<String> idColumnNames) {
		return getJoinedFutureEffectiveDateCriteria(entityClass, table, effectiveDatedColumnName, "CURRENT_DATE",
				idColumnNames);
	}

	@Override
	public String getJoinedFutureEffectiveDateCriteria(Class<?> entityClass, String table,
			String effectiveDatedColumnName, String asOfDate, Iterable<String> idColumnNames) {
		final var entity = getEntityMetaData(entityClass);
		return entity.buildEffectiveDateCriteria(entity.resolveAlias(table), effectiveDatedColumnName, asOfDate, true,
				idColumnNames);
	}

	@Override
	public String getJoinedAsOfEffectiveDateCriteria(Class<?> entityClass, String table,
			String effectiveDatedColumnName, String asOfDate, Iterable<String> idColumnNames) {
		final var entity = getEntityMetaData(entityClass);
		return entity.buildEffectiveDateCriteria(entity.resolveAlias(table), effectiveDatedColumnName, asOfDate, false,
				idColumnNames);
	}

	@Override
	public String getJoinedAsOfEffectiveDateSeqCriteria(Class<?> entityClass, String table,
			String effectiveDatedColumnName, String seqcol, String asOfDate, Iterable<String> idColumnNames) {
		final var entity = getEntityMetaData(entityClass);
		return entity.buildEffectiveDateSeqCriteria(entity.resolveAlias(table), effectiveDatedColumnName, seqcol,
				asOfDate, idColumnNames);
	}

	@Override
	public String buildForUpdateCursorQueryFromBean(Class<?> entityClass, Iterable<String> whereClauses) {
		return getEntityMetaData(entityClass).getSelectStatement(null, whereClauses, null, true);
	}

	@Override
	public Map<String, Object> mapIdColumnsToSqlTypes(Object entity) {
		final var entityClass = entity instanceof Class<?> clazz ? clazz : entity.getClass();
		final var meta = getEntityMetaData(entityClass);
		final var map = new LinkedHashMap<String, Object>();
		for (final var column : meta.idColumns)
			map.put(column.columnName, getSqlType(entityClass, column.propertyName));
		return map;
	}

	@Override
	public Map<String, Object> mapColumnsToSqlTypes(Object entity) {
		final var entityClass = entity instanceof Class<?> clazz ? clazz : entity.getClass();
		final var meta = getEntityMetaData(entityClass);
		final var map = new LinkedHashMap<String, Object>();
		for (final var column : meta.columns.values())
			if (column.columnName != null)
				map.put(column.columnName, getSqlType(entityClass, column.propertyName));
		return map;
	}

	@Override
	public String buildWhereClause(Iterable<?> entities) {
		if (entities == null)
			return "1 = 0";
		final var disjuncts = new ArrayList<String>();
		for (final var entity : entities) {
			if (entity == null)
				continue;
			final var meta = getEntityMetaData(entity.getClass());
			final var criteria = new ArrayList<String>();
			for (final var idColumn : meta.idColumns) {
				final var value = getPropertyValue(entity, idColumn.property);
				criteria.add(idColumn.reference() + (value == null ? " IS NULL" : " = " + getLiteral(value)));
			}
			if (!criteria.isEmpty())
				disjuncts.add("(" + String.join(" AND ", criteria) + ")");
		}
		if (disjuncts.isEmpty())
			return "1 = 0";
		return String.join(" OR ", disjuncts);
	}

	@Override
	public String getPropertyNameFromBean(Class<?> entityClass, String columnName) {
		return getEntityMetaData(entityClass).columnToPropertyNames.get(normalizeName(columnName));
	}
}
