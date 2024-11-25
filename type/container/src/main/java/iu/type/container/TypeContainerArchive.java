package iu.type.container;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.UnsafeRunnable;
import edu.iu.type.base.TemporaryFile;

/**
 * Parses a type container into shared API, primary component archive, module
 * dependencies, and private support libraries.
 */
class TypeContainerArchive implements AutoCloseable {

	private final Path primary;
	private final Path[] api;
	private final Path[] lib;
	private final Path[] support;
	private final Path[] embedded;

	private final UnsafeRunnable onClose;

	/**
	 * Constructor.
	 * 
	 * @param path {@link Path}
	 * @throws IOException If an error occurs reading the component source bundle
	 */
	TypeContainerArchive(Path path) throws IOException {
		final var init = new UnsafeRunnable() {
			Path primary = null;
			final Queue<Path> api = new ArrayDeque<>();
			final Queue<Path> lib = new ArrayDeque<>();
			final Queue<Path> support = new ArrayDeque<>();
			final Queue<Path> embedded = new ArrayDeque<>();

			@Override
			public void run() throws Throwable {
				try (final var in = Files.newInputStream(path); //
						final var componentJar = new JarInputStream(in)) {
					JarEntry entry;
					while ((entry = componentJar.getNextJarEntry()) != null) {
						final var name = entry.getName();
						if (name.endsWith("/"))
							continue;

						if (name.endsWith(".jar"))
							if (name.startsWith("api/"))
								api.offer(TemporaryFile.of(componentJar));
							else if (name.startsWith("lib/"))
								lib.offer(TemporaryFile.of(componentJar));
							else if (name.startsWith("support/"))
								support.offer(TemporaryFile.of(componentJar));
							else if (name.indexOf('/') != -1)
								embedded.offer(TemporaryFile.of(componentJar));
							else
								primary = IuObject.once(primary, TemporaryFile.of(componentJar));
						else if (name.endsWith(".war"))
							embedded.offer(TemporaryFile.of(componentJar));
						else
							throw new IllegalArgumentException("Unexpected entry " + name);
					}
				}
			}

		};
		onClose = TemporaryFile.init(init);

		this.primary = Objects.requireNonNull(init.primary);
		this.api = init.api.toArray(Path[]::new);
		this.lib = init.lib.toArray(Path[]::new);
		this.support = init.support.toArray(Path[]::new);
		this.embedded = init.embedded.toArray(Path[]::new);
	}

	/**
	 * Returns the primary component archive.
	 * 
	 * @return {@link Path}
	 */
	Path primary() {
		return primary;
	}

	/**
	 * Returns the shared API.
	 * 
	 * @return {@link Path}[]
	 */
	Path[] api() {
		return api;
	}

	/**
	 * Returns the module dependencies.
	 * 
	 * @return {@link Path}[]
	 */
	Path[] lib() {
		return lib;
	}

	/**
	 * Returns the support libraries.
	 * 
	 * @return {@link Path}[]
	 */
	Path[] support() {
		return support;
	}

	/**
	 * Returns the embedded components.
	 * 
	 * @return {@link Path}[]
	 */
	Path[] embedded() {
		return embedded;
	}

	@Override
	public void close() throws Exception {
		IuException.checked(onClose);
	}

}
