/*
 * Copyright © 2025 Indiana University
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
package edu.iu.jdbc.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.UnsafeBiConsumer;
import edu.iu.UnsafeRunnable;

/**
 * Intercepts, controls caching for, and logs activty on, {@link Statement},
 * {@link PreparedStatement}, and {@link CallableStatement} instances.
 */
public class IuStatementHandler implements InvocationHandler {

	private final Statement statement;
	private final UnsafeBiConsumer<UnsafeRunnable, Throwable> closeHandler;

	// TODO: implement SQL logging
	@SuppressWarnings("unused")
	private final String sql;

	/**
	 * Constructor.
	 * 
	 * @param statement {@link Statement} instance to delegate to.
	 */
	public IuStatementHandler(Statement statement) {
		this.sql = null;
		this.statement = statement;
		this.closeHandler = null;
	}

	/**
	 * Constructor.
	 * 
	 * @param sql          {@link PreparedStatement} SQL template
	 * @param statement    {@link PreparedStatement} instance to delegate to.
	 * @param closeHandler {@link UnsafeBiConsumer} to accept a thunk for delegating
	 *                     {@link Statement#close()} to close the actual statement
	 *                     and potentially a reference to the error that caused the
	 *                     statement to close.
	 */
	public IuStatementHandler(String sql, PreparedStatement statement,
			UnsafeBiConsumer<UnsafeRunnable, Throwable> closeHandler) {
		this.sql = Objects.requireNonNull(sql);
		this.statement = statement;
		this.closeHandler = closeHandler;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (closeHandler != null && method.getName().equals("close")) {
			final var preparedStatement = (PreparedStatement) statement;
			preparedStatement.clearParameters();
			preparedStatement.clearBatch();
			preparedStatement.clearWarnings();
			closeHandler.accept(statement::close, null);
			return null;
		}

		try {
			return IuException.checkedInvocation(() -> method.invoke(statement, args));
		} catch (Throwable e) {
			if (closeHandler != null)
				IuException.suppress(e, () -> closeHandler.accept(statement::close, e));
			throw e;
		}
	}

}
