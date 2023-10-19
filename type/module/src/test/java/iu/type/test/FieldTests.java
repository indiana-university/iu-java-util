package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;

@SuppressWarnings({ "javadoc", "unused" })
public class FieldTests {

	private static long timestamp = System.currentTimeMillis();
	private final String message = "foo";
	private transient int value;

	@Test
	public void testGet() {
		final var messageField = IuType.of(getClass()).field("message");
		assertTrue(messageField.serializable());
		assertEquals("foo", messageField.get(this));
		messageField.set(this, "bar");
		assertEquals("message", messageField.name());
		assertEquals("FieldTests#message:String", messageField.toString());
		assertSame(getClass(), messageField.declaringType().erasedClass());
	}

	@Test
	public void testGetSet() {
		final var rand = ThreadLocalRandom.current().nextInt();
		final var valueField = IuType.of(getClass()).field("value");
		assertFalse(valueField.serializable());
	}

	@Test
	public void testStatic() {
		final var timestampField = IuType.of(getClass()).field("timestamp");
		assertFalse(timestampField.serializable());
		assertEquals(timestamp, timestampField.get(null));
	}

}
