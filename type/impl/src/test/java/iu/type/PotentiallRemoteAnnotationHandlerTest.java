package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.legacy.Incompatible;
import edu.iu.legacy.IncompatibleEnum;
import edu.iu.legacy.NotResource;
import edu.iu.type.base.FilteringClassLoader;
import jakarta.annotation.Resource;
import jakarta.annotation.Resources;

@SuppressWarnings("javadoc")
public class PotentiallRemoteAnnotationHandlerTest {

	private static URLClassLoader legacy;

	@BeforeAll
	public static void setup() throws IOException {
		legacy = new URLClassLoader(TestArchives.getClassPath("testlegacy"),
				new FilteringClassLoader(Set.of(), ClassLoader.getPlatformClassLoader()));
	}

	@AfterAll
	public static void teardown() throws IOException {
		legacy.close();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testConvert() throws Throwable {
		final var legacyResource = BackwardsCompatibility.getCompatibleClass(Resource.class, legacy);
		assertEquals("javax.annotation.Resource", legacyResource.getName());
		final var legacyAnnotation = legacy.loadClass("edu.iu.legacy.LegacyResource")
				.getAnnotation((Class) legacyResource);
		final var h = new PotentiallyRemoteAnnotationHandler(Resource.class, legacyAnnotation);
		final var o = new Object();
		assertSame(o, h.convert(o, Object.class));
		assertThrows(IllegalStateException.class, () -> h.convert(o, Object[].class));
		assertThrows(IllegalStateException.class, () -> h.convert(o, Resource.class));

		TypeUtils.callWithContext(legacyResource, () -> {
			final var r = assertInstanceOf(Resource.class, h.convert(legacyAnnotation, Resource.class));
			assertTrue(r.shareable());
			assertEquals(r, legacyAnnotation);
			assertEquals(Resource.AuthenticationType.CONTAINER, r.authenticationType());
			assertSame(Resource.class, r.annotationType());
			assertThrows(IllegalStateException.class, () -> h.convert(r, NotResource.class));
			return null;
		});
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testMulti() throws Throwable {
		final var legacyResources = BackwardsCompatibility.getCompatibleClass(Resources.class, legacy);
		assertEquals("javax.annotation.Resources", legacyResources.getName());
		final var legacyAnnotation = legacy.loadClass("edu.iu.legacy.LegacyResource")
				.getAnnotation((Class) legacyResources);
		final var h = new PotentiallyRemoteAnnotationHandler(Resources.class, legacyAnnotation);
		final var r = assertInstanceOf(Resources.class,
				TypeUtils.callWithContext(legacyResources, () -> h.convert(legacyAnnotation, Resources.class)));
		final var rs = r.value();
		assertEquals(2, rs.length);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIncompatible() throws Throwable {
		final var incompatible = BackwardsCompatibility.getCompatibleClass(Incompatible.class, legacy);
		final var legacyAnnotation = legacy.loadClass("edu.iu.legacy.LegacyResource")
				.getAnnotation((Class) incompatible);
		final var h = new PotentiallyRemoteAnnotationHandler(Resources.class, legacyAnnotation);

		TypeUtils.callWithContext(incompatible, () -> {
			final var r = (Incompatible) h.convert(legacyAnnotation, Incompatible.class);
			assertThrows(IllegalStateException.class, () -> r.isAnEnum());
			assertThrows(IllegalStateException.class, () -> r.notAnEnum());
			return null;
		});
	}

}
