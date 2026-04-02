package edu.iu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuProcessTest {

	static final ByteArrayOutputStream OUT = new ByteArrayOutputStream();
	static final ByteArrayOutputStream ERR = new ByteArrayOutputStream();

	private static volatile int inPos;
	private static volatile byte[] in;

	static {
		System.setIn(new InputStream() {
			@Override
			public int read() throws IOException {
				synchronized (IuProcessTest.class) {
					while (inPos == -1)
						IuException.unchecked(() -> IuProcessTest.class.wait(250L));
				}
				if (in == null || inPos >= in.length)
					return -1;
				else
					return in[inPos++];
			}
		});
		System.setProperty("iu.crypt.pki.org", "/C=US/ST=Indiana/L=Bloomington/O=Indiana University/OU=Unit Testing");
	}

	static synchronized void input(String text) {
		in = IuText.utf8(text);
		inPos = 0;
		IuProcessTest.class.notifyAll();
	}

	void reset() {
		OUT.reset();
		ERR.reset();
		System.setOut(new PrintStream(OUT));
		System.setErr(new PrintStream(ERR));
		inPos = 0;
		in = null;
		synchronized (IuProcessTest.class) {
			IuProcessTest.class.notifyAll();
		}
	}

	@BeforeEach
	public void setup() {
		reset();
		assertEquals("", ERR.toString());
	}

	@Test
	public void testEcho() {
		IuException.unchecked(() -> Class.forName(IuProcess.class.getName()));
		final var log = LogManager.getLogManager().getLogger(IuProcess.class.getName());
		log.setLevel(Level.FINE);
		final var h = new Handler() {
			final Queue<LogRecord> records = new ArrayDeque<>();

			@Override
			public void publish(LogRecord record) {
				records.add(record);
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() throws SecurityException {
			}
		};
		log.addHandler(h);
		assertEquals("hello\n", IuProcess.exec("echo", "hello"));
		log.removeHandler(h);
		assertFalse(h.records.isEmpty());
		assertEquals("exec echo hello" + System.lineSeparator() + "hello\n" + "", h.records.poll().getMessage());
	}

	@Test
	public void testLsMissingFile() {
		final var name = "missing_file_" + IdGenerator.generateId();

		final var error = assertThrows(IllegalStateException.class, () -> IuProcess.exec("ls", name));
		assertEquals("exec ls " + name + System.lineSeparator() + "status: 2" + System.lineSeparator()
				+ "ls: cannot access '" + name + "': No such file or directory\n", error.getMessage());
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
			assertTrue(ERR.toString().contains("WARNING: Failed to delete all temporary files" + System.lineSeparator()
					+ "java.lang.RuntimeException" + System.lineSeparator()), ERR::toString);
		}
	}

	@Test
	public void testTempErrorsList() throws IOException {
		IuProcess.createTempDirectory();
		try (final var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockFiles.when(() -> Files.list(any())).thenThrow(IOException.class);
			assertDoesNotThrow(() -> IuProcess.deleteTempFiles());
			assertTrue(ERR.toString().contains("WARNING: Failed to delete all temporary files" + System.lineSeparator()
					+ "java.io.IOException" + System.lineSeparator()), ERR::toString);
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
		assertEquals("", ERR.toString());

		assertFalse(Files.exists(temp));
		assertFalse(Files.exists(tempDir));
	}

	@Test
	public void testTemp() throws IOException {
		final var msg = IdGenerator.generateId();
		final var temp = IuProcess.temp((ps, a) -> IuException.unchecked(() -> ps.write(msg.getBytes())), msg);
		assertEquals(msg, Files.readString(temp));
		IuProcess.deleteTempFiles();
		assertEquals("", ERR.toString());
		assertFalse(Files.exists(temp));
	}

	@Test
	public void testRead() {
		final var msg = IdGenerator.generateId();
		input(msg);
		assertEquals(msg, IuProcess.read());
	}

}
