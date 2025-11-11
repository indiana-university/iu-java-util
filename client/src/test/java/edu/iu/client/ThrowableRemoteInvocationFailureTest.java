package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class ThrowableRemoteInvocationFailureTest {

	@Test
	public void testProperties() {
		final var message = IdGenerator.generateId();
		final var remoteName = IdGenerator.generateId();
		final var remoteMethod = IdGenerator.generateId();
		final var suppressed = new Exception();
		final var cause = new Exception();
		final var ex = new Exception(message, cause);
		ex.addSuppressed(suppressed);
		final var failure = new ThrowableRemoteInvocationFailure(remoteName, remoteMethod, ex);
		assertEquals(Exception.class.getName(), failure.getExceptionType());
		assertEquals(remoteName, failure.getRemoteName());
		assertEquals(remoteMethod, failure.getRemoteMethod());
		assertEquals(message, failure.getMessage());
		assertNotNull(failure.getStackTrace());
		assertInstanceOf(ThrowableRemoteInvocationFailure.class, failure.getCause());
		assertInstanceOf(ThrowableRemoteInvocationFailure.class, failure.getSuppressed().iterator().next());
	}

}
