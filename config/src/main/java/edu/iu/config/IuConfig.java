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
package edu.iu.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVault;
import edu.iu.crypt.Init;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.stream.JsonParser.Event;

/**
 * Secure configuration utility.
 *
 * <p>
 * Configuration binds from JSON through {@link #jsonb()}: the web crypto
 * configuration, {@link Init#jsonbConfig()}, with property names in snake_case
 * and dates in ISO-8601, plus the components registered here.
 * </p>
 */
public class IuConfig {
	static {
		IuObject.assertNotOpen(IuConfig.class);
	}

	private abstract static class BaseConfig<T> {
		final Map<String, T> cache;

		private BaseConfig(Duration cacheTtl) {
			this.cache = new IuCacheMap<>(cacheTtl == null ? Duration.ofSeconds(15L) : cacheTtl);
		}

		abstract T load(String key);
	}

	private static class StorageConfig<T> extends BaseConfig<T> {
		private final String prefix;
		private final Class<T> configType;
		private final IuVault[] vault;

		private StorageConfig(String prefix, Class<T> configType, Duration cacheTtl, IuVault... vault) {
			super(cacheTtl);
			this.prefix = prefix;
			this.configType = configType;
			this.vault = vault;
		}

		T load(String key) {
			final var value = cache.get(key);
			if (value != null)
				return value;

			return (new Object() {
				T value;
				Throwable error;

				void check(IuVault vault) {
					final var value = jsonb().fromJson(vault.get(prefix + key).getValue(), configType);
					cache.put(key, value);
					this.value = value;
				}

				T load() {
					for (final var v : vault) {
						error = IuException.suppress(error, () -> check(v));
						if (value != null)
							return value;
					}
					throw IuException.unchecked(error);
				}
			}).load();
		}

	}

	private static class FactoryConfig<T> extends BaseConfig<T> {
		private final Function<String, T> load;

		private FactoryConfig(Function<String, T> load, Duration cacheTtl) {
			super(cacheTtl);
			this.load = load;
		}

		T load(String key) {
			synchronized (cache) {
				var value = cache.get(key);
				if (value == null) {
					value = load.apply(key);
					cache.put(key, value);
				}
				return value;
			}
		}
	}

	private static final List<JsonbAdapter<?, ?>> ADAPTERS = new ArrayList<>();
	private static final List<JsonbSerializer<?>> SERIALIZERS = new ArrayList<>();
	private static final List<JsonbDeserializer<?>> DESERIALIZERS = new ArrayList<>();
	private static final Map<Class<?>, BaseConfig<?>> CONFIG = new HashMap<>();
	private static boolean sealed;
	private static Jsonb jsonb;

	/**
	 * Registers a JSON-B adapter for configuration binding, for example for a
	 * custom value type.
	 *
	 * <p>
	 * A lambda has no type arguments to name the types it adapts; wrap it with
	 * {@link IuJsonAdapter#typedAdapter(java.lang.reflect.Type, java.lang.reflect.Type, JsonbAdapter)}.
	 * </p>
	 *
	 * @param adapter {@link JsonbAdapter}
	 * @throws IllegalStateException if sealed
	 */
	public static synchronized void registerAdapter(JsonbAdapter<?, ?> adapter) {
		requireNotSealed();
		ADAPTERS.add(Objects.requireNonNull(adapter, "Missing adapter"));
	}

	/**
	 * Registers a JSON-B serializer for configuration binding.
	 *
	 * <p>
	 * A lambda has no type argument to name the type it serializes; wrap it with
	 * {@link IuJsonAdapter#typedSerializer(java.lang.reflect.Type, JsonbSerializer)}.
	 * </p>
	 *
	 * @param serializer {@link JsonbSerializer}
	 * @throws IllegalStateException if sealed
	 */
	public static synchronized void registerSerializer(JsonbSerializer<?> serializer) {
		requireNotSealed();
		SERIALIZERS.add(Objects.requireNonNull(serializer, "Missing serializer"));
	}

	/**
	 * Registers a JSON-B deserializer for configuration binding.
	 *
	 * <p>
	 * A lambda has no type argument to name the type it deserializes; wrap it with
	 * {@link IuJsonAdapter#typedDeserializer(java.lang.reflect.Type, JsonbDeserializer)}.
	 * </p>
	 *
	 * @param deserializer {@link JsonbDeserializer}
	 * @throws IllegalStateException if sealed
	 */
	public static synchronized void registerDeserializer(JsonbDeserializer<?> deserializer) {
		requireNotSealed();
		DESERIALIZERS.add(Objects.requireNonNull(deserializer, "Missing deserializer"));
	}

	/**
	 * Registers factory method for a configuration type.
	 *
	 * @param <T>        configuration type
	 * @param configType configuration type
	 * @param load       factory method handle
	 */
	public static synchronized <T> void registerFactory(Class<T> configType, Function<String, T> load) {
		registerFactory(configType, load, null);
	}

	/**
	 * Registers factory method for a configuration type.
	 *
	 * <p>
	 * When concurrent callers request the same uncached key, the factory is invoked
	 * only once; callers that arrive while it is loading use the cached object once
	 * the load completes.
	 * </p>
	 *
	 * @param <T>        configuration type
	 * @param configType configuration type
	 * @param load       factory method handle
	 * @param cacheTtl   time period for caching config objects
	 */
	public static synchronized <T> void registerFactory(Class<T> configType, Function<String, T> load,
			Duration cacheTtl) {
		requireNotSealed();

		if (CONFIG.containsKey(configType))
			throw new IllegalArgumentException("already configured");

		CONFIG.put(configType, new FactoryConfig<>(Objects.requireNonNull(load, "Missing factory"), cacheTtl));
	}

	/**
	 * Registers a vault for loading authorization configuration using the default
	 * cache TTL of 15 seconds.
	 *
	 * @param <T>             configuration type
	 * @param prefix          prefix to append to vault key to classify the resource
	 *                        names used by {@link #load(Class, String)}
	 * @param configInterface configuration interface
	 * @param vault           vault to use for loading configuration
	 */
	public static synchronized <T> void registerInterface(String prefix, Class<T> configInterface, IuVault... vault) {
		registerInterface(prefix, configInterface, null, vault);
	}

	/**
	 * Registers a vault for loading authorization configuration.
	 *
	 * <p>
	 * Wherever the configuration type is bound, a JSON string refers to a stored
	 * value by key, loaded by {@link #load(Class, String)}; any other value binds
	 * by the type's own conversion, which for a configuration interface reads a
	 * JSON object with snake_case property names.
	 * </p>
	 *
	 * @param <T>        configuration type
	 * @param prefix     prefix to append to vault key to classify the resource
	 *                   names used by {@link #load(Class, String)}
	 * @param configType configuration type
	 * @param cacheTtl   time period for caching config objects
	 * @param vault      vault to use for loading configuration
	 */
	public static synchronized <T> void registerInterface(String prefix, Class<T> configType, Duration cacheTtl,
			IuVault... vault) {
		requireNotSealed();

		IuObject.require(Objects.requireNonNull(prefix, "Missing prefix"), a -> a.matches("\\p{Lower}+"),
				"invalid prefix " + prefix);

		if (CONFIG.containsKey(configType))
			throw new IllegalArgumentException("already configured");

		// a string refers to a stored value; anything else passes down the chain, to
		// the type's own conversion
		DESERIALIZERS.add(IuJsonAdapter.<T>typedDeserializer(configType, (parser, context, type) -> {
			if (Event.VALUE_STRING.equals(parser.currentEvent()))
				return load(configType, parser.getString());
			else
				return context.deserialize(configType, parser);
		}));
		CONFIG.put(configType, new StorageConfig<>(prefix + '/', configType, cacheTtl, vault));
	}

	/**
	 * Loads a configuration object from vault.
	 *
	 * @param <T>        configuration type
	 * @param configType configuration interface
	 * @param key        vault key
	 * @return loaded configuration
	 */
	public static <T> T load(Class<T> configType, String key) {
		return configType.cast(Objects.requireNonNull(CONFIG.get(configType), "not configured").load(key));
	}

	/**
	 * Seals the authentication and authorization configuration.
	 *
	 * <p>
	 * Until sealed, no per-realm configurations can be used. Once sealed, no new
	 * configurations or components can be registered. Configuration state is
	 * controlled by the auth module.
	 * </p>
	 */
	public static synchronized void seal() {
		sealed = true;
	}

	/**
	 * Gets the {@link Jsonb} instance that binds configuration.
	 *
	 * <p>
	 * Created on first use from {@link Init#jsonbConfig()} and the
	 * components registered, which seals registration.
	 * </p>
	 *
	 * @return {@link Jsonb}
	 */
	public static synchronized Jsonb jsonb() {
		if (jsonb == null) {
			seal();
			jsonb = JsonbBuilder.newBuilder("iu.client.jsonb.IuJsonbProvider")
					.withConfig(Init.<JsonbConfig>jsonbConfig() //
							.withAdapters(ADAPTERS.toArray(JsonbAdapter[]::new)) //
							.withSerializers(SERIALIZERS.toArray(JsonbSerializer[]::new)) //
							.withDeserializers(DESERIALIZERS.toArray(JsonbDeserializer[]::new)))
					.build();
		}
		return jsonb;
	}

	private static void requireNotSealed() {
		if (sealed)
			throw new IllegalStateException("sealed");
	}

	private IuConfig() {
	}
}
