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
