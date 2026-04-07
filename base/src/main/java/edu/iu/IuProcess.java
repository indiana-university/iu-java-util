package edu.iu;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple utility for interacting with the JVM process shell environment.
 */
public final class IuProcess {

	private static final Logger LOG = Logger.getLogger(IuProcess.class.getName());

	private static final Queue<Path> TEMP_FILES = new ConcurrentLinkedQueue<>();

	/**
	 * Reads text from {@link System#in}.
	 * 
	 * @return UTF-8 text read from {@link System#in}
	 */
	public static String read() {
		return IuException.unchecked(() -> IuUtilityTaskController.getBefore( //
				() -> IuText.utf8(IuStream.read(System.in)), Instant.now().plusSeconds(15L)));
	}

	/**
	 * Creates a temporary file, and queues for cleanup via
	 * {@link #deleteTempFiles()}.
	 * 
	 * @return temp file
	 */
	public static Path createTempFile() {
		final var temp = IuException.unchecked(() -> Files.createTempFile("iu-java-base-", ""));
		TEMP_FILES.offer(temp);
		return temp;
	}

	/**
	 * Creates a temporary directory, and queues for cleanup via
	 * {@link #deleteTempFiles()}.
	 * 
	 * @return temp file
	 */
	public static Path createTempDirectory() {
		final var temp = IuException.unchecked(() -> Files.createTempDirectory("iu-java-base-"));
		TEMP_FILES.offer(temp);
		return temp;
	}

	/**
	 * Deletes all temporary files and directories created .
	 */
	public static void deleteTempFiles() {
		Throwable error = null;
		while (!TEMP_FILES.isEmpty()) {
			final var path = TEMP_FILES.poll();
			if (Files.isDirectory(path))
				try (final var list = Files.list(path)) {
					for (final var file : IuIterable.of(list::iterator))
						error = IuException.suppress(error, () -> Files.delete(file));
				} catch (IOException e) {
					error = IuException.suppress(error, e);
				}

			error = IuException.suppress(error, () -> Files.deleteIfExists(path));
		}

		if (error != null)
			LOG.log(Level.WARNING, error, () -> "Failed to delete all temporary files");
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
	public static <T> Path temp(BiConsumer<PrintStream, T> printHandle, T value) {
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
	 * Executes a command.
	 *
	 * @param cmd command followed by arguments
	 * @return output if successful
	 */
	public static String exec(String... cmd) {
		return pipe(null, cmd);
	}

	/**
	 * Executes and pipes input into a command.
	 *
	 * @param input input data to feed to the command; null for no input
	 * @param cmd   command followed by arguments
	 * @return output if successful
	 */
	public static String pipe(byte[] input, String... cmd) {
		final var statusProcess = IuException.unchecked(() -> new ProcessBuilder(cmd).start());

		final var ttlSeconds = Optional
				.ofNullable(IuRuntimeEnvironment.envOptional("iu.util.processTtl", Integer::parseInt)).orElse(120);
		final var exp = Instant.now().plusSeconds(ttlSeconds);
		final var out = new StringBuffer();
		final var err = new StringBuffer();

		final IuUtilityTaskController<?> writeIn;
		if (input == null)
			writeIn = null;
		else
			writeIn = new IuUtilityTaskController<>(() -> {
				try (final var in = statusProcess.getOutputStream()) {
					in.write(input);
				}
				return null;
			}, exp);

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
			if (writeIn != null)
				writeIn.get();
			readOut.get();
			readErr.get();
			return checkStatusCode.get();
		});

		if (code == 0) {
			LOG.fine(() -> "exec " + String.join(" ", cmd) + System.lineSeparator() + err);
			return out.toString();
		} else {
			throw new IllegalStateException("exec " + String.join(" ", cmd) + System.lineSeparator() + "status: " + code
					+ System.lineSeparator() + err);
		}
	}

	private IuProcess() {
	}

}
