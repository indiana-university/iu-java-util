package iu.type;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class ComponentSourceTest {

	private InputStream createJar(Map<String, byte[]> source) throws IOException {
		var out = new ByteArrayOutputStream();
		try (JarOutputStream jar = new JarOutputStream(out)) {
			var manifest = new Manifest();
			jar.putNextEntry(new JarEntry("META-INF/"));
			jar.closeEntry();

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
		return new ByteArrayInputStream(out.toByteArray());
	}

	@Test
	public void testOpensAndClosesJar() throws IOException {
		var in = mock(InputStream.class);
		try (var mockJarInputStream = mockConstruction(JarInputStream.class, (jar, context) -> {
			assertSame(in, context.arguments().get(0));
			when(jar.getManifest()).thenReturn(new Manifest());
		})) {
			new ComponentSource(in).close();
			assertEquals(1, mockJarInputStream.constructed().size());
			verify(mockJarInputStream.constructed().get(0)).close();
			verify(in).close();
		}
	}

	@Test
	public void testConstructorHandlesIOException() throws IOException {
		var ioException = new IOException();
		var in = new InputStream() {
			@Override
			public int read() throws IOException {
				throw ioException;
			}
		};
		assertSame(ioException, assertThrows(IllegalStateException.class, () -> new ComponentSource(in)).getCause());
	}

	@Test
	public void testCloseHandlesIOException() throws IOException {
		var ioException = new IOException();
		var emptyJar = createJar(Map.of());
		var in = new InputStream() {
			@Override
			public int read() throws IOException {
				return emptyJar.read();
			}

			@Override
			public void close() throws IOException {
				throw ioException;
			}
		};
		assertSame(ioException,
				assertThrows(IllegalStateException.class, () -> new ComponentSource(in).close()).getCause());
	}

	@Test
	public void testReturnsData() throws IOException {
		byte[] data = new byte[] { -1 };
		try (var source = new ComponentSource(createJar(Map.of("", data)))) {
			var next = source.next();
			var nextData = next.data();
			assertArrayEquals(data, nextData);
			assertSame(nextData, next.data());
		}
	}

	@Test
	public void testHandlesReadFailure() throws IOException {
		var ioException = new IOException();
		var jar = createJar(Map.of("", new byte[0]));
		try ( //
				var mockInput = mockConstruction(JarInputStream.class, (jarInputStream, context) -> {
					assertEquals(List.of(jar), context.arguments());
					when(jarInputStream.getManifest()).thenReturn(new Manifest());
					when(jarInputStream.getNextJarEntry()).thenThrow(ioException);
				}); //
				var source = new ComponentSource(jar)) {
			assertSame(ioException, assertThrows(IllegalStateException.class, () -> source.hasNext()).getCause());
		}
	}

	@Test
	public void testIterates() throws IOException {
		byte[] data = new byte[] { -1 };
		try (var source = new ComponentSource(createJar(Map.of("", data)))) {
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
		var source = new ComponentSource(createJar(map));
		assertTrue(source.hasNext());
		assertEquals("anEntry", source.next().name());
		assertTrue(source.hasNext());
		source.close();
		assertFalse(source.hasNext());
		assertThrows(NoSuchElementException.class, source::next);
	}

	@Test
	public void testNameAndVersion() throws Throwable {
		ComponentSource empty = new ComponentSource(createJar(Map.of()));
		assertNull(empty.name());
		assertNull(empty.version());
		
		ComponentSource source = new ComponentSource(
				Files.newInputStream(Path.of(IuTest.getProperty("testruntime.jar"))));
		assertEquals("iu-java-type-testruntime", source.name());
		assertEquals(IuTest.getProperty("project.version"), source.version());
	}

	@Test
	public void testClassPath() throws Throwable {
		ComponentSource empty = new ComponentSource(createJar(Map.of()));
		assertFalse(empty.classPath().iterator().hasNext());

		ComponentSource source = new ComponentSource(
				Files.newInputStream(Path.of(IuTest.getProperty("testruntime.jar"))));
		assertEquals(List.of(
				"META-INF/lib/jakarta.interceptor-api-" + IuTest.getProperty("jakarta.interceptor-api.version")
						+ ".jar",
				"META-INF/lib/jakarta.annotation-api-" + IuTest.getProperty("jakarta.annotation-api.version") + ".jar",
				"META-INF/lib/jakarta.json-api-" + IuTest.getProperty("jakarta.json-api.version") + ".jar"),
				source.classPath());
	}

	@Test
	public void testDependencies() throws Throwable {
		ComponentSource empty = new ComponentSource(createJar(Map.of()));
		assertFalse(empty.dependencies().iterator().hasNext());

		ComponentSource source = new ComponentSource(
				Files.newInputStream(Path.of(IuTest.getProperty("testruntime.jar"))));
		var dependencies = source.dependencies().iterator();
		assertTrue(dependencies.hasNext());
		assertEquals(new ComponentDependency("jakarta.interceptor-api",
				IuTest.getProperty("jakarta.interceptor-api.version")), dependencies.next());
		assertTrue(dependencies.hasNext());
		assertEquals(
				new ComponentDependency("jakarta.annotation-api", IuTest.getProperty("jakarta.annotation-api.version")),
				dependencies.next());
		assertTrue(dependencies.hasNext());
		assertEquals(new ComponentDependency("jakarta.json-api", IuTest.getProperty("jakarta.json-api.version")),
				dependencies.next());
		assertFalse(dependencies.hasNext());
	}

}
