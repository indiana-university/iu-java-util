package iu.logging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuRuntimeEnvironment;
import iu.logging.internal.DefaultLogContext;
import iu.logging.internal.IuLogHandler;

/**
 * Per-context logging bootstrap.
 */
public final class Bootstrap {

	private static final Map<ClassLoader, DefaultLogContext> DEFAULT_CONTEXT = new WeakHashMap<>();

	/** Singleton handler instance */
	static final IuLogHandler HANDLER = new IuLogHandler();

	private Bootstrap() {
	}

	/**
	 * Ensures this module's log handler is present on the root logger.
	 * 
	 * @param context {@link ClassLoader} context loader
	 */
	public static void init(ClassLoader context) {
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try {
			current.setContextClassLoader(context);

			Logger.getLogger("");
			final var root = LogManager.getLogManager().getLogger("");
			for (final var handler : root.getHandlers())
				if (handler instanceof IuLogHandler)
					return; // already initialized

			final var configRoot = IuRuntimeEnvironment.env("iu.config");
			final var logManager = LogManager.getLogManager();
			final var loggingPropertiesFile = Path.of(configRoot, "logging.properties");
			final var loggingPropertiesExists = Files.isReadable(loggingPropertiesFile);
			if (loggingPropertiesExists)
				IuException.unchecked(() -> {
					try (final var in = Files.newInputStream(loggingPropertiesFile)) {
						logManager.updateConfiguration(in, null);
					}
				});

			final var endpoint = IuRuntimeEnvironment.env("iu.logging.endpoint");
			final var application = IuRuntimeEnvironment.env("iu.logging.application");
			final var environment = IuRuntimeEnvironment.env("iu.logging.environment");
			final var defaultContext = new DefaultLogContext(endpoint, application, environment);
			synchronized (DEFAULT_CONTEXT) {
				DEFAULT_CONTEXT.put(context, defaultContext);
			}

			root.setUseParentHandlers(false);
			root.addHandler(HANDLER);
			root.config("IU Logging Bootstrap initialized " + HANDLER + " " + defaultContext + "; context: " + context);
			if (loggingPropertiesExists)
				root.config("Logging configuration updated from " + loggingPropertiesFile);

		} finally {
			current.setContextClassLoader(restore);
		}
	}

	/**
	 * Gets the initialized {@link DefaultLogContext} for the current context
	 * {@link ClassLoader}.
	 * 
	 * @return {@link DefaultLogContext}
	 */
	public static DefaultLogContext getDefaultContext() {
		return Objects.requireNonNullElse(DEFAULT_CONTEXT.get(Thread.currentThread().getContextClassLoader()),
				Objects.requireNonNull(DEFAULT_CONTEXT.get(ClassLoader.getPlatformClassLoader())));
	}

}
