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
package edu.iu;

import java.util.Objects;
import java.util.function.Function;
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
	 * @param <T>                 value type
	 * @param name                property name. Must start with a letter and
	 *                            contain only letters, digits, '_', '-', and '.'.
	 *                            Will be converted to upper case, '.' and '-'
	 *                            replaced with '_', for checking the environment.
	 * @param textToValueFunction converts a non-null property value to the target
	 *                            type; not applied to null
	 * @return system property value if set, environment variable if not set, null
	 *         if blank or both are missing
	 */
	public static <T> T envOptional(String name, Function<String, T> textToValueFunction) {
		final var env = envName(name); // validates system property name
		final var value = System.getProperty(name, System.getenv(env));
		return value == null || value.isBlank() ? null : textToValueFunction.apply(value);
	}

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
		return envOptional(name, a -> a);
	}

	/**
	 * Checks for the presence of a system property, then defaults to an environment
	 * variable if not set.
	 * 
	 * @param <T>  value type
	 * @param name property name
	 * @return system property value if set, environment variable if not set
	 * @param textToValueFunction converts a non-null property value to the target
	 *                            type; not applied to null
	 * @throws NullPointerException if the system property is blank or both are
	 *                              missing
	 */
	public static <T> T env(String name, Function<String, T> textToValueFunction) {
		return Objects.requireNonNull(envOptional(name, textToValueFunction),
				"Missing system property " + name + " or environment variable " + envName(name));

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
		return env(name, a -> a);
	}

	private static String envName(String system) {
		if (!SYSTEM_PROPERTY_REGEX.matcher(system).matches())
			throw new IllegalArgumentException();
		else
			return system.toUpperCase().replace('.', '_').replace('-', '_');
	}

	/**
	 * Reads one optional boolean flag.
	 *
	 * <p>
	 * Only the exact value {@code true} enables a flag. Every one of these turns
	 * off a protection or trusts a header, so a typo has to read as "off" rather
	 * than as anything a truthiness rule might accept.
	 * </p>
	 *
	 * @param name environment variable name
	 * @return true when the variable is exactly {@code true}; else false
	 */
	public static boolean flag(String name) {
		return "true".equals(envOptional(name));
	}

	/**
	 * Reads one optional positive-integer bound.
	 *
	 * <p>
	 * Shared by every numeric setting the container accepts, so that each reports a
	 * bad value the same way &mdash; naming the variable, since an operator setting
	 * several of these needs to be told which one was rejected rather than only
	 * that some number would not parse.
	 * </p>
	 *
	 * @param name         environment variable name
	 * @param defaultValue value to apply when the variable is unset
	 * @return configured bound, or {@code defaultValue}
	 * @throws IllegalArgumentException if the configured value is not a positive
	 *                                  integer &mdash; including zero, which would
	 *                                  configure a bound that permits nothing
	 */
	public static int bound(String name, int defaultValue) {
		return (int) longBound(name, defaultValue, Integer.MAX_VALUE);
	}

	/**
	 * Reads one optional positive-integer bound.
	 *
	 * <p>
	 * Shared by every numeric setting the container accepts, so that each reports a
	 * bad value the same way &mdash; naming the variable, since an operator setting
	 * several of these needs to be told which one was rejected rather than only
	 * that some number would not parse.
	 * </p>
	 *
	 * @param name         environment variable name
	 * @param defaultValue value to apply when the variable is unset
	 * @param maxValue     maximum value to allow
	 * @return configured bound, or {@code defaultValue}
	 * @throws IllegalArgumentException if the configured value is not a positive
	 *                                  integer &mdash; including zero, which would
	 *                                  configure a bound that permits nothing
	 */
	public static int bound(String name, int defaultValue, int maxValue) {
		return (int) longBound(name, defaultValue, maxValue);
	}

	/**
	 * Reads one optional positive-long bound.
	 *
	 * @param name         environment variable name
	 * @param defaultValue value to apply when the variable is unset
	 * @return configured bound, or {@code defaultValue}
	 * @throws IllegalArgumentException if the configured value is not a positive
	 *                                  number
	 */
	public static long longBound(String name, long defaultValue) {
		return longBound(name, defaultValue, Long.MAX_VALUE);
	}

	/**
	 * Reads one optional positive-long bound.
	 *
	 * @param name         environment variable name
	 * @param defaultValue value to apply when the variable is unset
	 * @param maxValue     upper limit
	 * @return configured bound, or {@code defaultValue}
	 * @throws IllegalArgumentException if the configured value is not a positive
	 *                                  number
	 */
	public static long longBound(String name, long defaultValue, long maxValue) {
		final var configured = envOptional(name);
		if (configured == null)
			return defaultValue;

		final long value;
		try {
			value = Long.parseLong(configured.strip());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid " + name + " " + configured, e);
		}
		if (value < 1 //
				|| value > maxValue)
			throw new IllegalArgumentException("Invalid " + name + " " + configured);
		return value;
	}

	private IuRuntimeEnvironment() {
	}
}
