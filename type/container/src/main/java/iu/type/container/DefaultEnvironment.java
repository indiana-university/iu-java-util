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
package iu.type.container;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuRuntimeEnvironment;
import iu.type.container.spi.IuEnvironment;

/**
 * Default implementation of {@link IuEnvironment} to use when not provided by
 * the current thread's context.
 */
public class DefaultEnvironment implements IuEnvironment {

	private final static Logger LOG = Logger.getLogger(DefaultEnvironment.class.getName());

	private record Key(String application, String environment) {
	}

	private final Path config;
	private final String application;
	private final String environment;
	private final Map<Key, Map<String, String>> props = new LinkedHashMap<>();

	/**
	 * Constructor.
	 */
	public DefaultEnvironment() {
		final var configPathName = IuRuntimeEnvironment.envOptional("iu.config");
		if (configPathName == null)
			config = null;
		else
			config = Path.of(configPathName);

		application = IuRuntimeEnvironment.envOptional("iu.application");
		environment = IuRuntimeEnvironment.envOptional("iu.environment");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Map<String, String> props(String application, String environment) {
		if (config == null)
			return Collections.emptyMap();

		final var key = new Key(application, environment);

		final Path file;
		if (application == null)
			file = config.resolve("environment.properties");
		else if (environment == null)
			file = config.resolve(application + ".properties");
		else
			file = config.resolve(application + '-' + environment + ".properties");

		var props = this.props.get(key);
		if (props == null)
			if (Files.exists(file))
				props = IuException.unchecked(() -> {
					final var properties = new Properties();
					try (final var in = Files.newInputStream(file)) {
						properties.load(in);
					}

					final var newProps = Collections.unmodifiableMap((Map) properties);
					synchronized (this.props) {
						this.props.put(key, newProps);
					}
					LOG.config("loaded " + file);

					return newProps;
				});
			else {
				synchronized (this.props) {
					this.props.put(key, props = Collections.emptyMap());
				}
				LOG.config("missing " + file);
			}

		return props;
	}

	/**
	 * Converts a property value to the resource type.
	 * 
	 * @param <T>  resource type
	 * @param type resource class
	 * @param text text property value
	 * @return converted value
	 */
	@SuppressWarnings("unchecked")
	static <T> T convert(Class<T> type, String text) {
		if (text == null)
			return null;

		if (type == String.class)
			return (T) text;
		if (type == boolean.class || type == Boolean.class)
			return (T) Boolean.valueOf(text);
		if (type == int.class || type == Integer.class)
			return (T) Integer.valueOf(text);
		if (type == long.class || type == Long.class)
			return (T) Long.valueOf(text);
		if (type == BigDecimal.class)
			return (T) new BigDecimal(text);
		if (type == URI.class)
			return (T) URI.create(text);
		if (type == Duration.class)
			return (T) Duration.parse(text);

		LOG.finest(() -> "unsupported " + type.getName());
		return null;
	}

	@Override
	public <T> T resolve(String name, Class<T> type) {
		T value = convert(type, props(application, environment).get(name));
		if (value != null)
			return value;

		value = convert(type, props(application, null).get(name));
		if (value != null)
			return value;

		value = convert(type, props(null, null).get(name));
		if (value != null)
			return value;

		return null;
	}

	@Override
	public String toString() {
		return "DefaultEnvironment [config=" + config + ']';
	}

}
