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

/**
 * Immutable snapshot of the database metadata describing one table and its
 * columns.
 *
 * <p>
 * The catalog, schema, name, and type accessors correspond to the like-named
 * columns of the
 * {@link java.sql.DatabaseMetaData#getTables(String, String, String, String[])}
 * result row the definition was read from. Instances are value objects: they are
 * detached from the JDBC connection that produced them and remain valid after it
 * closes.
 * </p>
 *
 * @see IuDao#getTableDefinition(String)
 * @see java.sql.DatabaseMetaData#getTables(String, String, String, String[])
 */
public interface TableDefinition {

	/**
	 * Gets the catalog that contains the table.
	 *
	 * @return catalog name, or {@code null} when the database does not use catalogs
	 */
	String getTableCat();

	/**
	 * Gets the schema that contains the table.
	 *
	 * @return schema name, or {@code null} when the database does not use schemas
	 */
	String getTableSchem();

	/**
	 * Gets the table name as reported by the database, which may differ in case
	 * from the name passed to {@link IuDao#getTableDefinition(String)}.
	 *
	 * @return table name; never {@code null}
	 */
	String getTableName();

	/**
	 * Gets the table type reported by the database, for example {@code TABLE},
	 * {@code VIEW}, or {@code SYSTEM TABLE}.
	 *
	 * @return table type
	 */
	String getTableType();

	/**
	 * Gets the table's columns in the order the database reported them.
	 *
	 * @return unmodifiable column definitions; empty when the table has no columns
	 *         visible to the connected user
	 */
	Iterable<? extends ColumnDefinition> getColumns();
}
