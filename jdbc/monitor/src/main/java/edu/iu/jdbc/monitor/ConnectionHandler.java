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
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * {@link InvocationHandler} proxy for {@link Connection} that wraps
 * {@link Statement}, {@link PreparedStatement}, and {@link CallableStatement}
 * instances with monitoring proxies.
 */
class ConnectionHandler implements InvocationHandler {

	private final Connection delegate;

	/**
	 * Constructor.
	 *
	 * @param delegate the underlying {@link Connection} to delegate to
	 */
	ConnectionHandler(Connection delegate) {
		this.delegate = delegate;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		try {
			return switch (method.getName()) {
				case "createStatement" -> wrapStatement((Statement) method.invoke(delegate, args));
				case "prepareStatement" -> wrapProxy( //
						(PreparedStatement) method.invoke(delegate, args), //
						(String) args[0], PreparedStatement.class);
				case "prepareCall" -> wrapProxy( //
						(PreparedStatement) method.invoke(delegate, args), //
						(String) args[0], CallableStatement.class);
				default -> method.invoke(delegate, args);
			};
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	private Statement wrapStatement(Statement statement) {
		return (Statement) Proxy.newProxyInstance( //
				Statement.class.getClassLoader(), //
				new Class<?>[] { Statement.class }, //
				new StatementHandler(statement));
	}

	private Object wrapProxy(PreparedStatement ps, String sql, Class<?> iface) {
		return Proxy.newProxyInstance( //
				iface.getClassLoader(), //
				new Class<?>[] { iface }, //
				new PreparedStatementHandler(ps, sql));
	}

}
