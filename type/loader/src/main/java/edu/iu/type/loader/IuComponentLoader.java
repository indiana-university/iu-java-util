package edu.iu.type.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuStream;
import edu.iu.UnsafeRunnable;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;

/**
 * Loads components via IU type introspection into an isolated modular
 * environment, without corrupting the application class path.
 */
public class IuComponentLoader implements AutoCloseable {

	private static final URL[] TYPE_BUNDLE_MODULE_PATH = IuException.unchecked(() -> {
		final var loader = IuComponentLoader.class.getClassLoader();
		return new URL[] { //
				Objects.requireNonNull(loader.getResource("iu-java-type.jar")), //
				Objects.requireNonNull(loader.getResource("iu-java-base.jar")), //
				Objects.requireNonNull(loader.getResource("iu-java-type-api.jar")), //
				Objects.requireNonNull(loader.getResource("iu-java-type-base.jar")), //
		};
	});

	private final UnsafeRunnable destroy;
	private final ModularClassLoader typeBundleLoader;
	private final AutoCloseable component;
	private final ClassLoader loader;

	/**
	 * Validates a <strong>component archive</strong>, all <strong>dependency
	 * archives</strong>, loads a <strong>component</strong>, and returns a
	 * {@link ClassLoader} for accessing classes managed by the component.
	 * 
	 * @param componentArchiveSource           {@link InputStream} for reading the
	 *                                         <strong>component archive</strong>.
	 * @param providedDependencyArchiveSources {@link InputStream}s for reading all
	 *                                         <strong>provided dependency
	 *                                         archives</strong>.
	 * @throws IOException If an IO error occurs initializing the component
	 */
	public IuComponentLoader(InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources)
			throws IOException {
		class Box {
			ModularClassLoader typeBundleLoader;
			AutoCloseable component;
			ClassLoader loader;
		}
		final var box = new Box();

		destroy = TemporaryFile.init(() -> {
			final var typeBundleJars = new Path[TYPE_BUNDLE_MODULE_PATH.length];

			for (var i = 0; i < TYPE_BUNDLE_MODULE_PATH.length; i++) {
				final var connection = TYPE_BUNDLE_MODULE_PATH[i].openConnection();
				connection.setUseCaches(false);
				try (final var in = connection.getInputStream()) {
					typeBundleJars[i] = TemporaryFile.init(path -> {
						try (final var out = Files.newOutputStream(path)) {
							IuStream.copy(in, out);
						}
						return path;
					});
				}
			}

			box.typeBundleLoader = IuException.checked(IOException.class,
					() -> IuException.initialize(new ModularClassLoader(false, IuIterable.iter(typeBundleJars), null),
							typeBundleLoader -> {
								final var iuComponent = typeBundleLoader.loadClass("edu.iu.type.IuComponent");
								final var of = iuComponent.getMethod("of", InputStream.class, InputStream[].class);
								final var classLoader = iuComponent.getMethod("classLoader");

								return IuException.checkedInvocation(() -> {
									box.component = (AutoCloseable) of.invoke(null, componentArchiveSource,
											providedDependencyArchiveSources);
									box.loader = (ClassLoader) classLoader.invoke(box.component);
									return typeBundleLoader;
								});
							}));
		});

		typeBundleLoader = box.typeBundleLoader;
		component = box.component;
		loader = box.loader;
	}

	/**
	 * Gets the component's {@link ClassLoader}.
	 * 
	 * @return {@link ClassLoader}
	 */
	public ClassLoader getLoader() {
		return loader;
	}

	@Override
	public void close() throws IOException {
		var closeError = IuException.suppress(null, component::close);
		closeError = IuException.suppress(closeError, typeBundleLoader::close);
		closeError = IuException.suppress(closeError, destroy::run);
		if (closeError != null)
			throw IuException.checked(closeError, IOException.class);
	}

}
