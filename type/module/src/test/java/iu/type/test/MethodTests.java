package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class MethodTests {

	private static int add(int x, int y) {
		return x + y;
	}

	private String echo(String message) {
		return message;
	}

	@Test
	public void testDirectInvocation() {
		assertEquals("foobar", echo("foobar"));
		assertEquals(7, add(3, 4));
	}

	@Test
	public void testInstanceInvocation() throws Exception {
		var method = IuType.of(getClass()).method("echo", String.class);
		assertFalse(method.isStatic());
		assertEquals("echo", method.name());
		assertSame(String.class, method.returnType().erasedClass());
		assertEquals("foobar", method.exec(this, "foobar"));
	}

	@Test
	public void testStaticInvocation() throws Exception {
		var method = IuType.of(getClass()).method("add", int.class, int.class);
		assertTrue(method.isStatic());
		assertEquals("add", method.name());
		assertSame(int.class, method.returnType().erasedClass());
		assertEquals(7, method.exec(3, 4));
	}
}
