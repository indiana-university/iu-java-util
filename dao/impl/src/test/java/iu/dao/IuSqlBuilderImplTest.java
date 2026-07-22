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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import edu.iu.dao.EffectiveDated;
import edu.iu.dao.SpaceForNull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;

@SuppressWarnings("javadoc")
public class IuSqlBuilderImplTest {

	// =======================================================================
	// Test entity classes
	// =======================================================================

	/** Basic entity: one @Id column and one non-id column. */
	@Entity
	@Table(name = "items", schema = "s")
	public static class SimpleEntity {
		private long id;
		private String label;

		@Id
		@Column(name = "ID")
		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}

		@Column(name = "LABEL")
		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}
	}

	/** Like SimpleEntity but with class-level @SpaceForNull. */
	@Entity
	@Table(name = "items", schema = "s")
	@SpaceForNull
	public static class SpaceForNullEntity extends SimpleEntity {
	}

	/** Entity with a secondary table, for table-alias and joined-criteria tests. */
	@Entity
	@Table(name = "pri_tbl", schema = "s")
	@SecondaryTable(name = "sec_tbl", pkJoinColumns = @PrimaryKeyJoinColumn(name = "SEC_ID"))
	public static class SecEntity {
		private long id;
		private String detail;

		@Id
		@Column(name = "ID")
		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}

		@Column(name = "DETAIL", table = "sec_tbl")
		public String getDetail() {
			return detail;
		}

		public void setDetail(String detail) {
			this.detail = detail;
		}
	}

	/** Effective-dated entity for effective-date criteria tests. */
	@Entity
	@Table(name = "prices", schema = "s")
	@EffectiveDated(effectiveDatedColumns = "EFF_DATE")
	public static class EffDateEntity {
		private long id;
		private java.sql.Date effDate;

		@Id
		@Column(name = "ID")
		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}

		@Column(name = "EFF_DATE")
		public java.sql.Date getEffDate() {
			return effDate;
		}

		public void setEffDate(java.sql.Date effDate) {
			this.effDate = effDate;
		}
	}

	/** Entity with nullable (boxed) @Id, so a null ID can be stored. */
	@Entity
	@Table(name = "items", schema = "s")
	public static class NullableIdEntity {
		private Long id;

		@Id
		@Column(name = "ID")
		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}
	}

	/** Entity with no @Id columns, for buildWhereClause null-return path. */
	@Entity
	@Table(name = "items", schema = "s")
	public static class NoIdEntity {
		private String name;

		@Column(name = "NAME")
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	/**
	 * Entity with one @Column property and one @SqlColumn property.
	 * The @SqlColumn property has a null columnName and must be excluded from
	 * mapColumnsToSqlTypes.
	 */
	@Entity
	@Table(name = "items", schema = "s")
	public static class SqlColumnEntity {
		private long id;
		private String expr;

		@Id
		@Column(name = "ID")
		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}

		@edu.iu.dao.SqlColumn("UPPER(label)")
		public String getExpr() {
			return expr;
		}

		public void setExpr(String expr) {
			this.expr = expr;
		}
	}

	// shared instance
	private final IuSqlBuilderImpl sqlBuilder = new IuSqlBuilderImpl();

	// =======================================================================
	// getTableAlias
	// =======================================================================

	@Test
	public void testGetTableAlias_primaryTable_returnsA() {
		assertEquals("a", sqlBuilder.getTableAlias(SimpleEntity.class, "items"));
	}

	@Test
	public void testGetTableAlias_secondaryTable_returnsB() {
		assertEquals("b", sqlBuilder.getTableAlias(SecEntity.class, "sec_tbl"));
	}

	@Test
	public void testGetTableAlias_knownAlias_returnsSameAlias() {
		assertEquals("a", sqlBuilder.getTableAlias(SimpleEntity.class, "a"));
	}

	// =======================================================================
	// getPrimaryKeyProperties
	// =======================================================================

	@Test
	public void testGetPrimaryKeyProperties_returnsPkPropertyName() {
		final var keys = IuIterable.stream(sqlBuilder.getPrimaryKeyProperties(SimpleEntity.class)).toList();
		assertEquals(List.of("id"), keys);
	}

	// =======================================================================
	// getSelectAndFromClause
	// =======================================================================

	@Test
	public void testGetSelectAndFromClause_noProps_containsAllColumns() {
		final var sql = sqlBuilder.getSelectAndFromClause(SimpleEntity.class);
		assertTrue(sql.startsWith("SELECT"));
		assertTrue(sql.contains("a.ID"));
		assertTrue(sql.contains("a.LABEL"));
		assertTrue(sql.contains("FROM s.items a"));
	}

	@Test
	public void testGetSelectAndFromClause_withProps_limitsColumns() {
		final var sql = sqlBuilder.getSelectAndFromClause(SimpleEntity.class, List.of("id"));
		assertTrue(sql.contains("a.ID"));
		assertFalse(sql.contains("a.LABEL"));
	}

	// =======================================================================
	// getSelectStatement variants
	// =======================================================================

	@Test
	public void testGetSelectStatement_basic_noForUpdate() {
		final var sql = sqlBuilder.getSelectStatement(SimpleEntity.class, List.of("a.ID = ?"));
		assertTrue(sql.contains("WHERE a.ID = ?"));
		assertFalse(sql.contains("FOR UPDATE"));
	}

	@Test
	public void testGetOrderedSelectStatement_containsOrderBy() {
		final var sql = sqlBuilder.getOrderedSelectStatement(SimpleEntity.class, List.of(), List.of("a.ID"));
		assertTrue(sql.contains("ORDER BY a.ID"));
		assertFalse(sql.contains("FOR UPDATE"));
	}

	@Test
	public void testGetSelectStatement_withLockTimeout_appendsForUpdate() {
		final var sql = sqlBuilder.getSelectStatement(SimpleEntity.class, List.of(), 0);
		assertTrue(sql.endsWith("\nFOR UPDATE"));
	}

	@Test
	public void testGetSelectStatement_withOrderAndLockTimeout_orderedForUpdate() {
		final var sql = sqlBuilder.getSelectStatement(SimpleEntity.class, List.of(), List.of("a.ID"), 0);
		assertTrue(sql.contains("ORDER BY a.ID"));
		assertTrue(sql.endsWith("\nFOR UPDATE"));
	}

	@Test
	public void testGetSelectStatement_withProps_limitsSelectColumns() {
		final var sql = sqlBuilder.getSelectStatement(SimpleEntity.class, List.of("id"), List.of());
		assertTrue(sql.contains("a.ID"));
		assertFalse(sql.contains("a.LABEL"));
	}

	// =======================================================================
	// getBeanKeyCriteria / getBeanKeyArgs
	// =======================================================================

	@Test
	public void testGetBeanKeyCriteria_byPropertyName_nonNull_producesEquality() {
		final var criteria = IuIterable.stream(
				sqlBuilder.getBeanKeyCriteria(SimpleEntity.class, Map.of("id", 42L))).toList();
		assertEquals(1, criteria.size());
		assertEquals("a.ID = ?", criteria.get(0));
	}

	@Test
	public void testGetBeanKeyCriteria_byColumnName_nonNull_producesEquality() {
		final var criteria = IuIterable.stream(
				sqlBuilder.getBeanKeyCriteria(SimpleEntity.class, Map.of("ID", 42L))).toList();
		assertEquals(1, criteria.size());
		assertEquals("a.ID = ?", criteria.get(0));
	}

	@Test
	public void testGetBeanKeyCriteria_nullValue_producesIsNull() {
		final var map = new java.util.HashMap<String, Object>();
		map.put("id", null);
		final var criteria = IuIterable.stream(
				sqlBuilder.getBeanKeyCriteria(SimpleEntity.class, map)).toList();
		assertEquals("a.ID IS NULL", criteria.get(0));
	}

	@Test
	public void testGetBeanKeyCriteria_unknownKey_producesNoCriteria() {
		final var criteria = IuIterable.stream(
				sqlBuilder.getBeanKeyCriteria(SimpleEntity.class, Map.of("unknown", 1L))).toList();
		assertTrue(criteria.isEmpty());
	}

	@Test
	public void testGetBeanKeyArgs_nonNullValue_includesArg() {
		final var args = IuIterable.stream(
				sqlBuilder.getBeanKeyArgs(SimpleEntity.class, Map.of("id", 42L))).toList();
		assertEquals(List.of(42L), args);
	}

	@Test
	public void testGetBeanKeyArgs_nullValue_excludedFromArgs() {
		final var map = new java.util.HashMap<String, Object>();
		map.put("id", null);
		final var args = IuIterable.stream(
				sqlBuilder.getBeanKeyArgs(SimpleEntity.class, map)).toList();
		assertTrue(args.isEmpty());
	}

	@Test
	public void testGetBeanKeyArgs_byColumnName_includesArg() {
		// key "ID" is the physical column name, not the property name "id"
		final var args = IuIterable.stream(
				sqlBuilder.getBeanKeyArgs(SimpleEntity.class, Map.of("ID", 77L))).toList();
		assertEquals(List.of(77L), args);
	}

	@Test
	public void testGetBeanKeyArgs_missingIdColumn_skipsAndReturnsEmpty() {
		// idprops has no entry for "id" / "ID" — the id column is skipped entirely
		final var args = IuIterable.stream(
				sqlBuilder.getBeanKeyArgs(SimpleEntity.class, Map.of("label", "foo"))).toList();
		assertTrue(args.isEmpty());
	}

	// =======================================================================

	@Test
	public void testGetUpdateProperties_entity_returnsAllNonIdColumns() {
		final var e = new SimpleEntity();
		final var props = IuIterable.stream(sqlBuilder.getUpdateProperties(e)).toList();
		assertEquals(List.of("label"), props);
	}

	@Test
	public void testGetUpdateProperties_nullOriginal_returnsAllNonIdColumns() {
		final var e = new SimpleEntity();
		final var props = IuIterable.stream(sqlBuilder.getUpdateProperties(e, (Object) null)).toList();
		assertEquals(List.of("label"), props);
	}

	@Test
	public void testGetUpdateProperties_sameAsOriginal_returnsEmpty() {
		final var e = new SimpleEntity();
		e.setLabel("same");
		final var o = new SimpleEntity();
		o.setLabel("same");
		final var props = IuIterable.stream(sqlBuilder.getUpdateProperties(e, o)).toList();
		assertTrue(props.isEmpty());
	}

	@Test
	public void testGetUpdateProperties_changedFromOriginal_returnsChangedProperty() {
		final var e = new SimpleEntity();
		e.setLabel("new");
		final var o = new SimpleEntity();
		o.setLabel("old");
		final var props = IuIterable.stream(sqlBuilder.getUpdateProperties(e, o)).toList();
		assertEquals(List.of("label"), props);
	}

	@Test
	public void testGetUpdateProperties_passiveSupplierNull_returnsAllNonIdColumns() {
		final var e = new SimpleEntity();
		final var props = IuIterable.stream(sqlBuilder.getUpdateProperties(e, (java.util.function.Supplier<Object>) null)).toList();
		assertEquals(List.of("label"), props);
	}

	@Test
	public void testGetUpdateProperties_passiveSupplierReturnsOriginal_returnsChangedOnly() {
		final var e = new SimpleEntity();
		e.setLabel("new");
		final var o = new SimpleEntity();
		o.setLabel("old");
		final var props = IuIterable.stream(sqlBuilder.getUpdateProperties(e, () -> o)).toList();
		assertEquals(List.of("label"), props);
	}

	// =======================================================================
	// getUpdateArguments / getUpdateStatement
	// =======================================================================

	@Test
	public void testGetUpdateArguments_orderIsSetColumnsThenIdColumns() {
		final var e = new SimpleEntity();
		e.setId(7L);
		e.setLabel("foo");
		// SET label=?, WHERE id=?  →  [foo, 7]
		final var args = IuIterable.stream(
				sqlBuilder.getUpdateArguments(e, List.of("label"))).toList();
		assertEquals(List.of("foo", 7L), args);
	}

	@Test
	public void testGetUpdateStatement_delegatesToEntityMetaData() {
		final var sql = sqlBuilder.getUpdateStatement(SimpleEntity.class, List.of("label"));
		assertTrue(sql.startsWith("UPDATE s.items"));
		assertTrue(sql.contains("LABEL = ?"));
	}

	// =======================================================================
	// getDeleteArguments / getDeleteStatement
	// =======================================================================

	@Test
	public void testGetDeleteArguments_returnsIdColumnValue() {
		final var e = new SimpleEntity();
		e.setId(99L);
		final var args = IuIterable.stream(sqlBuilder.getDeleteArguments(e)).toList();
		assertEquals(List.of(99L), args);
	}

	@Test
	public void testGetDeleteStatement_containsDeleteFrom() {
		final var sql = sqlBuilder.getDeleteStatement(SimpleEntity.class);
		assertTrue(sql.startsWith("DELETE FROM s.items"));
	}

	// =======================================================================
	// getInsertStatement / getInsertArguments
	// =======================================================================

	@Test
	public void testGetInsertStatement_containsInsertInto() {
		final var sql = sqlBuilder.getInsertStatement(SimpleEntity.class);
		assertTrue(sql.startsWith("INSERT INTO s.items"));
	}

	@Test
	public void testGetInsertArguments_returnsAllPrimaryColumnValues() {
		final var e = new SimpleEntity();
		e.setId(1L);
		e.setLabel("bar");
		final var args = IuIterable.stream(sqlBuilder.getInsertArguments(e)).toList();
		assertEquals(2, args.size());
		assertTrue(args.contains(1L));
		assertTrue(args.contains("bar"));
	}

	// =======================================================================
	// getForSql
	// =======================================================================

	@Test
	public void testGetForSql_returnsPropertyValue() {
		final var e = new SimpleEntity();
		e.setLabel("hello");
		assertEquals("hello", sqlBuilder.getForSql(e, "label"));
	}

	@Test
	public void testGetForSql_unknownProperty_throwsIllegalArgument() {
		assertThrows(IllegalArgumentException.class, () -> sqlBuilder.getForSql(new SimpleEntity(), "nonexistent"));
	}

	@Test
	public void testGetForSql_nullWithSpaceForNull_returnsSpace() {
		final var e = new SpaceForNullEntity();
		// label is null, spaceForNull=true → normalizeArgument returns " "
		assertEquals(" ", sqlBuilder.getForSql(e, "label"));
	}

	// =======================================================================
	// getLiteral / getLiterals
	// =======================================================================

	@Test
	public void testGetLiteral_null_returnsNULL() {
		assertEquals("NULL", sqlBuilder.getLiteral(null));
	}

	@Test
	public void testGetLiteral_string_returnsSingleQuoted() {
		assertEquals("'hello'", sqlBuilder.getLiteral("hello"));
	}

	@Test
	public void testGetLiteral_string_withEmbeddedQuote_escaped() {
		assertEquals("'it''s'", sqlBuilder.getLiteral("it's"));
	}

	@Test
	public void testGetLiteral_sqlDate_returnsDateLiteral() {
		final var d = Date.valueOf("2024-01-15");
		assertEquals("DATE '2024-01-15'", sqlBuilder.getLiteral(d));
	}

	@Test
	public void testGetLiteral_sqlTimestamp_returnsTimestampLiteral() {
		final var ts = new Timestamp(0L);
		final var result = sqlBuilder.getLiteral(ts);
		assertTrue(result.startsWith("TIMESTAMP '"));
	}

	@Test
	public void testGetLiteral_utilDate_returnsTimestampLiteral() {
		final var result = sqlBuilder.getLiteral(new java.util.Date(0L));
		assertTrue(result.startsWith("TIMESTAMP '"));
	}

	@Test
	public void testGetLiteral_booleanTrue_returnsYLiteral() {
		assertEquals("'Y'", sqlBuilder.getLiteral(Boolean.TRUE));
	}

	@Test
	public void testGetLiteral_booleanFalse_returnsNLiteral() {
		assertEquals("'N'", sqlBuilder.getLiteral(Boolean.FALSE));
	}

	@Test
	public void testGetLiteral_integer_returnsNumberString() {
		assertEquals("42", sqlBuilder.getLiteral(42));
	}

	@Test
	public void testGetLiteral_long_returnsNumberString() {
		assertEquals("123456789", sqlBuilder.getLiteral(123456789L));
	}

	@Test
	public void testGetLiteral_unsupportedType_throwsUnsupportedOperation() {
		assertThrows(UnsupportedOperationException.class, () -> sqlBuilder.getLiteral(new Object()));
	}

	@Test
	public void testGetLiterals_nullIterable_returnsEmpty() {
		assertFalse(sqlBuilder.getLiterals(null).iterator().hasNext());
	}

	@Test
	public void testGetLiterals_values_mapsEachToLiteral() {
		final var result = IuIterable.stream(
				sqlBuilder.getLiterals(List.of("a", "b"))).toList();
		assertEquals(List.of("'a'", "'b'"), result);
	}

	// =======================================================================
	// getWhereClause
	// =======================================================================

	@Test
	public void testGetWhereClause_empty_returnsEmpty() {
		assertEquals("", sqlBuilder.getWhereClause(List.of()));
	}

	@Test
	public void testGetWhereClause_singleCriterion_includesWhereKeyword() {
		assertTrue(sqlBuilder.getWhereClause(List.of("a.ID = ?")).startsWith("\nWHERE "));
	}

	// =======================================================================
	// getInCriteria
	// =======================================================================

	@Test
	public void testGetInCriteria_producesInExpression() {
		assertEquals("a.COL IN ('x', 'y')", sqlBuilder.getInCriteria("a.COL", List.of("'x'", "'y'")));
	}

	// =======================================================================
	// getMultipartKeyListMatch
	// =======================================================================

	@Test
	public void testGetMultipartKeyListMatch_singleColumnSingleRow() {
		// column-major: one inner iterable for one column with one value
		final var result = sqlBuilder.getMultipartKeyListMatch(
				List.of("COL1"),
				List.of(List.of("'A'")));
		assertEquals("((COL1 = 'A'))", result);
	}

	@Test
	public void testGetMultipartKeyListMatch_singleColumnNullValue() {
		final var result = sqlBuilder.getMultipartKeyListMatch(
				List.of("COL1"),
				List.of(java.util.Arrays.asList((String) null)));
		assertEquals("((COL1 IS NULL))", result);
	}

	@Test
	public void testGetMultipartKeyListMatch_multiColumnMultiRow() {
		// column-major: [col1_values, col2_values] = [["'A'","'B'"], ["'X'","'Y'"]]
		final var result = sqlBuilder.getMultipartKeyListMatch(
				List.of("COL1", "COL2"),
				List.of(List.of("'A'", "'B'"), List.of("'X'", "'Y'")));
		assertEquals("((COL1 = 'A' AND COL2 = 'X')\n    OR (COL1 = 'B' AND COL2 = 'Y'))", result);
	}

	@Test
	public void testGetMultipartKeyListMatch_emptyInnerIterable_throwsIllegalArgument() {
		assertThrows(IllegalArgumentException.class, () ->
				sqlBuilder.getMultipartKeyListMatch(List.of("COL1"), List.of(List.of())));
	}

	// =======================================================================
	// getColumnMatchCriteria / getJoinedColumnMatchCriteria
	// =======================================================================

	@Test
	public void testGetColumnMatchCriteria_producesInExpression() {
		assertEquals("a.LABEL IN ('x', 'y')",
				sqlBuilder.getColumnMatchCriteria(SimpleEntity.class, "label", List.of("'x'", "'y'")));
	}

	@Test
	public void testGetJoinedColumnMatchCriteria_withSecondaryTable() {
		assertEquals("b.DETAIL IN ('v')",
				sqlBuilder.getJoinedColumnMatchCriteria(SecEntity.class, "sec_tbl", "detail", List.of("'v'")));
	}

	// =======================================================================
	// getColumnCompareCriteria / getJoinedColumnCompareCriteria
	// =======================================================================

	@Test
	public void testGetColumnCompareCriteria_singleValue() {
		assertEquals("a.LABEL = 'foo'",
				sqlBuilder.getColumnCompareCriteria(SimpleEntity.class, "label", "=", List.of("'foo'")));
	}

	@Test
	public void testGetColumnCompareCriteria_multipleValues() {
		assertEquals("(a.LABEL = 'a' OR a.LABEL = 'b')",
				sqlBuilder.getColumnCompareCriteria(SimpleEntity.class, "label", "=", List.of("'a'", "'b'")));
	}

	@Test
	public void testGetJoinedColumnCompareCriteria_withTable() {
		assertEquals("b.DETAIL LIKE 'X%'",
				sqlBuilder.getJoinedColumnCompareCriteria(SecEntity.class, "sec_tbl", "detail", "LIKE", List.of("'X%'")));
	}

	// =======================================================================
	// Effective-date criteria
	// =======================================================================

	@Test
	public void testGetEffectiveDateCriteria_usesCurrentDate() {
		final var sql = sqlBuilder.getEffectiveDateCriteria(EffDateEntity.class, "EFF_DATE");
		assertTrue(sql.contains("a_ed.EFF_DATE <= CURRENT_DATE"));
		assertTrue(sql.contains("SELECT MAX(a_ed.EFF_DATE)"));
	}

	@Test
	public void testGetAsOfEffectiveDateCriteria_usesSuppliedDate() {
		final var sql = sqlBuilder.getAsOfEffectiveDateCriteria(EffDateEntity.class, "EFF_DATE", "SYSDATE");
		assertTrue(sql.contains("a_ed.EFF_DATE <= SYSDATE"));
	}

	@Test
	public void testGetEffectiveDateSeqCriteria_usesCurrentDate() {
		final var sql = sqlBuilder.getEffectiveDateSeqCriteria(EffDateEntity.class, "EFF_DATE", "SEQ_NUM");
		assertTrue(sql.contains("SELECT MAX(a_ed.EFF_DATE)"));
		assertTrue(sql.contains("SELECT MAX(a_seq.SEQ_NUM)"));
		assertTrue(sql.contains("a_ed.EFF_DATE <= CURRENT_DATE"));
	}

	@Test
	public void testGetAsOfEffectiveDateSeqCriteria_usesSuppliedDate() {
		final var sql = sqlBuilder.getAsOfEffectiveDateSeqCriteria(EffDateEntity.class, "EFF_DATE", "SEQ_NUM", "SYSDATE");
		assertTrue(sql.contains("a_ed.EFF_DATE <= SYSDATE"));
		assertTrue(sql.contains("SELECT MAX(a_seq.SEQ_NUM)"));
	}

	@Test
	public void testGetMaxDateCriteria_producesMaxSubquery() {
		final var sql = sqlBuilder.getMaxDateCriteria(EffDateEntity.class, "EFF_DATE");
		assertTrue(sql.startsWith("a.EFF_DATE = (SELECT MAX(a_md.EFF_DATE)"));
	}

	@Test
	public void testGetFutureEffectiveDateCriteria_usesMIN() {
		final var sql = sqlBuilder.getFutureEffectiveDateCriteria(EffDateEntity.class, "EFF_DATE", "SYSDATE");
		assertTrue(sql.contains("SELECT MIN(a_ed.EFF_DATE)"));
		assertTrue(sql.contains("a_ed.EFF_DATE > SYSDATE"));
	}

	@Test
	public void testGetJoinedEffectiveDateCriteria_usesCurrentDate() {
		final var sql = sqlBuilder.getJoinedEffectiveDateCriteria(EffDateEntity.class, "prices", "EFF_DATE",
				List.of("ID"));
		assertTrue(sql.contains("EFF_DATE <= CURRENT_DATE"));
	}

	@Test
	public void testGetJoinedFutureEffectiveDateCriteria_4params_usesCurrentDate() {
		final var sql = sqlBuilder.getJoinedFutureEffectiveDateCriteria(EffDateEntity.class, "prices", "EFF_DATE",
				List.of("ID"));
		assertTrue(sql.contains("SELECT MIN("));
		assertTrue(sql.contains("EFF_DATE > CURRENT_DATE"));
	}

	@Test
	public void testGetJoinedFutureEffectiveDateCriteria_5params_usesSuppliedDate() {
		final var sql = sqlBuilder.getJoinedFutureEffectiveDateCriteria(EffDateEntity.class, "prices", "EFF_DATE",
				"SYSDATE", List.of("ID"));
		assertTrue(sql.contains("EFF_DATE > SYSDATE"));
	}

	@Test
	public void testGetJoinedAsOfEffectiveDateCriteria_usesSuppliedDate() {
		final var sql = sqlBuilder.getJoinedAsOfEffectiveDateCriteria(EffDateEntity.class, "prices", "EFF_DATE",
				"SYSDATE", List.of("ID"));
		assertTrue(sql.contains("EFF_DATE <= SYSDATE"));
	}

	@Test
	public void testGetJoinedAsOfEffectiveDateSeqCriteria_usesSuppliedDate() {
		final var sql = sqlBuilder.getJoinedAsOfEffectiveDateSeqCriteria(EffDateEntity.class, "prices", "EFF_DATE",
				"SEQ_NUM", "SYSDATE", List.of("ID"));
		assertTrue(sql.contains("EFF_DATE <= SYSDATE"));
		assertTrue(sql.contains("SELECT MAX(") && sql.contains("SEQ_NUM"));
	}

	// =======================================================================
	// buildForUpdateCursorQueryFromBean
	// =======================================================================

	@Test
	public void testBuildForUpdateCursorQueryFromBean_appendsForUpdate() {
		final var sql = sqlBuilder.buildForUpdateCursorQueryFromBean(SimpleEntity.class, List.of("a.ID = ?"));
		assertTrue(sql.contains("WHERE a.ID = ?"));
		assertTrue(sql.endsWith("\nFOR UPDATE"));
	}

	// =======================================================================
	// mapIdColumnsToSqlTypes / mapColumnsToSqlTypes
	// =======================================================================

	@Test
	public void testMapIdColumnsToSqlTypes_withInstance_containsIdColumn() {
		final var e = new SimpleEntity();
		final var map = sqlBuilder.mapIdColumnsToSqlTypes(e);
		assertTrue(map.containsKey("ID"));
		assertEquals(Long.class, map.get("ID"));
	}

	@Test
	public void testMapIdColumnsToSqlTypes_withClass_sameResult() {
		final var map = sqlBuilder.mapIdColumnsToSqlTypes(SimpleEntity.class);
		assertTrue(map.containsKey("ID"));
	}

	@Test
	public void testMapColumnsToSqlTypes_withInstance_containsAllMappedColumns() {
		final var map = sqlBuilder.mapColumnsToSqlTypes(new SimpleEntity());
		assertTrue(map.containsKey("ID"));
		assertTrue(map.containsKey("LABEL"));
	}

	@Test
	public void testMapColumnsToSqlTypes_withClass_sameResult() {
		final var map = sqlBuilder.mapColumnsToSqlTypes(SimpleEntity.class);
		assertTrue(map.containsKey("ID"));
		assertTrue(map.containsKey("LABEL"));
	}

	@Test
	public void testMapColumnsToSqlTypes_sqlColumnProperty_excluded() {
		// SqlColumnEntity has @Id @Column("ID") and @SqlColumn("UPPER(label)").
		// @SqlColumn properties have columnName=null and must be omitted from the map.
		final var map = sqlBuilder.mapColumnsToSqlTypes(SqlColumnEntity.class);
		assertTrue(map.containsKey("ID"), "ID should be present");
		assertFalse(map.containsKey("UPPER(label)"), "@SqlColumn expr must not appear as a key");
		assertFalse(map.containsKey("expr"), "@SqlColumn property name must not appear as a key");
		assertEquals(1, map.size(), "Only the @Column property should be mapped");
	}

	// =======================================================================
	// buildWhereClause
	// =======================================================================

	@Test
	public void testBuildWhereClause_nullInput_returnsNull() {
		assertNull(sqlBuilder.buildWhereClause(null));
	}

	@Test
	public void testBuildWhereClause_emptyList_returnsNull() {
		assertNull(sqlBuilder.buildWhereClause(List.of()));
	}

	@Test
	public void testBuildWhereClause_allNullElements_returnsNull() {
		assertNull(sqlBuilder.buildWhereClause(java.util.Arrays.asList(null, null)));
	}

	@Test
	public void testBuildWhereClause_noIdEntity_returnsNull() {
		final var e = new NoIdEntity();
		assertNull(sqlBuilder.buildWhereClause(List.of(e)));
	}

	@Test
	public void testBuildWhereClause_singleEntityNonNullId_producesEqualityCriterion() {
		final var e = new SimpleEntity();
		e.setId(5L);
		final var result = sqlBuilder.buildWhereClause(List.of(e));
		assertEquals("(a.ID = 5)", result);
	}

	@Test
	public void testBuildWhereClause_nullableIdEntity_nullId_producesIsNull() {
		final var e = new NullableIdEntity();
		// id is null
		final var result = sqlBuilder.buildWhereClause(List.of(e));
		assertEquals("(a.ID IS NULL)", result);
	}

	@Test
	public void testBuildWhereClause_multipleEntities_producesOrJoined() {
		final var e1 = new SimpleEntity();
		e1.setId(1L);
		final var e2 = new SimpleEntity();
		e2.setId(2L);
		final var result = sqlBuilder.buildWhereClause(List.of(e1, e2));
		assertEquals("(a.ID = 1) OR (a.ID = 2)", result);
	}

	@Test
	public void testBuildWhereClause_nullElementsSkipped() {
		final var e = new SimpleEntity();
		e.setId(3L);
		final var list = java.util.Arrays.asList(null, e, null);
		assertEquals("(a.ID = 3)", sqlBuilder.buildWhereClause(list));
	}

	// =======================================================================
	// getPropertyNameFromBean
	// =======================================================================

	@Test
	public void testGetPropertyNameFromBean_knownColumn_returnsPropertyName() {
		assertEquals("id", sqlBuilder.getPropertyNameFromBean(SimpleEntity.class, "ID"));
	}

	@Test
	public void testGetPropertyNameFromBean_unknownColumn_returnsNull() {
		assertNull(sqlBuilder.getPropertyNameFromBean(SimpleEntity.class, "NONEXISTENT"));
	}

	@Test
	public void testGetPropertyNameFromBean_caseInsensitive() {
		assertEquals("id", sqlBuilder.getPropertyNameFromBean(SimpleEntity.class, "id"));
	}

}
