/*
 * Copyright © 2025 Indiana University
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
package iu.logging.boot;

import java.lang.ModuleLayer.Controller;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.UnsafeRunnable;
import edu.iu.UnsafeSupplier;
import edu.iu.logging.IuLogContext;
import edu.iu.logging.IuLogEvent;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;

/**
 * Bootstraps the logging module.
 * 
 * <p>
 * Recommended use is to ensure iu-java-base, iu-java-type-base, and
 * iu-java-logging are in the ({@code -p}) module path and start the JVM with
 * {@code -Djava.util.logging.config.class=iu.logging.boot.IuLoggingBootstrap}.
 * Alternatively, {@link #IuLoggingBootstrap(boolean) new
 * IuLoggingBootstrap(true)} may be used via reflection to control logging
 * configuration after initialization.
 * </p>
 * 
 * <p>
 * Additional system proprties configure the logging implementation module and
 * may be overridden by an application container module:
 * </p>
 * <ul>
 * <li>{@code iu.config} - folder with logging.properties</li>
 * <li>{@code iu.logging.file.path} - root log folder</li>
 * <li>{@code iu.logging.file.maxSize} - maximum size, in bytes, for each log
 * file</li>
 * <li>{@code iu.logging.file.nLimit} - maximum number of backup files to
 * keep</li>
 * </ul>
 * 
 * <p>
 * During application initialization, within the application's
 * {@link Thread#getContextClassLoader() thread context}, invoke
 * {@link #initializeContext(String, boolean, String, String, String, String, String, String)}
 * to initialize logging.
 * </p>
 * 
 * @see LogManager
 */
public class IuLoggingBootstrap {

	private static IuLoggingBootstrap initialized;

	private volatile boolean closed;
	private volatile ModularClassLoader loader;
	private volatile Class<?> bootstrap;
	private volatile UnsafeRunnable destroy;

	/**
	 * Default constructor.
	 * 
	 * @throws Exception If an error occurs
	 */
	public IuLoggingBootstrap() throws Exception {
		this(false);
	}

	/**
	 * Constructor.
	 * 
	 * @param update true to update the existing configuration; false for initial
	 *               system configuration
	 * 
	 * @throws Exception If an error occurs
	 */
	public IuLoggingBootstrap(boolean update) throws Exception {
		final var toDestroy = initialized;

		IuException.checked(() -> IuLogManager.bound(() -> {
			destroy = TemporaryFile.init(() -> {
				loader = ModularClassLoader.of(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(),
						() -> TemporaryFile.readBundle(Objects.requireNonNull(
								IuLogContext.class.getClassLoader()
										.getResource("META-INF/component/iu-java-logging-impl-bundle.jar"),
								"Missing iu-java-logging-impl-bundle.jar")),
						IuLoggingBootstrap::initImplModule);
				try {
					bootstrap = loader.loadClass("iu.logging.Bootstrap");
					IuException.checkedInvocation(
							() -> bootstrap.getMethod("configure", boolean.class).invoke(null, update));
				} catch (Throwable e) {
					IuException.suppress(e, loader::close);
					throw e;
				}
			});
		}));

		initialized = this;

		if (toDestroy != null)
			try {
				toDestroy.destroy();
			} catch (Throwable e) {
				Logger.getLogger(getClass().getName()).log(Level.WARNING,
						"Failed to destroy logging implementation module after hot replace", e);
			}
	}

	private static void initImplModule(Controller c) {
		c.addReads(c.layer().findModule("iu.util.logging.impl").get(), IuObject.class.getModule());
	}

	/**
	 * Destroys logging resources and closes the implementation module.
	 * 
	 * @throws Exception If an error occurs
	 */
	synchronized void destroy() throws Exception {
		if (!closed) {
			closed = true;
			Throwable error = null;

			final var bootstrap = this.bootstrap;
			if (bootstrap != null) {
				this.bootstrap = null;
				error = IuException.suppress(error,
						() -> IuException.checkedInvocation(() -> bootstrap.getMethod("destroy").invoke(null)));
			}

			final var loader = this.loader;
			if (loader != null) {
				this.loader = null;
				error = IuException.suppress(error, loader::close);
			}

			final var destroy = this.destroy;
			if (destroy != null) {
				this.destroy = null;
				error = IuException.suppress(error, destroy);
			}

			if (this == initialized)
				initialized = null;

			if (error != null)
				throw IuException.checked(error);
		}
	}

	/**
	 * Determines whether or not the logging module controlled by this bootstrap
	 * instance has been replaced by a new instance and destroyed.
	 * 
	 * @return true if destroyed; else false
	 */
	boolean isDestroyed() {
		return closed;
	}

	private static Class<?> bootstrap() {
		return Objects.requireNonNull(initialized, "Not initialized").bootstrap;
	}

	/**
	 * Implements {@link IuLogContext#initialize()}.
	 */
	public static void initialize() {
		IuException.uncheckedInvocation(() -> bootstrap() //
				.getMethod("initialize") //
				.invoke(null));
	}

	/**
	 * Implements
	 * {@link IuLogContext#initializeContext(String, boolean, String, String, String, String, String, String)}.
	 * 
	 * @param nodeId      node identifier
	 * @param development development flag
	 * @param endpoint    endpoint
	 * @param application application
	 * @param environment environment
	 * @param module      module
	 * @param runtime     runtime
	 * @param component   component
	 */
	public static void initializeContext(String nodeId, boolean development, String endpoint, String application,
			String environment, String module, String runtime, String component) {
		IuException.uncheckedInvocation(() -> bootstrap() //
				.getMethod("initializeContext", String.class, boolean.class, String.class, String.class, String.class,
						String.class, String.class, String.class) //
				.invoke(null, nodeId, development, endpoint, application, environment, module, runtime, component));
	}

	/**
	 * Implements {@link IuLogContext#follow(IuLogContext, String, UnsafeSupplier)}.
	 * 
	 * @param <T>      return type
	 * @param context  context
	 * @param message  message
	 * @param supplier supplier
	 * @return return value
	 * @throws Throwable error
	 */
	@SuppressWarnings("unchecked")
	public static <T> T follow(IuLogContext context, String message, UnsafeSupplier<T> supplier) throws Throwable {
		return (T) IuException.checkedInvocation(() -> bootstrap() //
				.getMethod("follow", Object.class, String.class, Object.class) //
				.invoke(null, context, message, supplier));
	}

	/**
	 * Implements {@link IuLogEvent#subscribe()}.
	 * 
	 * @return {@link Stream} of log events.
	 */
	@SuppressWarnings("unchecked")
	public static Stream<IuLogEvent> subscribe() {
		return (Stream<IuLogEvent>) IuException.uncheckedInvocation(() -> bootstrap() //
				.getMethod("subscribe", Class.class) //
				.invoke(null, IuLogEvent.class));
	}

}
