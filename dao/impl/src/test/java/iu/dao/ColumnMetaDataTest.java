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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import edu.iu.dao.SpaceForNull;
import edu.iu.dao.SqlColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;

@SuppressWarnings("javadoc")
public class ColumnMetaDataTest {

	// =======================================================================
	// Test entity classes
	// =======================================================================

	/** Entity with a mix of column annotation styles. */
	@Entity
	@Table(name = "primary_tbl", schema = "s")
	@SecondaryTable(name = "sec_tbl", pkJoinColumns = @PrimaryKeyJoinColumn(name = "SEC_ID"))
	public static class MixedEntity {
		private long id;
		private String namedCol;
		private String unnamedCol;
		private String secCol;
		private String rawExpr;
		private String spaceCol;

		// @Id + @Column with explicit name.
		@Id
		@Column(name = "ID")
		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}

		// @Column with explicit name on primary table.
		@Column(name = "NAMED_COL")
		public String getNamedCol() {
			return namedCol;
		}

		public void setNamedCol(String namedCol) {
			this.namedCol = namedCol;
		}

		// @Column without a name — derives from camelToSnakeUpper("unnamedCol").
		@Column
		public String getUnnamedCol() {
			return unnamedCol;
		}

		public void setUnnamedCol(String unnamedCol) {
			this.unnamedCol = unnamedCol;
		}

		// @Column mapped to the secondary table.
		@Column(name = "SEC_COL", table = "sec_tbl")
		public String getSecCol() {
			return secCol;
		}

		public void setSecCol(String secCol) {
			this.secCol = secCol;
		}

		// @SqlColumn with a raw SQL expression.
		@SqlColumn("UPPER(named_col)")
		public String getRawExpr() {
			return rawExpr;
		}

		public void setRawExpr(String rawExpr) {
			this.rawExpr = rawExpr;
		}

		// @Column with method-level @SpaceForNull.
		@SpaceForNull
		@Column(name = "SPACE_COL")
		public String getSpaceCol() {
			return spaceCol;
		}

		public void setSpaceCol(String spaceCol) {
			this.spaceCol = spaceCol;
		}
	}

	/** Entity with class-level @SpaceForNull so all columns inherit the flag. */
	@Entity
	@SpaceForNull
	@Table(name = "spacey_tbl")
	public static class ClassSpaceForNullEntity {
		private String val;

		@Column(name = "VAL")
		public String getVal() {
			return val;
		}

		public void setVal(String val) {
			this.val = val;
		}
	}

	// =======================================================================
	// Helpers
	// =======================================================================

	private static ColumnMetaData col(Class<?> entityClass, String propertyName) {
		return EntityMetaData.of(entityClass).columns.get(propertyName);
	}

	// =======================================================================
	// @Column path — field values
	// =======================================================================

	@Test
	public void testColumn_propertyNameSet() {
		assertEquals("namedCol", col(MixedEntity.class, "namedCol").propertyName);
	}

	@Test
	public void testColumn_columnNameFromAnnotation() {
		assertEquals("NAMED_COL", col(MixedEntity.class, "namedCol").columnName);
	}

	@Test
	public void testColumn_columnNameDerivedFromPropertyWhenAnnotationNameBlank() {
		// @Column without name → camelToSnakeUpper("unnamedCol") = "UNNAMED_COL"
		assertEquals("UNNAMED_COL", col(MixedEntity.class, "unnamedCol").columnName);
	}

	@Test
	public void testColumn_sqlEqualsColumnName() {
		final var col = col(MixedEntity.class, "namedCol");
		assertEquals(col.columnName, col.sql);
	}

	@Test
	public void testColumn_selectAliasIsNull() {
		assertNull(col(MixedEntity.class, "namedCol").selectAlias);
	}

	@Test
	public void testColumn_columnAnnotationCaptured() {
		assertNotNull(col(MixedEntity.class, "namedCol").column);
	}

	@Test
	public void testColumn_sqlColumnIsNull() {
		assertNull(col(MixedEntity.class, "namedCol").sqlColumn);
	}

	@Test
	public void testColumn_tableIsPrimaryTableWhenNoTableAttribute() {
		final var meta = EntityMetaData.of(MixedEntity.class);
		assertSame(meta.primaryTable, col(MixedEntity.class, "namedCol").table);
	}

	@Test
	public void testColumn_tableIsSecondaryTableWhenTableAttributeSet() {
		final var meta = EntityMetaData.of(MixedEntity.class);
		final var secTable = meta.secondaryTables.iterator().next();
		assertSame(secTable, col(MixedEntity.class, "secCol").table);
	}

	// =======================================================================
	// @Column path — @Id
	// =======================================================================

	@Test
	public void testColumn_idAnnotationCapturedForIdProperty() {
		assertNotNull(col(MixedEntity.class, "id").id);
	}

	@Test
	public void testColumn_idAnnotationNullForNonIdProperty() {
		assertNull(col(MixedEntity.class, "namedCol").id);
	}

	// =======================================================================
	// @Column path — spaceForNull
	// =======================================================================

	@Test
	public void testColumn_spaceForNullFalseByDefault() {
		assertFalse(col(MixedEntity.class, "namedCol").spaceForNull);
	}

	@Test
	public void testColumn_spaceForNullTrueFromMethodAnnotation() {
		assertTrue(col(MixedEntity.class, "spaceCol").spaceForNull);
	}

	@Test
	public void testColumn_spaceForNullTrueInheritedFromEntityAnnotation() {
		assertTrue(col(ClassSpaceForNullEntity.class, "val").spaceForNull);
	}

	// =======================================================================
	// @Column path — isMappedColumn / isPrimaryColumn
	// =======================================================================

	@Test
	public void testColumn_isMappedColumnTrue() {
		assertTrue(col(MixedEntity.class, "namedCol").isMappedColumn());
	}

	@Test
	public void testColumn_isPrimaryColumnTrueForPrimaryTableColumn() {
		assertTrue(col(MixedEntity.class, "namedCol").isPrimaryColumn());
	}

	@Test
	public void testColumn_isPrimaryColumnFalseForSecondaryTableColumn() {
		assertFalse(col(MixedEntity.class, "secCol").isPrimaryColumn());
	}

	// =======================================================================
	// @Column path — reference()
	// =======================================================================

	@Test
	public void testColumn_referenceUsesTableAlias() {
		assertEquals("a.NAMED_COL", col(MixedEntity.class, "namedCol").reference());
	}

	@Test
	public void testColumn_referenceWithOverrideUsesProvidedAlias() {
		assertEquals("x.NAMED_COL", col(MixedEntity.class, "namedCol").reference("x"));
	}

	@Test
	public void testColumn_referenceNullOverrideFallsBackToTableAlias() {
		assertEquals("a.NAMED_COL", col(MixedEntity.class, "namedCol").reference(null));
	}

	@Test
	public void testColumn_referenceForSecondaryColumn() {
		assertEquals("b.SEC_COL", col(MixedEntity.class, "secCol").reference());
	}

	// =======================================================================
	// @SqlColumn path — field values
	// =======================================================================

	@Test
	public void testSqlColumn_propertyNameSet() {
		assertEquals("rawExpr", col(MixedEntity.class, "rawExpr").propertyName);
	}

	@Test
	public void testSqlColumn_columnNameIsNull() {
		assertNull(col(MixedEntity.class, "rawExpr").columnName);
	}

	@Test
	public void testSqlColumn_sqlHoldsRawExpression() {
		assertEquals("UPPER(named_col)", col(MixedEntity.class, "rawExpr").sql);
	}

	@Test
	public void testSqlColumn_selectAliasIsCamelToSnakeUpperOfPropertyName() {
		// camelToSnakeUpper("rawExpr") = "RAW_EXPR"
		assertEquals("RAW_EXPR", col(MixedEntity.class, "rawExpr").selectAlias);
	}

	@Test
	public void testSqlColumn_tableIsNull() {
		assertNull(col(MixedEntity.class, "rawExpr").table);
	}

	@Test
	public void testSqlColumn_columnAnnotationIsNull() {
		assertNull(col(MixedEntity.class, "rawExpr").column);
	}

	@Test
	public void testSqlColumn_sqlColumnAnnotationCaptured() {
		assertNotNull(col(MixedEntity.class, "rawExpr").sqlColumn);
		assertEquals("UPPER(named_col)", col(MixedEntity.class, "rawExpr").sqlColumn.value());
	}

	// =======================================================================
	// @SqlColumn path — isMappedColumn / isPrimaryColumn
	// =======================================================================

	@Test
	public void testSqlColumn_isMappedColumnFalse() {
		assertFalse(col(MixedEntity.class, "rawExpr").isMappedColumn());
	}

	@Test
	public void testSqlColumn_isPrimaryColumnFalse() {
		assertFalse(col(MixedEntity.class, "rawExpr").isPrimaryColumn());
	}

	// =======================================================================
	// @SqlColumn path — reference()
	// =======================================================================

	@Test
	public void testSqlColumn_referenceReturnsRawExpressionWithNoAlias() {
		assertEquals("UPPER(named_col)", col(MixedEntity.class, "rawExpr").reference());
	}

	@Test
	public void testSqlColumn_referenceWithOverrideStillReturnsRawExpressionWithNoAlias() {
		// aliasOverride is ignored when table is null
		assertEquals("UPPER(named_col)", col(MixedEntity.class, "rawExpr").reference("x"));
	}

	// =======================================================================
	// property field
	// =======================================================================

	@Test
	public void testProperty_descriptorIsSet() {
		final var col = col(MixedEntity.class, "namedCol");
		assertNotNull(col.property);
		assertEquals("namedCol", col.property.getName());
	}

	// =======================================================================
	// normalizeArgument
	// =======================================================================

	@Test
	public void testNormalizeArgument_nullWithSpaceForNullFalse_returnsNull() {
		assertNull(col(MixedEntity.class, "namedCol").normalizeArgument(null));
	}

	@Test
	public void testNormalizeArgument_nullWithSpaceForNullTrue_returnsSpace() {
		assertEquals(" ", col(MixedEntity.class, "spaceCol").normalizeArgument(null));
	}

	@Test
	public void testNormalizeArgument_instantValue_returnsTimestamp() {
		final var now = Instant.now();
		final var result = col(MixedEntity.class, "namedCol").normalizeArgument(now);
		assertInstanceOf(Timestamp.class, result);
		assertEquals(Timestamp.from(now), result);
	}

	@Test
	public void testNormalizeArgument_nonNullNonInstant_returnsSameInstance() {
		final var value = "hello";
		assertSame(value, col(MixedEntity.class, "namedCol").normalizeArgument(value));
	}

	@Test
	public void testNormalizeArgument_spaceForNullTrue_nonNullValue_returnsSameInstance() {
		// spaceForNull only triggers for null; non-null values pass through unchanged
		final var value = "not-null";
		assertSame(value, col(MixedEntity.class, "spaceCol").normalizeArgument(value));
	}

}
