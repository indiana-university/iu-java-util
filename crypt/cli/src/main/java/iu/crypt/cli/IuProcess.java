package iu.crypt.cli;

import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.IuUtilityTaskController;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Type;

/**
 * Simple utility for executing commands using the JVM process shell
 * environment.
 */
final class IuProcess {

	private static final Logger LOG = Logger.getLogger(IuProcess.class.getName());

	private static final Queue<Path> TEMP_FILES = new ConcurrentLinkedQueue<>();

	/**
	 * Reads text from {@link System#in}.
	 * 
	 * @return UTF-8 text read from {@link System#in}
	 */
	static String read() {
		return IuException.unchecked(() -> IuUtilityTaskController.getBefore( //
				() -> IuText.utf8(IuStream.read(System.in)), Instant.now().plusSeconds(15L)));
	}

	/**
	 * Read a passphrase from {@link System#in}.
	 * 
	 * @return {@link WebKey}
	 */
	static WebKey passphrase() {
		String passphraseInput = read().trim();
		if (passphraseInput.length() < 16)
			throw new IllegalArgumentException("Passphrase must be at least 16 characters");

		return WebKey //
				.builder(Type.RAW) //
				.key(IuText.utf8(passphraseInput)) //
				.build();
	}

	/**
	 * Creates a temporary file.
	 * 
	 * @return temp file
	 */
	static Path createTempFile() {
		final var temp = IuException.unchecked(() -> Files.createTempFile("iu-crypt-cli-", ""));
		TEMP_FILES.offer(temp);
		return temp;
	}

	/**
	 * Creates a temporary directory.
	 * 
	 * @return temp file
	 */
	static Path createTempDirectory() {
		final var temp = IuException.unchecked(() -> Files.createTempDirectory("iu-crypt-cli-"));
		TEMP_FILES.offer(temp);
		return temp;
	}

	/**
	 * Deletes all temporary files created so far.
	 */
	static void deleteTempFiles() {
		while (!TEMP_FILES.isEmpty())
			try {
				final var path = TEMP_FILES.poll();
				if (Files.isDirectory(path))
					try (final var list = Files.list(path)) {
						for (final var file : IuIterable.of(list::iterator))
							try {
								Files.delete(file);
							} catch (Throwable e) {
								e.printStackTrace();
							}
					}

				Files.deleteIfExists(path);
			} catch (Throwable e) {
				e.printStackTrace();
			}
	}

	/**
	 * Writes a value to a temp file
	 * 
	 * @param <T>         value type
	 * @param printHandle method handle for writing the content using
	 *                    {@link PrintStream}
	 * @param value       value
	 * @return {@link Path} to the temp file
	 */
	static <T> Path temp(BiConsumer<PrintStream, T> printHandle, T value) {
		final var path = createTempFile();
		IuException.unchecked(() -> {
			try (final var out = Files.newOutputStream(path); //
					final var printStream = new PrintStream(out)) {
				printHandle.accept(printStream, value);
			}
		});
		return path;
	}

	/**
	 * Prints encoded key data in PEM format, for interoperability with OpenSSL.
	 * 
	 * @param out     {@link PrintStream}, receives PEM data then flushes.
	 * @param header  PEM header string
	 * @param encoded encoded key data
	 */
	private static void pem(PrintStream out, String header, byte[] encoded) {
		out.println("-----BEGIN " + header + "-----");
		final var sb = new StringBuilder(IuText.base64(encoded));
		for (var pos = 0; pos < sb.length() - 1; pos += 65) {
			final var e = pos + 65;
			if (e < sb.length())
				out.println(sb.substring(pos, e));
			else
				out.println(sb.substring(pos));
		}
		out.println("-----END " + header + "-----");
		out.flush();
	}

	/**
	 * Prints a {@link PrivateKey} in PEM format.
	 * 
	 * @param out {@link PrintStream}
	 * @param key {@link PrivateKey}
	 * @see #pem(PrintStream, String, byte[])
	 */
	static void pem(PrintStream out, PrivateKey key) {
		pem(out, "PRIVATE KEY", key.getEncoded());
	}

	/**
	 * Prints a {@link X509Certificate} in PEM format.
	 * 
	 * @param out         {@link PrintStream}
	 * @param certificate {@link X509Certificate}
	 * @see #pem(PrintStream, String, byte[])
	 */
	static void pem(PrintStream out, X509Certificate certificate) {
		pem(out, "CERTIFICATE", IuException.unchecked(certificate::getEncoded));
	}

	/**
	 * Prints a {@link X509Certificate} in PEM format.
	 * 
	 * @param out {@link PrintStream}
	 * @param crl {@link X509CRL}
	 * @see #pem(PrintStream, String, byte[])
	 */
	static void pem(PrintStream out, X509CRL crl) {
		pem(out, "X509 CRL", IuException.unchecked(crl::getEncoded));
	}

	/**
	 * Executes a command.
	 * 
	 * @param cmd command followed by arguments
	 * @return output if successful
	 */
	static String exec(String... cmd) {
		final var statusProcess = IuException.unchecked(() -> new ProcessBuilder(cmd).start());

		final var exp = Instant.now().plusSeconds(15L);
		final var out = new StringBuffer();
		final var err = new StringBuffer();

		final var readOut = new IuUtilityTaskController<>(() -> {
			try (final var in = statusProcess.getInputStream(); //
					final var r = new InputStreamReader(in)) {
				int i;
				while ((i = r.read()) != -1) {
					char c = (char) i;
					out.append(c);
					err.append(c);
				}
			}
			return null;
		}, exp);

		final var readErr = new IuUtilityTaskController<>(() -> {
			try (final var in = statusProcess.getErrorStream(); //
					final var r = new InputStreamReader(in)) {
				int i;
				while ((i = r.read()) != -1) {
					char c = (char) i;
					err.append(c);
				}
			}
			return null;
		}, exp);

		final var checkStatusCode = new IuUtilityTaskController<>(() -> statusProcess.waitFor(), exp);
		final var code = IuException.unchecked(() -> {
			readOut.get();
			readErr.get();
			return checkStatusCode.get();
		});

		if (code == 0) {
			LOG.fine(() -> "exec " + String.join(" ", cmd) + "\n" + err);
			return out.toString();
		} else {
			final var message = "exec " + String.join(" ", cmd) + "\nstatus: " + code;
			final var e = new IllegalStateException(message);
			LOG.log(Level.INFO, e, () -> message + "\n" + err);
			throw e;
		}
	}

	private IuProcess() {
	}

}
