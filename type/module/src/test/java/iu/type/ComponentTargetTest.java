package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ComponentTargetTest {

	@BeforeAll
	public static void setupClass() throws ClassNotFoundException {
		// prevents mockStatic(Files.class) from interfering with class loader
		Class.forName(ComponentTarget.class.getName());
	}

	@Test
	public void testConstructorHandlesIOException() throws IOException {
		var ioException = new IOException();
		var path = mock(Path.class);
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.newOutputStream(path)).thenThrow(ioException);
			assertSame(ioException,
					assertThrows(IllegalStateException.class, () -> new ComponentTarget(path)).getCause());
		}
	}

	@Test
	public void testItWorks() throws IOException {
		var path = TemporaryFile.init(temp -> {
			try (var target = new ComponentTarget(temp)) {
				target.put("foo", new ByteArrayInputStream("bar".getBytes()));
			}
			return temp;
		});
		try ( //
				var in = Files.newInputStream(path); //
				var jar = new JarInputStream(in)) {
			assertNotNull(jar.getNextEntry());
			assertEquals("bar", new String(jar.readAllBytes()));
			jar.closeEntry();
			assertNull(jar.getNextEntry());
		}
		Files.delete(path);
	}

	@Test
	public void testHandlesIOException() throws IOException {
		var ioException = new IOException();
		var path = mock(Path.class);
		var out = mock(OutputStream.class);
		doThrow(ioException).when(out).write(any(byte[].class), any(int.class), any(int.class));
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.newOutputStream(path)).thenReturn(out);
			var target = new ComponentTarget(path);
			assertSame(ioException, assertThrows(IllegalStateException.class,
					() -> target.put("foo", new ByteArrayInputStream("bar".getBytes()))).getCause());
			assertSame(ioException, assertThrows(IllegalStateException.class,
					() -> target.close()).getCause());
		}
	}

	@Test
	public void testCloseHandlesIOException() throws IOException {
		var ioException = new IOException();
		var path = mock(Path.class);
		var out = mock(OutputStream.class);
		doThrow(ioException).when(out).close();
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.newOutputStream(path)).thenReturn(out);
			var target = new ComponentTarget(path);
			assertSame(ioException, assertThrows(IllegalStateException.class, () -> target.close()).getCause());
		}
	}

}
