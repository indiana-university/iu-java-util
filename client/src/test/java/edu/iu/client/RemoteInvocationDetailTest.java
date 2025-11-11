package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class RemoteInvocationDetailTest {

	@Test
	public void testFrom() {
		final var detail = mock(RemoteInvocationDetail.class);
		final var className = IdGenerator.generateId();
		final var methodName = IdGenerator.generateId();
		final var fileName = IdGenerator.generateId();
		final var lineNumber = ThreadLocalRandom.current().nextInt();
		when(detail.getClassName()).thenReturn(className);
		when(detail.getMethodName()).thenReturn(methodName);
		when(detail.getFileName()).thenReturn(fileName);
		when(detail.getLineNumber()).thenReturn(lineNumber);
		final var element = RemoteInvocationDetail.from(detail);
		assertEquals(className, element.getClassName());
		assertEquals(methodName, element.getMethodName());
		assertEquals(fileName, element.getFileName());
		assertEquals(lineNumber, element.getLineNumber());
	}

}
