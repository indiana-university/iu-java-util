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
 * Immutable snapshot of the database metadata describing one table column.
 *
 * <p>
 * Each accessor corresponds to the like-named column of the
 * {@link java.sql.DatabaseMetaData#getColumns(String, String, String, String)}
 * result row the definition was read from. Instances are value objects: they are
 * detached from the JDBC connection that produced them and remain valid after it
 * closes.
 * </p>
 *
 * @see TableDefinition#getColumns()
 * @see java.sql.DatabaseMetaData#getColumns(String, String, String, String)
 */
public interface ColumnDefinition {

	/**
	 * Gets the physical column name, as reported by the database.
	 *
	 * @return column name; never {@code null}
	 */
	String getColumnName();

	/**
	 * Gets the JDBC type code for the column.
	 *
	 * @return one of the type codes declared by {@link java.sql.Types}
	 */
	int getDataType();

	/**
	 * Gets the vendor-specific type name for the column, for example
	 * {@code VARCHAR} or {@code NUMBER}.
	 *
	 * @return vendor type name
	 */
	String getTypeName();

	/**
	 * Gets the declared size of the column: character length for character types,
	 * or precision for numeric types.
	 *
	 * @return declared column size, or zero when the database reports no size
	 */
	int getColumnSize();

	/**
	 * Gets the number of fractional digits for a numeric column.
	 *
	 * @return decimal scale, or zero for types to which scale does not apply
	 */
	int getDecimalDigits();

	/**
	 * Gets the radix used to interpret {@link #getColumnSize()} and
	 * {@link #getDecimalDigits()} for a numeric column, typically 10 or 2.
	 *
	 * @return numeric radix, or zero for types to which radix does not apply
	 */
	int getNumPrecRadix();
}
