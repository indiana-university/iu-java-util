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
	 * @param <T>                 value type
	 * @param name                property name
	 * @param textToValueFunction converts a non-null property value to the target
	 *                            type; not applied to null. A null return rejects
	 *                            the configured value.
	 * @return converted system property value if set, converted environment
	 *         variable if not set
	 * @throws NullPointerException if the system property is blank, both are
	 *                              missing, or {@code textToValueFunction} returns
	 *                              null
	 */
	public static <T> T env(String name, Function<String, T> textToValueFunction) {
		final var value = Objects.requireNonNull(envOptional(name),
				() -> "Missing system property " + name + " or environment variable " + envName(name));

		// reported separately from a missing value, since the opposite is true: the
		// property is set and its value was rejected. The value is not named because
		// this accessor reads secrets as well as switches
		return Objects.requireNonNull(textToValueFunction.apply(value),
				() -> "Invalid system property " + name + " or environment variable " + envName(name));
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
	 * Reads an optional boolean flag.
	 *
	 * <p>
	 * Only the exact value {@code true} enables a flag. A flag gates behavior that
	 * is off by default, so a typo has to read as off rather than as anything a
	 * truthiness rule might accept.
	 * </p>
	 *
	 * <p>
	 * Like every accessor in this class, the value is resolved as a system property
	 * first and only then as an environment variable. A flag intended to be
	 * controlled by the deployment environment can therefore also be set with
	 * {@code -Dname=true}, or by {@link System#setProperty(String, String)} from
	 * anywhere in the JVM at any point in its life. A flag that must not be
	 * settable in-process has to read {@link System#getenv(String)} directly
	 * instead.
	 * </p>
	 *
	 * @param name property name, resolved as a system property first and then as
	 *             the corresponding environment variable; see
	 *             {@link #envOptional(String)}
	 * @return true when the value is exactly {@code true}; false when it is any
	 *         other value, is blank, or is unset
	 */
	public static boolean flag(String name) {
		return "true".equals(envOptional(name));
	}

	/**
	 * Reads an optional positive {@code int} bound.
	 *
	 * <p>
	 * Equivalent to {@link #bound(String, int, int)} with {@link Integer#MAX_VALUE}
	 * as the upper limit: any positive value that fits in an {@code int} is
	 * accepted.
	 * </p>
	 *
	 * @param name         property name, resolved as a system property first and
	 *                     then as the corresponding environment variable; see
	 *                     {@link #envOptional(String)}
	 * @param defaultValue value to apply when neither is set
	 * @return configured bound, or {@code defaultValue}
	 * @throws IllegalArgumentException if the configured value is not an integer,
	 *                                  or is less than one &mdash; including zero,
	 *                                  which would configure a bound that permits
	 *                                  nothing
	 */
	public static int bound(String name, int defaultValue) {
		return (int) longBound(name, defaultValue, Integer.MAX_VALUE);
	}

	/**
	 * Reads an optional positive {@code int} bound with an upper limit.
	 *
	 * <p>
	 * A value above {@code maxValue} is rejected rather than clamped, so that a
	 * setting too large to honor is reported where it was configured instead of
	 * quietly taking effect as something other than what was asked for. Every
	 * rejection names the property, since an operator setting several of these
	 * needs to be told which one was refused rather than only that some number
	 * would not do.
	 * </p>
	 *
	 * @param name         property name, resolved as a system property first and
	 *                     then as the corresponding environment variable; see
	 *                     {@link #envOptional(String)}
	 * @param defaultValue value to apply when neither is set
	 * @param maxValue     largest accepted value
	 * @return configured bound, or {@code defaultValue}
	 * @throws IllegalArgumentException if the configured value is not an integer,
	 *                                  is less than one, or is greater than
	 *                                  {@code maxValue}
	 */
	public static int bound(String name, int defaultValue, int maxValue) {
		return (int) longBound(name, defaultValue, maxValue);
	}

	/**
	 * Reads an optional positive {@code long} bound.
	 *
	 * <p>
	 * Equivalent to {@link #longBound(String, long, long)} with
	 * {@link Long#MAX_VALUE} as the upper limit.
	 * </p>
	 *
	 * @param name         property name, resolved as a system property first and
	 *                     then as the corresponding environment variable; see
	 *                     {@link #envOptional(String)}
	 * @param defaultValue value to apply when neither is set
	 * @return configured bound, or {@code defaultValue}
	 * @throws IllegalArgumentException if the configured value is not a number, or
	 *                                  is less than one
	 */
	public static long longBound(String name, long defaultValue) {
		return longBound(name, defaultValue, Long.MAX_VALUE);
	}

	/**
	 * Reads an optional positive {@code long} bound with an upper limit.
	 *
	 * <p>
	 * The accessor the other bounds delegate to; see
	 * {@link #bound(String, int, int)} for why an out-of-range value is rejected
	 * rather than clamped.
	 * </p>
	 *
	 * @param name         property name, resolved as a system property first and
	 *                     then as the corresponding environment variable; see
	 *                     {@link #envOptional(String)}
	 * @param defaultValue value to apply when neither is set
	 * @param maxValue     largest accepted value
	 * @return configured bound, or {@code defaultValue}
	 * @throws IllegalArgumentException if the configured value is not a number, is
	 *                                  less than one, or is greater than
	 *                                  {@code maxValue}
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
