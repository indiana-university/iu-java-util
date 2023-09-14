package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class TypeFactoryTest {

	@Test
	public void testResolves() {
		var type = IuType.of(Object.class);
		assertNotNull(type);
		assertSame(Object.class, type.deref());
		assertEquals(Object.class.getName(), type.name());
	}
	
	@Test
	public void testParityWithClass() {
		assertSame(IuType.of(Object.class), IuType.of(Object.class));
	}
	
}
