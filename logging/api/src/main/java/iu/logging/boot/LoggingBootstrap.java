package iu.logging.boot;

import java.lang.ModuleLayer.Controller;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.LogManager;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.UnsafeRunnable;
import edu.iu.logging.IuLogContext;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;

/**
 * Bootstraps the logging module.
 */
public class LoggingBootstrap {

	private static final String IMPL_MODULE_NAME = "iu.util.logging.impl";

	/**
	 * Gets the implementation class loader derived from the root logger if it is
	 * has been assigned at least one handler by the implementation module.
	 * 
	 * @return Implementation {@link ClassLoader} if the root logger has been
	 *         assigned at least one handler from the implementation module; else
	 *         null
	 */
	static ClassLoader getImplLoader() {
		final var root = LogManager.getLogManager().getLogger("");
		if (root == null)
			return null;

		for (final var handler : root.getHandlers()) {
			final var handlerClass = handler.getClass();
			final var handlerModule = handlerClass.getModule();
			if (handlerModule != null && IMPL_MODULE_NAME.equals(handlerModule.getName()))
				return handlerClass.getClassLoader();
		}

		return null;
	}

	/**
	 * Bootstraps the root logger within a specific context using a loaded
	 * implementation loader.
	 * 
	 * @param implLoader    implementation loader
	 * @param contextLoader context loader to bootstrap
	 * @throws Throwable if an error occurs
	 */
	static void bootstrap(ClassLoader implLoader, ClassLoader contextLoader) throws Throwable {
		final var bootstrap = implLoader.loadClass("iu.logging.Bootstrap");
		final var init = bootstrap.getMethod("init", ClassLoader.class);
		IuException.checkedInvocation(() -> init.invoke(null, contextLoader));
	}

	/**
	 * Initializes the implementation module.
	 * 
	 * @param c {@link Controller}
	 */
	static void initImplModule(Controller c) {
		final var impl = c.layer().findModule(IMPL_MODULE_NAME).get();
		final var base = ModuleLayer.boot().findModule("iu.util");
		if (base.isPresent())
			c.addReads(impl, base.get());
	}

	/**
	 * Gets the bootstrapped implementation loader.
	 * 
	 * @return non-null implementation {@link ClassLoader}
	 */
	static ClassLoader implLoader() {
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try {
			final var platform = ClassLoader.getPlatformClassLoader();
			current.setContextClassLoader(platform);

			final var impl = getImplLoader();
			if (impl != null)
				return impl;

			class Init implements UnsafeRunnable {
				ClassLoader loader;

				@Override
				public void run() throws Throwable {
					final var bootLoader = ModularClassLoader.of(platform, ModuleLayer.boot(),
							() -> TemporaryFile.readBundle(Objects.requireNonNull(
									IuLogContext.class.getClassLoader()
											.getResource("META-INF/component/iu-java-logging-impl-bundle.jar"),
									"Missing iu-java-logging-impl-bundle.jar")),
							LoggingBootstrap::initImplModule);
					try {
						bootstrap(bootLoader, platform);

						final var implLoader = getImplLoader();
						if (implLoader == bootLoader) {
							this.loader = bootLoader;
							return;
						} else if (implLoader != null) {
							bootLoader.close();
							this.loader = implLoader;
						} else
							throw new IllegalStateException("Logging bootstrap didn't initialize root logger");

					} catch (Throwable e) {
						IuException.suppress(e, bootLoader::close);
						throw e;
					}
				}
			}

			final var init = new Init();
			IuException.unchecked(() -> TemporaryFile.init(init));
			return Objects.requireNonNull(init.loader, "Logging initialization failed");

		} finally {
			current.setContextClassLoader(restore);
		}
	}

	/**
	 * Gets the implementation module static equivalent to {@link ProcessLogger}.
	 * 
	 * @return implementation class
	 * @throws Throwable if an error occurs
	 */
	public static Class<?> impl() throws Throwable {
		return implLoader().loadClass("iu.logging.IuProcessLogger");
	}

	/**
	 * Initializes logging module resources.
	 */
	public static void init() {
		IuException.unchecked(() -> bootstrap(implLoader(), Thread.currentThread().getContextClassLoader()));
	}

	/**
	 * Temporarily overrides system properties to pass in logging environment
	 * defaults for initialization on the current context.
	 * 
	 * @param endpoint    endpoint (launchpad application) code
	 * @param application application code
	 * @param environment environment code
	 */
	public static void init(String endpoint, String application, String environment) {
		final var props = System.getProperties();
		final var restore = new HashMap<>(props);
		synchronized (props) {
			props.put("iu.logging.endpoint", endpoint);
			props.put("iu.logging.application", application);
			props.put("iu.logging.environment", environment);
			try {
				LoggingBootstrap.init();
			} finally {
				for (final var name : IuIterable.iter( //
						"iu.logging.endpoint", "iu.logging.application", //
						"iu.logging.environment"))
					if (restore.containsKey(name))
						props.put(name, restore.get(name));
					else
						props.remove(name);
			}
		}
	}

	/**
	 * Shuts down all initialized logging module resources.
	 */
	public static void shutdown() {
		final var root = LogManager.getLogManager().getLogger("");
		if (root == null)
			return;

		// TODO ensure shutdown doesn't interfere with other modules
		for (final var handler : root.getHandlers()) {
			final var handlerClass = handler.getClass();
			final var handlerModule = handlerClass.getModule();
			if (handlerModule != null && IMPL_MODULE_NAME.equals(handlerModule.getName())) {
				root.removeHandler(handler);
				IuException.unchecked(((AutoCloseable) handlerClass.getClassLoader())::close);
			}
		}

		return;
	}

}
