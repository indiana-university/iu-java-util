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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import jakarta.persistence.Column;
import jakarta.persistence.Transient;

@SuppressWarnings("javadoc")
public class DaoUtilsTest {

	// -----------------------------------------------------------------------
	// camelToSnakeUpper
	// -----------------------------------------------------------------------

	@Test
	public void testCamelToSnakeUpper_singleWord() {
		assertEquals("FOO", DaoUtils.camelToSnakeUpper("foo"));
	}

	@Test
	public void testCamelToSnakeUpper_alreadyUpper() {
		assertEquals("F_O_O", DaoUtils.camelToSnakeUpper("FOO"));
	}

	@Test
	public void testCamelToSnakeUpper_camelCase() {
		assertEquals("MY_COLUMN_NAME", DaoUtils.camelToSnakeUpper("myColumnName"));
	}

	@Test
	public void testCamelToSnakeUpper_leadingUpperNotPrefixed() {
		// Leading uppercase letter must NOT produce a leading underscore
		assertEquals("MY_COLUMN", DaoUtils.camelToSnakeUpper("MyColumn"));
	}

	@Test
	public void testCamelToSnakeUpper_empty() {
		assertEquals("", DaoUtils.camelToSnakeUpper(""));
	}

	// -----------------------------------------------------------------------
	// qualifyName
	// -----------------------------------------------------------------------

	@Test
	public void testQualifyName_withSchema() {
		assertEquals("myschema.mytable", DaoUtils.qualifyName("myschema", "mytable"));
	}

	@Test
	public void testQualifyName_nullSchema() {
		assertEquals("mytable", DaoUtils.qualifyName(null, "mytable"));
	}

	@Test
	public void testQualifyName_blankSchema() {
		assertEquals("mytable", DaoUtils.qualifyName("   ", "mytable"));
	}

	@Test
	public void testQualifyName_emptySchema() {
		assertEquals("mytable", DaoUtils.qualifyName("", "mytable"));
	}

	// -----------------------------------------------------------------------
	// normalizeName
	// -----------------------------------------------------------------------

	@Test
	public void testNormalizeName_null() {
		assertEquals("", DaoUtils.normalizeName(null));
	}

	@Test
	public void testNormalizeName_trimsAndUppercases() {
		assertEquals("HELLO", DaoUtils.normalizeName("  hello  "));
	}

	@Test
	public void testNormalizeName_alreadyNormal() {
		assertEquals("WORLD", DaoUtils.normalizeName("WORLD"));
	}

	// -----------------------------------------------------------------------
	// hasValue
	// -----------------------------------------------------------------------

	@Test
	public void testHasValue_null() {
		assertFalse(DaoUtils.hasValue(null));
	}

	@Test
	public void testHasValue_empty() {
		assertFalse(DaoUtils.hasValue(""));
	}

	@Test
	public void testHasValue_blank() {
		assertFalse(DaoUtils.hasValue("   "));
	}

	@Test
	public void testHasValue_nonBlank() {
		assertTrue(DaoUtils.hasValue("x"));
	}

	// -----------------------------------------------------------------------
	// getFingerprint
	// -----------------------------------------------------------------------

	@Test
	public void testGetFingerprint_null() {
		assertEquals(List.of(), DaoUtils.getFingerprint((Iterable<?>[]) null));
	}

	@Test
	public void testGetFingerprint_emptyVarargs() {
		assertEquals(List.of(), DaoUtils.getFingerprint());
	}

	@Test
	public void testGetFingerprint_sameInputSameResult() {
		final var a = List.of("x", "y");
		final var b = List.of("x", "y");
		assertEquals(List.of("x", "y"), DaoUtils.getFingerprint(a));
		assertEquals(DaoUtils.getFingerprint(a), DaoUtils.getFingerprint(b));
	}

	@Test
	public void testGetFingerprint_preservesOrder() {
		final var a = List.of("x", "y");
		final var b = List.of("y", "x");
		assertEquals(a, DaoUtils.getFingerprint(a));
		assertEquals(b, DaoUtils.getFingerprint(b));
	}

	@Test
	public void testGetFingerprint_nullElement() {
		final List<String> withNull = new ArrayList<>();
		withNull.add(null);
		final var fp = DaoUtils.getFingerprint(withNull);
		assertEquals(withNull, fp);
	}

	@Test
	public void testGetFingerprint_nullIterable() {
		assertEquals(List.of(), DaoUtils.getFingerprint((Iterable<?>) null));
	}

	@Test
	public void testGetFingerprint_multipleIterables() {
		final var a = List.of(1, 2);
		final var b = List.of(3, 4);
		assertEquals(List.of(1, 2, 3, 4), DaoUtils.getFingerprint(a, b));
		assertEquals(List.of(3, 4, 1, 2), DaoUtils.getFingerprint(b, a));
	}

	@Test
	public void testGetFingerprint_copiesValuesForStableKey() {
		final var values = new ArrayList<>(List.of("x"));
		final var fingerprint = DaoUtils.getFingerprint(values);
		values.add("y");
		assertEquals(List.of("x"), fingerprint);
	}

	// -----------------------------------------------------------------------
	// getAlias
	// -----------------------------------------------------------------------

	@Test
	public void testGetAlias_first() {
		assertEquals("a", DaoUtils.getAlias(0));
	}

	@Test
	public void testGetAlias_last() {
		assertEquals("z", DaoUtils.getAlias(25));
	}

	@Test
	public void testGetAlias_twoChars() {
		// index 26 → "ba" (inner loop produces "b", prepended before "a")
		final var alias = DaoUtils.getAlias(26);
		assertNotNull(alias);
		assertTrue(alias.length() > 1, alias);
	}

	@Test
	public void testGetAlias_unique() {
		// First 26 aliases must all be distinct single letters
		final var aliases = new ArrayList<String>();
		for (int i = 0; i < 26; i++)
			aliases.add(DaoUtils.getAlias(i));
		assertEquals(26, aliases.stream().distinct().count());
	}

	// -----------------------------------------------------------------------
	// getAllBeanProperties
	// -----------------------------------------------------------------------

	static class SimpleBean {
		private String name;
		private int value;

		@Column
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getValue() {
			return value;
		}

		public void setValue(int value) {
			this.value = value;
		}

		@Column
		public boolean isThisAProblem() {
			throw new IllegalStateException("yes");
		}
	}

	static class SubBean extends SimpleBean {
		private boolean active;

		public boolean isActive() {
			return active;
		}

		public void setActive(boolean active) {
			this.active = active;
		}

		public void setWriteOnly(Object o) {

		}
	}

	@Test
	public void testGetAllBeanProperties_simpleBean() {
		final var props = DaoUtils.getAllBeanProperties(SimpleBean.class);
		final var names = IuIterable.stream(props).map(p -> p.getName()).toList();
		assertTrue(names.contains("name"));
		assertTrue(names.contains("value"));
		assertFalse(names.contains("class"));
	}

	@Test
	public void testGetAllBeanProperties_subclassIncludesParentProps() {
		final var props = DaoUtils.getAllBeanProperties(SubBean.class);
		final var names = IuIterable.stream(props).map(p -> p.getName()).toList();
		assertTrue(names.contains("name"));
		assertTrue(names.contains("value"));
		assertTrue(names.contains("active"));
	}

	@Test
	public void testGetAllBeanProperties_noClassProperty() {
		final var props = DaoUtils.getAllBeanProperties(SimpleBean.class);
		assertTrue(IuIterable.stream(props).noneMatch(p -> "class".equals(p.getName())));
	}

	// -----------------------------------------------------------------------
	// getAnnotationFromHierarchy
	// -----------------------------------------------------------------------

	@jakarta.persistence.Entity
	static class AnnotatedParent {
	}

	static class AnnotatedChild extends AnnotatedParent {
	}

	static class Unannotated {
	}

	@Test
	public void testGetAnnotationFromHierarchy_directlyAnnotated() {
		assertNotNull(DaoUtils.getAnnotationFromHierarchy(AnnotatedParent.class, jakarta.persistence.Entity.class));
	}

	@Test
	public void testGetAnnotationFromHierarchy_inheritedAnnotation() {
		assertNotNull(DaoUtils.getAnnotationFromHierarchy(AnnotatedChild.class, jakarta.persistence.Entity.class));
	}

	@Test
	public void testGetAnnotationFromHierarchy_notPresent() {
		assertNull(DaoUtils.getAnnotationFromHierarchy(Unannotated.class, jakarta.persistence.Entity.class));
	}

	// -----------------------------------------------------------------------
	// getPropertyValue
	// -----------------------------------------------------------------------

	@Test
	public void testGetPropertyValue_returnsValue() throws Exception {
		final var bean = new SimpleBean();
		bean.setName("hello");
		final var column = EntityMetaData.of(SimpleBean.class).columns.get("name");
		assertEquals("hello", DaoUtils.getPropertyValue(bean, column));
	}

	@Test
	public void testGetPropertyValue_throws() throws Exception {
		final var bean = new SimpleBean();
		final var column = EntityMetaData.of(SimpleBean.class).columns.get("thisAProblem");
		final var err = assertThrows(IllegalStateException.class, () -> DaoUtils.getPropertyValue(bean, column));
		assertEquals("Failed to read thisAProblem from " + SimpleBean.class, err.getMessage());
		assertEquals("yes", err.getCause().getMessage());
	}

	// -----------------------------------------------------------------------
	// autobox
	// -----------------------------------------------------------------------

	@Test
	public void testAutobox_nonPrimitive() {
		assertEquals(String.class, DaoUtils.autobox(String.class));
	}

	@Test
	public void testAutobox_boolean() {
		assertEquals(Boolean.class, DaoUtils.autobox(boolean.class));
	}

	@Test
	public void testAutobox_byte() {
		assertEquals(Byte.class, DaoUtils.autobox(byte.class));
	}

	@Test
	public void testAutobox_short() {
		assertEquals(Short.class, DaoUtils.autobox(short.class));
	}

	@Test
	public void testAutobox_int() {
		assertEquals(Integer.class, DaoUtils.autobox(int.class));
	}

	@Test
	public void testAutobox_long() {
		assertEquals(Long.class, DaoUtils.autobox(long.class));
	}

	@Test
	public void testAutobox_float() {
		assertEquals(Float.class, DaoUtils.autobox(float.class));
	}

	@Test
	public void testAutobox_double() {
		assertEquals(Double.class, DaoUtils.autobox(double.class));
	}

	@Test
	public void testAutobox_char() {
		assertEquals(Character.class, DaoUtils.autobox(char.class));
	}

	@Test
	public void testAutobox_void() {
		assertEquals(void.class, DaoUtils.autobox(void.class));
	}

	// -----------------------------------------------------------------------
	// findProperty
	// -----------------------------------------------------------------------

	@Test
	public void testFindProperty_found() {
		assertNotNull(DaoUtils.findProperty(SimpleBean.class, "name"));
	}

	@Test
	public void testFindProperty_notFound() {
		assertThrows(NoSuchElementException.class, () -> DaoUtils.findProperty(SimpleBean.class, "nonExistent"));
	}

	// -----------------------------------------------------------------------
	// resolveSqlType — via @Column columnDefinition
	// -----------------------------------------------------------------------

	static class SqlTypeBean {
		@Column(columnDefinition = "VARCHAR(100)")
		public String getCharProp() {
			return null;
		}

		@Column(columnDefinition = "INTEGER")
		public long getIntProp() {
			return 0;
		}

		@Column(columnDefinition = "NUMBER(10,2)")
		public double getNumberProp() {
			return 0;
		}

		@Column(columnDefinition = "NUMERIC(10,2)")
		public double getNumericProp() {
			return 0;
		}

		@Column(columnDefinition = "DECIMAL(10,2)")
		public double getDecimalProp() {
			return 0;
		}

		@Column(columnDefinition = "DATE")
		public String getDateProp() {
			return null;
		}

		@Column(columnDefinition = "TIME")
		public String getTimeProp() {
			return null;
		}

		@Column(columnDefinition = "TIMESTAMP")
		public String getTimestampProp() {
			return null;
		}

		@Column(columnDefinition = "DATETIME")
		public String getDatetimeProp() {
			return null;
		}

		@Column(columnDefinition = "CLOB")
		public String getClobProp() {
			return null;
		}

		@Column(columnDefinition = "TEXT")
		public String getTextProp() {
			return null;
		}

		@Column(columnDefinition = "BLOB")
		public String getUnknownSqlProp() {
			return null;
		}

		@Column
		public String getNoValueProp() {
			return null;
		}

		public int getNoAnnotationProp() {
			return 0;
		}
	}

	private static Class<?> resolveSqlType(String propertyName) {
		final var property = DaoUtils.findProperty(SqlTypeBean.class, propertyName);
		final var column = property.getReadMethod().getAnnotation(Column.class);
		return DaoUtils.resolveSqlType(property.getPropertyType(), column);
	}

	@Test
	public void testResolveSqlType_charColumnDef() {
		assertEquals(String.class, resolveSqlType("charProp"));
	}

	@Test
	public void testResolveSqlType_intColumnDef() {
		assertEquals(Long.class, resolveSqlType("intProp"));
	}

	@Test
	public void testResolveSqlType_numberColumnDef() {
		assertEquals(Number.class, resolveSqlType("numberProp"));
	}

	@Test
	public void testResolveSqlType_numericColumnDef() {
		assertEquals(Number.class, resolveSqlType("numericProp"));
	}

	@Test
	public void testResolveSqlType_decimalColumnDef() {
		assertEquals(Number.class, resolveSqlType("decimalProp"));
	}

	@Test
	public void testResolveSqlType_dateColumnDef() {
		assertEquals(Date.class, resolveSqlType("dateProp"));
	}

	@Test
	public void testResolveSqlType_timeColumnDef() {
		assertEquals(Time.class, resolveSqlType("timeProp"));
	}

	@Test
	public void testResolveSqlType_timestampColumnDef() {
		assertEquals(Timestamp.class, resolveSqlType("timestampProp"));
	}

	@Test
	public void testResolveSqlType_datetimeColumnDef() {
		assertEquals(Timestamp.class, resolveSqlType("datetimeProp"));
	}

	@Test
	public void testResolveSqlType_clobColumnDef() {
		assertArrayEquals(new char[0], (char[]) java.lang.reflect.Array
				.newInstance(resolveSqlType("clobProp").getComponentType(), 0));
		assertEquals(char[].class, resolveSqlType("clobProp"));
	}

	@Test
	public void testResolveSqlType_textColumnDef() {
		assertEquals(char[].class, resolveSqlType("textProp"));
	}

	@Test
	public void testResolveSqlType_unknownSqlType() {
		assertEquals(Object.class, resolveSqlType("unknownSqlProp"));
	}

	@Test
	public void testResolveSqlType_noDefinition_autoboxed() {
		assertEquals(String.class, resolveSqlType("noValueProp"));
	}

	@Test
	public void testResolveSqlType_noAnnotation_autoboxed() {
		assertEquals(Integer.class, resolveSqlType("noAnnotationProp"));
	}

	// -----------------------------------------------------------------------
	// isTransient
	// -----------------------------------------------------------------------

	@Test
	public void testIsTransient_nonTransientProperty() {
		final var prop = DaoUtils.findProperty(SqlTypeBean.class, "noValueProp");
		assertFalse(DaoUtils.isTransient(SqlTypeBean.class, prop));
	}

	// -----------------------------------------------------------------------
	// getPropertyField / getPropertyAnnotation
	// -----------------------------------------------------------------------

	/** Superclass holding a field the subclass inherits. */
	public static class InheritedFieldBean {
		@Transient
		private String inherited;

		public String getInherited() {
			return inherited;
		}
	}

	/** Annotations on fields, on a getter, and on neither. */
	public static class FieldAnnotatedBean extends InheritedFieldBean {
		@Column(name = "ON_FIELD")
		private String onField;
		@Column(name = "ON_FIELD_TOO")
		private String onBoth;
		private String onNeither;

		public String getOnField() {
			return onField;
		}

		@Column(name = "ON_GETTER")
		public String getOnBoth() {
			return onBoth;
		}

		public String getOnNeither() {
			return onNeither;
		}

		public String getComputed() {
			return "computed";
		}
	}

	@Test
	public void testGetPropertyField_searchesTheClassHierarchy() {
		assertEquals("onField", DaoUtils.getPropertyField(FieldAnnotatedBean.class, "onField").getName());
		assertEquals(InheritedFieldBean.class,
				DaoUtils.getPropertyField(FieldAnnotatedBean.class, "inherited").getDeclaringClass());
		assertEquals(null, DaoUtils.getPropertyField(FieldAnnotatedBean.class, "computed"));
	}

	@Test
	public void testGetPropertyAnnotation_prefersTheGetterThenTheField() {
		assertEquals("ON_FIELD", propertyColumn("onField").name());
		assertEquals("ON_GETTER", propertyColumn("onBoth").name());
		assertEquals(null, propertyColumn("onNeither"));
		assertEquals(null, propertyColumn("computed"));
	}

	@Test
	public void testIsTransient_readsTheAnnotationFromAnInheritedField() {
		assertTrue(DaoUtils.isTransient(FieldAnnotatedBean.class,
				DaoUtils.findProperty(FieldAnnotatedBean.class, "inherited")));
	}

	private static Column propertyColumn(String propertyName) {
		return DaoUtils.getPropertyAnnotation(FieldAnnotatedBean.class,
				DaoUtils.findProperty(FieldAnnotatedBean.class, propertyName), Column.class);
	}

	// -----------------------------------------------------------------------
	// joinKeyword
	// -----------------------------------------------------------------------

	@Test
	public void testJoinKeyword_inner() {
		assertEquals("JOIN", DaoUtils.joinKeyword(edu.iu.dao.SqlJoinType.Type.INNER));
	}

	@Test
	public void testJoinKeyword_left() {
		assertEquals("LEFT OUTER JOIN", DaoUtils.joinKeyword(edu.iu.dao.SqlJoinType.Type.LEFT));
	}

	@Test
	public void testJoinKeyword_right() {
		assertEquals("RIGHT OUTER JOIN", DaoUtils.joinKeyword(edu.iu.dao.SqlJoinType.Type.RIGHT));
	}

	@Test
	public void testJoinKeyword_full() {
		assertEquals("FULL OUTER JOIN", DaoUtils.joinKeyword(edu.iu.dao.SqlJoinType.Type.FULL));
	}

	// -----------------------------------------------------------------------
	// buildWhere
	// -----------------------------------------------------------------------

	@Test
	public void testBuildWhere_empty() {
		assertEquals("", DaoUtils.buildWhere(List.of()));
	}

	@Test
	public void testBuildWhere_singleCriterion() {
		assertEquals("\nWHERE a.ID = ?", DaoUtils.buildWhere(List.of("a.ID = ?")));
	}

	@Test
	public void testBuildWhere_multipleCriteria() {
		assertEquals("\nWHERE a.ID = ?\n  AND a.STATUS = 'A'",
				DaoUtils.buildWhere(List.of("a.ID = ?", "a.STATUS = 'A'")));
	}

	@Test
	public void testBuildWhere_nullElementsSkipped() {
		final List<String> withNulls = new ArrayList<>();
		withNulls.add(null);
		withNulls.add("a.X = 1");
		withNulls.add(null);
		assertEquals("\nWHERE a.X = 1", DaoUtils.buildWhere(withNulls));
	}

	// -----------------------------------------------------------------------
	// appendOrderBy
	// -----------------------------------------------------------------------

	@Test
	public void testAppendOrderBy_empty() {
		final var sb = new StringBuilder("SELECT 1");
		DaoUtils.appendOrderBy(sb, List.of());
		assertEquals("SELECT 1", sb.toString());
	}

	@Test
	public void testAppendOrderBy_singleItem() {
		final var sb = new StringBuilder("SELECT 1");
		DaoUtils.appendOrderBy(sb, List.of("a.NAME"));
		assertEquals("SELECT 1\nORDER BY a.NAME", sb.toString());
	}

	@Test
	public void testAppendOrderBy_multipleItems() {
		final var sb = new StringBuilder("SELECT 1");
		DaoUtils.appendOrderBy(sb, List.of("a.NAME", "a.ID DESC"));
		assertEquals("SELECT 1\nORDER BY a.NAME, a.ID DESC", sb.toString());
	}

	// -----------------------------------------------------------------------
	// idCriteria
	// -----------------------------------------------------------------------

	@jakarta.persistence.Entity
	@jakarta.persistence.Table(name = "id_crit_tbl")
	static class IdCriteriaEntity {
		private long id;

		@jakarta.persistence.Id
		@Column(name = "MY_ID")
		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}
	}

	@Test
	public void testIdCriteria_withAlias() {
		final var meta = EntityMetaData.of(IdCriteriaEntity.class);
		final var criteria = IuIterable.stream(DaoUtils.idCriteria("x", meta.idColumns, "?")).toList();
		assertEquals(1, criteria.size());
		assertEquals("x.MY_ID = ?", criteria.get(0));
	}

	@Test
	public void testIdCriteria_noAlias() {
		final var meta = EntityMetaData.of(IdCriteriaEntity.class);
		final var criteria = IuIterable.stream(DaoUtils.idCriteria(meta.idColumns, "?")).toList();
		assertEquals(1, criteria.size());
		assertEquals("MY_ID = ?", criteria.get(0));
	}

	// -----------------------------------------------------------------------
	// getInCriteria
	// -----------------------------------------------------------------------

	@Test
	public void testGetInCriteria_basic() {
		assertEquals("a.COL IN ('x', 'y')", DaoUtils.getInCriteria("a.COL", List.of("'x'", "'y'")));
	}

	// -----------------------------------------------------------------------
	// appendCorrelation
	// -----------------------------------------------------------------------

	@Test
	public void testAppendCorrelation_empty_returnsFalse() {
		final var sb = new StringBuilder();
		assertFalse(DaoUtils.appendCorrelation(sb, "a", "sub", List.of()));
		assertEquals("", sb.toString());
	}

	@Test
	public void testAppendCorrelation_singleColumn() {
		final var sb = new StringBuilder();
		assertTrue(DaoUtils.appendCorrelation(sb, "a", "sub", List.of("MY_ID")));
		assertEquals("sub.MY_ID = a.MY_ID", sb.toString());
	}

	@Test
	public void testAppendCorrelation_multipleColumns() {
		final var sb = new StringBuilder();
		DaoUtils.appendCorrelation(sb, "a", "sub", List.of("K1", "K2"));
		assertEquals("sub.K1 = a.K1\n         AND sub.K2 = a.K2", sb.toString());
	}

	// -----------------------------------------------------------------------
	// singleQuoted
	// -----------------------------------------------------------------------

	@Test
	public void testSingleQuoted_plainString_wrapsInSingleQuotes() {
		assertEquals("'foo'", DaoUtils.singleQuoted("foo"));
	}

	@Test
	public void testSingleQuoted_embeddedSingleQuote_isDoubled() {
		assertEquals("'it''s'", DaoUtils.singleQuoted("it's"));
	}

	@Test
	public void testSingleQuoted_emptyString_producesEmptyLiteral() {
		assertEquals("''", DaoUtils.singleQuoted(""));
	}

	@Test
	public void testSingleQuoted_multipleEmbeddedQuotes_allDoubled() {
		assertEquals("'a''b''c'", DaoUtils.singleQuoted("a'b'c"));
	}

	@Test
	public void testSingleQuoted_rejectsNullCharacter() {
		final var error = assertThrows(IllegalArgumentException.class, () -> DaoUtils.singleQuoted("a\0b"));
		assertEquals("String literal contains characters unsafe for SQL embedding", error.getMessage());
	}

	@Test
	public void testSingleQuoted_rejectsSubstituteCharacter() {
		final var error = assertThrows(IllegalArgumentException.class, () -> DaoUtils.singleQuoted("a\u001ab"));
		assertEquals("String literal contains characters unsafe for SQL embedding", error.getMessage());
	}

	// -----------------------------------------------------------------------
	// literalFromDate
	// -----------------------------------------------------------------------

	@Test
	public void testLiteralFromDate_producesTimestampKeyword() {
		final var result = DaoUtils.literalFromDate(new java.util.Date());
		assertTrue(result.startsWith("TIMESTAMP '"), "Expected TIMESTAMP prefix, got: " + result);
		assertTrue(result.endsWith("'"), "Expected trailing quote, got: " + result);
	}

	@Test
	public void testLiteralFromDate_matchesTimestampFormat() {
		// Format: TIMESTAMP 'yyyy-MM-dd HH:mm:ss.SSS'
		final var result = DaoUtils.literalFromDate(new java.util.Date());
		assertTrue(result.matches("TIMESTAMP '\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}'"),
				"Unexpected format: " + result);
	}

	@Test
	public void testLiteralFromDate_acceptsSqlTimestamp() {
		final var ts = new Timestamp(0L);
		final var result = DaoUtils.literalFromDate(ts);
		assertTrue(result.matches("TIMESTAMP '\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}'"),
				"Unexpected format: " + result);
	}

}
