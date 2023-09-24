package iu.type;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ComponentEntryTest {

	@Test
	public void testWrapsName() {
		try (var componentEntry = new ComponentEntry("entry name", null)) {
			assertEquals("entry name", componentEntry.name());
		}
	}

	@Test
	public void testReadsData() throws IOException {
		var data = new byte[32768];
		ThreadLocalRandom.current().nextBytes(data);
		try (var componentEntry = new ComponentEntry(null, new ByteArrayInputStream(data))) {
			assertArrayEquals(data, componentEntry.data());
			assertArrayEquals(data, componentEntry.data());
		}
	}

	@Test
	public void testOnlyReadsOnce() throws IOException {
		var in = mock(InputStream.class);
		var inputStreamConsumer = mock(ComponentEntry.InputStreamConsumer.class);
		try (var componentEntry = new ComponentEntry(null, in)) {
			componentEntry.read(inputStreamConsumer);
			assertEquals("already read", assertThrows(IllegalStateException.class, () -> componentEntry.read(inputStreamConsumer)).getMessage());
			assertEquals("already read", assertThrows(IllegalStateException.class, componentEntry::data).getMessage());
		}
		verify(inputStreamConsumer, times(1)).accept(in);
	}

	@Test
	public void testHandlesIOException() throws IOException {
		var ioException = new IOException();
		try (var componentEntry = new ComponentEntry(null, new InputStream() {
			@Override
			public int read() throws IOException {
				throw ioException;
			}
		})) {
			assertSame(ioException, assertThrows(IllegalStateException.class, componentEntry::data).getCause());
		}
	}

	@Test
	public void testClose() {
		var componentEntry = new ComponentEntry(null, null);
		componentEntry.close();
		assertEquals("closed", assertThrows(IllegalStateException.class, componentEntry::name).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> componentEntry.read(null)).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, componentEntry::data).getMessage());
	}
}
