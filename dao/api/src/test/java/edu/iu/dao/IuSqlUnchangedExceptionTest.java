package edu.iu.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class IuSqlUnchangedExceptionTest {

	@Test
	public void testConstructors() {
		assertNull(new IuSqlUnchangedException().getMessage());
		final var msg = IdGenerator.generateId();
		assertEquals(msg, new IuSqlUnchangedException(msg).getMessage());
	}

}
