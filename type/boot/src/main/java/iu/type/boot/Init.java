package iu.type.boot;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.UnsafeRunnable;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;

/**
 * Base component container initialization hook.
 */
public class Init implements AutoCloseable {
	static {
		IuObject.assertNotOpen(Init.class);
	}

	private static final Logger LOG = Logger.getLogger(Init.class.getName());

	private volatile ModularClassLoader loader;
	private volatile AutoCloseable container;

	/**
	 * Initializes an isolated container runtime environment.
	 * 
	 * @throws IOException If an I/O errors occurs reading the container bundler or
	 *                     a component archive.
	 */
	public Init() throws IOException {
		final var init = new UnsafeRunnable() {
			private ModularClassLoader loader;
			private AutoCloseable containerBootstrap;

			@Override
			public void run() throws Throwable {
				LOG.fine("before init loader");
				loader = new ModularClassLoader(false,
						TemporaryFile.readBundle(ClassLoader.getSystemResource("iu-java-type-container-bundle.jar")),
						ModuleLayer.boot(), ClassLoader.getSystemClassLoader(), c -> {
							final var bootModule = Init.class.getModule();
							final var containerModule = c.layer().findModule("iu.util.type.container").get();
							c.addExports(containerModule, "iu.type.container", bootModule);

							final var iuTypeBase = ModuleLayer.boot().findModule("iu.util.type.base").get();
							c.addReads(containerModule, iuTypeBase);
							c.addReads(c.layer().findModule("iu.util.type.bundle").get(), iuTypeBase);
						});

				try {
					LOG.fine("after init loader " + loader.getModuleLayer());
					containerBootstrap = (AutoCloseable) loader //
							.loadClass("iu.type.container.TypeContainerBootstrap").getConstructor().newInstance();

					LOG.fine("after init container bootstrap " + containerBootstrap);
				} catch (Throwable e) {
					IuException.suppress(e, loader::close);
					throw e;
				}
			}
		};
		TemporaryFile.init(init);
		loader = Objects.requireNonNull(init.loader);
		container = Objects.requireNonNull(init.containerBootstrap);
	}

	@Override
	public synchronized void close() {
		final var container = this.container;
		final var loader = this.loader;
		if (container != null) {
			this.container = null;
			this.loader = null;

			LOG.fine("before destroy container bootstrap " + container);
			var error = IuException.suppress(null, container::close);

			LOG.fine("before destroy loader " + loader.getModuleLayer());
			error = IuException.suppress(error, loader::close);

			if (error != null)
				throw IuException.unchecked(error);
		}
	}

	/**
	 * Entry point.
	 * 
	 * @param a arguments
	 */
	public static void main(String... a) {
		IuException.unchecked(() -> {
			try (final var init = new Init()) {
				((UnsafeRunnable) init.container).run();
			}
		});
	}

}
