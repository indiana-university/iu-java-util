package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.legacy.Incompatible;
import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;

@ExtendWith(LegacyContextSupport.class)
@SuppressWarnings("javadoc")
public class BackwardsCompatibilityTest {

	@Test
	public void testSameLoader() {
		assertSame(getClass(), BackwardsCompatibility.getCompatibleClass(getClass()));
	}

	@Test
	public void testSameName() {
		assertNotSame(Incompatible.class,
				BackwardsCompatibility.getCompatibleClass(Incompatible.class, LegacyContextSupport.loader));
	}

	@Test
	public void testNotFound() {
		final var error = assertThrows(NoClassDefFoundError.class,
				() -> BackwardsCompatibility.getCompatibleClass(getClass(), LegacyContextSupport.loader));
		assertEquals(getClass().getName(),
				assertInstanceOf(ClassNotFoundException.class, error.getCause()).getMessage());
	}

	@Test
	public void testCompatibleNames() {
		final var legacyResource = BackwardsCompatibility.getCompatibleClass(Resource.class,
				LegacyContextSupport.loader);
		assertNotSame(Resource.class, legacyResource);
		assertEquals("javax.annotation.Resource", legacyResource.getName());
		assertSame(Resource.class, BackwardsCompatibility.getCompatibleClass(legacyResource));
	}

	@Test
	public void testCompatibleNotFound() {
		final var error = assertThrows(NoClassDefFoundError.class,
				() -> BackwardsCompatibility.getCompatibleClass(Jsonb.class, LegacyContextSupport.loader));
		assertEquals("javax.json.bind.Jsonb",
				assertInstanceOf(ClassNotFoundException.class, error.getCause()).getMessage());
		assertEquals(Jsonb.class.getName(),
				assertInstanceOf(ClassNotFoundException.class, error.getSuppressed()[0]).getMessage());
	}

}
