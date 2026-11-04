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

import java.sql.PreparedStatement;

import edu.iu.dao.SqlStatement;

/**
 * A {@link SqlStatement} that invalidates what it changed once it has run.
 *
 * <p>
 * The invalidation is raised whether or not the statement reported success. A
 * statement that failed part way through has still changed rows, and one that
 * never reached the database has only cost an invalidation — the cheaper of the
 * two mistakes to make.
 * </p>
 *
 * <p>
 * {@link #execute()} may be called repeatedly, and each call invalidates again,
 * because each call re-runs the SQL.
 * </p>
 */
final class CachedSqlStatement implements SqlStatement {

	/** Statement this one runs. */
	private final SqlStatement delegate;

	/** Raises the invalidation the statement implies. */
	private final Runnable invalidation;

	/**
	 * Constructor.
	 *
	 * @param delegate     statement to run
	 * @param invalidation raises the invalidation the statement implies
	 */
	CachedSqlStatement(SqlStatement delegate, Runnable invalidation) {
		this.delegate = delegate;
		this.invalidation = invalidation;
	}

	@Override
	public int execute() {
		try {
			return delegate.execute();
		} finally {
			invalidation.run();
		}
	}

	@Override
	public String getQuery() {
		return delegate.getQuery();
	}

	@Override
	public Iterable<?> getArguments() {
		return delegate.getArguments();
	}

	@Override
	public PreparedStatement getPreparedStatement() {
		return delegate.getPreparedStatement();
	}

	@Override
	public void close() {
		delegate.close();
	}
}
