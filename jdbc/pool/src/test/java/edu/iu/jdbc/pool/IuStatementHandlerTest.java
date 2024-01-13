package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeBiConsumer;
import edu.iu.UnsafeRunnable;

@SuppressWarnings("javadoc")
public class IuStatementHandlerTest {

	@Test
	public void testProxy() throws Throwable {
		final var s = mock(Statement.class);
		when(s.execute("bad query")).thenThrow(SQLException.class);
		final var sp = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
				new Class<?>[] { Statement.class }, new IuStatementHandler(s));
		sp.execute("");
		verify(s).execute("");
		
		// errors don't interfere
		assertThrows(SQLException.class, () -> sp.execute("bad query"));
		
		sp.close();
		verify(s).close();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPreparedProxy() throws Throwable {
		final var ps = mock(PreparedStatement.class);
		final var onClose = mock(UnsafeBiConsumer.class);
		final var sp = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, new IuStatementHandler("", ps, onClose));
		sp.execute();
		verify(ps).execute();
		sp.close();
		verify(ps, never()).close();

		class Box {
			UnsafeRunnable finishClose;
		}
		final var box = new Box();
		verify(onClose).accept(argThat(a -> {
			box.finishClose = (UnsafeRunnable) a;
			return true;
		}), isNull());

		box.finishClose.run();
		verify(ps).close();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPreparedError() throws Throwable {
		final var ps = mock(PreparedStatement.class);
		when(ps.execute()).thenThrow(SQLException.class);
		final var onClose = mock(UnsafeBiConsumer.class);
		final var sp = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, new IuStatementHandler("", ps, onClose));
		assertThrows(SQLException.class, () -> sp.execute());
		class Box {
			UnsafeRunnable finishClose;
		}
		final var box = new Box();
		verify(onClose).accept(argThat(a -> {
			box.finishClose = (UnsafeRunnable) a;
			return true;
		}), isA(SQLException.class));

		box.finishClose.run();
		verify(ps).close();
	}

}
