/*
 * Copyright © 2023 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import edu.iu.test.IuTest;
import edu.iu.type.IuComponent.Kind;
import edu.iu.type.IuComponentVersion;

@SuppressWarnings("javadoc")
public class ComponentArchiveTest extends IuTypeTestCase {

	private static final byte[] B0 = new byte[0];

	private ArchiveSource createSource(Iterable<String> classPath, Map<String, byte[]> entries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("META-INF/"));
			jar.closeEntry();

			jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
			var manifest = new Manifest();
			var mainAttributes = manifest.getMainAttributes();
			mainAttributes.put(Name.MANIFEST_VERSION, "1.0");

			if (classPath != null) {
				StringBuilder classPathAttribute = new StringBuilder();
				for (var classPathEntry : classPath) {
					if (classPathAttribute.length() > 0)
						classPathAttribute.append(' ');
					classPathAttribute.append(classPathEntry);
				}
				if (classPathAttribute.length() > 0)
					mainAttributes.put(Name.CLASS_PATH, classPathAttribute.toString());
			}

			manifest.write(jar);
			jar.closeEntry();

			for (var entry : entries.entrySet()) {
				jar.putNextEntry(new JarEntry(entry.getKey()));
				jar.write(entry.getValue());
				jar.closeEntry();
			}
		}
		return new ArchiveSource(new ByteArrayInputStream(out.toByteArray()));
	}

	private void assertInvalidEntries(String expectedMessage, String... entryNames) {
		assertInvalidSource(expectedMessage, null, entryNames);
	}

	private void assertInvalidSource(String expectedMessage, Iterable<String> classPath, String... entryNames) {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		for (var entryName : entryNames)
			if (entryName.endsWith(".jar"))
				entries.put(entryName, TestArchives.EMPTY_JAR);
			else
				entries.put(entryName, B0);
		assertInvalidSource(expectedMessage, classPath, entries);
	}

	private void assertInvalidSource(String expectedMessage, Iterable<String> classPath, Map<String, byte[]> entries) {
		var threw = assertThrows(IllegalArgumentException.class,
				() -> ComponentArchive.from(createSource(classPath, entries)));
		try {
			assertEquals(expectedMessage, threw.getMessage());
		} catch (AssertionFailedError e) {
			e.addSuppressed(threw);
			throw e;
		}
	}

	private void assertReadsArchive(String name, Consumer<ComponentArchive> archiveConsumer,
			Map<String, Integer> expectedContents) throws IOException {
		assertReadsArchive(name, (archive, source) -> archiveConsumer.accept(archive), expectedContents);
	}

	private void assertReadsArchive(String name, BiConsumer<ComponentArchive, ArchiveSource> archiveAndSourceConsumer,
			Map<String, Integer> expectedContents) throws IOException {
		try ( //
				var testcomponent = TestArchives.getComponentArchive(name); //
				var source = new ArchiveSource(testcomponent)) {
			assertReadsArchive(name, source, archive -> archiveAndSourceConsumer.accept(archive, source),
					expectedContents);
		}
	}

	private void assertReadsArchive(String name, ArchiveSource source, Consumer<ComponentArchive> archiveConsumer,
			Map<String, Integer> expectedContents) throws IOException {
		ComponentArchive archive = ComponentArchive.from(source);
		Throwable thrown = null;
		try {
			archiveConsumer.accept(archive);
			var expectedEntryNames = new LinkedHashSet<>();
			expectedContents.forEach((entryName, size) -> {
				if (size >= 0L)
					expectedEntryNames.add(entryName);
			});

			Map<String, Integer> listing = new LinkedHashMap<>();
			try (InputStream in = Files.newInputStream(archive.path()); JarInputStream jar = new JarInputStream(in)) {
				JarEntry entry;
				while ((entry = jar.getNextJarEntry()) != null) {
					var entryName = entry.getName();
					var data = jar.readAllBytes();
					listing.put(entryName, data.length);

					var expectedSize = expectedContents.get(entryName);
					if (expectedSize == null)
						continue;

					assertTrue(expectedSize >= 0, "Unexpected " + entryName);
					if (expectedEntryNames.remove(entryName))
						assertTrue(data.length >= expectedSize, "Expected " + entryName + " to be at least "
								+ expectedSize + " bytes, was " + data.length);

					jar.closeEntry();
				}
			}

			printListing(name, listing);

			assertTrue(expectedEntryNames.isEmpty(),
					"Not all expected entries were listed, missing " + expectedEntryNames + " from " + listing);
		} catch (RuntimeException | Error e) {
			thrown = e;
			throw e;
		} finally {
			try {
				Files.delete(archive.path());
			} catch (IOException | RuntimeException | Error e) {
				if (thrown != null)
					e.addSuppressed(thrown);
				throw e;
			}
		}
	}

	private void printListing(String name, Map<String, Integer> entries) {
		System.out.println(name);
		for (var entry : entries.entrySet())
			System.out.printf("  %2$6d %1$s\n", entry.getKey(), entry.getValue());
		System.out.println();
	}

	@Test
	public void testRequiresPomProperties() {
		var expectedMessage = "Component archive missing META-INF/maven/.../pom.properties";
		assertInvalidEntries(expectedMessage);
	}

	@Test
	public void testRequiresArtifactId() {
		var expectedMessage = "Component archive must provide a name as artifactId in pom.properties";
		assertInvalidSource(expectedMessage, List.of(), "META-INF/maven/a/pom.properties");
	}

	@Test
	public void testRequiresVersion() {
		var expectedMessage = "Component archive must provide a version in pom.properties";
		assertInvalidSource(expectedMessage, List.of(),
				Map.of("META-INF/maven/a/pom.properties", "artifactId=a".getBytes()));
	}

	@Test
	public void testRejectsEarOrRar() {
		var expectedMessage = "Component archive must not be an Enterprise Application (ear) or Resource Adapter Archive (rar) file";
		assertInvalidEntries(expectedMessage, //
				"META-INF/ejb/endorsed/.jar", "META-INF/ejb/lib/.jar", "META-INF/lib/.jar", ".jar");
		assertInvalidEntries(expectedMessage, "META-INF/application.xml");
		assertInvalidEntries(expectedMessage, "META-INF/ra.xml");
		assertInvalidEntries(expectedMessage, "any/.war");
		assertInvalidEntries(expectedMessage, "any/.rar");
		assertInvalidEntries(expectedMessage, "any/.so");
		assertInvalidEntries(expectedMessage, "any/.dll");
	}

	@Test
	public void testRejectsUberJar() {
		var expectedMessage = "Component archive must not be a shaded (uber-)jar";
		assertInvalidEntries(expectedMessage, "META-INF/maven/a/pom.properties", "META-INF/maven/b/pom.properties");
	}

	@Test
	public void testRejectsTypesAsWebResource() {
		var expectedMessage = "Web archive must not define types outside WEB-INF/classes/";
		assertInvalidEntries(expectedMessage, ".class", "WEB-INF/");
		assertInvalidEntries(expectedMessage, "WEB-INF/", ".class");
	}

	@Test
	public void testRejectsWebEmbeddingDependenciesOutsideWebInfLib() {
		var expectedMessage = "Web archive must not embed components outside WEB-INF/lib/";
		assertInvalidEntries(expectedMessage, "META-INF/lib/.jar", "WEB-INF/");
		assertInvalidEntries(expectedMessage, "WEB-INF/", "META-INF/lib/.jar");
		assertInvalidEntries(expectedMessage, "WEB-INF/lib/.jar", "META-INF/lib/.jar");
	}

	@Test
	public void testRejectsIuTypePropertiesAsWebResource() {
		var expectedMessage = "Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties";
		assertInvalidEntries(expectedMessage, "META-INF/iu-type.properties", "WEB-INF/");
		assertInvalidEntries(expectedMessage, "WEB-INF/", "META-INF/iu-type.properties");
	}

	@Test
	public void testRejectsIuPropertiesAsWebResource() {
		var expectedMessage = "Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties";
		assertInvalidEntries(expectedMessage, "META-INF/iu.properties", "WEB-INF/");
		assertInvalidEntries(expectedMessage, "WEB-INF/", "META-INF/iu.properties");
	}

	@Test
	public void testRejectsIuPropertiesInModularComponent() {
		var expectedMessage = "Modular component archive must not include META-INF/iu.properties";
		assertInvalidEntries(expectedMessage, "META-INF/iu.properties", "module-info.class");
		assertInvalidEntries(expectedMessage, "module-info.class", "META-INF/iu.properties");
	}

	@Test
	public void testRejectsWebModularComponent() {
		var expectedMessage = "Modular component archive must not include META-INF/iu.properties";
		assertInvalidEntries(expectedMessage, "META-INF/iu.properties", "module-info.class");
		assertInvalidEntries(expectedMessage, "module-info.class", "META-INF/iu.properties");
	}

	@Test
	public void testRejetsIncompleteClassPath() {
		var expectedMessage = "Component archive didn't include all bundled dependencies, missing [missing.jar]";
		assertInvalidSource(expectedMessage, List.of("not-missing.jar", "missing.jar"),
				Map.of("not-missing.jar", TestArchives.EMPTY_JAR));
	}

	@Test
	public void testReadsTestComponent() throws IOException {
		assertReadsArchive("testcomponent", archive -> {
			assertEquals(Kind.MODULAR_JAR, archive.kind());
			assertEquals("iu-java-type-testcomponent", archive.version().name());
			assertEquals(IuTest.getProperty("project.version"), archive.version().implementationVersion());
			assertFalse(archive.nonEnclosedTypeNames().contains("module-info"));
			assertFalse(archive.nonEnclosedTypeNames().contains("edu.iu.type.testcomponent.package-info"));
			assertTrue(archive.nonEnclosedTypeNames().contains("edu.iu.type.testcomponent.TestBeanImpl"));
			assertFalse(archive.nonEnclosedTypeNames()
					.contains("edu.iu.type.testcomponent.TestBeanImpl$InternalSupportingClass"));
			assertTrue(archive.webResources().isEmpty());
			assertEquals("testcomponent", archive.properties().getProperty("remotableModules"));
		}, Map.of( //
				"META-INF/MANIFEST.MF", -1, //
				"META-INF/maven/edu.iu.util/iu-java-type-testcomponent/pom.properties", 80, //
				"META-INF/lib/", -1, //
				"META-INF/iu-type.properties", 10, //
				"module-info.class", 300, //
				"edu/iu/type/testcomponent/package-info.class", 100, //
				"edu/iu/type/testcomponent/TestBeanImpl$InternalSupportingClass.class", 400,
				"edu/iu/type/testcomponent/TestBean.class", 400));
	}

	@Test
	public void testReadsTestRuntime() throws IOException {
		assertReadsArchive("testruntime", (archive, source) -> {
			assertEquals(Kind.MODULAR_JAR, archive.kind());
			assertEquals("iu-java-type-testruntime", archive.version().name());
			assertEquals(IuTest.getProperty("project.version"), archive.version().implementationVersion());
			assertFalse(archive.nonEnclosedTypeNames().contains("module-info"));
			assertFalse(archive.nonEnclosedTypeNames().contains("edu.iu.type.testruntime.package-info"));
			assertTrue(archive.nonEnclosedTypeNames().contains("edu.iu.type.testruntime.TestRuntime"));
			assertTrue(archive.webResources().isEmpty());

			var expectedDependencies = new LinkedHashSet<>(source.dependencies());
			assertEquals(1, expectedDependencies.size());
			assertTrue(expectedDependencies.contains(new ComponentVersion("parsson", 1, 1)));

			var bundledDependencies = archive.bundledDependencies();
			assertEquals(3, bundledDependencies.size());
			for (var bundledDependency : bundledDependencies)
				try {
					var bundledArchive = ComponentArchive.from(bundledDependency);
					assertFalse(expectedDependencies.contains(bundledArchive.version()));
					bundledDependency.close();
					Files.delete(bundledArchive.path());
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}

			assertNull(archive.properties());
		}, Map.of( //
				"META-INF/MANIFEST.MF", -1, //
				"META-INF/maven/edu.iu.util/iu-java-type-testruntime/pom.properties", 50, //
				"META-INF/lib/", -1, //
				"module-info.class", 300, //
				"edu/iu/type/testruntime/package-info.class", 100, //
				"edu/iu/type/testruntime/TestRuntime.class", 100));
	}

	@Test
	public void testReadsTestWeb() throws IOException {
		assertReadsArchive("testweb", (archive, source) -> {
			assertEquals(Kind.MODULAR_WAR, archive.kind());
			assertEquals("iu-java-type-testweb", archive.version().name());
			assertEquals(IuTest.getProperty("project.version"), archive.version().implementationVersion());
			assertFalse(archive.nonEnclosedTypeNames().contains("module-info"),
					archive.nonEnclosedTypeNames().toString());
			assertFalse(archive.nonEnclosedTypeNames().contains("edu.iu.type.testweb.package-info"),
					archive.nonEnclosedTypeNames().toString());
			assertTrue(archive.nonEnclosedTypeNames().contains("edu.iu.type.testweb.TestServlet"),
					archive.nonEnclosedTypeNames().toString());
			assertTrue(archive.webResources().containsKey("WEB-INF/web.xml"),
					"missing WEB-INF/web.xml " + archive.webResources().toString());
			assertTrue(archive.webResources().containsKey("index.html"),
					"missing index.html " + archive.webResources().toString());
			assertFalse(archive.webResources().containsKey("META-INF/"),
					"shouldn't include META-INF/ " + archive.webResources().toString());
			assertFalse(archive.webResources().containsKey("WEB-INF/lib/"),
					"shouldn't include WEB-INF/ " + archive.webResources().toString());
			assertFalse(archive.webResources().containsKey("WEB-INF/lib/"),
					"shouldn't include WEB-INF/lib/ " + archive.webResources().toString());
			assertFalse(archive.webResources().containsKey("WEB-INF/classes/"),
					"shouldn't include WEB-INF/classes/ " + archive.webResources().toString());

			var expected = new LinkedHashSet<>(source.dependencies());
			assertEquals(3, expected.size());
			assertTrue(expected.contains(new ComponentVersion("iu-java-type-testruntime", 7, 0)), expected::toString);
			assertTrue(expected.contains(new ComponentVersion("jakarta.servlet-api", 6, 0)), expected::toString);
			assertTrue(expected.contains(new ComponentVersion("jakarta.servlet.jsp-api", 3, 1)), expected::toString);

			Set<IuComponentVersion> bundled = new LinkedHashSet<>();
			for (var bundledDependency : archive.bundledDependencies()) {
				ComponentArchive bundledArchive;
				try {
					bundledArchive = ComponentArchive.from(bundledDependency);
					var bundledVersion = bundledArchive.version();
					assertFalse(expected.remove(bundledVersion));
					bundled.add(bundledVersion);
					Files.delete(bundledArchive.path());
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			}
			assertEquals(2, bundled.size());
			assertTrue(bundled.contains(new ComponentVersion("jakarta.el-api", "5.0.0")), bundled::toString);
			assertTrue(bundled.contains(new ComponentVersion("jakarta.servlet.jsp.jstl-api", "3.0.0")), bundled::toString);

			assertEquals("true", archive.properties().getProperty("sample.type.property"));

		}, Map.of( //
				"META-INF/MANIFEST.MF", -1, //
				"META-INF/maven/edu.iu.util/iu-java-type-testweb/pom.properties", 50, //
				"WEB-INF/", -1, //
				"WEB-INF/lib/", -1, //
				"module-info.class", 180, //
				"edu/iu/type/testweb/package-info.class", 100, //
				"edu/iu/type/testweb/TestServlet.class", 100));
	}

	@Test
	public void testReadsTestWebWithoutNamedDeps() throws IOException {
		try (var in = TestArchives.getComponentArchive("testweb")) {
			var out = new ByteArrayOutputStream();
			try (var inJar = new JarInputStream(in); var outJar = new JarOutputStream(out)) {
				outJar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				var manifest = inJar.getManifest();
				var attr = manifest.getMainAttributes();
				attr.remove(Name.EXTENSION_LIST);
				manifest.write(outJar);
				outJar.closeEntry();

				JarEntry entry;
				while ((entry = inJar.getNextJarEntry()) != null) {
					outJar.putNextEntry(entry);
					outJar.write(inJar.readAllBytes());
					outJar.closeEntry();
					inJar.closeEntry();
				}
			}
			try (var source = new ArchiveSource(new ByteArrayInputStream(out.toByteArray()))) {
				assertReadsArchive("testweb (w/o named deps)", source, archive -> {
					assertEquals(Kind.MODULAR_WAR, archive.kind());
				}, Map.of());
			}
		}
	}

	@Test
	public void testReadsTestWebWithoutModuleInfo() throws IOException {
		try (var in = TestArchives.getComponentArchive("testweb")) {
			var out = new ByteArrayOutputStream();
			try (var inJar = new JarInputStream(in); var outJar = new JarOutputStream(out)) {
				outJar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				inJar.getManifest().write(outJar);
				outJar.closeEntry();

				JarEntry entry;
				while ((entry = inJar.getNextJarEntry()) != null) {
					if (!entry.getName().equals("WEB-INF/classes/module-info.class")) {
						outJar.putNextEntry(entry);
						outJar.write(inJar.readAllBytes());
						outJar.closeEntry();
					}
					inJar.closeEntry();
				}
			}
			try (var source = new ArchiveSource(new ByteArrayInputStream(out.toByteArray()))) {
				assertReadsArchive("testweb (w/o module-info)", source, archive -> {
					assertEquals(Kind.MODULAR_WAR, archive.kind());
				}, Map.of("WEB-INF/classes/module-info.class", -1));
			}
		}
	}

	@Test
	public void testReadsTestLegacy() throws IOException {
		assertReadsArchive("testlegacy", (archive, source) -> {
			assertEquals(Kind.LEGACY_JAR, archive.kind());
			assertEquals("iu-java-type-testlegacy", archive.version().name());
			assertEquals(IuTest.getProperty("project.version"), archive.version().implementationVersion());
			assertTrue(archive.nonEnclosedTypeNames().contains("edu.iu.legacy.LegacyInterface"),
					archive.nonEnclosedTypeNames().toString());

			assertTrue(archive.webResources().isEmpty());
			assertTrue(source.dependencies().isEmpty());

			assertEquals(4, archive.bundledDependencies().size());
			Set<ComponentVersion> expectedDependencies = new HashSet<>();
			expectedDependencies.add(new ComponentVersion("javax.annotation-api", "1.3.2"));
			expectedDependencies.add(new ComponentVersion("javax.interceptor-api", "1.2.2"));
			expectedDependencies.add(new ComponentVersion("javax.json-api", "1.1.4"));
			expectedDependencies.add(new ComponentVersion("javax.json", "1.1.4"));
			for (var bundledDependency : archive.bundledDependencies()) {
				try {
					var bundledArchive = ComponentArchive.from(bundledDependency);
					assertTrue(expectedDependencies.remove(bundledArchive.version()));
					bundledDependency.close();
					Files.delete(bundledArchive.path());
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			}

			assertEquals("legacytest", archive.properties().getProperty("application"));

			try {
				var c = new URL("jar:" + archive.path().toUri().toURL() + "!/html/index.html").openConnection();
				c.setUseCaches(false);
				try (var in = c.getInputStream()) {
					var text = new String(in.readAllBytes()).replace("\r\n", "\n");
					assertEquals("<html>\n\t<body>\n\t\t<main>This is a legacy component</main>\n\t</body>\n</html>",
							text);
				}

			} catch (IOException e) {
				throw new IllegalStateException(e);
			}

		}, Map.of( //
				"META-INF/MANIFEST.MF", -1, //
				"META-INF/maven/edu.iu.util/iu-java-type-testlegacy/pom.properties", 50, //
				"META-INF/lib/", -1, //
				"META-INF/lib/javax.json-api-1.1.4.jar", -1, //
				"html/index.html", 75, //
				"edu/iu/legacy/LegacyInterface.class", 100));
	}

	@Test
	public void testReadsTestLegacyWeb() throws IOException {
		assertReadsArchive("testlegacyweb", (archive, source) -> {
			assertEquals(Kind.LEGACY_WAR, archive.kind());
			assertEquals("iu-java-type-testlegacyweb", archive.version().name());
			assertEquals(IuTest.getProperty("project.version"), archive.version().implementationVersion());
			assertTrue(archive.nonEnclosedTypeNames().contains("edu.iu.type.testlegacyweb.TestLegacyWebServlet"),
					archive.nonEnclosedTypeNames().toString());

			assertTrue(archive.webResources().containsKey("WEB-INF/web.xml"),
					"missing WEB-INF/web.xml " + archive.webResources().toString());
			assertTrue(archive.webResources().containsKey("index.jsp"),
					"missing index.jsp " + archive.webResources().toString());
			assertFalse(archive.webResources().containsKey("META-INF/"),
					"shouldn't include META-INF/ " + archive.webResources().toString());
			assertFalse(archive.webResources().containsKey("META-INF/legacyweb.properties"),
					"shouldn't include META-INF/legacyweb.properties " + archive.webResources().toString());
			assertFalse(archive.webResources().containsKey("WEB-INF/lib/"),
					"shouldn't include WEB-INF/ " + archive.webResources().toString());
			assertFalse(archive.webResources().containsKey("WEB-INF/lib/"),
					"shouldn't include WEB-INF/lib/ " + archive.webResources().toString());
			assertFalse(archive.webResources().containsKey("WEB-INF/classes/"),
					"shouldn't include WEB-INF/classes/ " + archive.webResources().toString());
			assertTrue(source.dependencies().isEmpty());

			assertEquals(1, archive.bundledDependencies().size());
			Set<ComponentVersion> expectedDependencies = new HashSet<>();
			expectedDependencies.add(new ComponentVersion("jakarta.servlet.jsp.jstl-api", "1.2.7"));
			for (var bundledDependency : archive.bundledDependencies()) {
				try {
					var bundledArchive = ComponentArchive.from(bundledDependency);
					assertTrue(expectedDependencies.remove(bundledArchive.version()));
					bundledDependency.close();
					Files.delete(bundledArchive.path());
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			}

			assertNull(archive.properties());

			var text = new String(archive.webResources().get("index.jsp")).replace("\r\n", "\n");
			assertEquals(
					"<html>\n\n<body>\n\t<h1>Some items</h1>\n\t<c:forEach var=\"item\" items=\"${items}\">\n\t\t<div>${item}</div>\n\t</c:forEach>\n</body>\n\n</html>",
					text);

		}, Map.of( //
				"META-INF/MANIFEST.MF", -1, //
				"META-INF/maven/edu.iu.util/iu-java-type-testlegacy/pom.properties", -1, //
				"META-INF/legacyweb.properties", 0, //
				"edu/iu/type/testlegacyweb/TestLegacyWebServlet.class", 300));
	}

	@Test
	public void testReadsTestLegacyWebWithNamedDeps() throws IOException {
		try (var in = TestArchives.getComponentArchive("testlegacyweb")) {
			var out = new ByteArrayOutputStream();
			try (var inJar = new JarInputStream(in); var outJar = new JarOutputStream(out)) {
				outJar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				var manifest = inJar.getManifest();
				var attr = manifest.getMainAttributes();
				attr.put(Name.EXTENSION_LIST, "jakarta.servlet-api");
				attr.put(new Name("jakarta_servlet-api-Extension-Name"), "jakarta.servlet-api");
				attr.put(new Name("jakarta_servlet-api-Specification-Version"), "4.0");
				manifest.write(outJar);
				outJar.closeEntry();

				JarEntry entry;
				while ((entry = inJar.getNextJarEntry()) != null) {
					outJar.putNextEntry(entry);
					outJar.write(inJar.readAllBytes());
					outJar.closeEntry();
					inJar.closeEntry();
				}
			}
			try (var source = new ArchiveSource(new ByteArrayInputStream(out.toByteArray()))) {
				assertReadsArchive("testlegacyweb (w/ named deps)", source, archive -> {
					assertEquals(Kind.LEGACY_WAR, archive.kind());
				}, Map.of());
			}
		}
	}

	@Test
	public void testReadsTestLegacyWebWithIuProperties() throws IOException {
		try (var in = TestArchives.getComponentArchive("testlegacyweb")) {
			var out = new ByteArrayOutputStream();
			try (var inJar = new JarInputStream(in); var outJar = new JarOutputStream(out)) {
				outJar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				inJar.getManifest().write(outJar);
				outJar.closeEntry();

				JarEntry entry;
				while ((entry = inJar.getNextJarEntry()) != null) {
					outJar.putNextEntry(entry);
					outJar.write(inJar.readAllBytes());
					outJar.closeEntry();
					inJar.closeEntry();
				}

				outJar.putNextEntry(new JarEntry("WEB-INF/classes/META-INF/iu.properties"));
				outJar.write("component=testlegacyweb\n".getBytes());
				outJar.closeEntry();
			}

			try (var source = new ArchiveSource(new ByteArrayInputStream(out.toByteArray()))) {
				assertReadsArchive("testlegacyweb (w/ iu.properties)", source, archive -> {
					assertEquals(Kind.LEGACY_WAR, archive.kind());
					assertEquals("testlegacyweb", archive.properties().getProperty("component"));
				}, Map.of("META-INF/iu.properties", -1));
			}
		}
	}

}
