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

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@SuppressWarnings("javadoc")
public class JoinConditionTest {

	// -----------------------------------------------------------------------
	// Test entity: single primary-key column
	// -----------------------------------------------------------------------

	@Entity
	@Table(name = "single_pk")
	public static class SinglePkEntity {
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

	// -----------------------------------------------------------------------
	// Test entity: named primary-key column (used for referencedColumnName tests)
	// -----------------------------------------------------------------------

	@Entity
	@Table(name = "parent")
	public static class ParentEntity {
		private long parentId;
		private String name;

		@Id
		@Column(name = "PARENT_ID")
		public long getParentId() {
			return parentId;
		}

		public void setParentId(long parentId) {
			this.parentId = parentId;
		}

		@Column(name = "NAME")
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	// -----------------------------------------------------------------------
	// Test entity: composite primary key (two @Id columns)
	// -----------------------------------------------------------------------

	@Entity
	@Table(name = "multi_pk")
	public static class MultiPkEntity {
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

	// -----------------------------------------------------------------------
	// Annotation holder classes for PrimaryKeyJoinColumn instances
	// -----------------------------------------------------------------------

	/** name only — triggers single-ID-column fallback path */
	@PrimaryKeyJoinColumn(name = "FK_ID")
	private static class PkJoinNameOnly {
	}

	/** explicit referencedColumnName that exists in entity */
	@PrimaryKeyJoinColumn(name = "FK_ID", referencedColumnName = "PARENT_ID")
	private static class PkJoinWithRef {
	}

	/** name only on a multi-PK entity — triggers pkJoinColumn.name() fallback */
	@PrimaryKeyJoinColumn(name = "FALLBACK_ID")
	private static class PkJoinFallback {
	}

	/** referencedColumnName that is NOT present in entity columns */
	@PrimaryKeyJoinColumn(name = "UNKNOWN_REF", referencedColumnName = "NOT_IN_ENTITY")
	private static class PkJoinUnknownRef {
	}

	// -----------------------------------------------------------------------
	// Constructor 1: JoinCondition(String secondaryAlias, ColumnMetaData column)
	// -----------------------------------------------------------------------

	@Test
	public void testFromColumnMetaData_appendTo() {
		final var entity = EntityMetaData.of(SinglePkEntity.class);
		final var idColumn = entity.idColumns.iterator().next(); // columnName = "ID", table.alias = "a"
		final var join = new JoinCondition("b", idColumn);
		final var sb = new StringBuilder();
		join.appendTo(sb);
		assertEquals("a.ID = b.ID", sb.toString());
	}

	// -----------------------------------------------------------------------
	// Constructor 2: JoinCondition(String, PrimaryKeyJoinColumn, EntityMetaData)
	// -----------------------------------------------------------------------

	@Test
	public void testFromPkJoinColumn_withReferencedColumnName_columnFound() {
		// referencedColumnName is set and matches a column in entity -> uses that column
		final var entity = EntityMetaData.of(ParentEntity.class);
		final var pkJoin = PkJoinWithRef.class.getAnnotation(PrimaryKeyJoinColumn.class);
		final var join = new JoinCondition("b", pkJoin, entity);
		final var sb = new StringBuilder();
		join.appendTo(sb);
		assertEquals("a.PARENT_ID = b.FK_ID", sb.toString());
	}

	@Test
	public void testFromPkJoinColumn_noReferencedColumnName_singleIdColumn() {
		// No referencedColumnName + single @Id column -> uses entity.idColumns.get(0)
		final var entity = EntityMetaData.of(ParentEntity.class);
		final var pkJoin = PkJoinNameOnly.class.getAnnotation(PrimaryKeyJoinColumn.class);
		final var join = new JoinCondition("b", pkJoin, entity);
		final var sb = new StringBuilder();
		join.appendTo(sb);
		assertEquals("a.PARENT_ID = b.FK_ID", sb.toString());
	}

	@Test
	public void testFromPkJoinColumn_noReferencedColumnName_multipleIdColumns() {
		// No referencedColumnName + multiple @Id columns -> fallback to pkJoinColumn.name()
		// The fallback name is not a known column, so primary side uses entity primary-table alias
		final var entity = EntityMetaData.of(MultiPkEntity.class);
		final var pkJoin = PkJoinFallback.class.getAnnotation(PrimaryKeyJoinColumn.class);
		final var join = new JoinCondition("b", pkJoin, entity);
		final var sb = new StringBuilder();
		join.appendTo(sb);
		assertEquals("a.FALLBACK_ID = b.FALLBACK_ID", sb.toString());
	}

	@Test
	public void testFromPkJoinColumn_referencedColumnNameNotInEntity() {
		// referencedColumnName is set but does not match any entity column ->
		// primary side falls back to entity primary-table alias + raw lookup name
		final var entity = EntityMetaData.of(SinglePkEntity.class);
		final var pkJoin = PkJoinUnknownRef.class.getAnnotation(PrimaryKeyJoinColumn.class);
		final var join = new JoinCondition("b", pkJoin, entity);
		final var sb = new StringBuilder();
		join.appendTo(sb);
		assertEquals("a.NOT_IN_ENTITY = b.UNKNOWN_REF", sb.toString());
	}

}
