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
public class ArchiveSourceTest {

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
		try (var source = new ArchiveSource(createJar(Map.of("", data)))) {
			var next = source.next();
			var nextData = next.data();
			assertArrayEquals(data, nextData);
			assertSame(nextData, next.data());
		}
	}

	@Test
	public void testIterates() throws IOException {
		byte[] data = new byte[] { -1 };
		try (var source = new ArchiveSource(createJar(Map.of("", data)))) {
			assertTrue(source.hasNext());
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
		var source = new ArchiveSource(createJar(map));
		assertTrue(source.hasNext());
		assertEquals("anEntry", source.next().name());
		assertTrue(source.hasNext());
		source.close();
		assertFalse(source.hasNext());
		assertThrows(NoSuchElementException.class, source::next);
	}

	@Test
	public void testClassPath() throws Throwable {
		ArchiveSource empty = new ArchiveSource(createJar(Map.of()));
		assertFalse(empty.classPath().iterator().hasNext());

		ArchiveSource source = new ArchiveSource(Files.newInputStream(Path.of(IuTest.getProperty("testruntime.archive"))));
		assertEquals(List.of(
				"META-INF/lib/jakarta.interceptor-api-" + IuTest.getProperty("jakarta.interceptor-api.version")
						+ ".jar",
				"META-INF/lib/jakarta.annotation-api-" + IuTest.getProperty("jakarta.annotation-api.version") + ".jar",
				"META-INF/lib/jakarta.json-api-" + IuTest.getProperty("jakarta.json-api.version") + ".jar"),
				source.classPath());
	}

	@Test
	public void testDependencies() throws Throwable {
		ArchiveSource empty = new ArchiveSource(createJar(Map.of()));
		assertFalse(empty.dependencies().iterator().hasNext());

		ArchiveSource source = new ArchiveSource(Files.newInputStream(Path.of(IuTest.getProperty("testruntime.archive"))));
		var dependencies = source.dependencies().iterator();
		assertTrue(dependencies.hasNext());
		assertEquals(new ComponentVersion("jakarta.interceptor-api", 
				IuTest.getProperty("jakarta.interceptor-api.version")), dependencies.next());
		assertTrue(dependencies.hasNext());
		assertEquals(
				new ComponentVersion("jakarta.annotation-api", IuTest.getProperty("jakarta.annotation-api.version")),
				dependencies.next());
		assertTrue(dependencies.hasNext());
		assertEquals(new ComponentVersion("jakarta.json-api", IuTest.getProperty("jakarta.json-api.version")),
				dependencies.next());
		assertFalse(dependencies.hasNext());
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
					new ComponentVersion("jakarta.json-api", IuTest.getProperty("jakarta.json-api.version"))),
					source.dependencies());
			assertTrue(source.sealed());
		}
	}

	@Test
	public void testReadsTestRuntime() throws IOException {
		var testcomponent = TestArchives.getComponentArchive("testruntime");
		try (var source = new ArchiveSource(testcomponent)) {
			assertEquals(
					List.of("META-INF/lib/jakarta.interceptor-api-"
							+ IuTest.getProperty("jakarta.interceptor-api.version") + ".jar",
							"META-INF/lib/jakarta.annotation-api-"
									+ IuTest.getProperty("jakarta.annotation-api.version") + ".jar",
							"META-INF/lib/jakarta.json-api-" + IuTest.getProperty("jakarta.json-api.version") + ".jar"),
					source.classPath());
			assertEquals(List.of(
					new ComponentVersion("jakarta.interceptor-api",
							IuTest.getProperty("jakarta.interceptor-api.version")),
					new ComponentVersion("jakarta.annotation-api",
							IuTest.getProperty("jakarta.annotation-api.version")),
					new ComponentVersion("jakarta.json-api", IuTest.getProperty("jakarta.json-api.version"))),
					source.dependencies());
			assertTrue(source.sealed());
		}
	}

}
