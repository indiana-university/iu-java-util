package iu.type;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import iu.type.TemporaryFile.TempFileInitializer;

@SuppressWarnings("javadoc")
public class TemporaryFileTest {

	@BeforeAll
	private static void setupClass() throws ClassNotFoundException {
		Class.forName(TemporaryFile.class.getName());
	}

	@Test
	public void testCantCreateTempFile() {
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenThrow(IOException.class);
			assertThrows(IOException.class, () -> TemporaryFile.init(null));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testThrowsDeletesOnError() throws IOException {
		var temp = mock(Path.class);
		var initializer = mock(TempFileInitializer.class);
		when(initializer.initialize(temp)).thenThrow(IOException.class);
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
			assertThrows(IOException.class, () -> TemporaryFile.init(initializer));
			mockFiles.verify(() -> Files.deleteIfExists(temp));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSuppressesDeleteError() throws IOException {
		var temp = mock(Path.class);
		var deleteError = new IOException();
		var initializer = mock(TempFileInitializer.class);
		when(initializer.initialize(temp)).thenThrow(new IOException());
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
			mockFiles.when(() -> Files.deleteIfExists(temp)).thenThrow(deleteError);
			assertSame(deleteError,
					assertThrows(IOException.class, () -> TemporaryFile.init(initializer)).getSuppressed()[0]);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testWorks() throws IOException {
		var temp = mock(Path.class);
		var initializer = mock(TempFileInitializer.class);
		when(initializer.initialize(temp)).thenReturn(temp);
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
			assertSame(temp, TemporaryFile.init(initializer));
			verify(initializer).initialize(temp);
		}
	}

}
