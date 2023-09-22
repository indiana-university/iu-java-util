package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.jar.JarEntry;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ComponentEntryTest {

	@Test
	public void testWrapsName() {
		var jarEntry = new JarEntry("entry name");
		try (var componentJarEntry = new ComponentEntry(jarEntry, null)) {
			assertEquals("entry name", componentJarEntry.name());
		}
	}

	@Test
	public void testReadsData() throws IOException {
		var data = new byte[0];
		var input = mock(ComponentEntry.Input.class);
		when(input.read()).thenReturn(data).thenThrow(IOException.class);
		try (var componentJarEntry = new ComponentEntry(null, input)) {
			assertSame(data, componentJarEntry.data());
			assertSame(data, componentJarEntry.data());
			verify(input, times(1)).read();
		}
	}

	@Test
	public void testHandlesIOException() {
		var ioException = new IOException();
		try (var componentJarEntry = new ComponentEntry(null, () -> {
			throw ioException;
		})) {
			assertSame(ioException, assertThrows(IllegalStateException.class, componentJarEntry::data).getCause());
		}
	}
	
	@Test
	public void testClose() {
		var componentJarEntry = new ComponentEntry(null, null);
		componentJarEntry.close();
		assertEquals("closed", assertThrows(IllegalStateException.class, componentJarEntry::name).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, componentJarEntry::data).getMessage());
	}
}
