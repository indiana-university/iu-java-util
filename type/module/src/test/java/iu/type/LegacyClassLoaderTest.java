package iu.type;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;
import edu.iu.type.IuComponent;

@SuppressWarnings("javadoc")
public class LegacyClassLoaderTest {

	@Test
	public void testDoesNotDelegateToSystem() throws Throwable {
		var url = Path.of(IuTest.getProperty("testlegacy.jar")).toRealPath().toUri().toURL();
		try (var loader = new LegacyClassLoader(Set.of(), new URL[] { url }, null)) {
			assertThrows(ClassNotFoundException.class, () -> loader.loadClass(IuComponent.class.getName()));
			assertSame(Object.class, loader.loadClass(Object.class.getName()));
			var timer = loader.loadClass("java.util.Timer");
			assertSame(java.util.Timer.class, timer);
			assertNull(loader.getResource("META-INF/iu-test.properties"));
		}
	}

	@Test
	public void testEndorsed() throws Throwable {
		var url = Path.of(IuTest.getProperty("testlegacy.jar")).toRealPath().toUri().toURL();
		try (var loader = new LegacyClassLoader(Set.of(), new URL[] { url }, null)) {
			var legacyInterface = loader.loadClass("edu.iu.legacy.LegacyInterface");
			var nonEndorsedChild = new LegacyClassLoader(Set.of(), new URL[] { url }, loader);
			assertSame(legacyInterface, nonEndorsedChild.loadClass("edu.iu.legacy.LegacyInterface"));
			try (var endorsedChild = new LegacyClassLoader(Set.of("edu.iu.legacy.LegacyInterface"), new URL[] { url },
					loader)) {
				assertNotSame(legacyInterface, endorsedChild.loadClass("edu.iu.legacy.LegacyInterface"));
			}
			try (var endorsedChild = new LegacyClassLoader(Set.of("edu.iu.legacy.LegacyInterface"), new URL[] { url },
					loader)) {
				assertNotSame(legacyInterface, endorsedChild.loadClass("edu.iu.legacy.LegacyInterface", true));
			}
		}
	}

}
