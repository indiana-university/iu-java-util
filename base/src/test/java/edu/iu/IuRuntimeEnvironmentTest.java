package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuRuntimeEnvironmentTest {

	@Test
	public void testInvalidPropertyName() {
		assertThrows(IllegalArgumentException.class, () -> IuRuntimeEnvironment.env("@!#$%"));
	}

	@Test
	public void testMissing() {
		final var id = id();
		assertNull(IuRuntimeEnvironment.envOptional(id));
		final var e = assertThrows(NullPointerException.class, () -> IuRuntimeEnvironment.env(id));
		assertEquals("Missing system property " + id + " or environment variable "
				+ id.toUpperCase().replace('.', '_').replace('-', '_'), e.getMessage());
	}

	@Test
	public void testBlank() {
		final var id = id();
		System.setProperty(id, "");
		assertNull(IuRuntimeEnvironment.envOptional(id));
		final var e = assertThrows(NullPointerException.class, () -> IuRuntimeEnvironment.env(id));
		assertEquals("Missing system property " + id + " or environment variable "
				+ id.toUpperCase().replace('.', '_').replace('-', '_'), e.getMessage());
	}

	@Test
	public void testSet() {
		final var id = id();
		final var val = id();
		System.setProperty(id, val);
		assertEquals(val, IuRuntimeEnvironment.envOptional(id));
		assertEquals(val, IuRuntimeEnvironment.env(id));
	}

	private String id() {
		String id;
		do
			id = IdGenerator.generateId();
		while (!Character.isLetter(id.charAt(0)));
		return id;
	}
}
