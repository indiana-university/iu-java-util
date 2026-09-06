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
import java.sql.ResultSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import edu.iu.dao.SqlQuery;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.NonUniqueResultException;

/**
 * A {@link SqlQuery} that reads its cursor once, into a list, and answers every
 * accessor from it.
 *
 * <h2>What draining buys, and what it costs</h2>
 *
 * <p>
 * The delegate is read to exhaustion on the first accessor that returns rows,
 * and the rows are handed to a publisher so that a query can populate the
 * process-wide cache with the individual rows it read. That is the point of
 * this class: one query answers many later loads.
 * </p>
 *
 * <p>
 * The cost is that {@link #getResults(int)} is no longer lazy. It still pages —
 * each call returns the next {@code maxRows} rows and leaves the cursor where it
 * stopped — but the rows behind those pages have already been materialized, so
 * paging bounds what the caller handles at once rather than what the query
 * holds. A query over a result set too large to hold in memory should not be
 * wrapped.
 * </p>
 *
 * <h2>Cursor semantics</h2>
 *
 * <p>
 * The cursor is honored as {@link SqlQuery} describes it. Accessors read
 * forward from the current position; {@link #getSingleResult()},
 * {@link #getFirstRecord()} and {@link #getResults()} are terminal and close;
 * {@link #getResults(int)} leaves the position where it stopped. Closing rewinds
 * to the first row, and because the rows are already held, reopening replays
 * them rather than re-reading the database — which is what makes this a cache
 * and not merely a buffer.
 * </p>
 *
 * <p>
 * {@link #getResultSet()} is the one accessor a list cannot answer, since it
 * hands the caller the driver's own cursor. It discards whatever has been
 * drained and returns the delegate's result set, so a later list accessor reads
 * forward from wherever the caller left that cursor.
 * </p>
 *
 * @param <B> materialized row type
 */
final class CachedSqlQuery<B> implements SqlQuery<B> {

	/** Query this one reads through. */
	private final SqlQuery<B> delegate;

	/**
	 * Opened immediately before the delegate is read and applied immediately
	 * after, so that whatever sequence position the publisher captures precedes
	 * the read it publishes.
	 */
	private final Supplier<Consumer<List<B>>> publisher;

	/** Rows read from the delegate; null until drained. */
	private List<B> rows;

	/** Cursor position within {@link #rows}. */
	private int position;

	/**
	 * Constructor.
	 *
	 * @param delegate  query to read through
	 * @param publisher publishes the rows read; opened before the read and applied
	 *                  after it
	 */
	CachedSqlQuery(SqlQuery<B> delegate, Supplier<Consumer<List<B>>> publisher) {
		this.delegate = delegate;
		this.publisher = publisher;
	}

	/**
	 * Reads the delegate to exhaustion, once, and publishes what it read.
	 */
	private void drain() {
		if (rows != null)
			return;

		// opened first: an invalidation raised while the cursor is open is newer
		// than every row this query is about to carry, and has to outrank them
		final var publish = publisher.get();
		final var drained = List.copyOf(delegate.getResults());
		rows = drained;
		position = 0;
		publish.accept(drained);
	}

	/**
	 * Gets the rows at and after the cursor, draining first.
	 *
	 * @return remaining rows, as a view onto the drained list
	 */
	private List<B> remaining() {
		drain();
		return rows.subList(position, rows.size());
	}

	@Override
	public B getSingleResult() {
		try {
			final var results = remaining();
			if (results.isEmpty())
				throw new EntityNotFoundException("No result for SQL: " + getQuery());
			if (results.size() > 1)
				throw new NonUniqueResultException("Expected one result for SQL: " + getQuery());

			return results.get(0);
		} finally {
			close();
		}
	}

	@Override
	public B getFirstRecord() {
		try {
			final var results = remaining();
			if (results.isEmpty())
				throw new EntityNotFoundException("No result for SQL: " + getQuery());

			return results.get(0);
		} finally {
			close();
		}
	}

	@Override
	public List<B> getResults() {
		try {
			return List.copyOf(remaining());
		} finally {
			close();
		}
	}

	@Override
	public List<B> getResults(int maxRows) {
		if (maxRows < 1)
			throw new IllegalArgumentException("maxRows must be positive");

		final var results = remaining();
		final var page = List.copyOf(results.subList(0, Math.min(maxRows, results.size())));
		position += page.size();
		return page;
	}

	@Override
	public Stream<B> getResultStream() {
		final var results = List.copyOf(remaining());
		position = rows.size();
		return results.stream();
	}

	@Override
	public ResultSet getResultSet() {
		// the caller is taking the driver's cursor, which no list can stand in for;
		// drop what was drained so that a later accessor reads forward from wherever
		// the caller leaves it
		rows = null;
		position = 0;
		return delegate.getResultSet();
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

		// the rows are kept: this query already paid for them, and a reopened query
		// reads from the first row again
		position = 0;
	}
}
