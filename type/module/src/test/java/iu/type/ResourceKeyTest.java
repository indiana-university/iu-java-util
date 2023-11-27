package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ResourceKeyTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testSameSame() {
		final var type = TypeFactory.resolveRawClass(Object.class);
		final var k1 = new ResourceKey("", type);
		assertEquals("", k1.name());
		assertSame(type, k1.type());
		assertEquals(k1.hashCode(), k1.hashCode());
		assertEquals(k1, k1);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testTypeCheck() {
		final var type = TypeFactory.resolveRawClass(Object.class);
		final var k1 = new ResourceKey("", type);
		assertNotEquals(k1, this);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testDifferentNames() {
		final var type = TypeFactory.resolveRawClass(Object.class);
		final var k1 = new ResourceKey("a", type);
		final var k2 = new ResourceKey("b", type);
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testDifferentTypes() {
		final var type = TypeFactory.resolveRawClass(Object.class);
		final var type2 = TypeFactory.resolveRawClass(Test.class);
		final var k1 = new ResourceKey("", type);
		final var k2 = new ResourceKey("", type2);
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);
	}

}
