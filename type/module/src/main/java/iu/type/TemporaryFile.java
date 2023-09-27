package iu.type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TemporaryFile {

	@FunctionalInterface
	static interface TempFileInitializer<T> {
		T initialize(Path temp) throws IOException ;
	}

	static <T> T init(TempFileInitializer<T> tempFileInitializer) throws IOException {
		Path temp = Files.createTempFile("iu-type-", ".jar");

		try {
			return tempFileInitializer.initialize(temp);
		} catch (IOException | RuntimeException | Error e) {
			try {
				Files.deleteIfExists(temp);
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
	}

	private TemporaryFile() {
	}

}
