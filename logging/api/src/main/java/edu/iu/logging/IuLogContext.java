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
package edu.iu.logging;

import java.net.InetAddress;
import java.util.logging.Level;

import edu.iu.IuRuntimeEnvironment;
import edu.iu.UnsafeSupplier;
import iu.logging.boot.IuLoggingBootstrap;

/**
 * Provides thread-bound context information for log events.
 */
public interface IuLogContext {

	/**
	 * Ensures that logging is fully initialized for the
	 * {@link ClassLoader#getSystemClassLoader() system} and
	 * {@link ClassLoader#getPlatformClassLoader() platform} {@link ClassLoader}s.
	 * 
	 * <p>
	 * {@link IuRuntimeEnvironment#envOptional(String) Runtime properties}:
	 * </p>
	 * <ul>
	 * <li>iu.endpoint - refers to the external port or client node identifier for
	 * the active runtime</li>
	 * <li>iu.application - refers to the application configuration code relative to
	 * the runtime environment</li>
	 * <li>iu.environment - refers to the application's environment code, for
	 * classifying runtime configuration</li>
	 * <li>iu.logging.maxEvents - maximum number of log events to retain in
	 * buffer</li>
	 * <li>iu.logging.eventTtl - maximum time to live for buffered log events</li>
	 * <li>iu.logging.consoleLevel - minimum log level to write to
	 * {@link System#out}</li>
	 * </ul>
	 * 
	 * @see #initializeContext(String, boolean, String, String, String, String, String, String)
	 */
	static void initialize() {
		IuLoggingBootstrap.initialize();
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
	static void initializeContext(String nodeId, boolean development, String endpoint, String application,
			String environment, String module, String runtime, String component) {
		IuLoggingBootstrap.initializeContext(nodeId, development, endpoint, application, environment, module, runtime,
				component);
	}

	/**
	 * Applies full context attributes to log events generated during the invocation
	 * of an application-defined {@link UnsafeSupplier}.
	 *
	 * <p>
	 * Every record published within the boundary is also appended to the process
	 * trace, whatever its level, and the accumulated trace is attached to each
	 * event at {@link java.util.logging.Level#WARNING} or above &mdash; so an
	 * 80-character prefix of a message logged at {@link
	 * java.util.logging.Level#FINE} reaches whatever destination a later warning
	 * reaches, including the error log and every {@link IuLogEvent#subscribe()
	 * subscriber}. Nothing logged inside the boundary is level-gated for the
	 * purpose of the trace, so credentials and personal data must be kept out of
	 * records at <em>every</em> level, not only the ones normally published.
	 * </p>
	 *
	 * <p>
	 * The trace is bounded, and messages past the bound are dropped with a marker
	 * in their place rather than retained, so a long-lived or high-volume boundary
	 * does not grow without limit. The whole of it is reported when the boundary
	 * completes, whether the task returned or threw.
	 * </p>
	 *
	 * @param <T>      return type
	 * @param context  {@link IuLogContext}
	 * @param message  short message to augment trace behavior
	 * @param supplier processing task to follow
	 * @return value from {@link UnsafeSupplier#get()}
	 * @throws Throwable from {@link UnsafeSupplier#get()}
	 */
	static <T> T follow(IuLogContext context, String message, UnsafeSupplier<T> supplier) throws Throwable {
		return IuLoggingBootstrap.follow(context, message, supplier);
	}

	/**
	 * Captures the process being {@link #follow(IuLogContext, String, UnsafeSupplier)
	 * followed} by the current thread, for {@link #join(Object, Runnable) joining}
	 * from a worker thread.
	 *
	 * <p>
	 * The value returned is an opaque handle with no externally defined contract; it
	 * is only useful as the {@code forkedContext} argument to
	 * {@link #join(Object, Runnable)}, and is null when the current thread is not
	 * following a process.
	 * </p>
	 *
	 * <p>
	 * The forking thread <em>must</em> ensure joined tasks complete before it stops
	 * following the process; messages traced by a task that outlives the process are
	 * not guaranteed to be reported.
	 * </p>
	 *
	 * <p>
	 * The handle is valid only for the lifetime of the process that produced it. It
	 * <em>must not</em> be retained across a logging reconfiguration: while it is
	 * held it keeps the logging implementation's {@link ClassLoader} reachable, and
	 * once that implementation has been replaced {@link #join(Object, Runnable)}
	 * rejects it with {@link IllegalArgumentException}.
	 * </p>
	 *
	 * <pre>
	 * final var fork = IuLogContext.fork();
	 * executor.submit(() -&gt; IuLogContext.join(fork, this::handle));
	 * </pre>
	 *
	 * @return opaque handle on the process being followed; null if not following
	 */
	static Object fork() {
		return IuLoggingBootstrap.fork();
	}

	/**
	 * Applies a {@link #fork() forked} process to log events generated during the
	 * invocation of an application-defined {@link Runnable}.
	 *
	 * <p>
	 * Log events published by the task report the forked process' context, and
	 * process trace messages recorded by the task are merged into the forked
	 * process' trace. Any process already followed by the current thread is restored
	 * when the task completes, so a task <em>may</em> be joined by the thread that
	 * forked it, i.e. when a work queue falls back to executing on the submitting
	 * thread.
	 * </p>
	 *
	 * <p>
	 * Each joined task is assigned the next sequential join ID for the forked
	 * process, and its trace messages are prefixed with {@code joinId + "> "} so
	 * that work forked onto other threads can be told apart from the forking
	 * thread's own messages once merged into a single trace.
	 * </p>
	 *
	 * @param forkedContext opaque handle returned by {@link #fork()}
	 * @param task          task to run with the forked process applied
	 * @throws IllegalArgumentException if {@code forkedContext} did not come from
	 *                                  {@link #fork()}
	 */
	static void join(Object forkedContext, Runnable task) {
		IuLoggingBootstrap.join(forkedContext, task);
	}

	/**
	 * Gets the unique identifier for the active request
	 * 
	 * @return unique request ID
	 */
	String getRequestId();

	/**
	 * Gets log level to use for reporting information messages and process trace
	 * dumps related to this logging context.
	 * 
	 * @return {@link Level}
	 */
	Level getLevel();

	/**
	 * Gets the caller IP address to report with logged messages
	 * 
	 * @return caller IP address
	 */
	String getCallerIpAddress();

	/**
	 * Gets the called URL to report with logged messages
	 * 
	 * @return called URL
	 */
	String getCalledUrl();

	/**
	 * Gets the caller principal name to report with logged messages
	 * 
	 * @return caller principal name
	 */
	String getCallerPrincipalName();

	/**
	 * Gets the impersonated principal name to report with logged messages
	 * 
	 * @return impersonated principal name
	 */
	String getImpersonatedPrincipalName();

}
