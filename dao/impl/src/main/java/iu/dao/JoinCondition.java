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

import jakarta.persistence.PrimaryKeyJoinColumn;

/**
 * Represents a single SQL equality condition used to join a secondary table to
 * the primary table in a JPA entity mapping.
 *
 * <p>
 * Instances are created during {@link EntityMetaData} initialization and
 * consumed by the SQL builder when constructing {@code JOIN ... ON} clauses.
 * The condition always takes the form:
 * </p>
 *
 * <pre>
 * primaryAlias.primaryColumn = secondaryAlias.secondaryColumn
 * </pre>
 */
class JoinCondition {
	private final String primaryAlias;
	private final String primaryColumn;
	private final String secondaryAlias;
	private final String secondaryColumn;

	/**
	 * Creates a join condition where the primary and secondary tables share the
	 * same column name.
	 *
	 * <p>
	 * Used when a {@link jakarta.persistence.SecondaryTable} declares no explicit
	 * {@link PrimaryKeyJoinColumn} entries; each {@code @Id}-mapped column of the
	 * entity becomes an implicit join column.
	 * </p>
	 *
	 * @param secondaryAlias alias assigned to the secondary table
	 * @param column         {@code @Id}-mapped column from the primary table whose
	 *                       name is shared with the secondary table
	 */
	JoinCondition(String secondaryAlias, ColumnMetaData column) {
		this.primaryAlias = column.table.alias;
		this.primaryColumn = column.columnName;
		this.secondaryAlias = secondaryAlias;
		this.secondaryColumn = column.columnName;
	}

	/**
	 * Creates a join condition from an explicit {@link PrimaryKeyJoinColumn}
	 * annotation.
	 *
	 * <p>
	 * The primary-side column is resolved in the following order:
	 * </p>
	 * <ol>
	 * <li>If {@link PrimaryKeyJoinColumn#referencedColumnName()} is non-blank, it
	 * is used as the primary column lookup key.</li>
	 * <li>Otherwise, if the entity has exactly one {@code @Id} column, that
	 * column's name is used.</li>
	 * <li>Otherwise, {@link PrimaryKeyJoinColumn#name()} is used as the fallback
	 * lookup key.</li>
	 * </ol>
	 *
	 * <p>
	 * If the resolved key matches a known column in the entity, that column's alias
	 * and name are used; otherwise the entity's primary-table alias and the raw
	 * lookup key are used directly.
	 * </p>
	 *
	 * @param secondaryAlias alias assigned to the secondary table
	 * @param pkJoinColumn   annotation that describes the join column mapping
	 * @param entity         metadata for the owning entity, used to resolve column
	 *                       aliases and names
	 */
	JoinCondition(String secondaryAlias, PrimaryKeyJoinColumn pkJoinColumn, EntityMetaData entity) {
		final String primaryLookup;
		if (DaoUtils.hasValue(pkJoinColumn.referencedColumnName()))
			primaryLookup = pkJoinColumn.referencedColumnName();
		else {
			final var i = entity.idColumns.iterator();
			String singlePkColumnName;
			if (i.hasNext() //
					&& DaoUtils.hasValue(singlePkColumnName = i.next().columnName) //
					&& !i.hasNext())
				primaryLookup = singlePkColumnName;
			else
				primaryLookup = pkJoinColumn.name();
		}

		final var column = entity.columnsByNormalizedColumn.get(DaoUtils.normalizeName(primaryLookup));
		if (column == null) {
			this.primaryAlias = entity.primaryTable.alias;
			this.primaryColumn = primaryLookup;
		} else {
			this.primaryAlias = column.table.alias;
			this.primaryColumn = column.columnName;
		}
		this.secondaryAlias = secondaryAlias;
		this.secondaryColumn = pkJoinColumn.name().isEmpty() //
				? primaryLookup
				: pkJoinColumn.name();
	}

	/**
	 * Appends the SQL equality condition to the given {@link StringBuilder}.
	 *
	 * <p>
	 * Produces output of the form:
	 * {@code primaryAlias.primaryColumn = secondaryAlias.secondaryColumn}.
	 * </p>
	 *
	 * @param sb target {@link StringBuilder} to append to
	 */
	void appendTo(StringBuilder sb) {
		sb.append(primaryAlias).append('.').append(primaryColumn).append(" = ").append(secondaryAlias).append('.')
				.append(secondaryColumn);
	}

}
