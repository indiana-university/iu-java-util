package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class RemoteInvocationStackTraceElementDetailTest {

	@Test
	public void testProperties() {
		final var className = IdGenerator.generateId();
		final var methodName = IdGenerator.generateId();
		final var fileName = IdGenerator.generateId();
		final var lineNumber = ThreadLocalRandom.current().nextInt();
		final var detail = new RemoteInvocationStackTraceElementDetail(
				new StackTraceElement(className, methodName, fileName, lineNumber));
		assertEquals(className, detail.getClassName());
		assertEquals(methodName, detail.getMethodName());
		assertEquals(fileName, detail.getFileName());
		assertEquals(lineNumber, detail.getLineNumber());
	}

}
