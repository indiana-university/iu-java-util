/*
 * Copyright © 2024 Indiana University
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
import iu.logging.internal.DefaultLogContext;
import iu.logging.internal.IuLogHandler;
import iu.logging.internal.IuLoggingProxy;
import iu.logging.internal.ProcessLogger;

/**
 * Logging implementation bootstrap.
 * 
 * <p>
 * To be invoked externally by a module that controls the logging implementation
 * {@link Controller}, typically by a class providing via the
 * {@link LogManager#readConfiguration(java.io.InputStream)
 * -Djava.util.logging.config.class} JVM argument. Invoke
 * {@link #configure(boolean)} first, then
 * {@link #initializeContext(String,String,String)} for each class loader prior
 * to using {@link #follow(Object, String, Object)} within its
 * {@link Thread#getContextClassLoader() context}.
 * </p>
 */
public final class Bootstrap {

	private static final Map<ClassLoader, DefaultLogContext> DEFAULT_CONTEXT = new WeakHashMap<>();

	private Bootstrap() {
	}

	/**
	 * Reads the logging configuration from {@link IuRuntimeEnvironment#env(String)
	 * iu.config}/logging.properties.
	 * 
	 * @param update True to update the existing configuration; false for initial
	 *               system configuration.
	 * @see LogManager#readConfiguration(java.io.InputStream)
	 */
	public static void configure(boolean update) {
		final var configRoot = IuRuntimeEnvironment.env("iu.config");
		final var logManager = LogManager.getLogManager();
		final var loggingPropertiesFile = Path.of(configRoot, "logging.properties");
		final var loggingPropertiesExists = Files.isReadable(loggingPropertiesFile);
		if (loggingPropertiesExists)
			IuException.unchecked(() -> {
				try (final var in = Files.newInputStream(loggingPropertiesFile)) {
					if (update)
						logManager.updateConfiguration(in, null);
					else
						logManager.readConfiguration(in);
				}
				Logger.getLogger("").config("Logging configuration updated from " + loggingPropertiesFile);
			});
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
	 * Binds top-level attributes to log events observed in the
	 * {@link Thread#getContextClassLoader() current thread's context}.
	 * 
	 * <p>
	 * This method SHOULD be invoked exactly once during initialization, typically
	 * once per container, to bind per-{@link ClassLoader} node-level runtime
	 * attributes.
	 * </p>
	 * 
	 * @param endpoint    endpoint identifier
	 * @param application application code
	 * @param environment environment code
	 */
	public static void initializeContext(String endpoint, String application, String environment) {
		final var handler = handler();

		final var context = Thread.currentThread().getContextClassLoader();
		var defaultContext = new DefaultLogContext(endpoint, application, environment);
		synchronized (DEFAULT_CONTEXT) {
			DEFAULT_CONTEXT.put(context, defaultContext);
		}

		Logger.getLogger("")
				.config("IU Logging Bootstrap initialized " + handler + " " + defaultContext + "; context: " + context);
	}

	/**
	 * Gets the initialized {@link DefaultLogContext} for the current context
	 * {@link ClassLoader}.
	 * 
	 * @return {@link DefaultLogContext}
	 */
	public static LogContext getDefaultContext() {
		final var defaultContext = DEFAULT_CONTEXT.get(Thread.currentThread().getContextClassLoader());
		if (defaultContext == null)
			return Objects.requireNonNull(DEFAULT_CONTEXT.get(ClassLoader.getPlatformClassLoader()),
					"Not initialized; invoke init(ClassLoader.getPlatformClassLoader()) first");
		else
			return defaultContext;
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

}
