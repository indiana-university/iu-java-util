package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ThreadLocalRandom;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ComponentVersionTest {

	@Test
	public void testSpecificationVersion() {
		var version = new ComponentVersion("", 1, 0);
		assertEquals("", version.name());
		assertEquals(1, version.major());
		assertEquals(0, version.minor());
	}

	@Test
	public void testSpecMustUseNonNegativeMajor() {
		assertEquals("Component major version number must be non-negative",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("", -1, 0)).getMessage());
	}

	@Test
	public void testSpecMustUseNonNegativeMinor() {
		assertEquals("Component minor version number must be non-negative",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("", 0, -1)).getMessage());
	}

	@Test
	public void testSpecRequiesName() {
		assertThrows(NullPointerException.class, () -> new ComponentVersion(null, 0, 0));
	}

	@Test
	public void testImplRequiesName() {
		assertThrows(NullPointerException.class, () -> new ComponentVersion(null, (String) null));
	}

	@Test
	public void testImplRequiresVersion() {
		assertThrows(NullPointerException.class, () -> new ComponentVersion("", (String) null));
	}

	@Test
	public void testImplRequiresSemanticVersion() {
		assertEquals("Invalid version for my-component, must be a valid semantic version",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my-component", ""))
						.getMessage());
	}

	@Test
	public void testImplRequiresNonNegativeMajor() {
		assertEquals("Invalid version for my-component, must be a valid semantic version",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my-component", "-1.0.0"))
						.getMessage());
	}

	@Test
	public void testImplRequiresNonNegativeMinor() {
		assertEquals("Invalid version for my-component, must be a valid semantic version",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my-component", "0.-1.0"))
						.getMessage());
	}

	@Test
	public void testImplExtractsMajorAndMinor() {
		var version = new ComponentVersion("", "1.0.2");
		assertEquals("", version.name());
		assertEquals(1, version.major());
		assertEquals(0, version.minor());
	}

	@Test
	public void testDepRequiresName() {
		var attr = new Manifest().getMainAttributes();
		assertEquals("Missing my_dependency-api-Extension-Name in META-INF/MANIFEST.MF main attributes",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my.dependency-api", attr))
						.getMessage());
	}

}
