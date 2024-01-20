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
package iu.type;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.type.IuComponent;
import jakarta.json.JsonString;

/**
 * Configures internal type introspection, component, and JSON-B features.
 */
public final class TypeProperties {

	/**
	 * Determines if {@link Class} should be allowed as a value type for JSON-B
	 * serialization/deserialization.
	 * 
	 * <p>
	 * Class serialization <em>may</em> be useful for runtime configuration and
	 * similar class metadata descriptors. It is dangerous and <em>must</em> be
	 * disabled in application components that interact with user input.
	 * </p>
	 * 
	 * <p>
	 * When allowed, classes will:
	 * </p>
	 * <ul>
	 * <li>serialize to {@link JsonString} containing {@link Class#getName()}.</li>
	 * <li>deserialize from {@link JsonString} using
	 * {@link ClassLoader#loadClass(String)} in the
	 * {@link Thread#getContextClassLoader() current thread's context}.</li>
	 * </ul>
	 * 
	 * <p>
	 * Default value is {@code false}
	 * </p>
	 */
	public static final String JSONB_CLASS_SERIALIZATION_ALLOWED = "jsonb.classSerialziationAllowed";

	/**
	 * Determines whether or not JSON-B serialization/deserialization failures
	 * result in {@link IllegalArgumentException} or a {@link Level#FINEST} level
	 * log event.
	 * 
	 * <p>
	 * Default is {@code true}
	 * </p>
	 */
	public static final String JSONB_FAILSAFE = "jsonb.failsafe";

	private static final Map<ClassLoader, Properties> PROPERTIES = new WeakHashMap<>();
	private static final Set<String> COMPONENT_SAFE = Set.of(JSONB_FAILSAFE);

	private static Properties getProperties() {
		final var loader = Thread.currentThread().getContextClassLoader();

		final var cachedProperties = PROPERTIES.get(loader);
		if (cachedProperties != null)
			return cachedProperties;

		final var properties = new Properties();
		IuException.unchecked(() -> {
			for (var propertyResource : IuIterable.of(
					() -> IuException.unchecked(() -> loader.getResources("META-INF/iu-type.properties").asIterator())))
				try (var propertyResourceStream = propertyResource.openStream()) {
					properties.load(propertyResourceStream);
				}
		});

		synchronized (PROPERTIES) {
			PROPERTIES.put(loader, properties);
		}

		return properties;
	}

	/**
	 * Determines whether or not a type property is safe for use by application
	 * components.
	 * 
	 * @param propertyName property name
	 * @return true if the type property <em>may</em> be discoverable by
	 *         {@link IuComponent#classLoader()}; else false
	 */
	public static boolean isComponentSafe(String propertyName) {
		return COMPONENT_SAFE.contains(propertyName);
	}

	/**
	 * Checks for a {@link Boolean#parseBoolean(String) true} property value in a
	 * resource named {@code META-INF/iu-type.properties} defined the current
	 * thread's {@link Thread#getContextClassLoader() context ClassLoader}.
	 * 
	 * @param key property key
	 * @return property value
	 */
	public static boolean is(String key) {
		final var value = get(key);
		return value != null && Boolean.parseBoolean(value);
	}

	/**
	 * Gets a property value from a resource named
	 * {@code META-INF/iu-type.properties} defined the current thread's
	 * {@link Thread#getContextClassLoader() context ClassLoader}.
	 * 
	 * @param key property key
	 * @return property value
	 */
	public static String get(String key) {
		return getProperties().getProperty(key);
	}

	private TypeProperties() {
	}

}
