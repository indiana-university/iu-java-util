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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Level;

/**
 * {@link InvocationHandler} proxy for {@link PreparedStatement} (and
 * {@link java.sql.CallableStatement CallableStatement}) that intercepts execute
 * methods to log SQL text, execution time, and affected row counts, and wraps
 * returned {@link ResultSet} instances with monitoring proxies.
 */
class PreparedStatementHandler implements InvocationHandler {

	private final PreparedStatement delegate;
	private final String sql;

	/**
	 * Constructor.
	 *
	 * @param delegate the underlying {@link PreparedStatement} to delegate to
	 * @param sql      the prepared SQL text
	 */
	PreparedStatementHandler(PreparedStatement delegate, String sql) {
		this.delegate = delegate;
		this.sql = sql;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		try {
			return switch (method.getName()) {
			case "executeQuery" -> handleExecuteQuery(method, args);
			case "executeUpdate", "executeLargeUpdate" -> handleExecuteUpdate(method, args);
			case "execute" -> handleExecute(method, args);
			case "executeBatch" -> handleExecuteBatch(method, args);
			case "executeLargeBatch" -> handleLargeExecuteBatch(method, args);
			default -> method.invoke(delegate, args);
			};
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	private Object handleExecuteQuery(Method method, Object[] args) throws ReflectiveOperationException {
		final var start = Instant.now();
		final var rs = (ResultSet) method.invoke(delegate, args);
		final var executeDuration = Duration.between(start, Instant.now());
		return ResultSetHandler.wrap(rs, sql, executeDuration);
	}

	private Object handleExecuteUpdate(Method method, Object[] args) throws ReflectiveOperationException {
		final var start = Instant.now();
		final var result = method.invoke(delegate, args);
		final var executeDuration = Duration.between(start, Instant.now());
		IuJdbcMonitor.LOG.log(Level.INFO, () -> "jdbc-monitor: execute; sql=" + sql //
				+ "; execute=" + executeDuration + "; affected=" + result);
		return result;
	}

	private Object handleExecute(Method method, Object[] args) throws ReflectiveOperationException {
		final var start = Instant.now();
		final var result = method.invoke(delegate, args);
		final var executeDuration = Duration.between(start, Instant.now());
		IuJdbcMonitor.LOG.log(Level.INFO, () -> "jdbc-monitor: execute; sql=" + sql + "; execute=" + executeDuration);
		return result;
	}

	private Object handleExecuteBatch(Method method, Object[] args) throws ReflectiveOperationException {
		final var start = Instant.now();
		final var result = (int[]) method.invoke(delegate, args);
		final var executeDuration = Duration.between(start, Instant.now());
		final var affected = Arrays.stream(result).filter(c -> c >= 0).sum();
		IuJdbcMonitor.LOG.log(Level.INFO, () -> "jdbc-monitor: batch; sql=" + sql //
				+ "; execute=" + executeDuration + "; affected=" + affected);
		return result;
	}

	private Object handleLargeExecuteBatch(Method method, Object[] args) throws ReflectiveOperationException {
		final var start = Instant.now();
		final var result = (long[]) method.invoke(delegate, args);
		final var executeDuration = Duration.between(start, Instant.now());
		final var affected = Arrays.stream(result).filter(c -> c >= 0).sum();
		IuJdbcMonitor.LOG.log(Level.INFO, () -> "jdbc-monitor: batch; sql=" + sql //
				+ "; execute=" + executeDuration + "; affected=" + affected);
		return result;
	}

}
