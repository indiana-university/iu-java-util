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
