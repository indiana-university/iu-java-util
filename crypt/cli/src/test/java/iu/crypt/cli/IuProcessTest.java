package iu.crypt.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
@ExtendWith(CryptCliTestSupport.class)
public class IuProcessTest {

	@Test
	public void testEcho() {
		IuTestLogger.expect(IuProcess.class.getName(), Level.FINE, "exec echo hello\nhello\n");
		assertEquals("hello\n", IuProcess.exec("echo", "hello"));
	}

	@Test
	public void testLsMissingFile() {
		final var name = "missing_file_" + IdGenerator.generateId();

		IuTestLogger.expect(IuProcess.class.getName(), Level.INFO, "exec ls " + name //
				+ "\nstatus: 2\n" //
				+ "ls: cannot access '" + name + "': No such file or directory\n", IllegalStateException.class);

		final var error = assertThrows(IllegalStateException.class, () -> IuProcess.exec("ls", name));
		assertEquals("exec ls " + name + "\nstatus: 2", error.getMessage());
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
			assertEquals(
					"java.lang.RuntimeException" + System.lineSeparator() + "java.lang.RuntimeException"
							+ System.lineSeparator() + "java.lang.RuntimeException" + System.lineSeparator(),
					CryptCliTestSupport.ERR.toString());
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
		assertEquals("", CryptCliTestSupport.ERR.toString());

		assertFalse(Files.exists(temp));
		assertFalse(Files.exists(tempDir));
	}

	@Test
	public void testTemp() throws IOException {
		final var msg = IdGenerator.generateId();
		final var temp = IuProcess.temp((ps, a) -> IuException.unchecked(() -> ps.write(msg.getBytes())), msg);
		assertEquals(msg, Files.readString(temp));
		IuProcess.deleteTempFiles();
		assertEquals("", CryptCliTestSupport.ERR.toString());
		assertFalse(Files.exists(temp));
	}

	@Test
	public void testPemPrivateKey() {
		final var encoded = IdGenerator.generateId().getBytes();
		final var key = mock(PrivateKey.class);
		when(key.getEncoded()).thenReturn(encoded);
		try (final var out = new PrintStream(CryptCliTestSupport.OUT)) {
			IuProcess.pem(out, key);
		}
		assertEquals(
				"-----BEGIN PRIVATE KEY-----" + System.lineSeparator() + IuText.base64(encoded) + System.lineSeparator()
						+ "-----END PRIVATE KEY-----" + System.lineSeparator() + "",
				CryptCliTestSupport.OUT.toString());
	}

	@Test
	public void testPemCert() throws CertificateEncodingException {
		final var encoded = new byte[80];
		ThreadLocalRandom.current().nextBytes(encoded);
		final var key = mock(X509Certificate.class);
		when(key.getEncoded()).thenReturn(encoded);
		try (final var out = new PrintStream(CryptCliTestSupport.OUT)) {
			IuProcess.pem(out, key);
		}
		assertEquals(
				"-----BEGIN CERTIFICATE-----" + System.lineSeparator() + IuText.base64(encoded).substring(0, 65)
						+ System.lineSeparator() + IuText.base64(encoded).substring(65) + System.lineSeparator()
						+ "-----END CERTIFICATE-----" + System.lineSeparator() + "",
				CryptCliTestSupport.OUT.toString());
	}

	@Test
	public void testPemCRL() throws CRLException {
		final var encoded = IdGenerator.generateId().getBytes();
		final var key = mock(X509CRL.class);
		when(key.getEncoded()).thenReturn(encoded);
		try (final var out = new PrintStream(CryptCliTestSupport.OUT)) {
			IuProcess.pem(out, key);
		}
		assertEquals("-----BEGIN X509 CRL-----" + System.lineSeparator() + IuText.base64(encoded)
				+ System.lineSeparator() + "-----END X509 CRL-----" + System.lineSeparator() + "",
				CryptCliTestSupport.OUT.toString());
	}

	@Test
	public void testRead() {
		final var msg = IdGenerator.generateId();
		CryptCliTestSupport.input(msg);
		assertEquals(msg, IuProcess.read());
	}

	@Test
	public void testPassphrase() {
		final var msg = IdGenerator.generateId();
		CryptCliTestSupport.input(msg);
		assertEquals(msg, IuText.utf8(IuProcess.passphrase().getKey()));
	}

	@Test
	public void testPassphraseTooShort() {
		CryptCliTestSupport.input("foo");
		assertThrows(IllegalArgumentException.class, IuProcess::passphrase);
	}

}
