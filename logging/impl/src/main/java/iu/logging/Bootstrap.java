/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.logging;

import java.lang.ModuleLayer.Controller;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.UnsafeSupplier;
import iu.logging.internal.IuLogHandler;
import iu.logging.internal.IuLoggingProxy;
import iu.logging.internal.LogEnvironmentImpl;
import iu.logging.internal.ProcessLogger;

/**
 * Logging implementation bootstrap.
 * 
 * <p>
 * To be invoked externally by a module that controls the logging implementation
 * {@link Controller}, typically by a class provided via the
 * {@link LogManager#readConfiguration(java.io.InputStream)
 * -Djava.util.logging.config.class} JVM argument. Invoke
 * {@link #configure(boolean)} first, then
 * {@link #initializeContext(String, boolean, String, String, String, String, String, String)}
 * for each class loader prior to using {@link #follow(Object, String, Object)}
 * within its {@link Thread#getContextClassLoader() context}.
 * </p>
 */
public final class Bootstrap {

	private static final Map<ClassLoader, LogEnvironmentImpl> ENVIRONMENT = new WeakHashMap<>();
	private static final LogEnvironmentImpl PLATFORM = new LogEnvironmentImpl();

	private Bootstrap() {
	}

	/**
	 * Reads the logging configuration from {@link IuRuntimeEnvironment#env(String)
	 * iu.config}/logging.properties.
	 * 
	 * @param update True to update the existing configuration; false for initial
	 *               system configuration.
	 * @return true if logging configuration exists and was updated
	 * @see LogManager#readConfiguration(java.io.InputStream)
	 */
	public static boolean configure(boolean update) {
		final var configRoot = IuRuntimeEnvironment.envOptional("iu.config");
		if (configRoot == null)
			if (update)
				return false;
			else
				throw new NullPointerException("Missing system property iu.config or environment variable IU_CONFIG");

		final var loggingPropertiesFile = Path.of(configRoot, "logging.properties");
		final var loggingPropertiesExists = Files.exists(loggingPropertiesFile);
		if (loggingPropertiesExists)
			IuException.unchecked(() -> {
				try (final var in = Files.newInputStream(loggingPropertiesFile)) {
					final var logManager = LogManager.getLogManager();
					if (update)
						logManager.updateConfiguration(in, null);
					else
						logManager.readConfiguration(in);
				}
			});

		return loggingPropertiesExists;
	}

	private static IuLogHandler handler() {
		Logger.getLogger("");
		final var root = LogManager.getLogManager().getLogger("");

		IuLogHandler handler = null;
		for (final var h : root.getHandlers())
			if (h instanceof IuLogHandler) {
				handler = (IuLogHandler) h;
				break;
			}
		if (handler == null)
			root.addHandler(handler = new IuLogHandler());

		return handler;
	}

	/**
	 * Ensures that logging is fully initialized for the
	 * {@link ClassLoader#getSystemClassLoader() system} and
	 * {@link ClassLoader#getPlatformClassLoader() platform} {@link ClassLoader}s.
	 * 
	 * <p>
	 * {@link IuRuntimeEnvironment#envOptional(String) Runtime properties}:
	 * </p>
	 * <ul>
	 * <li>iu.development - development environment flag</li>
	 * <li>iu.endpoint - refers to the external port or client node identifier for
	 * the active runtime</li>
	 * <li>iu.application - refers to the application configuration code relative to
	 * the runtime environment</li>
	 * <li>iu.environment - refers to the application's environment code, for
	 * classifying runtime configuration</li>
	 * <li>iu.module - refers to the module configuration code, relative to
	 * application and environment</li>
	 * <li>iu.runtime - refers to the runtime configuration code, relative to
	 * application and environment</li>
	 * <li>iu.component - refers to the component name, relative to application,
	 * environment, and runtime</li>
	 * </ul>
	 * 
	 * @see #initializeContext(String, boolean, String, String, String, String,
	 *      String, String)
	 */
	public static void initialize() {
		final var handler = handler();
		Logger.getLogger("").config("IuLogContext initialized " + handler + " " + PLATFORM);
	}

	/**
	 * Initializes attributes for the {@link Thread#getContextClassLoader() current
	 * thread's context}.
	 * 
	 * @param nodeId      runtime node identifier; defaults to
	 *                    {@link InetAddress#getLocalHost()}{@link InetAddress#getHostName()
	 *                    .getHostName()}
	 * @param development development environment flag
	 * @param endpoint    external port or client node identifier
	 * @param application application configuration code
	 * @param environment environment code
	 * @param module      module configuration code, relative to application and
	 *                    environment
	 * @param runtime     runtime configuration code, relative to application and
	 *                    environment
	 * @param component   component name, relative to application, environment, and
	 *                    runtime
	 * @see #initialize()
	 */
	public static void initializeContext(String nodeId, boolean development, String endpoint, String application,
			String environment, String module, String runtime, String component) {
		final var context = Thread.currentThread().getContextClassLoader();
		if (context == ClassLoader.getPlatformClassLoader() //
				|| context == ClassLoader.getSystemClassLoader())
			throw new IllegalStateException("Already initialized; " + PLATFORM);

		final var handler = handler();

		LogEnvironmentImpl env = null;
		synchronized (ENVIRONMENT) {
			env = ENVIRONMENT.get(context);
			if (env != null)
				throw new IllegalStateException("Already initialized; " + env);

			ENVIRONMENT.put(context, env = new LogEnvironmentImpl(PLATFORM, nodeId, development, endpoint, application,
					environment, module, runtime, component));
		}

		Logger.getLogger("").config("IuLogContext initialized " + handler + " " + env + "; context: " + context);
	}

	/**
	 * Gets the initialized {@link LogEnvironment} for the current context
	 * {@link ClassLoader}.
	 * 
	 * @return {@link LogEnvironment}
	 */
	public static LogEnvironment getEnvironment() {
		return Objects.requireNonNullElse(ENVIRONMENT.get(Thread.currentThread().getContextClassLoader()), PLATFORM);
	}

	/**
	 * Gets the active context on the current thread.
	 * 
	 * @param <T>         context type
	 * @param contextType context type
	 * @return log context
	 */
	public static <T> T getActiveContext(Class<T> contextType) {
		return IuLoggingProxy.adapt(contextType, ProcessLogger.getActiveContext());
	}

	/**
	 * Proxy method for
	 * {@link ProcessLogger#follow(LogContext, String, UnsafeSupplier)}.
	 * 
	 * @param context  externally defined {@link LogContext}
	 * @param header   process log header message
	 * @param supplier externally defined {@link UnsafeSupplier}
	 * @return return value
	 * @throws Throwable if an error occurs
	 * 
	 */
	@SuppressWarnings("unchecked")
	public static Object follow(Object context, String header, Object supplier) throws Throwable {
		return ProcessLogger.follow(IuLoggingProxy.adapt(LogContext.class, context), header,
				IuLoggingProxy.adapt(UnsafeSupplier.class, supplier));
	}

	/**
	 * Subscribes to log events via proxy to a remotely defined interface.
	 * 
	 * @param <T>        event type
	 * @param eventClass event class
	 * @return {@link Stream}
	 */
	public static <T> Stream<T> subscribe(Class<T> eventClass) {
		return ((Stream<?>) handler().subscribe().stream()).map(a -> IuLoggingProxy.adapt(eventClass, a));
	}

	/**
	 * Adds a message to the process trace without logging.
	 * 
	 * @param messageSupplier Message {@link Supplier}
	 */
	public static void trace(Supplier<String> messageSupplier) {
		ProcessLogger.trace(messageSupplier);
	}

	/**
	 * Tears down all registered resources.
	 */
	public static void destroy() {
		final var root = LogManager.getLogManager().getLogger("");
		if (root == null)
			return;

		for (final var h : root.getHandlers())
			if (h instanceof IuLogHandler)
				root.removeHandler(h);
	}

}
