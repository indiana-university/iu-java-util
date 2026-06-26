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
package edu.iu.dao;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds SQL statements and related metadata for JPA-annotated beans.
 */
public interface IuSqlBuilder {

	/**
	 * Gets the generated alias for a mapped table.
	 *
	 * @param entityClass entity class
	 * @param table       mapped table name, qualified table name, or known alias
	 * @return table alias
	 */
	String getTableAlias(Class<?> entityClass, String table);

	/**
	 * Gets the mapped primary-key property names.
	 *
	 * @param entityClass entity class
	 * @return primary-key property names
	 */
	Iterable<String> getPrimaryKeyProperties(Class<?> entityClass);

	/**
	 * Builds the default {@code SELECT ... FROM ...} clause for the entity.
	 *
	 * @param entityClass entity class
	 * @return select and from clause
	 */
	String getSelectAndFromClause(Class<?> entityClass);

	/**
	 * Builds the {@code SELECT ... FROM ...} clause for the supplied properties.
	 *
	 * @param entityClass entity class
	 * @param props       properties or mapped SQL expressions to include
	 * @return select and from clause
	 */
	String getSelectAndFromClause(Class<?> entityClass, Iterable<String> props);

	/**
	 * Builds a select statement.
	 *
	 * @param entityClass entity class
	 * @param where       where conditions
	 * @return select statement
	 */
	String getSelectStatement(Class<?> entityClass, Iterable<String> where);

	/**
	 * Builds an ordered select statement.
	 *
	 * @param entityClass entity class
	 * @param where       where conditions
	 * @param order       order expressions
	 * @return select statement
	 */
	String getOrderedSelectStatement(Class<?> entityClass, Iterable<String> where, Iterable<String> order);

	/**
	 * Builds a select-for-update statement.
	 *
	 * @param entityClass entity class
	 * @param where       where conditions
	 * @param lockTimeout ignored lock timeout retained for compatibility
	 * @return select statement
	 */
	String getSelectStatement(Class<?> entityClass, Iterable<String> where, int lockTimeout);

	/**
	 * Builds an ordered select-for-update statement.
	 *
	 * @param entityClass entity class
	 * @param where       where conditions
	 * @param order       order expressions
	 * @param lockTimeout ignored lock timeout retained for compatibility
	 * @return select statement
	 */
	String getSelectStatement(Class<?> entityClass, Iterable<String> where, Iterable<String> order, int lockTimeout);

	/**
	 * Builds a select statement for explicit properties.
	 *
	 * @param entityClass entity class
	 * @param props       selected properties
	 * @param where       where conditions
	 * @return select statement
	 */
	String getSelectStatement(Class<?> entityClass, Iterable<String> props, Iterable<String> where);

	/**
	 * Builds key criteria using bean properties.
	 *
	 * @param entityClass entity class
	 * @param idprops     id property values by property or column name
	 * @return key criteria fragments
	 */
	Iterable<String> getBeanKeyCriteria(Class<?> entityClass, Map<String, ?> idprops);

	/**
	 * Gets arguments for the key criteria built by
	 * {@link #getBeanKeyCriteria(Class, Map)}.
	 *
	 * @param entityClass entity class
	 * @param idprops     id property values by property or column name
	 * @return bind arguments
	 */
	Iterable<?> getBeanKeyArgs(Class<?> entityClass, Map<String, ?> idprops);

	/**
	 * Gets updateable properties for an entity, treating all primary-table non-id
	 * columns as changed.
	 *
	 * @param entity entity
	 * @return changed properties
	 */
	Iterable<String> getUpdateProperties(Object entity);

	/**
	 * Gets updateable properties that differ from an original entity.
	 *
	 * @param entity   updated entity
	 * @param original original entity
	 * @return changed properties
	 */
	Iterable<String> getUpdateProperties(Object entity, Object original);

	/**
	 * Gets updateable properties that differ from a lazily supplied original
	 * entity.
	 *
	 * @param entity  updated entity
	 * @param passive original entity supplier
	 * @return changed properties
	 */
	Iterable<String> getUpdateProperties(Object entity, Supplier<Object> passive);

	/**
	 * Gets bind arguments for an update statement.
	 *
	 * @param entity     entity
	 * @param properties properties being updated
	 * @return update arguments
	 */
	Iterable<?> getUpdateArguments(Object entity, Iterable<String> properties);

	/**
	 * Builds an update statement for the supplied properties.
	 *
	 * @param entityClass entity class
	 * @param properties  properties being updated
	 * @return update statement
	 */
	String getUpdateStatement(Class<?> entityClass, Iterable<String> properties);

	/**
	 * Gets delete arguments for an entity.
	 *
	 * @param entity entity
	 * @return delete arguments
	 */
	Iterable<?> getDeleteArguments(Object entity);

	/**
	 * Builds a delete statement for an entity.
	 *
	 * @param entityClass entity class
	 * @return delete statement
	 */
	String getDeleteStatement(Class<?> entityClass);

	/**
	 * Builds an insert statement for an entity.
	 *
	 * @param entityClass entity class
	 * @return insert statement
	 */
	String getInsertStatement(Class<?> entityClass);

	/**
	 * Gets insert arguments for an entity.
	 *
	 * @param entity entity
	 * @return insert arguments
	 */
	Iterable<?> getInsertArguments(Object entity);

	/**
	 * Gets a property value prepared for SQL binding.
	 *
	 * @param entity       entity
	 * @param propertyName property name
	 * @return value prepared for SQL binding
	 */
	Object getForSql(Object entity, String propertyName);

	/**
	 * Gets a SQL literal for a value.
	 *
	 * @param o value
	 * @return SQL literal
	 */
	String getLiteral(Object o);

	/**
	 * Maps values to SQL literals.
	 *
	 * @param o values
	 * @return SQL literals
	 */
	Iterable<String> getLiterals(Iterable<Object> o);

	/**
	 * Builds a where clause from criteria.
	 *
	 * @param whereClause criteria
	 * @return where clause, including the {@code WHERE} keyword when criteria are
	 *         present
	 */
	String getWhereClause(Iterable<String> whereClause);

	/**
	 * Builds an {@code IN} criterion.
	 *
	 * @param leftHandSide  column or expression
	 * @param matchCriteria literals or parameter placeholders
	 * @return criterion
	 */
	String getInCriteria(String leftHandSide, Iterable<String> matchCriteria);

	/**
	 * Builds a multi-column list match criterion.
	 *
	 * @param leftHandSide  left-hand column expressions
	 * @param matchCriteria right-hand tuples
	 * @return criterion
	 */
	String getMultipartKeyListMatch(Iterable<String> leftHandSide, Iterable<Iterable<String>> matchCriteria);

	/**
	 * Builds a primary-table column match criterion.
	 *
	 * @param entityClass entity class
	 * @param col         column or property name
	 * @param matchList   literals or placeholders
	 * @return criterion
	 */
	String getColumnMatchCriteria(Class<?> entityClass, String col, Iterable<String> matchList);

	/**
	 * Builds a joined-table column match criterion.
	 *
	 * @param entityClass entity class
	 * @param tab         table name, qualified table name, or alias
	 * @param col         column or property name
	 * @param matchList   literals or placeholders
	 * @return criterion
	 */
	String getJoinedColumnMatchCriteria(Class<?> entityClass, String tab, String col, Iterable<String> matchList);

	/**
	 * Builds a primary-table comparison criterion.
	 *
	 * @param entityClass entity class
	 * @param col         column or property name
	 * @param comp        comparison operator
	 * @param matchList   literals or placeholders
	 * @return criterion
	 */
	String getColumnCompareCriteria(Class<?> entityClass, String col, String comp, Iterable<String> matchList);

	/**
	 * Builds a joined-table comparison criterion.
	 *
	 * @param entityClass entity class
	 * @param tab         table name, qualified table name, or alias
	 * @param col         column or property name
	 * @param comp        comparison operator
	 * @param matchList   literals or placeholders
	 * @return criterion
	 */
	String getJoinedColumnCompareCriteria(Class<?> entityClass, String tab, String col, String comp,
			Iterable<String> matchList);

	/**
	 * Builds current-row effective-date criteria using {@code CURRENT_DATE}.
	 *
	 * @param entityClass              entity class
	 * @param effectiveDatedColumnName effective-dated column
	 * @return criterion
	 */
	String getEffectiveDateCriteria(Class<?> entityClass, String effectiveDatedColumnName);

	/**
	 * Builds as-of effective-date criteria.
	 *
	 * @param entityClass              entity class
	 * @param effectiveDatedColumnName effective-dated column
	 * @param asOfDate                 SQL expression used as the as-of date
	 * @return criterion
	 */
	String getAsOfEffectiveDateCriteria(Class<?> entityClass, String effectiveDatedColumnName, String asOfDate);

	/**
	 * Builds effective-date plus sequence criteria using {@code CURRENT_DATE}.
	 *
	 * @param entityClass              entity class
	 * @param effectiveDatedColumnName effective-dated column
	 * @param seqcol                   sequence column
	 * @return criterion
	 */
	String getEffectiveDateSeqCriteria(Class<?> entityClass, String effectiveDatedColumnName, String seqcol);

	/**
	 * Builds as-of effective-date plus sequence criteria.
	 *
	 * @param entityClass              entity class
	 * @param effectiveDatedColumnName effective-dated column
	 * @param sequenceColumnName       sequence column
	 * @param asOfDate                 SQL expression used as the as-of date
	 * @return criterion
	 */
	String getAsOfEffectiveDateSeqCriteria(Class<?> entityClass, String effectiveDatedColumnName,
			String sequenceColumnName, String asOfDate);

	/**
	 * Builds max-date criteria.
	 *
	 * @param entityClass   entity class
	 * @param maxDateColumn max-date column
	 * @return criterion
	 */
	String getMaxDateCriteria(Class<?> entityClass, String maxDateColumn);

	/**
	 * Builds future effective-date criteria.
	 *
	 * @param entityClass              entity class
	 * @param effectiveDatedColumnName effective-dated column
	 * @param asOfDate                 SQL expression used as the as-of date
	 * @return criterion
	 */
	String getFutureEffectiveDateCriteria(Class<?> entityClass, String effectiveDatedColumnName, String asOfDate);

	/**
	 * Builds joined effective-date criteria using {@code CURRENT_DATE}.
	 *
	 * @param entityClass              entity class
	 * @param table                    table name, qualified table name, or alias
	 *                                 for the outer reference
	 * @param effectiveDatedColumnName effective-dated column
	 * @param idColumnNames            correlated id columns
	 * @return criterion
	 */
	String getJoinedEffectiveDateCriteria(Class<?> entityClass, String table, String effectiveDatedColumnName,
			Iterable<String> idColumnNames);

	/**
	 * Builds joined future effective-date criteria using {@code CURRENT_DATE}.
	 *
	 * @param entityClass              entity class
	 * @param table                    table name, qualified table name, or alias
	 *                                 for the outer reference
	 * @param effectiveDatedColumnName effective-dated column
	 * @param idColumnNames            correlated id columns
	 * @return criterion
	 */
	String getJoinedFutureEffectiveDateCriteria(Class<?> entityClass, String table, String effectiveDatedColumnName,
			Iterable<String> idColumnNames);

	/**
	 * Builds joined future effective-date criteria.
	 *
	 * @param entityClass              entity class
	 * @param table                    table name, qualified table name, or alias
	 *                                 for the outer reference
	 * @param effectiveDatedColumnName effective-dated column
	 * @param asOfDate                 SQL expression used as the as-of date
	 * @param idColumnNames            correlated id columns
	 * @return criterion
	 */
	String getJoinedFutureEffectiveDateCriteria(Class<?> entityClass, String table, String effectiveDatedColumnName,
			String asOfDate, Iterable<String> idColumnNames);

	/**
	 * Builds joined as-of effective-date criteria.
	 *
	 * @param entityClass              entity class
	 * @param table                    table name, qualified table name, or alias
	 *                                 for the outer reference
	 * @param effectiveDatedColumnName effective-dated column
	 * @param asOfDate                 SQL expression used as the as-of date
	 * @param idColumnNames            correlated id columns
	 * @return criterion
	 */
	String getJoinedAsOfEffectiveDateCriteria(Class<?> entityClass, String table, String effectiveDatedColumnName,
			String asOfDate, Iterable<String> idColumnNames);

	/**
	 * Builds joined as-of effective-date plus sequence criteria.
	 *
	 * @param entityClass              entity class
	 * @param table                    table name, qualified table name, or alias
	 *                                 for the outer reference
	 * @param effectiveDatedColumnName effective-dated column
	 * @param seqcol                   sequence column
	 * @param asOfDate                 SQL expression used as the as-of date
	 * @param idColumnNames            correlated id columns
	 * @return criterion
	 */
	String getJoinedAsOfEffectiveDateSeqCriteria(Class<?> entityClass, String table, String effectiveDatedColumnName,
			String seqcol, String asOfDate, Iterable<String> idColumnNames);

	/**
	 * Builds a select-for-update query from explicit where clauses.
	 *
	 * @param entityClass  entity class
	 * @param whereClauses where clauses
	 * @return select-for-update query
	 */
	String buildForUpdateCursorQueryFromBean(Class<?> entityClass, Iterable<String> whereClauses);

	/**
	 * Maps id columns to SQL-friendly Java types.
	 *
	 * @param entity entity instance or entity class
	 * @return id column type map
	 */
	Map<String, Object> mapIdColumnsToSqlTypes(Object entity);

	/**
	 * Maps columns to SQL-friendly Java types.
	 *
	 * @param entity entity instance or entity class
	 * @return column type map
	 */
	Map<String, Object> mapColumnsToSqlTypes(Object entity);

	/**
	 * Builds an {@code OR}-combined key clause for a collection of entities.
	 *
	 * @param entities entities
	 * @return criteria without the {@code WHERE} keyword
	 */
	String buildWhereClause(Iterable<?> entities);

	/**
	 * Resolves a property name from a mapped column name.
	 *
	 * @param entityClass entity class
	 * @param columnName  column name
	 * @return property name, or {@code null} when the column is unmapped
	 */
	String getPropertyNameFromBean(Class<?> entityClass, String columnName);
}
