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

import java.sql.PreparedStatement;

/**
 * A single SQL operation bound to its arguments, which prepares JDBC resources
 * lazily and owns every resource it opens.
 *
 * <p>
 * Constructing an operation performs no database work: the connection and
 * {@link PreparedStatement} are acquired on first use and released by
 * {@link #close()}. The terminal methods on the subinterfaces —
 * {@link SqlStatement#execute()}, {@link SqlQuery#getSingleResult()},
 * {@link SqlQuery#getFirstRecord()}, and {@link SqlQuery#getResults()} — close
 * their own resources, so those calls need no surrounding
 * {@code try}-with-resources. Every other entry point leaves resources open for
 * continued reading and therefore requires the caller to close the operation:
 * </p>
 *
 * <pre>
 * try (var query = dao.getQuery(MyBean.class, sql, args)) {
 * 	final var page = query.getResults(100);
 * 	// ...
 * }
 * </pre>
 *
 * <p>
 * Instances are stateful and are not safe for concurrent use by multiple
 * threads.
 * </p>
 */
public interface ParameterizedSql extends AutoCloseable {

	/**
	 * Gets the SQL text this operation was created with, verbatim.
	 *
	 * @return SQL text; never {@code null}
	 */
	String getQuery();

	/**
	 * Gets the bind arguments in placeholder order.
	 *
	 * @return unmodifiable argument values; empty when the SQL has no placeholders
	 */
	Iterable<?> getArguments();

	/**
	 * Opens a connection if one is not already held, prepares the SQL, and binds
	 * {@link #getArguments()}.
	 *
	 * <p>
	 * Each call discards and closes any statement and connection from a previous
	 * call, so the returned reference must not be retained across invocations. The
	 * returned statement remains owned by this operation and is closed by
	 * {@link #close()}; callers must not close it directly.
	 * </p>
	 *
	 * @return prepared JDBC statement, positioned before execution
	 * @throws IllegalStateException if the connection cannot be obtained or the SQL
	 *                               cannot be prepared
	 */
	PreparedStatement getPreparedStatement();

	/**
	 * Releases the JDBC resources held by this operation.
	 *
	 * <p>
	 * Closing is idempotent and never throws: failures encountered while releasing
	 * resources are discarded so that they cannot mask an application failure
	 * already in flight. After closing, the operation may be used again, which
	 * re-prepares the SQL against a new connection.
	 * </p>
	 */
	@Override
	void close();
}
