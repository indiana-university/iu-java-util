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

	private final Path[] typeBundleJars;
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
		typeBundleJars = new Path[4];
		try {
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

			class Box {
				AutoCloseable component;
				ClassLoader loader;
			}
			final var box = new Box();

			typeBundleLoader = IuException.initialize(
					new ModularClassLoader(false, IuIterable.iter(typeBundleJars), null, null), typeBundleLoader -> {
						final var iuComponent = typeBundleLoader.loadClass("edu.iu.type.IuComponent");
						final var of = iuComponent.getMethod("of", InputStream.class, InputStream[].class);
						final var classLoader = iuComponent.getMethod("classLoader");

						return IuException.checkedInvocation(() -> {
							box.component = (AutoCloseable) of.invoke(null, componentArchiveSource,
									providedDependencyArchiveSources);
							box.loader = (ClassLoader) classLoader.invoke(box.component);
							return typeBundleLoader;
						});
					});

			component = box.component;
			loader = box.loader;

		} catch (Throwable e) {
			for (final var jar : typeBundleJars)
				IuException.suppress(e, () -> Files.deleteIfExists(jar));
			throw IuException.checked(e, IOException.class);
		}
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
		for (final var jar : typeBundleJars)
			closeError = IuException.suppress(closeError, () -> Files.deleteIfExists(jar));

		if (closeError != null)
			throw IuException.checked(closeError, IOException.class);
	}

}
