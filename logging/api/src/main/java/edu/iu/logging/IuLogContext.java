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
