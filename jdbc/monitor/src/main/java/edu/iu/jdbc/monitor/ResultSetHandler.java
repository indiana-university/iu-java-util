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
package edu.iu.jdbc.monitor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

import edu.iu.IuListener;

/**
 * {@link InvocationHandler} proxy for {@link ResultSet} that counts rows, logs
 * scan progress every 10000 rows, and logs final metrics when the result set is
 * exhausted.
 */
class ResultSetHandler implements InvocationHandler {

	private final ResultSet delegate;
	private final String sql;
	private final Duration executeDuration;
	private final Instant scanStart;
	private final IuJdbcObservableEvent openEvent;
	private int rowCount;

	/**
	 * Constructor.
	 *
	 * @param delegate        the underlying {@link ResultSet} to delegate to
	 * @param sql             the SQL statement that produced this result set
	 * @param executeDuration wall-clock time the statement took to execute
	 * @param uri             JDBC connection URL
	 */
	ResultSetHandler(ResultSet delegate, String sql, Duration executeDuration, URI uri) {
		this.delegate = delegate;
		this.sql = sql;
		this.executeDuration = executeDuration;
		this.scanStart = Instant.now();
		this.openEvent = new IuJdbcObservableEvent(uri, "jdbc.resultset", "open");
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		try {
			if ("next".equals(method.getName())) {
				boolean hasNext = (boolean) method.invoke(delegate, args);
				if (hasNext) {
					rowCount++;
					if (rowCount % 10000 == 0) {
						final var rows = rowCount;
						final var elapsed = Duration.between(scanStart, Instant.now());
						IuJdbcMonitor.LOG.log(Level.FINE,
								() -> "jdbc-monitor: scan; sql=" + sql + "; rows=" + rows + "; elapsed=" + elapsed);
					}
				} else {
					final var rows = rowCount;
					final var scanDuration = Duration.between(scanStart, Instant.now());
					IuJdbcMonitor.LOG.log(Level.FINE, () -> "jdbc-monitor: complete; sql=" + sql //
							+ "; execute=" + executeDuration //
							+ "; rows=" + rows + "; scan=" + scanDuration);
				}
				return hasNext;
			}
			if ("close".equals(method.getName()))
				IuListener.observe(openEvent.end("close"));
			return method.invoke(delegate, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	/**
	 * Wraps a {@link ResultSet} with a monitoring proxy.
	 *
	 * @param rs              the result set to wrap
	 * @param sql             the SQL statement that produced the result set
	 * @param executeDuration wall-clock time the statement took to execute
	 * @param uri             JDBC connection URL
	 * @return a {@link ResultSet} proxy
	 */
	static ResultSet wrap(ResultSet rs, String sql, Duration executeDuration, URI uri) {
		return (ResultSet) java.lang.reflect.Proxy.newProxyInstance( //
				ResultSet.class.getClassLoader(), //
				new Class<?>[] { ResultSet.class }, //
				new ResultSetHandler(rs, sql, executeDuration, uri));
	}

}
