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
package edu.iu;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Provides basic utilities for inspecting the runtime environment.
 * 
 * <p>
 * Runtime properties should be used sparingly, sufficient only to support
 * bootstrapping the application's configuration management layer.
 * </p>
 */
public class IuRuntimeEnvironment {

	private static final Pattern SYSTEM_PROPERTY_REGEX = Pattern.compile("[a-zA-Z][\\w\\.\\-]*");

	/**
	 * Checks for the presence of a system property, then defaults to an environment
	 * variable if not set. If the system property is set, but blank, returns null.
	 * 
	 * @param name property name. Must start with a letter and contain only letters,
	 *             digits, '_', '-', and '.'. Will be converted to upper case, '.'
	 *             and '-' replaced with '_', for checking the environment.
	 * @return system property value if set, environment variable if not set, null
	 *         if blank or both are missing
	 */
	public static String envOptional(String name) {
		final var env = envName(name); // validates system property name
		final var value = System.getProperty(name, System.getenv(env));
		return value == null || value.isBlank() ? null : value;
	}

	/**
	 * Checks for the presence of a system property, then defaults to an environment
	 * variable if not set.
	 * 
	 * @param name property name
	 * @return system property value if set, environment variable if not set
	 * @throws NullPointerException if the system property is blank or both are
	 *                              missing
	 */
	public static String env(String name) {
		return Objects.requireNonNull(envOptional(name),
				"Missing system property " + name + " or environment variable " + envName(name));
	}

	private static String envName(String system) {
		if (!SYSTEM_PROPERTY_REGEX.matcher(system).matches())
			throw new IllegalArgumentException();
		else
			return system.toUpperCase().replace('.', '_').replace('-', '_');
	}

	private IuRuntimeEnvironment() {
	}
}
