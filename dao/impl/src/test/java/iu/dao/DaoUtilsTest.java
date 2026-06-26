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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;

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
		assertEquals("MyColumn", "MyColumn");
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
		assertEquals(0L, DaoUtils.getFingerprint((Iterable<?>[]) null));
	}

	@Test
	public void testGetFingerprint_emptyVarargs() {
		assertEquals(0L, DaoUtils.getFingerprint());
	}

	@Test
	public void testGetFingerprint_sameInputSameResult() {
		final var a = List.of("x", "y");
		final var b = List.of("x", "y");
		assertEquals(DaoUtils.getFingerprint(a), DaoUtils.getFingerprint(b));
	}

	@Test
	public void testGetFingerprint_differentOrderDifferentResult() {
		final var a = List.of("x", "y");
		final var b = List.of("y", "x");
		assertNotEquals(DaoUtils.getFingerprint(a), DaoUtils.getFingerprint(b));
	}

	@Test
	public void testGetFingerprint_nullElement() {
		final List<String> withNull = new ArrayList<>();
		withNull.add(null);
		final var fp = DaoUtils.getFingerprint(withNull);
		// should not throw and should be deterministic
		assertEquals(fp, DaoUtils.getFingerprint(withNull));
	}

	@Test
	public void testGetFingerprint_nullIterable() {
		// A null iterable inside the varargs should be handled gracefully
		final var fp = DaoUtils.getFingerprint((Iterable<?>) null);
		assertEquals(fp, DaoUtils.getFingerprint((Iterable<?>) null));
	}

	@Test
	public void testGetFingerprint_multipleIterables() {
		final var a = List.of(1, 2);
		final var b = List.of(3, 4);
		final long combined = DaoUtils.getFingerprint(a, b);
		// reversed order of iterables must differ
		assertNotEquals(combined, DaoUtils.getFingerprint(b, a));
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
		final var names = props.stream().map(p -> p.getName()).toList();
		assertTrue(names.contains("name"));
		assertTrue(names.contains("value"));
		assertFalse(names.contains("class"));
	}

	@Test
	public void testGetAllBeanProperties_subclassIncludesParentProps() {
		final var props = DaoUtils.getAllBeanProperties(SubBean.class);
		final var names = props.stream().map(p -> p.getName()).toList();
		assertTrue(names.contains("name"));
		assertTrue(names.contains("value"));
		assertTrue(names.contains("active"));
	}

	@Test
	public void testGetAllBeanProperties_noClassProperty() {
		final var props = DaoUtils.getAllBeanProperties(SimpleBean.class);
		assertTrue(props.stream().noneMatch(p -> "class".equals(p.getName())));
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
		final var prop = DaoUtils.findProperty(SimpleBean.class, "name");
		assertEquals("hello", DaoUtils.getPropertyValue(bean, prop));
	}

	@Test
	public void testGetPropertyValue_throws() throws Exception {
		final var bean = new SimpleBean();
		final var prop = DaoUtils.findProperty(SimpleBean.class, "thisAProblem");
		final var err = assertThrows(IllegalStateException.class, () -> DaoUtils.getPropertyValue(bean, prop));
		assertEquals("Failed to read property thisAProblem from " + SimpleBean.class, err.getMessage());
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
	// getSqlType — via @Column columnDefinition
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

	@Test
	public void testGetSqlType_charColumnDef() {
		assertEquals(String.class, DaoUtils.getSqlType(SqlTypeBean.class, "charProp"));
	}

	@Test
	public void testGetSqlType_intColumnDef() {
		assertEquals(Long.class, DaoUtils.getSqlType(SqlTypeBean.class, "intProp"));
	}

	@Test
	public void testGetSqlType_numberColumnDef() {
		assertEquals(Number.class, DaoUtils.getSqlType(SqlTypeBean.class, "numberProp"));
	}

	@Test
	public void testGetSqlType_numericColumnDef() {
		assertEquals(Number.class, DaoUtils.getSqlType(SqlTypeBean.class, "numericProp"));
	}

	@Test
	public void testGetSqlType_decimalColumnDef() {
		assertEquals(Number.class, DaoUtils.getSqlType(SqlTypeBean.class, "decimalProp"));
	}

	@Test
	public void testGetSqlType_dateColumnDef() {
		assertEquals(Date.class, DaoUtils.getSqlType(SqlTypeBean.class, "dateProp"));
	}

	@Test
	public void testGetSqlType_timeColumnDef() {
		assertEquals(Time.class, DaoUtils.getSqlType(SqlTypeBean.class, "timeProp"));
	}

	@Test
	public void testGetSqlType_timestampColumnDef() {
		assertEquals(Timestamp.class, DaoUtils.getSqlType(SqlTypeBean.class, "timestampProp"));
	}

	@Test
	public void testGetSqlType_datetimeColumnDef() {
		assertEquals(Timestamp.class, DaoUtils.getSqlType(SqlTypeBean.class, "datetimeProp"));
	}

	@Test
	public void testGetSqlType_clobColumnDef() {
		assertArrayEquals(new char[0], (char[]) java.lang.reflect.Array
				.newInstance(DaoUtils.getSqlType(SqlTypeBean.class, "clobProp").getComponentType(), 0));
		assertEquals(char[].class, DaoUtils.getSqlType(SqlTypeBean.class, "clobProp"));
	}

	@Test
	public void testGetSqlType_textColumnDef() {
		assertEquals(char[].class, DaoUtils.getSqlType(SqlTypeBean.class, "textProp"));
	}

	@Test
	public void testGetSqlType_unknownSqlType() {
		assertEquals(Object.class, DaoUtils.getSqlType(SqlTypeBean.class, "unknownSqlProp"));
	}

	@Test
	public void testGetSqlType_noDefinition_autoboxed() {
		assertEquals(String.class, DaoUtils.getSqlType(SqlTypeBean.class, "noValueProp"));
	}

	@Test
	public void testGetSqlType_noAnnotation_autoboxed() {
		assertEquals(Integer.class, DaoUtils.getSqlType(SqlTypeBean.class, "noAnnotationProp"));
	}

	// -----------------------------------------------------------------------
	// toList
	// -----------------------------------------------------------------------

	@Test
	public void testToList_null() {
		assertEquals(List.of(), DaoUtils.toList(null));
	}

	@Test
	public void testToList_list() {
		final var source = Arrays.asList("a", "b", "c");
		assertEquals(source, DaoUtils.toList(source));
	}

	@Test
	public void testToList_mutable() {
		final var result = DaoUtils.toList(List.of("x"));
		result.add("y"); // must not throw
		assertEquals(2, result.size());
	}

	// -----------------------------------------------------------------------
	// appendAll
	// -----------------------------------------------------------------------

	@Test
	public void testAppendAll_nullSource() {
		final var target = new ArrayList<String>();
		DaoUtils.appendAll(target, null); // must not throw
		assertTrue(target.isEmpty());
	}

	@Test
	public void testAppendAll_skipsNulls() {
		final var target = new ArrayList<String>();
		final List<String> source = new ArrayList<>();
		source.add("a");
		source.add(null);
		source.add("b");
		DaoUtils.appendAll(target, source);
		assertEquals(List.of("a", "b"), target);
	}

	@Test
	public void testAppendAll_appendsToExisting() {
		final var target = new ArrayList<>(List.of("x"));
		DaoUtils.appendAll(target, List.of("y", "z"));
		assertEquals(List.of("x", "y", "z"), target);
	}

}
