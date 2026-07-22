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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import edu.iu.dao.Distinct;
import edu.iu.dao.EffectiveDated;
import edu.iu.dao.Filtered;
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

@SuppressWarnings("javadoc")
public class EntityMetaDataTest {

	// =======================================================================
	// Test entity classes
	// =======================================================================

	/** No annotations at all — table name falls back to the simple class name. */
	public static class BareEntity {
	}

	/** @Table with explicit name and schema. */
	@Entity
	@Table(name = "items", schema = "pub")
	public static class TableNamedEntity {
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

	/** @Entity with a name but no @Table — table name comes from @Entity.name(). */
	@Entity(name = "ent_name")
	public static class EntityNamedEntity {
	}

	/**
	 * @Table present but name() is blank + @Entity with a name — table name falls
	 *        through to @Entity.name().
	 */
	@Entity(name = "from_entity")
	@Table
	public static class BlankTableNameWithEntityNameEntity {
	}

	/**
	 * @Table present but name() is blank + @Entity with blank name — both blank, so
	 *        table name falls back to the simple class name.
	 */
	@Entity
	@Table
	public static class BlankTableNameBlankEntityNameEntity {
	}

	/**
	 * @Entity present but name() is blank and no @Table — table name falls back to
	 *         the simple class name.
	 */
	@Entity
	public static class BlankEntityNameNoTableEntity {
	}

	/** @Distinct flag on a subclass of TableNamedEntity. */
	@Entity
	@Distinct
	@Table(name = "items", schema = "pub")
	public static class DistinctEntity extends TableNamedEntity {
	}

	/** @SpaceForNull at the class level. */
	@Entity
	@SpaceForNull
	@Table(name = "items", schema = "pub")
	public static class SpaceForNullEntity extends TableNamedEntity {
	}

	/** @SqlJoinType overrides the default INNER join. */
	@Entity
	@SqlJoinType(SqlJoinType.Type.LEFT)
	@Table(name = "items", schema = "pub")
	public static class LeftJoinEntity extends TableNamedEntity {
	}

	/** @Filtered annotation is captured. */
	@Entity
	@Table(name = "items", schema = "pub")
	@Filtered(filters = @SqlFilter(sql = "a.active = TRUE"))
	public static class FilteredEntity extends TableNamedEntity {
	}

	/** Exercises the {@code "effectiveDate"} named filter with default asOfDate. */
	@Entity
	@Table(name = "prices", schema = "pub")
	@Filtered(filters = @SqlFilter(name = "effectiveDate", params = "EFF_DATE"))
	public static class EffectiveDateFilterEntity extends EffectiveDatedEntity {
	}

	/** Exercises the {@code "effectiveDate"} named filter with an explicit asOfDate. */
	@Entity
	@Table(name = "prices", schema = "pub")
	@Filtered(filters = @SqlFilter(name = "effectiveDate", params = { "EFF_DATE", "SYSDATE" }))
	public static class EffectiveDateExplicitAsOfFilterEntity extends EffectiveDatedEntity {
	}

	/** Exercises the {@code "maxDate"} named filter. */
	@Entity
	@Table(name = "prices", schema = "pub")
	@Filtered(filters = @SqlFilter(name = "maxDate", params = "EFF_DATE"))
	public static class MaxDateFilterEntity extends EffectiveDatedEntity {
	}

	/** Exercises the {@code "columnMatch"} named filter. */
	@Entity
	@Table(name = "items", schema = "pub")
	@Filtered(filters = @SqlFilter(name = "columnMatch", params = { "LABEL", "'A'", "'B'" }))
	public static class ColumnMatchFilterEntity extends TableNamedEntity {
	}

	/** Exercises the {@code "columnCompare"} named filter with one value. */
	@Entity
	@Table(name = "items", schema = "pub")
	@Filtered(filters = @SqlFilter(name = "columnCompare", params = { "LABEL", "=", "'active'" }))
	public static class ColumnCompareFilterEntity extends TableNamedEntity {
	}

	/** Exercises the {@code "columnCompare"} named filter with no values (null result). */
	@Entity
	@Table(name = "items", schema = "pub")
	@Filtered(filters = @SqlFilter(name = "columnCompare", params = { "LABEL", "=" }))
	public static class ColumnCompareNoValuesFilterEntity extends TableNamedEntity {
	}

	/** Exercises an unsupported filter name to trigger {@link IllegalArgumentException}. */
	@Entity
	@Table(name = "items", schema = "pub")
	@Filtered(filters = @SqlFilter(name = "bogus"))
	public static class UnsupportedFilterNameEntity extends TableNamedEntity {
	}

	/** @EffectiveDated annotation is captured. */
	@Entity
	@Table(name = "prices", schema = "pub")
	@EffectiveDated(effectiveDatedColumns = "EFF_DATE")
	public static class EffectiveDatedEntity {
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

	/**
	 * Like {@link EffectiveDatedEntity} but with {@code currentOnly = true} so
	 * that {@link EntityMetaData#resolveEffectiveDatedCriteria()} produces a
	 * predicate. Uses the default {@code asOfDate = "CURRENT_DATE"}.
	 */
	@EffectiveDated(effectiveDatedColumns = "EFF_DATE", currentOnly = true)
	public static class CurrentOnlyEffectiveDatedEntity extends EffectiveDatedEntity {
	}

	/**
	 * Compound-key entity where {@code EFF_DATE} is itself an {@code @Id} column,
	 * so {@link EntityMetaData#effectiveDateKeyColumns} must exclude it from the
	 * key set when it is passed as the effective-date column.
	 */
	@Entity
	@Table(name = "prices", schema = "pub")
	@EffectiveDated(effectiveDatedColumns = "EFF_DATE")
	public static class CompoundKeyEffectiveDatedEntity {
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

		@Id
		@Column(name = "EFF_DATE")
		public java.sql.Date getEffDate() {
			return effDate;
		}

		public void setEffDate(java.sql.Date effDate) {
			this.effDate = effDate;
		}
	}

	/** Single @SecondaryTable with an explicit pkJoinColumn. */
	@Entity
	@Table(name = "primary_tbl", schema = "pub")
	@SecondaryTable(name = "sec_tbl", pkJoinColumns = @PrimaryKeyJoinColumn(name = "SEC_ID"))
	public static class SecondaryTableEntity {
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

	/** @SecondaryTables with two secondary tables. */
	@Entity
	@Table(name = "multi_primary")
	@SecondaryTables({ @SecondaryTable(name = "sec_one", pkJoinColumns = @PrimaryKeyJoinColumn(name = "S1_ID")),
			@SecondaryTable(name = "sec_two", pkJoinColumns = @PrimaryKeyJoinColumn(name = "S2_ID")) })
	public static class MultiSecondaryEntity {
		private long id;

		@Id
		@Column(name = "ID")
		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}
	}

	/**
	 * Secondary table with two explicit pkJoinColumns — produces two join
	 * conditions on a single secondary table.
	 */
	@Entity
	@Table(name = "multi_fk_primary")
	@SecondaryTable(name = "multi_fk_sec", pkJoinColumns = { @PrimaryKeyJoinColumn(name = "FK1"),
			@PrimaryKeyJoinColumn(name = "FK2") })
	public static class MultiJoinConditionEntity {
		private long k1;
		private long k2;
		private String secData;

		@Id
		@Column(name = "K1")
		public long getK1() {
			return k1;
		}

		public void setK1(long k1) {
			this.k1 = k1;
		}

		@Id
		@Column(name = "K2")
		public long getK2() {
			return k2;
		}

		public void setK2(long k2) {
			this.k2 = k2;
		}

		@Column(name = "SEC_DATA", table = "multi_fk_sec")
		public String getSecData() {
			return secData;
		}

		public void setSecData(String secData) {
			this.secData = secData;
		}
	}

	/** Exercises all column-scanning paths. */
	@Entity
	@Table(name = "scan_tbl")
	public static class ColumnScanEntity {
		private long colId;
		private String colA;
		private String sqlExpr;
		private String ignored;
		private String trans;

		@Id
		@Column(name = "COL_ID")
		public long getColId() {
			return colId;
		}

		public void setColId(long colId) {
			this.colId = colId;
		}

		@Column(name = "COL_A")
		public String getColA() {
			return colA;
		}

		public void setColA(String colA) {
			this.colA = colA;
		}

		// @SqlColumn — raw SQL expression, no physical column name.
		@SqlColumn("UPPER(col_a)")
		public String getSqlExpr() {
			return sqlExpr;
		}

		public void setSqlExpr(String sqlExpr) {
			this.sqlExpr = sqlExpr;
		}

		// Not annotated with @Column or @SqlColumn — must be excluded
		public String getIgnored() {
			return ignored;
		}

		public void setIgnored(String ignored) {
			this.ignored = ignored;
		}

		// Annotated @Transient — must be excluded even if it carries @Column.
		@Transient
		@Column(name = "TRANS")
		public String getTrans() {
			return trans;
		}

		public void setTrans(String trans) {
			this.trans = trans;
		}
	}

	// =======================================================================
	// Constructor — table name resolution
	// =======================================================================

	@Test
	public void testTableName_fromTableAnnotation() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("items", meta.primaryTable.name);
	}

	@Test
	public void testTableName_fromEntityAnnotation_whenNoTableAnnotation() {
		final var meta = EntityMetaData.of(EntityNamedEntity.class);
		assertEquals("ent_name", meta.primaryTable.name);
	}

	@Test
	public void testTableName_fromEntityAnnotation_whenTableAnnotationHasBlankName() {
		// @Table present but name() == "" → falls through to @Entity.name()
		final var meta = EntityMetaData.of(BlankTableNameWithEntityNameEntity.class);
		assertEquals("from_entity", meta.primaryTable.name);
	}

	@Test
	public void testTableName_fromSimpleClassName_whenTableAnnotationBlankAndEntityAnnotationBlank() {
		// @Table blank + @Entity blank → falls back to simple class name
		final var meta = EntityMetaData.of(BlankTableNameBlankEntityNameEntity.class);
		assertEquals("BlankTableNameBlankEntityNameEntity", meta.primaryTable.name);
	}

	@Test
	public void testTableName_fromSimpleClassName_whenEntityAnnotationBlankAndNoTable() {
		// @Entity present but name() == "" and no @Table → falls back to simple class
		// name
		final var meta = EntityMetaData.of(BlankEntityNameNoTableEntity.class);
		assertEquals("BlankEntityNameNoTableEntity", meta.primaryTable.name);
	}

	@Test
	public void testTableName_fromSimpleClassName_whenNoAnnotations() {
		final var meta = EntityMetaData.of(BareEntity.class);
		assertEquals("BareEntity", meta.primaryTable.name);
	}

	@Test
	public void testPrimaryTable_schemaQualifiedFullName() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("pub.items", meta.primaryTable.fullName);
	}

	@Test
	public void testPrimaryTable_alwaysHasAliasA() {
		final var meta = EntityMetaData.of(BareEntity.class);
		assertEquals("a", meta.primaryTable.alias);
	}

	@Test
	public void testPrimaryTable_isPrimary() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertTrue(meta.primaryTable.primary);
	}

	// =======================================================================
	// Constructor — feature flags
	// =======================================================================

	@Test
	public void testDistinct_trueWhenAnnotationPresent() {
		assertTrue(EntityMetaData.of(DistinctEntity.class).distinct);
	}

	@Test
	public void testDistinct_falseByDefault() {
		assertFalse(EntityMetaData.of(TableNamedEntity.class).distinct);
	}

	@Test
	public void testSpaceForNull_trueWhenAnnotationPresent() {
		assertTrue(EntityMetaData.of(SpaceForNullEntity.class).spaceForNull);
	}

	@Test
	public void testSpaceForNull_falseByDefault() {
		assertFalse(EntityMetaData.of(TableNamedEntity.class).spaceForNull);
	}

	@Test
	public void testJoinType_defaultIsInner() {
		assertEquals(SqlJoinType.Type.INNER, EntityMetaData.of(TableNamedEntity.class).joinType);
	}

	@Test
	public void testJoinType_overriddenBySqlJoinTypeAnnotation() {
		assertEquals(SqlJoinType.Type.LEFT, EntityMetaData.of(LeftJoinEntity.class).joinType);
	}

	@Test
	public void testFiltered_capturedWhenAnnotationPresent() {
		final var meta = EntityMetaData.of(FilteredEntity.class);
		assertNotNull(meta.filtered);
		assertEquals(1, meta.filtered.filters().length);
		assertEquals("a.active = TRUE", meta.filtered.filters()[0].sql());
	}

	@Test
	public void testFiltered_nullWhenAbsent() {
		assertNull(EntityMetaData.of(TableNamedEntity.class).filtered);
	}

	@Test
	public void testEffectiveDated_capturedWhenAnnotationPresent() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		assertNotNull(meta.effectiveDated);
		assertEquals("EFF_DATE", meta.effectiveDated.effectiveDatedColumns()[0]);
	}

	@Test
	public void testEffectiveDated_nullWhenAbsent() {
		assertNull(EntityMetaData.of(TableNamedEntity.class).effectiveDated);
	}

	// =======================================================================
	// Constructor — secondary tables
	// =======================================================================

	@Test
	public void testSecondaryTables_emptyWhenNoneAnnotated() {
		assertFalse(EntityMetaData.of(TableNamedEntity.class).secondaryTables.iterator().hasNext());
	}

	@Test
	public void testSecondaryTables_singleEntryFromSecondaryTableAnnotation() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertEquals(1, IuIterable.stream(meta.secondaryTables).count());
		assertEquals("sec_tbl", IuIterable.stream(meta.secondaryTables).findAny().get().name);
	}

	@Test
	public void testSecondaryTables_firstSecondaryTableGetsAliasB() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertEquals("b", IuIterable.stream(meta.secondaryTables).findAny().get().alias);
	}

	@Test
	public void testSecondaryTables_twoEntriesFromSecondaryTablesAnnotation() {
		final var meta = EntityMetaData.of(MultiSecondaryEntity.class);
		assertEquals(2, IuIterable.stream(meta.secondaryTables).count());
		assertEquals("sec_one", IuIterable.stream(meta.secondaryTables).toArray(TableMetaData[]::new)[0].name);
		assertEquals("sec_two", IuIterable.stream(meta.secondaryTables).toArray(TableMetaData[]::new)[1].name);
	}

	@Test
	public void testSecondaryTables_secondaryTablesAliasesSequential() {
		final var meta = EntityMetaData.of(MultiSecondaryEntity.class);
		assertEquals("b", IuIterable.stream(meta.secondaryTables).toArray(TableMetaData[]::new)[0].alias);
		assertEquals("c", IuIterable.stream(meta.secondaryTables).toArray(TableMetaData[]::new)[1].alias);
	}

	@Test
	public void testSecondaryTables_areNotPrimary() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertFalse(IuIterable.stream(meta.secondaryTables).findAny().get().primary);
	}

	// =======================================================================
	// Constructor — tablesByReference
	// =======================================================================

	@Test
	public void testTablesByReference_primaryTableRegisteredByName() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertSame(meta.primaryTable, meta.tablesByReference.get("ITEMS"));
	}

	@Test
	public void testTablesByReference_primaryTableRegisteredByFullName() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertSame(meta.primaryTable, meta.tablesByReference.get("PUB.ITEMS"));
	}

	@Test
	public void testTablesByReference_primaryTableRegisteredByAlias() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertSame(meta.primaryTable, meta.tablesByReference.get("A"));
	}

	@Test
	public void testTablesByReference_secondaryTableRegisteredByName() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		final var secondary = IuIterable.stream(meta.secondaryTables).findAny().get();
		assertSame(secondary, meta.tablesByReference.get("SEC_TBL"));
	}

	@Test
	public void testTablesByReference_secondaryTableRegisteredByAlias() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		final var secondary = IuIterable.stream(meta.secondaryTables).findAny().get();
		assertSame(secondary, meta.tablesByReference.get("B"));
	}

	// =======================================================================
	// Constructor — column scanning
	// =======================================================================

	@Test
	public void testColumns_columnAnnotatedPropertyIncluded() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		assertTrue(meta.columns.containsKey("colA"));
	}

	@Test
	public void testColumns_sqlColumnAnnotatedPropertyIncluded() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		assertTrue(meta.columns.containsKey("sqlExpr"));
	}

	@Test
	public void testColumns_unannotatedPropertyExcluded() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		assertFalse(meta.columns.containsKey("ignored"));
	}

	@Test
	public void testColumns_transientPropertyExcluded() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		assertFalse(meta.columns.containsKey("trans"));
	}

	@Test
	public void testColumnsByNormalizedColumn_columnAnnotatedPropertyIncluded() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		assertNotNull(meta.columnsByNormalizedColumn.get("COL_A"));
	}

	@Test
	public void testColumnsByNormalizedColumn_sqlColumnPropertyExcluded() {
		// @SqlColumn has no physical column name, so it must not appear here
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		assertNull(meta.columnsByNormalizedColumn.get("SQLEXPR"));
		assertNull(meta.columnsByNormalizedColumn.get("SQL_EXPR"));
	}

	@Test
	public void testColumnToPropertyNames_mapsNormalizedColumnToPropertyName() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		assertEquals("colA", meta.columnToPropertyNames.get("COL_A"));
	}

	@Test
	public void testIdColumns_containsIdAnnotatedColumn() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		assertEquals(1, IuIterable.stream(meta.idColumns).count());
		assertEquals("colId", IuIterable.stream(meta.idColumns).findAny().get().propertyName);
	}

	@Test
	public void testPrimaryColumns_includesBothIdAndNonIdPrimaryColumns() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		final var names = IuIterable.stream(meta.primaryColumns).map(c -> c.propertyName).toList();
		assertTrue(names.contains("colId"));
		assertTrue(names.contains("colA"));
	}

	@Test
	public void testPrimaryColumns_excludesSqlColumnProperty() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		final var names = IuIterable.stream(meta.primaryColumns).map(c -> c.propertyName).toList();
		assertFalse(names.contains("sqlExpr"));
	}

	@Test
	public void testPrimaryNonIdColumns_excludesIdColumn() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		final var names = IuIterable.stream(meta.primaryNonIdColumns).map(c -> c.propertyName).toList();
		assertFalse(names.contains("colId"));
		assertTrue(names.contains("colA"));
	}

	@Test
	public void testSecondaryTableColumn_notInPrimaryColumns() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		final var names = IuIterable.stream(meta.primaryColumns).map(c -> c.propertyName).toList();
		assertTrue(names.contains("id"));
		assertFalse(names.contains("detail"));
	}

	@Test
	public void testSecondaryTableColumn_isInColumns() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertTrue(meta.columns.containsKey("detail"));
	}

	// =======================================================================
	// resolveTable
	// =======================================================================

	@Test
	public void testResolveTable_nullReturnsPrimaryTable() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertSame(meta.primaryTable, meta.resolveTable(null));
	}

	@Test
	public void testResolveTable_blankReturnsPrimaryTable() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertSame(meta.primaryTable, meta.resolveTable("   "));
	}

	@Test
	public void testResolveTable_byTableName() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertSame(meta.primaryTable, meta.resolveTable("items"));
	}

	@Test
	public void testResolveTable_byFullyQualifiedName() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertSame(meta.primaryTable, meta.resolveTable("pub.items"));
	}

	@Test
	public void testResolveTable_byAlias() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertSame(meta.primaryTable, meta.resolveTable("a"));
	}

	@Test
	public void testResolveTable_secondaryTableByName() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertSame(meta.secondaryTables.iterator().next(), meta.resolveTable("sec_tbl"));
	}

	@Test
	public void testResolveTable_secondaryTableByAlias() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertSame(meta.secondaryTables.iterator().next(), meta.resolveTable("b"));
	}

	@Test
	public void testResolveTable_unknownNameThrows() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertThrows(IllegalArgumentException.class, () -> meta.resolveTable("no_such_table"));
	}

	// =======================================================================
	// resolveAlias
	// =======================================================================

	@Test
	public void testResolveAlias_nullReturnsPrimaryAlias() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("a", meta.resolveAlias(null));
	}

	@Test
	public void testResolveAlias_blankReturnsPrimaryAlias() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("a", meta.resolveAlias(""));
	}

	@Test
	public void testResolveAlias_byPrimaryTableName() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("a", meta.resolveAlias("items"));
	}

	@Test
	public void testResolveAlias_bySecondaryTableName() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertEquals("b", meta.resolveAlias("sec_tbl"));
	}

	@Test
	public void testResolveAlias_unknownPassesThroughUnchanged() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("my_alias", meta.resolveAlias("my_alias"));
	}

	// =======================================================================
	// resolveColumn
	// =======================================================================

	@Test
	public void testResolveColumn_byPropertyName() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		final var col = meta.resolveColumn("colA");
		assertNotNull(col);
		assertEquals("colA", col.propertyName);
	}

	@Test
	public void testResolveColumn_byNormalizedColumnName_caseInsensitive() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		final var col = meta.resolveColumn("col_a");
		assertNotNull(col);
		assertEquals("colA", col.propertyName);
	}

	@Test
	public void testResolveColumn_bySqlColumnPropertyName() {
		// @SqlColumn properties have no physical column name but are reachable by
		// property name
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		final var col = meta.resolveColumn("sqlExpr");
		assertNotNull(col);
		assertEquals("sqlExpr", col.propertyName);
	}

	@Test
	public void testResolveColumn_unknownReturnsNull() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		assertNull(meta.resolveColumn("no_such_column"));
	}

	// =======================================================================
	// defaultPropertyNames
	// =======================================================================

	@Test
	public void testDefaultPropertyNames_containsAllMappedProperties() {
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		final var names = new ArrayList<String>();
		for (final var name : meta.defaultPropertyNames())
			names.add(name);
		assertTrue(names.contains("colId"));
		assertTrue(names.contains("colA"));
		assertTrue(names.contains("sqlExpr"));
		assertFalse(names.contains("ignored"));
		assertFalse(names.contains("trans"));
	}

	// =======================================================================
	// requirePrimaryColumn
	// =======================================================================

	@Test
	public void testRequirePrimaryColumn_returnsKnownPrimaryColumn() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var col = meta.requirePrimaryColumn("label");
		assertNotNull(col);
		assertEquals("label", col.propertyName);
	}

	@Test
	public void testRequirePrimaryColumn_throwsForUnknownProperty() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertThrows(IllegalArgumentException.class, () -> meta.requirePrimaryColumn("nosuchprop"));
	}

	@Test
	public void testRequirePrimaryColumn_throwsForSecondaryTableColumn() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertThrows(IllegalArgumentException.class, () -> meta.requirePrimaryColumn("detail"));
	}

	// =======================================================================
	// columnReference
	// =======================================================================

	@Test
	public void testColumnReference_knownProperty_usesColumnAlias() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("a.LABEL", meta.columnReference(null, "label"));
	}

	@Test
	public void testColumnReference_withTableOverride() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("x.LABEL", meta.columnReference("x", "label"));
	}

	@Test
	public void testColumnReference_unknownProperty_usesRawName() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("a.RAW_COL", meta.columnReference(null, "RAW_COL"));
	}

	// =======================================================================
	// getColumnMatchCriteria
	// =======================================================================

	@Test
	public void testGetColumnMatchCriteria_producesInExpression() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("a.LABEL IN ('x', 'y')", meta.getColumnMatchCriteria("label", List.of("'x'", "'y'")));
	}

	// =======================================================================
	// getColumnCompareCriteria / getJoinedColumnCompareCriteria
	// =======================================================================

	@Test
	public void testGetColumnCompareCriteria_emptyListReturnsNull() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertNull(meta.getColumnCompareCriteria("label", "=", List.of()));
	}

	@Test
	public void testGetColumnCompareCriteria_singleValue() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("a.LABEL = 'foo'", meta.getColumnCompareCriteria("label", "=", List.of("'foo'")));
	}

	@Test
	public void testGetColumnCompareCriteria_multipleValues_eachPrefixedWithReferenceAndOp() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertEquals("(a.LABEL = 'x' OR a.LABEL = 'y')",
				meta.getColumnCompareCriteria("label", "=", List.of("'x'", "'y'")));
	}

	@Test
	public void testGetJoinedColumnCompareCriteria_withTableAlias() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertEquals("(b.DETAIL LIKE 'a%' OR b.DETAIL LIKE 'b%')",
				meta.getJoinedColumnCompareCriteria("sec_tbl", "detail", "LIKE", List.of("'a%'", "'b%'")));
	}


	@Test
	public void testGetInsertStatement_containsInsertIntoTableName() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getInsertStatement();
		assertTrue(sql.startsWith("INSERT INTO pub.items"));
	}

	@Test
	public void testGetInsertStatement_containsColumnNamesAndPlaceholders() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getInsertStatement();
		assertTrue(sql.contains("ID"));
		assertTrue(sql.contains("LABEL"));
		assertTrue(sql.contains("?"));
	}

	@Test
	public void testGetInsertStatement_returnsSameInstanceOnRepeatCall() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertSame(meta.getInsertStatement(), meta.getInsertStatement());
	}

	// =======================================================================
	// getDeleteStatement
	// =======================================================================

	@Test
	public void testGetDeleteStatement_containsDeleteFromTableName() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getDeleteStatement();
		assertTrue(sql.startsWith("DELETE FROM pub.items"));
	}

	@Test
	public void testGetDeleteStatement_containsWhereIdCondition() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertTrue(meta.getDeleteStatement().contains("ID = ?"));
	}

	@Test
	public void testGetDeleteStatement_throwsForEffectiveDatedEntity() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		assertThrows(UnsupportedOperationException.class, meta::getDeleteStatement);
	}

	// =======================================================================
	// getUpdateStatement
	// =======================================================================

	@Test
	public void testGetUpdateStatement_containsUpdateAndSet() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getUpdateStatement(List.of("label"));
		assertTrue(sql.startsWith("UPDATE pub.items"));
		assertTrue(sql.contains("LABEL = ?"));
	}

	@Test
	public void testGetUpdateStatement_throwsWhenEmpty() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertThrows(edu.iu.dao.IuSqlUnchangedException.class,
				() -> meta.getUpdateStatement(List.of()));
	}

	@Test
	public void testGetUpdateStatement_throwsForSecondaryTableColumn() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		assertThrows(IllegalArgumentException.class, () -> meta.getUpdateStatement(List.of("detail")));
	}

	@Test
	public void testGetUpdateStatement_repeatCallReturnsSameStringInstance() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var props = List.of("label");
		final var first = meta.getUpdateStatement(props);
		final var second = meta.getUpdateStatement(props);
		assertSame(first, second);
	}

	// =======================================================================
	// getSelectAndFromClause
	// =======================================================================

	@Test
	public void testGetSelectAndFromClause_containsSelectAndFrom() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getSelectAndFromClause(null);
		assertTrue(sql.startsWith("SELECT"));
		assertTrue(sql.contains("FROM pub.items a"));
	}

	@Test
	public void testGetSelectAndFromClause_withSecondaryTable_containsJoin() {
		final var meta = EntityMetaData.of(SecondaryTableEntity.class);
		final var sql = meta.getSelectAndFromClause(null);
		assertTrue(sql.contains("JOIN sec_tbl b"));
		assertTrue(sql.contains("ON a.ID = b.SEC_ID"));
	}

	@Test
	public void testGetSelectAndFromClause_distinctPrefixWhenDistinctEntity() {
		final var meta = EntityMetaData.of(DistinctEntity.class);
		assertTrue(meta.getSelectAndFromClause(null).startsWith("SELECT DISTINCT"));
	}

	@Test
	public void testGetSelectAndFromClause_throwsWhenPropsEmpty() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		assertThrows(IllegalArgumentException.class,
				() -> meta.getSelectAndFromClause(List.of()));
	}

	@Test
	public void testGetSelectAndFromClause_repeatCallReturnsSameStringInstance() {
		// Result is cached by fingerprint; same props → same String object.
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var props = List.of("id", "label");
		final var first = meta.getSelectAndFromClause(props);
		final var second = meta.getSelectAndFromClause(props);
		assertSame(first, second);
	}

	@Test
	public void testGetSelectAndFromClause_unknownPropAppendsRawString() {
		// resolveColumn returns null → raw prop string emitted with no alias prefix.
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getSelectAndFromClause(List.of("SYSDATE"));
		assertTrue(sql.contains("\n    SYSDATE"));
		assertFalse(sql.contains("SYSDATE.") || sql.contains("AS "));
	}

	@Test
	public void testGetSelectAndFromClause_sqlColumnPropertyAppendsSelectAlias() {
		// @SqlColumn columns have a non-null selectAlias → "sql AS ALIAS" in SELECT.
		final var meta = EntityMetaData.of(ColumnScanEntity.class);
		final var sql = meta.getSelectAndFromClause(List.of("sqlExpr"));
		// ColumnScanEntity.sqlExpr: @SqlColumn("UPPER(col_a)") → selectAlias = "SQL_EXPR"
		assertTrue(sql.contains("UPPER(col_a) AS SQL_EXPR"));
	}

	@Test
	public void testGetSelectAndFromClause_multipleJoinConditions_allAppearWithAndSeparator() {
		// MultiJoinConditionEntity has one secondary table with two pkJoinColumns:
		// FK1 and FK2. Both conditions must appear in the ON clause joined by AND.
		final var meta = EntityMetaData.of(MultiJoinConditionEntity.class);
		final var sql = meta.getSelectAndFromClause(null);
		assertTrue(sql.contains("JOIN multi_fk_sec b"));
		assertTrue(sql.contains("a.FK1 = b.FK1"));
		assertTrue(sql.contains("a.FK2 = b.FK2"));
		// AND separator between the two conditions
		assertTrue(sql.contains("a.FK1 = b.FK1 AND a.FK2 = b.FK2"));
	}

	// =======================================================================
	// getSelectStatement
	// =======================================================================

	@Test
	public void testGetSelectStatement_basic_noWhereNoOrderNoForUpdate() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getSelectStatement(null, List.of(), List.of(), false);
		assertTrue(sql.startsWith("SELECT"));
		assertTrue(sql.contains("FROM pub.items a"));
		assertFalse(sql.contains("WHERE"));
		assertFalse(sql.contains("ORDER BY"));
		assertFalse(sql.contains("FOR UPDATE"));
	}

	@Test
	public void testGetSelectStatement_withWhere_producesWhereClause() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getSelectStatement(null, List.of("a.ID = ?"), List.of(), false);
		assertTrue(sql.contains("WHERE a.ID = ?"));
	}

	@Test
	public void testGetSelectStatement_withOrder_producesOrderByClause() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getSelectStatement(null, List.of(), List.of("a.LABEL"), false);
		assertTrue(sql.contains("ORDER BY a.LABEL"));
	}

	@Test
	public void testGetSelectStatement_forUpdate_appendsForUpdate() {
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getSelectStatement(null, List.of(), List.of(), true);
		assertTrue(sql.endsWith("\nFOR UPDATE"));
	}

	@Test
	public void testGetSelectStatement_filteredAnnotation_filterAppearsInWhere() {
		// FilteredEntity has @Filtered(sql = "a.active = TRUE") — resolveFilters()
		// injects it even when no explicit where criteria are passed.
		final var meta = EntityMetaData.of(FilteredEntity.class);
		final var sql = meta.getSelectStatement(null, List.of(), List.of(), false);
		assertTrue(sql.contains("WHERE a.active = TRUE"));
	}

	@Test
	public void testGetSelectStatement_combinedWhereAndFilteredAnnotation() {
		final var meta = EntityMetaData.of(FilteredEntity.class);
		final var sql = meta.getSelectStatement(null, List.of("a.STATUS = 'A'"), List.of(), false);
		// Explicit where comes first, then the @Filtered predicate
		assertTrue(sql.contains("a.STATUS = 'A'"));
		assertTrue(sql.contains("a.active = TRUE"));
		assertTrue(sql.indexOf("a.STATUS = 'A'") < sql.indexOf("a.active = TRUE"));
	}

	@Test
	public void testGetSelectStatement_withEffectiveDatedCurrentOnly_criteriaInWhere() {
		// CurrentOnlyEffectiveDatedEntity has currentOnly=true → effective-date
		// subquery is injected into WHERE automatically.
		final var meta = EntityMetaData.of(CurrentOnlyEffectiveDatedEntity.class);
		final var sql = meta.getSelectStatement(null, List.of(), List.of(), false);
		assertTrue(sql.contains("WHERE"));
		assertTrue(sql.contains("a.EFF_DATE = (SELECT MAX(a_ed.EFF_DATE)"));
	}

	@Test
	public void testGetSelectStatement_repeatCallReturnsSameStringInstance() {
		// Result is cached by fingerprint of all four inputs.
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var where = List.of("a.ID = ?");
		final var order = List.of("a.LABEL");
		final var first = meta.getSelectStatement(List.of("id"), where, order, false);
		final var second = meta.getSelectStatement(List.of("id"), where, order, false);
		assertSame(first, second);
	}

	@Test
	public void testGetSelectStatement_nullPropsUsesDefaultPropertyNames() {
		// null props → defaultPropertyNames() → all mapped columns appear in SELECT
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getSelectStatement(null, List.of(), List.of(), false);
		assertTrue(sql.contains("a.ID"));
		assertTrue(sql.contains("a.LABEL"));
	}

	@Test
	public void testGetSelectStatement_whereBeforeOrderBeforeForUpdate() {
		// Clause ordering: WHERE → ORDER BY → FOR UPDATE
		final var meta = EntityMetaData.of(TableNamedEntity.class);
		final var sql = meta.getSelectStatement(List.of("id"), List.of("a.ID = ?"),
				List.of("a.ID"), true);
		final int whereIdx = sql.indexOf("WHERE");
		final int orderIdx = sql.indexOf("ORDER BY");
		final int forUpdateIdx = sql.indexOf("FOR UPDATE");
		assertTrue(whereIdx > 0);
		assertTrue(orderIdx > whereIdx);
		assertTrue(forUpdateIdx > orderIdx);
	}

	// =======================================================================

	@Test
	public void testResolveFilters_emptyWhenNoFilteredAnnotation() {
		assertFalse(EntityMetaData.of(TableNamedEntity.class).resolveFilters().iterator().hasNext());
	}

	@Test
	public void testResolveFilters_returnsSqlWhenFilteredAnnotationPresent() {
		final var filters = IuIterable.stream(EntityMetaData.of(FilteredEntity.class).resolveFilters()).toList();
		assertEquals(1, filters.size());
		assertEquals("a.active = TRUE", filters.get(0));
	}

	// =======================================================================
	// resolveFilter — named filter scenarios
	// =======================================================================

	@Test
	public void testResolveFilter_effectiveDate_defaultAsOfDate_producesSubquery() {
		final var meta = EntityMetaData.of(EffectiveDateFilterEntity.class);
		final var sql = meta.resolveFilter(meta.filtered.filters()[0]);
		// Default asOfDate = "CURRENT_DATE", past direction (<=)
		assertTrue(sql.startsWith("a.EFF_DATE = (SELECT MAX(a_ed.EFF_DATE)"),
				"Expected effective-date past subquery, got: " + sql);
		assertTrue(sql.contains("a_ed.EFF_DATE <= CURRENT_DATE"));
		// ID is the only key column (EFF_DATE itself is excluded)
		assertTrue(sql.contains("a_ed.ID = a.ID"));
	}

	@Test
	public void testResolveFilter_effectiveDate_explicitAsOfDate_usesSuppliedDate() {
		final var meta = EntityMetaData.of(EffectiveDateExplicitAsOfFilterEntity.class);
		final var sql = meta.resolveFilter(meta.filtered.filters()[0]);
		assertTrue(sql.contains("a_ed.EFF_DATE <= SYSDATE"),
				"Expected SYSDATE as asOfDate, got: " + sql);
	}

	@Test
	public void testResolveFilter_maxDate_producesMaxDateSubquery() {
		final var meta = EntityMetaData.of(MaxDateFilterEntity.class);
		final var sql = meta.resolveFilter(meta.filtered.filters()[0]);
		assertTrue(sql.startsWith("a.EFF_DATE = (SELECT MAX(a_md.EFF_DATE)"),
				"Expected max-date subquery, got: " + sql);
		assertTrue(sql.contains("a_md.ID = a.ID"));
	}

	@Test
	public void testResolveFilter_columnMatch_producesInPredicate() {
		final var meta = EntityMetaData.of(ColumnMatchFilterEntity.class);
		final var sql = meta.resolveFilter(meta.filtered.filters()[0]);
		assertEquals("a.LABEL IN ('A', 'B')", sql);
	}

	@Test
	public void testResolveFilter_columnCompare_producesComparisonPredicate() {
		final var meta = EntityMetaData.of(ColumnCompareFilterEntity.class);
		final var sql = meta.resolveFilter(meta.filtered.filters()[0]);
		assertEquals("a.LABEL = 'active'", sql);
	}

	@Test
	public void testResolveFilter_columnCompare_noValues_returnsNull() {
		// Empty match list → getJoinedColumnCompareCriteria returns null
		final var meta = EntityMetaData.of(ColumnCompareNoValuesFilterEntity.class);
		assertNull(meta.resolveFilter(meta.filtered.filters()[0]));
	}

	@Test
	public void testResolveFilters_nullReturnFilteredOut() {
		// columnCompare with no values → resolveFilter returns null → resolveFilters
		// filters it out, so the resulting Iterable is empty
		final var filters = IuIterable.stream(
				EntityMetaData.of(ColumnCompareNoValuesFilterEntity.class).resolveFilters()).toList();
		assertTrue(filters.isEmpty());
	}

	@Test
	public void testResolveFilter_unsupportedName_throwsIllegalArgumentException() {
		final var meta = EntityMetaData.of(UnsupportedFilterNameEntity.class);
		assertThrows(IllegalArgumentException.class, () -> meta.resolveFilter(meta.filtered.filters()[0]));
	}

	// =======================================================================
	// resolveEffectiveDatedCriteria
	// =======================================================================

	@Test
	public void testResolveEffectiveDatedCriteria_emptyWhenNoAnnotation() {
		assertFalse(EntityMetaData.of(TableNamedEntity.class).resolveEffectiveDatedCriteria().iterator().hasNext());
	}

	@Test
	public void testResolveEffectiveDatedCriteria_emptyWhenCurrentOnlyFalse() {
		// EffectiveDatedEntity uses default currentOnly() which is false
		assertFalse(
				EntityMetaData.of(EffectiveDatedEntity.class).resolveEffectiveDatedCriteria().iterator().hasNext());
	}

	// =======================================================================
	// buildEffectiveDateCriteria
	// =======================================================================

	// Uses EffectiveDatedEntity: primary table pub.prices, single @Id column "ID".

	@Test
	public void testBuildEffectiveDateCriteria_past_singleIdColumn() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildEffectiveDateCriteria("a", "EFF_DATE", "CURRENT_DATE", false, List.of("ID"));
		assertEquals(
				"a.EFF_DATE = (SELECT MAX(a_ed.EFF_DATE)\n"
						+ "        FROM pub.prices a_ed\n"
						+ "       WHERE a_ed.ID = a.ID\n"
						+ "         AND a_ed.EFF_DATE <= CURRENT_DATE)",
				sql);
	}

	@Test
	public void testBuildEffectiveDateCriteria_future_singleIdColumn() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildEffectiveDateCriteria("a", "EFF_DATE", "CURRENT_DATE", true, List.of("ID"));
		assertEquals(
				"a.EFF_DATE = (SELECT MIN(a_ed.EFF_DATE)\n"
						+ "        FROM pub.prices a_ed\n"
						+ "       WHERE a_ed.ID = a.ID\n"
						+ "         AND a_ed.EFF_DATE > CURRENT_DATE)",
				sql);
	}

	@Test
	public void testBuildEffectiveDateCriteria_emptyIdColumns_noCorrelation() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildEffectiveDateCriteria("a", "EFF_DATE", "CURRENT_DATE", false, List.of());
		assertEquals(
				"a.EFF_DATE = (SELECT MAX(a_ed.EFF_DATE)\n"
						+ "        FROM pub.prices a_ed\n"
						+ "       WHERE a_ed.EFF_DATE <= CURRENT_DATE)",
				sql);
	}

	@Test
	public void testBuildEffectiveDateCriteria_multipleIdColumns() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildEffectiveDateCriteria("a", "EFF_DATE", "CURRENT_DATE", false,
				List.of("ID", "REGION"));
		assertEquals(
				"a.EFF_DATE = (SELECT MAX(a_ed.EFF_DATE)\n"
						+ "        FROM pub.prices a_ed\n"
						+ "       WHERE a_ed.ID = a.ID\n"
						+ "         AND a_ed.REGION = a.REGION\n"
						+ "         AND a_ed.EFF_DATE <= CURRENT_DATE)",
				sql);
	}

	@Test
	public void testBuildEffectiveDateCriteria_customOuterAlias() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildEffectiveDateCriteria("p", "EFF_DATE", ":asOf", false, List.of("ID"));
		// sub-alias is outerAlias + "_ed" = "p_ed"
		assertTrue(sql.startsWith("p.EFF_DATE = (SELECT MAX(p_ed.EFF_DATE)"));
		assertTrue(sql.contains("FROM pub.prices p_ed"));
		assertTrue(sql.contains("p_ed.ID = p.ID"));
		assertTrue(sql.contains("p_ed.EFF_DATE <= :asOf)"));
	}

	// =======================================================================
	// buildMaxDateCriteria
	// =======================================================================

	// Uses EffectiveDatedEntity: primary table pub.prices, single @Id column "ID".
	// Note: appendCorrelation return value is NOT checked, so empty idColumnNames
	// produces a trailing "WHERE )" with no condition.

	@Test
	public void testBuildMaxDateCriteria_singleIdColumn() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildMaxDateCriteria("a", "MAX_DATE", List.of("ID"));
		assertEquals(
				"a.MAX_DATE = (SELECT MAX(a_md.MAX_DATE)\n"
						+ "        FROM pub.prices a_md\n"
						+ "       WHERE a_md.ID = a.ID)",
				sql);
	}

	@Test
	public void testBuildMaxDateCriteria_multipleIdColumns() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildMaxDateCriteria("a", "MAX_DATE", List.of("ID", "REGION"));
		assertEquals(
				"a.MAX_DATE = (SELECT MAX(a_md.MAX_DATE)\n"
						+ "        FROM pub.prices a_md\n"
						+ "       WHERE a_md.ID = a.ID\n"
						+ "         AND a_md.REGION = a.REGION)",
				sql);
	}

	@Test
	public void testBuildMaxDateCriteria_emptyIdColumns_producesWhereWithNoCondition() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildMaxDateCriteria("a", "MAX_DATE", List.of());
		assertEquals(
				"a.MAX_DATE = (SELECT MAX(a_md.MAX_DATE)\n"
						+ "        FROM pub.prices a_md)",
				sql);
	}

	@Test
	public void testBuildMaxDateCriteria_customOuterAlias() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildMaxDateCriteria("p", "MAX_DATE", List.of("ID"));
		// sub-alias is outerAlias + "_md" = "p_md"
		assertTrue(sql.startsWith("p.MAX_DATE = (SELECT MAX(p_md.MAX_DATE)"));
		assertTrue(sql.contains("FROM pub.prices p_md"));
		assertTrue(sql.contains("p_md.ID = p.ID)"));
	}

	// =======================================================================
	// buildEffectiveDateSeqCriteria
	// =======================================================================

	// Produces two correlated subqueries:
	//   1. effective-date part (always future=false) via buildEffectiveDateCriteria
	//   2. sequence part using sub-alias outerAlias+"_seq"
	// idColumnNames is iterated twice — List.of() is re-iterable.

	@Test
	public void testBuildEffectiveDateSeqCriteria_singleIdColumn() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildEffectiveDateSeqCriteria("a", "EFF_DATE", "SEQ_NUM", "CURRENT_DATE",
				List.of("ID"));
		assertEquals(
				"a.EFF_DATE = (SELECT MAX(a_ed.EFF_DATE)\n"
						+ "        FROM pub.prices a_ed\n"
						+ "       WHERE a_ed.ID = a.ID\n"
						+ "         AND a_ed.EFF_DATE <= CURRENT_DATE)\n"
						+ "  AND a.SEQ_NUM = (SELECT MAX(a_seq.SEQ_NUM)\n"
						+ "        FROM pub.prices a_seq\n"
						+ "       WHERE a_seq.ID = a.ID\n"
						+ "         AND a_seq.EFF_DATE = a.EFF_DATE)",
				sql);
	}

	@Test
	public void testBuildEffectiveDateSeqCriteria_emptyIdColumns_noCorrelation() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildEffectiveDateSeqCriteria("a", "EFF_DATE", "SEQ_NUM", "CURRENT_DATE", List.of());
		assertEquals(
				"a.EFF_DATE = (SELECT MAX(a_ed.EFF_DATE)\n"
						+ "        FROM pub.prices a_ed\n"
						+ "       WHERE a_ed.EFF_DATE <= CURRENT_DATE)\n"
						+ "  AND a.SEQ_NUM = (SELECT MAX(a_seq.SEQ_NUM)\n"
						+ "        FROM pub.prices a_seq\n"
						+ "       WHERE a_seq.EFF_DATE = a.EFF_DATE)",
				sql);
	}

	@Test
	public void testBuildEffectiveDateSeqCriteria_multipleIdColumns() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildEffectiveDateSeqCriteria("a", "EFF_DATE", "SEQ_NUM", "CURRENT_DATE",
				List.of("ID", "REGION"));
		assertEquals(
				"a.EFF_DATE = (SELECT MAX(a_ed.EFF_DATE)\n"
						+ "        FROM pub.prices a_ed\n"
						+ "       WHERE a_ed.ID = a.ID\n"
						+ "         AND a_ed.REGION = a.REGION\n"
						+ "         AND a_ed.EFF_DATE <= CURRENT_DATE)\n"
						+ "  AND a.SEQ_NUM = (SELECT MAX(a_seq.SEQ_NUM)\n"
						+ "        FROM pub.prices a_seq\n"
						+ "       WHERE a_seq.ID = a.ID\n"
						+ "         AND a_seq.REGION = a.REGION\n"
						+ "         AND a_seq.EFF_DATE = a.EFF_DATE)",
				sql);
	}

	@Test
	public void testBuildEffectiveDateSeqCriteria_customOuterAliasAndBindParam() {
		final var meta = EntityMetaData.of(EffectiveDatedEntity.class);
		final var sql = meta.buildEffectiveDateSeqCriteria("p", "EFF_DATE", "SEQ_NUM", ":asOf", List.of("ID"));
		// effective-date sub-alias = "p_ed", sequence sub-alias = "p_seq"
		assertTrue(sql.contains("SELECT MAX(p_ed.EFF_DATE)"));
		assertTrue(sql.contains("p_ed.EFF_DATE <= :asOf)"));
		assertTrue(sql.contains("SELECT MAX(p_seq.SEQ_NUM)"));
		assertTrue(sql.contains("p_seq.ID = p.ID"));
		assertTrue(sql.contains("p_seq.EFF_DATE = p.EFF_DATE)"));
	}

	// =======================================================================
	// effectiveDateKeyColumns
	// =======================================================================

	@Test
	public void testEffectiveDateKeyColumns_effectiveColumnInIdColumns_isExcluded() {
		// CompoundKeyEffectiveDatedEntity has two @Id columns: ID and EFF_DATE.
		// When EFF_DATE is the effective-date column it must be excluded from the
		// correlated key set, leaving only ID.
		final var meta = EntityMetaData.of(CompoundKeyEffectiveDatedEntity.class);
		final var keys = IuIterable.stream(
				meta.effectiveDateKeyColumns("EFF_DATE", new String[0], new String[0])).toList();
		assertEquals(List.of("ID"), keys);
	}

}
