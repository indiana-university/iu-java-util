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
package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class RemoteInvocationExceptionTest {

	@Test
	public void testFrom() {
		final var failure = mock(RemoteInvocationFailure.class);
		when(failure.getExceptionType()).thenReturn(IllegalStateException.class.getName());
		when(failure.getSuppressed()).thenReturn(null);

		final var message = IdGenerator.generateId();
		when(failure.getMessage()).thenReturn(message);

		final var className = IdGenerator.generateId();
		final var methodName = IdGenerator.generateId();
		final var fileName = IdGenerator.generateId();
		final var lineNumber = ThreadLocalRandom.current().nextInt();
		final var detail = new RemoteInvocationStackTraceElementDetail(
				new StackTraceElement(className, methodName, fileName, lineNumber));

		when(failure.getStackTrace()).thenReturn(List.of(detail));

		final var remoteName = IdGenerator.generateId();
		final var remoteMethod = IdGenerator.generateId();
		when(failure.getRemoteName()).thenReturn(remoteName);
		when(failure.getRemoteMethod()).thenReturn(remoteMethod);

		final var reconstructed = new RemoteInvocationException(failure);
		assertEquals(message, reconstructed.getMessage());
		
		final var ste0 = reconstructed.getStackTrace()[0];
		assertEquals(IllegalStateException.class.getName(), ste0.getClassName());
		assertEquals("<init>", ste0.getMethodName());
		assertEquals(null, ste0.getFileName());
		assertEquals(-1, ste0.getLineNumber());

		final var ste1 = reconstructed.getStackTrace()[1];
		assertEquals(className, ste1.getClassName());
		assertEquals(methodName, ste1.getMethodName());
		assertEquals(fileName, ste1.getFileName());
		assertEquals(lineNumber, ste1.getLineNumber());

		final var ste2 = reconstructed.getStackTrace()[2];
		assertEquals(remoteName, ste2.getClassName());
		assertEquals(remoteMethod, ste2.getMethodName());
		assertEquals(null, ste2.getFileName());
		assertEquals(-1, ste2.getLineNumber());
	}

	@Test
	public void testFromCauseAndSuppress() {
		final var failure = mock(RemoteInvocationFailure.class);
		when(failure.getExceptionType()).thenReturn(IllegalStateException.class.getName());
		when(failure.getSuppressed()).thenReturn(null);

		final var message = IdGenerator.generateId();
		when(failure.getMessage()).thenReturn(message);

		final var cause = mock(RemoteInvocationFailure.class);
		when(cause.getExceptionType()).thenReturn(Exception.class.getName());
		when(cause.getSuppressed()).thenReturn(null);

		final var causeMessage = IdGenerator.generateId();
		when(cause.getMessage()).thenReturn(causeMessage);

		when(failure.getCause()).thenReturn(cause);

		final var suppressed = mock(RemoteInvocationFailure.class);
		when(suppressed.getExceptionType()).thenReturn(Exception.class.getName());
		when(suppressed.getSuppressed()).thenReturn(null);

		final var suppressedMessage = IdGenerator.generateId();
		when(suppressed.getMessage()).thenReturn(suppressedMessage);

		when(failure.getSuppressed()).thenReturn(List.of(suppressed));

		final var reconstructed = new RemoteInvocationException(failure);
		assertEquals(message, reconstructed.getMessage());
	}

	@Test
	public void testFromWithTrace() {
		final var failure = mock(RemoteInvocationFailure.class);
		when(failure.getExceptionType()).thenReturn(IllegalStateException.class.getName());

		final var message = IdGenerator.generateId();
		when(failure.getMessage()).thenReturn(message);

		final var remoteName = IdGenerator.generateId();
		final var remoteMethod = IdGenerator.generateId();
		when(failure.getRemoteName()).thenReturn(remoteName);
		when(failure.getRemoteMethod()).thenReturn(remoteMethod);

		final var reconstructed = new RemoteInvocationException(failure);
		assertEquals(message, reconstructed.getMessage());

		final var ste0 = reconstructed.getStackTrace()[0];
		assertEquals(IllegalStateException.class.getName(), ste0.getClassName());
		assertEquals("<init>", ste0.getMethodName());
		assertEquals(null, ste0.getFileName());
		assertEquals(-1, ste0.getLineNumber());

		final var ste1 = reconstructed.getStackTrace()[1];
		assertEquals(remoteName, ste1.getClassName());
		assertEquals(remoteMethod, ste1.getMethodName());
		assertEquals(null, ste1.getFileName());
		assertEquals(-1, ste1.getLineNumber());
	}

}
