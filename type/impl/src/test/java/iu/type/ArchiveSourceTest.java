/*
 * Copyright © 2025 Indiana University
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class ArchiveSourceTest extends IuTypeTestCase {

	private ArchiveSource read(ComponentEntry componentEntry) throws IOException {
		return new ArchiveSource(new ByteArrayInputStream(componentEntry.data()));
	}

	private void assertManifest(ComponentEntry componentEntry) throws IOException {
		assertEquals("META-INF/MANIFEST.MF", componentEntry.name());
		final var manifest = new Manifest();
		componentEntry.read(manifest::read);
		assertEquals("1.0", manifest.getMainAttributes().getValue(Name.MANIFEST_VERSION));
	}

	private ComponentEntry createJar(Map<String, byte[]> source) throws IOException {
		var out = new ByteArrayOutputStream();
		try (JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("META-INF/"));
			jar.closeEntry();

			var manifest = new Manifest();
			manifest.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
			jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
			manifest.write(jar);
			jar.closeEntry();

			for (var entry : source.entrySet()) {
				var jarEntry = new JarEntry(entry.getKey());
				jar.putNextEntry(jarEntry);
				jar.write(entry.getValue());
				jar.closeEntry();
			}
		}
		return new ComponentEntry(null, new ByteArrayInputStream(out.toByteArray()));
	}

	@Test
	public void testRequiresManifest() throws IOException {
		var out = new ByteArrayOutputStream();
		try (JarOutputStream jar = new JarOutputStream(out)) {
		}
		assertEquals("Missing META-INF/MANIFEST.MF", assertThrows(IllegalArgumentException.class,
				() -> new ArchiveSource(new ByteArrayInputStream(out.toByteArray()))).getMessage());
	}

	@Test
	public void testRequiresManifestVersion() throws IOException {
		var out = new ByteArrayOutputStream();
		try (JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
			new Manifest().write(jar);
			jar.closeEntry();
		}
		assertEquals("Missing Manifest-Version attribute in META-INF/MANIFEST.MF",
				assertThrows(IllegalArgumentException.class,
						() -> new ArchiveSource(new ByteArrayInputStream(out.toByteArray()))).getMessage());
	}

	@Test
	public void testReturnsData() throws IOException {
		byte[] data = new byte[] { -1 };
		try (var source = read(createJar(Map.of("", data)))) {
			assertManifest(source.next());
			var next = source.next();
			var nextData = next.data();
			assertArrayEquals(data, nextData);
			assertSame(nextData, next.data());
		}
	}

	@Test
	public void testIterates() throws IOException {
		byte[] data = new byte[] { -1 };
		try (var source = read(createJar(Map.of("", data)))) {
			assertTrue(source.hasNext());
			assertManifest(source.next());
			assertArrayEquals(data, source.next().data());
			assertFalse(source.hasNext());
			assertThrows(NoSuchElementException.class, source::next);
		}
	}

	@Test
	public void testClose() throws IOException {
		byte[] data = new byte[] { -1 };
		Map<String, byte[]> map = new LinkedHashMap<>();
		map.put("anEntry", data);
		map.put("anotherEntry", data);
		var source = read(createJar(map));
		assertManifest(source.next());
		assertTrue(source.hasNext());
		assertEquals("anEntry", source.next().name());
		assertTrue(source.hasNext());
		source.close();
		assertFalse(source.hasNext());
		assertThrows(NoSuchElementException.class, source::next);
	}

	@Test
	public void testClassPath() throws Throwable {
		ArchiveSource empty = read(createJar(Map.of()));
		assertFalse(empty.classPath().iterator().hasNext());

		try (final var source = new ArchiveSource(
				Files.newInputStream(Path.of(IuTest.getProperty("testruntime.archive"))))) {
			assertEquals(List.of(
					"META-INF/lib/jakarta.interceptor-api-" + IuTest.getProperty("jakarta.interceptor-api.version")
							+ ".jar",
					"META-INF/lib/jakarta.annotation-api-" + IuTest.getProperty("jakarta.annotation-api.version")
							+ ".jar",
					"META-INF/lib/jakarta.json-api-" + IuTest.getProperty("jakarta.json-api.version") + ".jar",
					"META-INF/lib/commons-lang-2.6.jar", "META-INF/lib/jakarta.ejb-api-4.0.0.jar",
					"META-INF/lib/jakarta.transaction-api-2.0.0.jar"), source.classPath());
		}
	}

	@Test
	public void testEmptyDependenciesAreEmpty() throws Throwable {
		ArchiveSource empty = read(createJar(Map.of()));
		assertFalse(empty.dependencies().iterator().hasNext());
	}

	@Test
	public void testReadsTestComponent() throws IOException {
		var testcomponent = TestArchives.getComponentArchive("testcomponent");
		try (var source = new ArchiveSource(testcomponent)) {
			assertEquals(List.of(), source.classPath());
			assertEquals(List.of(
					new ComponentVersion("iu-java-type-testruntime", IuTest.getProperty("project.version")),
					new ComponentVersion("jakarta.interceptor-api",
							IuTest.getProperty("jakarta.interceptor-api.version")),
					new ComponentVersion("jakarta.annotation-api",
							IuTest.getProperty("jakarta.annotation-api.version")),
					new ComponentVersion("jakarta.json-api", IuTest.getProperty("jakarta.json-api.version")),
					new ComponentVersion("commons-lang", "2.6"), new ComponentVersion("jakarta.ejb-api", "4.0.0"),
					new ComponentVersion("jakarta.transaction-api", "2.0.0")), source.dependencies());
			assertTrue(source.sealed());
		}
	}

	@Test
	public void testReadsTestRuntime() throws IOException {
		var testcomponent = TestArchives.getComponentArchive("testruntime");
		try (var source = new ArchiveSource(testcomponent)) {
			assertEquals(List.of(
					"META-INF/lib/jakarta.interceptor-api-" + IuTest.getProperty("jakarta.interceptor-api.version")
							+ ".jar",
					"META-INF/lib/jakarta.annotation-api-" + IuTest.getProperty("jakarta.annotation-api.version")
							+ ".jar",
					"META-INF/lib/jakarta.json-api-" + IuTest.getProperty("jakarta.json-api.version") + ".jar",
					"META-INF/lib/commons-lang-2.6.jar", "META-INF/lib/jakarta.ejb-api-4.0.0.jar",
					"META-INF/lib/jakarta.transaction-api-2.0.0.jar"), source.classPath());
			assertEquals(List.of(new ComponentVersion("parsson", 1, 1)), source.dependencies());
			assertTrue(source.sealed());
		}
	}

	@Test
	public void testToString() throws IOException {
		var testcomponent = TestArchives.getComponentArchive("testruntime");
		try (var source = new ArchiveSource(testcomponent)) {
			assertEquals(
					"ArchiveSource [sealed=true, classPath=[META-INF/lib/jakarta.interceptor-api-2.2.0.jar, META-INF/lib/jakarta.annotation-api-3.0.0.jar, META-INF/lib/jakarta.json-api-2.1.2.jar, META-INF/lib/commons-lang-2.6.jar, META-INF/lib/jakarta.ejb-api-4.0.0.jar, META-INF/lib/jakarta.transaction-api-2.0.0.jar], dependencies=[parsson-1.1+], closed=false]",
					source.toString());
			source.hasNext();
			assertTrue(source.toString().startsWith(
					"ArchiveSource [sealed=true, classPath=[META-INF/lib/jakarta.interceptor-api-2.2.0.jar, META-INF/lib/jakarta.annotation-api-3.0.0.jar, META-INF/lib/jakarta.json-api-2.1.2.jar, META-INF/lib/commons-lang-2.6.jar, META-INF/lib/jakarta.ejb-api-4.0.0.jar, META-INF/lib/jakarta.transaction-api-2.0.0.jar], dependencies=[parsson-1.1+], next=Optional[ComponentEntry [name="),
					source::toString);
			source.next();
			assertTrue(source.toString().startsWith(
					"ArchiveSource [sealed=true, classPath=[META-INF/lib/jakarta.interceptor-api-2.2.0.jar, META-INF/lib/jakarta.annotation-api-3.0.0.jar, META-INF/lib/jakarta.json-api-2.1.2.jar, META-INF/lib/commons-lang-2.6.jar, META-INF/lib/jakarta.ejb-api-4.0.0.jar, META-INF/lib/jakarta.transaction-api-2.0.0.jar], dependencies=[parsson-1.1+], last=ComponentEntry [name="),
					source::toString);
		}
	}

}
