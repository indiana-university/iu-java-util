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
