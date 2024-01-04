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
package edu.iu.runtime;

import iu.runtime.RuntimeFactory;

/**
 * Provides access to runtime configuration.
 * 
 * <p>
 * <strong>NOTICE</strong>: This interface is intended for bootstrapping the
 * application's platform-level secrets engine. All values provided by this
 * interface <em>must</em> be handled as secrets.
 * </p>
 * 
 * <ul>
 * <li>Configuration values must not be written to application logs.</li>
 * <li>Configuration values must not be visible to users.</li>
 * <li>Configuration values should not be visible to application developers,
 * i.e. by including in a JEE components's JNDI environment.</li>
 * </ul>
 * 
 * <p>
 * JEE containers may use this interface for platform-level initialization, but
 * should prevent its direct use by applications by placing the
 * {@link iu.util.runtime} module in the
 * {@link ClassLoader#getSystemClassLoader() system class loader} and providing
 * an implementation that only throws {@link UnsupportedOperationException}.
 * </p>
 */
public interface IuRuntime {

	/**
	 * Fully initialized runtime provider.
	 * 
	 * <p>
	 * IU Java Utilities components use this field to access runtime environment
	 * configuration.
	 * </p>
	 */
	static IuRuntime PROVIDER = RuntimeFactory.getProvider();

	/**
	 * Gets the global environment configuration.
	 * 
	 * <p>
	 * Environment configuration is read once, at bootstrap initialization time,
	 * from information provided by the underlying platform.
	 * </p>
	 * 
	 * @return environment configuration
	 * @throws UnsupportedOperationException If accessed from a context that doesn't
	 *                                       allow access to the runtime
	 *                                       environment.
	 */
	IuRuntimeConfiguration getEnvironment() throws UnsupportedOperationException;

	/**
	 * Gets the build configuration for the current context.
	 * 
	 * <p>
	 * Build-time configuration is embedded at build time. Build configuration is
	 * are context sensitive, and read once per context ({@link ClassLoader}) on
	 * first use.
	 * </p>
	 * 
	 * @return build configuration
	 * @throws UnsupportedOperationException If accessed from a context that doesn't
	 *                                       allow access to the runtime
	 *                                       environment.
	 */
	IuRuntimeConfiguration getBuildConfiguration() throws UnsupportedOperationException;

	/**
	 * Reads configuration from a secrets engine.
	 * 
	 * @param secret secret name
	 * @return configuration values
	 * @throws IllegalArgumentException      If {@code secret} cannot be read.
	 * @throws UnsupportedOperationException If accessed from a context that doesn't
	 *                                       allow access to the runtime
	 *                                       environment.
	 */
	IuRuntimeConfiguration getSecret(String secret) throws IllegalArgumentException, UnsupportedOperationException;

}
