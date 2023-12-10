package edu.iu.type.loader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
	 * @param controllerCallback               receives a reference to an
	 *                                         {@link IuComponentController} that
	 *                                         may be used to set up access rules
	 *                                         for the component. This reference
	 *                                         <em>should not</em> be passed beyond
	 *                                         the scope of the callback.
	 * @param componentArchiveSource           {@link InputStream} for reading the
	 *                                         <strong>component archive</strong>.
	 * @param providedDependencyArchiveSources {@link InputStream}s for reading all
	 *                                         <strong>provided dependency
	 *                                         archives</strong>.
	 * @throws IOException If an IO error occurs initializing the component
	 */
	public IuComponentLoader(Consumer<IuComponentController> controllerCallback, InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) throws IOException {
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

			box.typeBundleLoader = IuException.checked(IOException.class, () -> IuException
					.initialize(new ModularClassLoader(false, IuIterable.iter(typeBundleJars), controller -> {
						controller.addOpens(controller.layer().findModule("iu.util.type.bundle").get(),
								"iu.type.bundle", getClass().getModule());
					}), typeBundleLoader -> {
						final var typeImpl = typeBundleLoader.loadClass("edu.iu.type.spi.TypeImplementation");
						final var provider = typeImpl.getField("PROVIDER");
						provider.setAccessible(true);
						final var bundleSpi = typeBundleLoader.loadClass("iu.type.bundle.TypeBundleSpi");
						final var getModule = bundleSpi.getMethod("getModule");
						getModule.setAccessible(true);
						final var typeModule = (Module) getModule.invoke(provider.get(typeImpl));

						final var iuComponent = typeBundleLoader.loadClass("edu.iu.type.IuComponent");
						final var of = iuComponent.getMethod("of", BiConsumer.class, InputStream.class,
								InputStream[].class);
						final var classLoader = iuComponent.getMethod("classLoader");

						class ComponentController implements IuComponentController {
							private final Module componentModule;
							private final Controller controller;

							ComponentController(Module componentModule, Controller controller) {
								this.componentModule = componentModule;
								this.controller = controller;
							}

							@Override
							public Module getTypeModule() {
								return typeModule;
							}

							@Override
							public Module getComponentModule() {
								return componentModule;
							}

							@Override
							public Controller getController() {
								return controller;
							}
						}

						return IuException.checkedInvocation(() -> {
							box.component = (AutoCloseable) of.invoke(null,
									(BiConsumer<Module, Controller>) (module, controller) -> {
										if (controllerCallback != null)
											controllerCallback.accept(new ComponentController(module, controller));
									}, componentArchiveSource, providedDependencyArchiveSources);
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
