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

import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * A row-returning SQL operation that materializes each row as an instance of
 * {@code B}.
 *
 * <p>
 * The query executes on the first call to any result accessor and reads forward
 * only. Repeated calls therefore continue from the current cursor position rather
 * than restarting, which is what makes {@link #getResults(int)} usable as a
 * paging primitive. {@link #getSingleResult()}, {@link #getFirstRecord()}, and
 * {@link #getResults()} are terminal and close their own resources; the
 * remaining accessors leave the cursor open and require the caller to
 * {@link #close()} the query. Closing a query and calling an accessor again
 * re-executes the SQL from the first row.
 * </p>
 *
 * @param <B> materialized row type
 * @see IuDao#getQuery(Class, String, Iterable)
 * @see IuDao#getFactoryQuery(java.util.function.Function, String, Iterable)
 */
public interface SqlQuery<B> extends ParameterizedSql {

	/**
	 * Reads the one row the query is expected to return, then closes this query.
	 *
	 * @return the single result; never {@code null}
	 * @throws jakarta.persistence.EntityNotFoundException  if the query returned no
	 *                                                      rows
	 * @throws jakarta.persistence.NonUniqueResultException if the query returned
	 *                                                      more than one row
	 * @throws IllegalStateException                        if the query fails or a
	 *                                                      row cannot be
	 *                                                      materialized
	 */
	B getSingleResult();

	/**
	 * Reads the next row and closes this query, discarding any rows that follow.
	 *
	 * @return the first result; never {@code null}
	 * @throws jakarta.persistence.EntityNotFoundException if the query returned no
	 *                                                     rows
	 * @throws IllegalStateException                       if the query fails or a
	 *                                                     row cannot be
	 *                                                     materialized
	 */
	B getFirstRecord();

	/**
	 * Reads every remaining row and closes this query.
	 *
	 * @return all remaining results; empty when the cursor is already exhausted
	 * @throws IllegalStateException if the query fails or a row cannot be
	 *                               materialized
	 */
	List<B> getResults();

	/**
	 * Reads up to {@code maxRows} remaining rows, leaving the cursor open so that a
	 * subsequent call returns the next page.
	 *
	 * <p>
	 * A result smaller than {@code maxRows} — including an empty result — means the
	 * cursor is exhausted. Because this method never closes, callers must close this
	 * query, normally with {@code try}-with-resources.
	 * </p>
	 *
	 * @param maxRows maximum number of rows to read; must be positive
	 * @return up to {@code maxRows} results, in cursor order
	 * @throws IllegalArgumentException if {@code maxRows} is not positive
	 * @throws IllegalStateException    if the query fails or a row cannot be
	 *                                  materialized
	 */
	List<B> getResults(int maxRows);

	/**
	 * Gets a sequential stream over the remaining rows, reading each row from the
	 * cursor on demand.
	 *
	 * <p>
	 * The stream releases this query's JDBC resources when it is closed and when it
	 * reaches the end of the cursor. A stream abandoned before either happens leaves
	 * the connection open, so use {@code try}-with-resources on the stream or on
	 * this query whenever the traversal may end early.
	 * </p>
	 *
	 * @return sequential stream over the remaining results
	 * @throws IllegalStateException if the query fails or a row cannot be
	 *                               materialized, thrown from the terminal operation
	 */
	Stream<B> getResultStream();

	/**
	 * Executes the query if it has not already executed and gets the underlying
	 * JDBC result set, for callers that need to read columns directly.
	 *
	 * <p>
	 * The result set remains owned by this query and is closed by {@link #close()};
	 * callers must not close it directly, and must close this query.
	 * </p>
	 *
	 * @return open, forward-only result set positioned at the current cursor row
	 * @throws IllegalStateException if the query cannot be prepared or fails to
	 *                               execute
	 */
	ResultSet getResultSet();
}
