package edu.iu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuProcessTest {

	final Queue<LogRecord> logRecords = new ArrayDeque<>();
	final Handler logHandler = new Handler() {
		@Override
		public void publish(LogRecord record) {
			logRecords.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws SecurityException {
		}
	};

	@BeforeEach
	void setup() {
		logRecords.clear();

		IuException.unchecked(() -> Class.forName(IuProcess.class.getName()));
		final var log = LogManager.getLogManager().getLogger(IuProcess.class.getName());
		log.setLevel(Level.FINE);
		log.setUseParentHandlers(false);
		log.addHandler(logHandler);
	}

	@AfterEach
	void tearDown() {
		final var log = LogManager.getLogManager().getLogger(IuProcess.class.getName());
		log.setLevel(Level.INFO);
		log.setUseParentHandlers(true);
		log.removeHandler(logHandler);
	}

	@Test
	public void testEcho() {
		assertEquals("hello\n", IuProcess.exec("echo", "hello"));
		assertFalse(logRecords.isEmpty());
		final var record = logRecords.poll();
		assertEquals(Level.FINE, record.getLevel());
		assertEquals("exec echo hello" + System.lineSeparator() + "hello\n", record.getMessage());
	}

	@Test
	public void testCat() {
		assertEquals("hello\n", IuProcess.pipe("hello\n".getBytes(), "cat"));
		assertFalse(logRecords.isEmpty());
		final var record = logRecords.poll();
		assertEquals(Level.FINE, record.getLevel());
		assertEquals("exec cat" + System.lineSeparator() + "hello\n", record.getMessage());
	}

	@Test
	public void testLsMissingFile() {
		final var name = "missing_file_" + IdGenerator.generateId();

		final var error = assertThrows(IllegalStateException.class, () -> IuProcess.exec("ls", name));
		assertTrue(error.getMessage().contains("No such file or directory"), error::getMessage);
	}

	@Test
	public void testTempErrorsFile() throws IOException {
		final var msg = IdGenerator.generateId();
		final var msg2 = IdGenerator.generateId();
		final var temp = IuProcess.createTempFile();
		final var tempDir = IuProcess.createTempDirectory();
		Files.write(tempDir.resolve("msg2"), msg2.getBytes());
		Files.write(temp, msg.getBytes());
		assertEquals(msg, Files.readString(temp));
		assertEquals(msg2, Files.readString(tempDir.resolve("msg2")));

		try (final var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockFiles.when(() -> Files.delete(any())).thenThrow(RuntimeException.class);
			mockFiles.when(() -> Files.deleteIfExists(any())).thenThrow(RuntimeException.class);
			assertDoesNotThrow(() -> IuProcess.deleteTempFiles());
			assertFalse(logRecords.isEmpty());
			final var record = logRecords.poll();
			assertEquals(Level.WARNING, record.getLevel());
			assertEquals("Failed to delete all temporary files", record.getMessage());
			assertInstanceOf(RuntimeException.class, record.getThrown());
		}
	}

	@Test
	public void testTempErrorsList() throws IOException {
		IuProcess.createTempDirectory();
		try (final var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockFiles.when(() -> Files.list(any())).thenThrow(IOException.class);
			assertDoesNotThrow(() -> IuProcess.deleteTempFiles());
			assertFalse(logRecords.isEmpty());
			final var record = logRecords.poll();
			assertEquals(Level.WARNING, record.getLevel());
			assertEquals("Failed to delete all temporary files", record.getMessage());
			assertInstanceOf(IOException.class, record.getThrown());
		}
	}

	@Test
	public void testTempFile() throws IOException {
		final var msg = IdGenerator.generateId();
		final var msg2 = IdGenerator.generateId();
		final var temp = IuProcess.createTempFile();
		final var tempDir = IuProcess.createTempDirectory();
		Files.write(tempDir.resolve("msg2"), msg2.getBytes());
		Files.write(temp, msg.getBytes());
		assertEquals(msg, Files.readString(temp));
		assertEquals(msg2, Files.readString(tempDir.resolve("msg2")));

		IuProcess.deleteTempFiles();

		assertFalse(Files.exists(temp));
		assertFalse(Files.exists(tempDir));
	}

	@Test
	public void testTemp() throws IOException {
		final var msg = IdGenerator.generateId();
		final var temp = IuProcess.temp((ps, a) -> IuException.unchecked(() -> ps.write(msg.getBytes())), msg);
		assertEquals(msg, Files.readString(temp));
		IuProcess.deleteTempFiles();
		assertFalse(Files.exists(temp));
	}

	@Test
	public void testRead() {
		final var msg = IdGenerator.generateId();
		System.setIn(new ByteArrayInputStream(msg.getBytes()));
		assertEquals(msg, IuProcess.read());
	}

}
