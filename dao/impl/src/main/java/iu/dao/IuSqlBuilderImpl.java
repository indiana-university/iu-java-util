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

	@Override
	public String getTableAlias(Class<?> entityClass, String table) {
		return EntityMetaData.of(entityClass).resolveAlias(table);
	}

	@Override
	public Iterable<String> getPrimaryKeyProperties(Class<?> entityClass) {
		return IuIterable.map(EntityMetaData.of(entityClass).idColumns, c -> c.propertyName);
	}

	@Override
	public String getSelectAndFromClause(Class<?> entityClass) {
		return EntityMetaData.of(entityClass).getSelectAndFromClause(null);
	}

	@Override
	public String getSelectAndFromClause(Class<?> entityClass, Iterable<String> props) {
		return EntityMetaData.of(entityClass).getSelectAndFromClause(props);
	}

	@Override
	public String getSelectStatement(Class<?> entityClass, Iterable<String> where) {
		return EntityMetaData.of(entityClass).getSelectStatement(null, where, null, false);
	}

	@Override
	public String getOrderedSelectStatement(Class<?> entityClass, Iterable<String> where, Iterable<String> order) {
		return EntityMetaData.of(entityClass).getSelectStatement(null, where, order, false);
	}

	@Override
	public String getSelectStatement(Class<?> entityClass, Iterable<String> where, int lockTimeout) {
		return EntityMetaData.of(entityClass).getSelectStatement(null, where, null, true);
	}

	@Override
	public String getSelectStatement(Class<?> entityClass, Iterable<String> where, Iterable<String> order,
			int lockTimeout) {
		return EntityMetaData.of(entityClass).getSelectStatement(null, where, order, true);
	}

	@Override
	public String getSelectStatement(Class<?> entityClass, Iterable<String> props, Iterable<String> where) {
		return EntityMetaData.of(entityClass).getSelectStatement(props, where, null, false);
	}

	@Override
	public Iterable<String> getBeanKeyCriteria(Class<?> entityClass, Map<String, ?> idprops) {
		final var entity = EntityMetaData.of(entityClass);
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
		final var entity = EntityMetaData.of(entityClass);
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
			args.add(column.normalizeArgument(getPropertyValue(entity, column.property)));
		}
		for (final var idColumn : meta.idColumns)
			args.add(idColumn.normalizeArgument(getPropertyValue(entity, idColumn.property)));
		return args;
	}

	@Override
	public String getUpdateStatement(Class<?> entityClass, Iterable<String> properties) {
		return EntityMetaData.of(entityClass).getUpdateStatement(properties);
	}

	@Override
	public Iterable<?> getDeleteArguments(Object entity) {
		Objects.requireNonNull(entity, "entity");
		final var meta = getEntityMetaData(entity.getClass());
		final var args = new ArrayList<>();
		for (final var idColumn : meta.idColumns)
			args.add(idColumn.normalizeArgument(getPropertyValue(entity, idColumn.property)));
		return args;
	}

	@Override
	public String getDeleteStatement(Class<?> entityClass) {
		return EntityMetaData.of(entityClass).getDeleteStatement();
	}

	@Override
	public String getInsertStatement(Class<?> entityClass) {
		return EntityMetaData.of(entityClass).getInsertStatement();
	}

	@Override
	public Iterable<?> getInsertArguments(Object entity) {
		Objects.requireNonNull(entity, "entity");
		final var meta = getEntityMetaData(entity.getClass());
		final var args = new ArrayList<>();
		for (final var column : meta.primaryColumns)
			args.add(column.normalizeArgument(getPropertyValue(entity, column.property)));
		return args;
	}

	@Override
	public Object getForSql(Object entity, String propertyName) {
		Objects.requireNonNull(entity, "entity");
		final var meta = getEntityMetaData(entity.getClass());
		final var column = meta.resolveColumn(propertyName);
		if (column == null)
			throw new IllegalArgumentException("Unknown property " + propertyName + " for " + entity.getClass());
		return column.normalizeArgument(getPropertyValue(entity, column.property));
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
		return getInCriteria(columnReference(EntityMetaData.of(entityClass), null, col), matchList);
	}

	@Override
	public String getJoinedColumnMatchCriteria(Class<?> entityClass, String tab, String col,
			Iterable<String> matchList) {
		return getInCriteria(columnReference(EntityMetaData.of(entityClass), tab, col), matchList);
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
		final var reference = columnReference(EntityMetaData.of(entityClass), tab, col);
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
		final var entity = EntityMetaData.of(entityClass);
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
		final var entity = EntityMetaData.of(entityClass);
		return entity.buildEffectiveDateSeqCriteria(entity.primaryTable.alias, effectiveDatedColumnName,
				sequenceColumnName, asOfDate,
				entity.effectiveDateKeyColumns(effectiveDatedColumnName, new String[0], new String[0]));
	}

	@Override
	public String getMaxDateCriteria(Class<?> entityClass, String maxDateColumn) {
		final var entity = EntityMetaData.of(entityClass);
		return entity.buildMaxDateCriteria(entity.primaryTable.alias, maxDateColumn,
				entity.effectiveDateKeyColumns(maxDateColumn, new String[0], new String[0]));
	}

	@Override
	public String getFutureEffectiveDateCriteria(Class<?> entityClass, String effectiveDatedColumnName,
			String asOfDate) {
		final var entity = EntityMetaData.of(entityClass);
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
		final var entity = EntityMetaData.of(entityClass);
		return entity.buildEffectiveDateCriteria(entity.resolveAlias(table), effectiveDatedColumnName, asOfDate, true,
				idColumnNames);
	}

	@Override
	public String getJoinedAsOfEffectiveDateCriteria(Class<?> entityClass, String table,
			String effectiveDatedColumnName, String asOfDate, Iterable<String> idColumnNames) {
		final var entity = EntityMetaData.of(entityClass);
		return entity.buildEffectiveDateCriteria(entity.resolveAlias(table), effectiveDatedColumnName, asOfDate, false,
				idColumnNames);
	}

	@Override
	public String getJoinedAsOfEffectiveDateSeqCriteria(Class<?> entityClass, String table,
			String effectiveDatedColumnName, String seqcol, String asOfDate, Iterable<String> idColumnNames) {
		final var entity = EntityMetaData.of(entityClass);
		return entity.buildEffectiveDateSeqCriteria(entity.resolveAlias(table), effectiveDatedColumnName, seqcol,
				asOfDate, idColumnNames);
	}

	@Override
	public String buildForUpdateCursorQueryFromBean(Class<?> entityClass, Iterable<String> whereClauses) {
		return EntityMetaData.of(entityClass).getSelectStatement(null, whereClauses, null, true);
	}

	@Override
	public Map<String, Object> mapIdColumnsToSqlTypes(Object entity) {
		final var entityClass = entity instanceof Class<?> clazz ? clazz : entity.getClass();
		final var meta = EntityMetaData.of(entityClass);
		final var map = new LinkedHashMap<String, Object>();
		for (final var column : meta.idColumns)
			map.put(column.columnName, DaoUtils.getSqlType(entityClass, column.propertyName));
		return map;
	}

	@Override
	public Map<String, Object> mapColumnsToSqlTypes(Object entity) {
		final var entityClass = entity instanceof Class<?> clazz ? clazz : entity.getClass();
		final var meta = EntityMetaData.of(entityClass);
		final var map = new LinkedHashMap<String, Object>();
		for (final var column : meta.columns.values())
			if (column.columnName != null)
				map.put(column.columnName, DaoUtils.getSqlType(entityClass, column.propertyName));
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
			final var meta = EntityMetaData.of(entity.getClass());
			final var criteria = new ArrayList<String>();
			for (final var idColumn : meta.idColumns) {
				final var value = DaoUtils.getPropertyValue(entity, idColumn.property);
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
		return EntityMetaData.of(entityClass).columnToPropertyNames.get(DaoUtils.normalizeName(columnName));
	}
}
