/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuComponentVersion;

@SuppressWarnings("javadoc")
public class ComponentVersionTest extends IuTypeTestCase {

	@Test
	public void testSpecRequiesName() {
		assertEquals(
				"Component name must be non-null, start with a letter, and contain only letters, numbers, dots '.', and hyphens '-'",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion(null, 0, 0)).getMessage());
	}

	@Test
	public void testSpecMustUseNonNegativeMajor() {
		assertEquals("Component major version number must be non-negative",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my.component", -1, 0))
						.getMessage());
	}

	@Test
	public void testSpecMustUseNonNegativeMinor() {
		assertEquals("Component minor version number must be non-negative",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my.component", 0, -1))
						.getMessage());
	}

	@Test
	public void testSpecificationVersion() {
		var version = new ComponentVersion("my.component", 1, 0);
		assertEquals("my.component", version.name());
		assertNull(version.implementationVersion());
		assertEquals(1, version.major());
		assertEquals(0, version.minor());
	}

	@Test
	public void testImplRequiesName() {
		assertEquals(
				"Component name must be non-null, start with a letter, and contain only letters, numbers, dots '.', and hyphens '-'",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion(null, (String) null))
						.getMessage());
	}

	@Test
	public void testImplRequiresVersion() {
		assertEquals("Missing version for my-component, must be a valid semantic version",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my-component", (String) null))
						.getMessage());
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
		var version = new ComponentVersion("a", "1.0.2");
		assertEquals("a", version.name());
		assertEquals("1.0.2", version.implementationVersion());
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

	@Test
	public void testDepRequiresImplOrSpecVersion() {
		var attr = new Manifest().getMainAttributes();
		attr.put(new Name("my_dependency-api-" + Name.EXTENSION_NAME), "my.dependency-api");
		assertEquals(
				"Missing my_dependency-api-Implementation-Version or my_dependency-api-Specification-Version in META-INF/MANIFEST.MF main attributes",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my.dependency-api", attr))
						.getMessage());
	}

	@Test
	public void testDepRequiresValidSpecVersion() {
		var attr = new Manifest().getMainAttributes();
		attr.put(new Name("my_dependency-api-" + Name.EXTENSION_NAME), "my.dependency-api");
		attr.put(new Name("my_dependency-api-" + Name.SPECIFICATION_VERSION), "");
		assertEquals(
				"Invalid version for my_dependency-api-Specification-Version in META-INF/MANIFEST.MF main attributes , must be a valid semantic version",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my.dependency-api", attr))
						.getMessage());
	}

	@Test
	public void testDepSpecificationVersion() {
		var attr = new Manifest().getMainAttributes();
		attr.put(new Name("my_dependency-api-" + Name.EXTENSION_NAME), "my.dependency-api");
		attr.put(new Name("my_dependency-api-" + Name.SPECIFICATION_VERSION), "1.0");
		var version = new ComponentVersion("my.dependency-api", attr);
		assertEquals("my.dependency-api", version.name());
		assertNull(version.implementationVersion());
		assertEquals(1, version.major());
		assertEquals(0, version.minor());
	}

	@Test
	public void testDepRequiresValidImplVersion() {
		var attr = new Manifest().getMainAttributes();
		attr.put(new Name("my_dependency-api-" + Name.EXTENSION_NAME), "my.dependency-api");
		attr.put(new Name("my_dependency-api-" + Name.IMPLEMENTATION_VERSION), "");
		assertEquals(
				"Invalid version for my_dependency-api-Implementation-Version in META-INF/MANIFEST.MF main attributes, must be a valid semantic version",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my.dependency-api", attr))
						.getMessage());
	}

	@Test
	public void testDepImplementationVersion() {
		var attr = new Manifest().getMainAttributes();
		attr.put(new Name("my_dependency-api-" + Name.EXTENSION_NAME), "my.dependency-api");
		attr.put(new Name("my_dependency-api-" + Name.IMPLEMENTATION_VERSION), "1.0.2");
		var version = new ComponentVersion("my.dependency-api", attr);
		assertEquals("my.dependency-api", version.name());
		assertEquals("1.0.2", version.implementationVersion());
		assertEquals(1, version.major());
		assertEquals(0, version.minor());
	}

	@Test
	public void testSpecFromSpec() {
		var version = new ComponentVersion("a", 1, 0);
		assertSame(version, version.specificationVersion());
	}

	@Test
	public void testSpecFromImpl() {
		var version = new ComponentVersion("a", "1.0.2").specificationVersion();
		assertEquals("a", version.name());
		assertNull(version.implementationVersion());
		assertEquals(1, version.major());
		assertEquals(0, version.minor());
	}

	@Test
	public void testHashCode() {
		var versionSet = new HashSet<IuComponentVersion>();
		versionSet.add(new ComponentVersion("a", 1, 0));
		assertTrue(versionSet.contains(new ComponentVersion("a", 1, 0)));
		assertFalse(versionSet.contains(new ComponentVersion("a", "1.0.2")));
		assertTrue(versionSet.contains(new ComponentVersion("a", "1.0.2").specificationVersion()));
	}

	@Test
	public void testSameIsEquals() {
		var version = new ComponentVersion("a", 1, 0);
		assertEquals(version, version);
	}

	@Test
	public void testImplsAreEqual() {
		assertEquals(new ComponentVersion("a", 1, 0), new ComponentVersion("a", 1, 0));
	}

	@Test
	public void testNullIsNotEquals() {
		assertNotEquals(new ComponentVersion("a", 1, 0), null);
	}

	@Test
	public void testNonVersionNotEquals() {
		assertNotEquals(new ComponentVersion("a", 1, 0), this);
	}

	@Test
	public void testOtherImplEquals() {
		assertEquals(new ComponentVersion("a", 1, 0), new IuComponentVersion() {
			@Override
			public String name() {
				return "a";
			}

			@Override
			public int major() {
				return 1;
			}

			@Override
			public int minor() {
				return 0;
			}

			@Override
			public String implementationVersion() {
				return null;
			}
		});
	}

	@Test
	public void testNameMismatchNotEquals() {
		assertNotEquals(new ComponentVersion("a", 1, 0), new ComponentVersion("b", 1, 0));
	}

	@Test
	public void testMajorMismatchNotEquals() {
		assertNotEquals(new ComponentVersion("a", 1, 0), new ComponentVersion("a", 2, 0));
	}

	@Test
	public void testMinorMismatchNotEquals() {
		assertNotEquals(new ComponentVersion("a", 1, 0), new ComponentVersion("a", 1, 1));
	}

	@Test
	public void testSpecAndImplNotEquals() {
		var version = new ComponentVersion("a", "1.0.2");
		assertNotEquals(version, version.specificationVersion());
	}

	@Test
	public void testSpecToString() {
		assertEquals("a-1.0+", new ComponentVersion("a", 1, 0).toString());
	}

	@Test
	public void testImplToString() {
		assertEquals("a-1.0.2", new ComponentVersion("a", "1.0.2").toString());
	}

	@Test
	public void testEmptyJar() throws IOException {
		final var p = Files.createTempFile("iu-type-ComponentVersionTest", ".jar");
		try {
			try (final var out = Files.newOutputStream(p)) {
				out.write(TestArchives.EMPTY_JAR);
			}
			assertEquals("Missing META-INF/maven/{groupId}/{artifactId}/pom.properties",
					assertThrows(IllegalArgumentException.class, () -> ComponentVersion.of(p)).getMessage());
		} finally {
			Files.deleteIfExists(p);
		}
	}

	@Test
	public void testMalformedJar() throws IOException {
		final var p = Files.createTempFile("iu-type-ComponentVersionTest", ".jar");
		try {
			try (final var out = Files.newOutputStream(p); JarOutputStream jar = new JarOutputStream(out)) {
				jar.putNextEntry(new JarEntry("META-INF/"));
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				var manifest = new Manifest();
				var mainAttributes = manifest.getMainAttributes();
				mainAttributes.put(Name.MANIFEST_VERSION, "1.0");
				manifest.write(jar);
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("META-INF/maven/"));
				jar.closeEntry();
			}
			assertEquals("Missing META-INF/maven/{groupId}/{artifactId}/pom.properties",
					assertThrows(IllegalArgumentException.class, () -> ComponentVersion.of(p)).getMessage());
		} finally {
			Files.deleteIfExists(p);
		}
	}

}
