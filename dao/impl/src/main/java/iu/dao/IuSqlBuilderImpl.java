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

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Supplier;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.dao.IuSqlBuilder;

/**
 * Default {@link IuSqlBuilder} implementation.
 */
public class IuSqlBuilderImpl implements IuSqlBuilder {

	/**
	 * Default constructor.
	 */
	public IuSqlBuilderImpl() {
	}

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
		final Queue<String> criteria = new ArrayDeque<>();
		for (final var idColumn : entity.idColumns) {
			final Object value;
			if (idprops.containsKey(idColumn.propertyName))
				value = idprops.get(idColumn.propertyName);
			else if (idprops.containsKey(idColumn.columnName))
				value = idprops.get(idColumn.columnName);
			else
				continue;
			criteria.offer(value == null ? idColumn.reference() + " IS NULL" : idColumn.reference() + " = ?");
		}
		return criteria::iterator;
	}

	@Override
	public Iterable<?> getBeanKeyArgs(Class<?> entityClass, Map<String, ?> idprops) {
		final var entity = EntityMetaData.of(entityClass);
		final Queue<Object> args = new ArrayDeque<>();
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
		return args::iterator;
	}

	@Override
	public Iterable<String> getUpdateProperties(Object entity) {
		return IuIterable.map(
				EntityMetaData.of(Objects.requireNonNull(entity, "entity").getClass()).primaryNonIdColumns,
				c -> c.propertyName);
	}

	@Override
	public Iterable<String> getUpdateProperties(Object entity, Object original) {
		if (original == null)
			return getUpdateProperties(entity);

		final var meta = EntityMetaData.of(Objects.requireNonNull(entity, "entity").getClass());
		final Queue<String> properties = new ArrayDeque<>();
		for (final var column : meta.primaryNonIdColumns) {
			final var currentValue = DaoUtils.getPropertyValue(entity, column.property);
			final var originalValue = DaoUtils.getPropertyValue(original, column.property);
			if (!IuObject.equals(currentValue, originalValue))
				properties.offer(column.propertyName);
		}
		return properties::iterator;
	}

	@Override
	public Iterable<String> getUpdateProperties(Object entity, Supplier<Object> passive) {
		return getUpdateProperties(entity, passive == null ? null : passive.get());
	}

	@Override
	public Iterable<?> getUpdateArguments(Object entity, Iterable<String> properties) {
		final var meta = EntityMetaData.of(Objects.requireNonNull(entity, "entity").getClass());
		final Queue<Object> args = new ArrayDeque<>();

		for (final var property : properties) {
			final var column = meta.requirePrimaryColumn(property);
			args.add(column.normalizeArgument(DaoUtils.getPropertyValue(entity, column.property)));
		}

		for (final var idColumn : meta.idColumns)
			args.add(idColumn.normalizeArgument(DaoUtils.getPropertyValue(entity, idColumn.property)));

		return args::iterator;
	}

	@Override
	public String getUpdateStatement(Class<?> entityClass, Iterable<String> properties) {
		return EntityMetaData.of(entityClass).getUpdateStatement(properties);
	}

	@Override
	public Iterable<?> getDeleteArguments(Object entity) {
		final var meta = EntityMetaData.of(Objects.requireNonNull(entity, "entity").getClass());
		final Queue<Object> args = new ArrayDeque<>();

		for (final var idColumn : meta.idColumns)
			args.add(idColumn.normalizeArgument(DaoUtils.getPropertyValue(entity, idColumn.property)));

		return args::iterator;
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
		final var meta = EntityMetaData.of(Objects.requireNonNull(entity, "entity").getClass());
		final Queue<Object> args = new ArrayDeque<>();

		for (final var column : meta.primaryColumns)
			args.add(column.normalizeArgument(DaoUtils.getPropertyValue(entity, column.property)));

		return args::iterator;
	}

	@Override
	public Object getForSql(Object entity, String propertyName) {
		final var meta = EntityMetaData.of(Objects.requireNonNull(entity, "entity").getClass());
		final var column = meta.resolveColumn(propertyName);
		if (column == null)
			throw new IllegalArgumentException("Unknown property " + propertyName + " for " + entity.getClass());
		return column.normalizeArgument(DaoUtils.getPropertyValue(entity, column.property));
	}

	@Override
	public String getLiteral(Object o) {
		if (o == null)
			return "NULL";
		if (o instanceof String s)
			return DaoUtils.singleQuoted(s);
		if (o instanceof Date date)
			return "DATE '" + date.toLocalDate() + "'";
		if (o instanceof Timestamp timestamp)
			return DaoUtils.literalFromDate(timestamp);
		if (o instanceof java.util.Date date)
			return DaoUtils.literalFromDate(date);
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
		return DaoUtils.buildWhere(whereClause);
	}

	@Override
	public String getInCriteria(String leftHandSide, Iterable<String> matchCriteria) {
		return leftHandSide + " IN (" + String.join(", ", matchCriteria) + ")";
	}

	@Override
	public String getMultipartKeyListMatch(Iterable<String> leftHandSides, Iterable<Iterable<String>> match) {
		Queue<Iterator<String>> valueQueue = new ArrayDeque<>();
		for (Iterable<String> m : match) {
			Iterator<String> i = m.iterator();
			if (!i.hasNext())
				throw new IllegalArgumentException("No values to match against");
			valueQueue.offer(i);
		}

		final var sb = new StringBuilder();
		boolean firstValue = true;
		boolean done = false;
		while (!done) {
			if (firstValue) {
				sb.append('(');
				firstValue = false;
			} else
				sb.append("\n    OR ");

			boolean firstColumn = true;
			Iterator<Iterator<String>> valueIterator = valueQueue.iterator();
			for (String left : leftHandSides) {
				Iterator<String> rightValues = valueIterator.next();

				if (firstColumn) {
					sb.append('(');
					firstColumn = false;
				} else
					sb.append(" AND ");

				sb.append(left);
				String right = rightValues.next();
				if (right == null)
					sb.append(" IS NULL");
				else
					sb.append(" = ").append(right);

				done = !rightValues.hasNext();
			}
			sb.append(')');
		}
		sb.append(')');
		return sb.toString();
	}

	@Override
	public String getColumnMatchCriteria(Class<?> entityClass, String col, Iterable<String> matchList) {
		return EntityMetaData.of(entityClass).getColumnMatchCriteria(col, matchList);
	}

	@Override
	public String getJoinedColumnMatchCriteria(Class<?> entityClass, String tab, String col,
			Iterable<String> matchList) {
		return getInCriteria(EntityMetaData.of(entityClass).columnReference(tab, col), matchList);
	}

	@Override
	public String getColumnCompareCriteria(Class<?> entityClass, String col, String comp, Iterable<String> matchList) {
		return EntityMetaData.of(entityClass).getColumnCompareCriteria(col, comp, matchList);
	}

	@Override
	public String getJoinedColumnCompareCriteria(Class<?> entityClass, String tab, String col, String comp,
			Iterable<String> matchList) {
		return EntityMetaData.of(entityClass).getJoinedColumnCompareCriteria(tab, col, comp, matchList);
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
			return null;

		final var i = IuIterable.filter(entities, Objects::nonNull).iterator();
		if (!i.hasNext())
			return null;

		final var firstEntity = i.next();
		final var entityClass = firstEntity.getClass();
		final var meta = EntityMetaData.of(entityClass);
		if (!meta.idColumns.iterator().hasNext())
			return null;

		final Queue<String> disjuncts = new ArrayDeque<>();
		for (final var entity : entities) {
			if (entity == null)
				continue;

			entityClass.cast(entity);

			final Queue<String> criteria = new ArrayDeque<>();
			for (final var idColumn : meta.idColumns) {
				final var value = DaoUtils.getPropertyValue(entity, idColumn.property);
				criteria.offer(idColumn.reference() + (value == null ? " IS NULL" : " = " + getLiteral(value)));
			}

			disjuncts.add("(" + String.join(" AND ", criteria) + ")");
		}

		return String.join(" OR ", disjuncts);
	}

	@Override
	public String getPropertyNameFromBean(Class<?> entityClass, String columnName) {
		return EntityMetaData.of(entityClass).columnToPropertyNames.get(DaoUtils.normalizeName(columnName));
	}
}
