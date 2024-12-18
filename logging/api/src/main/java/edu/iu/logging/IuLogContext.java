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
package edu.iu.logging;

import java.util.logging.Level;

import edu.iu.UnsafeSupplier;
import iu.logging.boot.IuLoggingBootstrap;

/**
 * Provides thread-bound context information for log events.
 */
public interface IuLogContext {

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
		IuLoggingBootstrap.initializeContext(endpoint, application, environment);
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
	public static <T> T follow(IuLogContext context, String message, UnsafeSupplier<T> supplier) throws Throwable {
		return IuLoggingBootstrap.follow(context, message, supplier);
	}

	/**
	 * Gets the unique identifier for the active request
	 * 
	 * @return unique request ID
	 */
	String getRequestId();

	/**
	 * Gets the node identifier.
	 * 
	 * @return Unique node identifier
	 */
	String getNodeId();

	/**
	 * Gets the endpoint identifier.
	 * 
	 * @return endpoint identifier
	 */
	String getEndpoint();

	/**
	 * Gets the application code.
	 * 
	 * @return application code
	 */
	String getApplication();

	/**
	 * Gets the application environment.
	 * 
	 * @return application environment
	 */
	String getEnvironment();

	/**
	 * Gets the module code.
	 * 
	 * @return module code
	 */
	String getModule();

	/**
	 * Gets the component code.
	 * 
	 * @return component code
	 */
	String getComponent();

	/**
	 * Gets log level to use for reporting information messages and process trace
	 * dumps related to this logging context.
	 * 
	 * @return {@link Level}
	 */
	Level getLevel();

	/**
	 * Determines whether or not to enable extended debug logging appropriate for
	 * development environments.
	 * 
	 * @return true to enable extended debug logging; else false
	 */
	boolean isDevelopment();

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
