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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;

@SuppressWarnings("javadoc")
public class TableMetaDataTest {

	// =======================================================================
	// Test entity classes (for initializeJoinConditions tests)
	// =======================================================================

	/** Secondary table with no pkJoinColumns — implicit join via @Id columns. */
	@Entity
	@Table(name = "primary_tbl", schema = "s")
	@SecondaryTable(name = "sec_tbl")
	public static class ImplicitJoinEntity {
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

	/** Secondary table with no pkJoinColumns and two @Id columns. */
	@Entity
	@Table(name = "multi_pk_tbl")
	@SecondaryTable(name = "multi_sec")
	public static class MultiPkImplicitJoinEntity {
		private long key1;
		private long key2;

		@Id
		@Column(name = "KEY1")
		public long getKey1() {
			return key1;
		}

		public void setKey1(long key1) {
			this.key1 = key1;
		}

		@Id
		@Column(name = "KEY2")
		public long getKey2() {
			return key2;
		}

		public void setKey2(long key2) {
			this.key2 = key2;
		}
	}

	/** Secondary table with an explicit pkJoinColumn. */
	@Entity
	@Table(name = "parent_tbl", schema = "s")
	@SecondaryTable(name = "child_tbl", pkJoinColumns = @PrimaryKeyJoinColumn(name = "PARENT_ID"))
	public static class ExplicitJoinEntity {
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

	/** Secondary table with two explicit pkJoinColumns. */
	@Entity
	@Table(name = "multi_fk_tbl")
	@SecondaryTable(name = "multi_fk_sec", pkJoinColumns = { @PrimaryKeyJoinColumn(name = "FK1"),
			@PrimaryKeyJoinColumn(name = "FK2") })
	public static class MultiExplicitJoinEntity {
		private long k1;
		private long k2;

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
	}

	// =======================================================================
	// Constructor — field values
	// =======================================================================

	@Test
	public void testConstructor_nameSet() {
		final var t = new TableMetaData("my_table", "s", "a", true, null);
		assertEquals("my_table", t.name);
	}

	@Test
	public void testConstructor_fullNameSchemaQualified() {
		final var t = new TableMetaData("my_table", "my_schema", "a", true, null);
		assertEquals("my_schema.my_table", t.fullName);
	}

	@Test
	public void testConstructor_fullNameUnqualifiedWhenSchemaBlank() {
		final var t = new TableMetaData("my_table", "", "a", true, null);
		assertEquals("my_table", t.fullName);
	}

	@Test
	public void testConstructor_fullNameUnqualifiedWhenSchemaNul() {
		final var t = new TableMetaData("my_table", null, "a", true, null);
		assertEquals("my_table", t.fullName);
	}

	@Test
	public void testConstructor_aliasSet() {
		final var t = new TableMetaData("t", "", "z", true, null);
		assertEquals("z", t.alias);
	}

	@Test
	public void testConstructor_primaryFlagSet() {
		assertTrue(new TableMetaData("t", "", "a", true, null).primary);
		assertFalse(new TableMetaData("t", "", "b", false, null).primary);
	}

	@Test
	public void testConstructor_secondaryTableAnnotationSet() {
		final var annotation = ImplicitJoinEntity.class.getAnnotation(SecondaryTable.class);
		final var t = new TableMetaData("sec_tbl", "", "b", false, annotation);
		assertSame(annotation, t.secondaryTable);
	}

	@Test
	public void testConstructor_secondaryTableNullForPrimary() {
		assertNull(new TableMetaData("t", "", "a", true, null).secondaryTable);
	}

	@Test
	public void testConstructor_joinConditionsInitiallyEmpty() {
		final var t = new TableMetaData("t", "", "a", true, null);
		assertFalse(t.joinConditions.iterator().hasNext());
	}

	// =======================================================================
	// initializeJoinConditions — primary table (no-op)
	// =======================================================================

	@Test
	public void testInitializeJoinConditions_primaryTableRemainsEmpty() {
		final var entity = EntityMetaData.of(ImplicitJoinEntity.class);
		final var t = new TableMetaData("t", "", "a", true, null);
		t.initializeJoinConditions(entity);
		assertFalse(t.joinConditions.iterator().hasNext());
	}

	// =======================================================================
	// initializeJoinConditions — implicit join (no pkJoinColumns)
	// =======================================================================

	@Test
	public void testInitializeJoinConditions_implicitJoin_singleIdColumn_producesOneCondition() {
		final var entity = EntityMetaData.of(ImplicitJoinEntity.class);
		final var secTable = entity.secondaryTables.iterator().next();
		assertEquals(1, IuIterable.stream(secTable.joinConditions).count());
	}

	@Test
	public void testInitializeJoinConditions_implicitJoin_singleIdColumn_conditionSQL() {
		final var entity = EntityMetaData.of(ImplicitJoinEntity.class);
		final var secTable = entity.secondaryTables.iterator().next();
		final var sb = new StringBuilder();
		secTable.joinConditions.iterator().next().appendTo(sb);
		assertEquals("a.ID = b.ID", sb.toString());
	}

	@Test
	public void testInitializeJoinConditions_implicitJoin_twoIdColumns_producesTwoConditions() {
		final var entity = EntityMetaData.of(MultiPkImplicitJoinEntity.class);
		final var secTable = entity.secondaryTables.iterator().next();
		assertEquals(2, IuIterable.stream(secTable.joinConditions).count());
	}

	@Test
	public void testInitializeJoinConditions_implicitJoin_twoIdColumns_conditionSQL() {
		final var entity = EntityMetaData.of(MultiPkImplicitJoinEntity.class);
		final var secTable = entity.secondaryTables.iterator().next();
		final var sb = new StringBuilder();
		final var jcIter = secTable.joinConditions.iterator();
		jcIter.next().appendTo(sb);
		sb.append(" AND ");
		jcIter.next().appendTo(sb);
		final var sql = sb.toString();
		assertTrue(sql.contains("a.KEY1 = b.KEY1"));
		assertTrue(sql.contains("a.KEY2 = b.KEY2"));
	}

	// =======================================================================
	// initializeJoinConditions — explicit pkJoinColumns
	// =======================================================================

	@Test
	public void testInitializeJoinConditions_explicitJoin_singlePkJoinColumn_producesOneCondition() {
		final var entity = EntityMetaData.of(ExplicitJoinEntity.class);
		final var secTable = entity.secondaryTables.iterator().next();
		assertEquals(1, IuIterable.stream(secTable.joinConditions).count());
	}

	@Test
	public void testInitializeJoinConditions_explicitJoin_singlePkJoinColumn_conditionSQL() {
		// pkJoinColumn name="PARENT_ID", no referencedColumnName → single @Id column
		// "ID"
		final var entity = EntityMetaData.of(ExplicitJoinEntity.class);
		final var secTable = entity.secondaryTables.iterator().next();
		final var sb = new StringBuilder();
		secTable.joinConditions.iterator().next().appendTo(sb);
		assertEquals("a.ID = b.PARENT_ID", sb.toString());
	}

	@Test
	public void testInitializeJoinConditions_explicitJoin_twoPkJoinColumns_producesTwoConditions() {
		final var entity = EntityMetaData.of(MultiExplicitJoinEntity.class);
		final var secTable = entity.secondaryTables.iterator().next();
		assertEquals(2, IuIterable.stream(secTable.joinConditions).count());
	}

	@Test
	public void testInitializeJoinConditions_explicitJoin_twoPkJoinColumns_conditionSQL() {
		// Multiple id columns + multiple pkJoinColumns → fallback to
		// pkJoinColumn.name()
		final var entity = EntityMetaData.of(MultiExplicitJoinEntity.class);
		final var secTable = entity.secondaryTables.iterator().next();
		final var jcIter = secTable.joinConditions.iterator();
		final var sb0 = new StringBuilder();
		jcIter.next().appendTo(sb0);
		final var sb1 = new StringBuilder();
		jcIter.next().appendTo(sb1);
		assertEquals("a.FK1 = b.FK1", sb0.toString());
		assertEquals("a.FK2 = b.FK2", sb1.toString());
	}

}
