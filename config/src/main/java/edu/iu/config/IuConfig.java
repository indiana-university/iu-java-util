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

import java.lang.reflect.Type;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuVault;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Secure configuration utility.
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
					final var keyedValue = vault.get(prefix + key).getValue();
					final var config = IuJson.parse(keyedValue).asJsonObject();

					final var value = IuJson.wrap(config, configType, IuConfig::adaptJson);
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

	private static final Map<Class<?>, IuJsonAdapter<?>> JSON = new HashMap<>();
	private static final Map<Class<?>, BaseConfig<?>> CONFIG = new HashMap<>();
	private static boolean sealed;

	static {
		registerDefaults();
	}

	private static void registerDefaults() {
		registerAdapter(JsonValue.class, IuJsonAdapter.from( //
				a -> a, //
				a -> a));
		registerAdapter(JsonArray.class, IuJsonAdapter.from( //
				a -> a == null //
						? null //
						: a.asJsonArray(), //
				a -> a));
		registerAdapter(JsonObject.class, IuJsonAdapter.from( //
				a -> a == null //
						? null //
						: a.asJsonObject(), //
				a -> a));
		registerAdapter(WebKey.class, IuJsonAdapter.from( //
				v -> v == null //
						? null //
						: WebKey.parse(v.toString()), //
				v -> v == null //
						? null //
						: IuJson.parse(v.toString()) //
		));
		registerAdapter(X509Certificate.class, IuJsonAdapter.from( //
				v -> v == null //
						? null //
						: PemEncoded.asCertificate(IuText.base64(((JsonString) v).getString())), //
				v -> v == null //
						? null //
						: IuJson.string(IuText.base64(IuException.unchecked(v::getEncoded))) //
		));
		registerAdapter(X509CRL.class, IuJsonAdapter.from( //
				v -> v == null //
						? null //
						: PemEncoded.asCRL(IuText.base64(((JsonString) v).getString())), //
				v -> v == null //
						? null //
						: IuJson.string(IuText.base64(IuException.unchecked(v::getEncoded))) //
		));
	}

	/**
	 * Registers a JSON type adapter for a non-interface configuration class, for
	 * example a custom enum.
	 * 
	 * @param <T>     type
	 * @param type    class
	 * @param adapter {@link IuJsonAdapter}
	 */
	public static synchronized <T> void registerAdapter(Class<T> type, IuJsonAdapter<T> adapter) {
		if (sealed)
			throw new IllegalStateException("sealed");

		if (JSON.containsKey(type))
			throw new IllegalArgumentException("already configured");

		JSON.put(type, adapter);
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
		if (sealed)
			throw new IllegalStateException("sealed");

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
	 * @param <T>        configuration type
	 * @param prefix     prefix to append to vault key to classify the resource
	 *                   names used by {@link #load(Class, String)}
	 * @param configType configuration type
	 * @param cacheTtl   time period for caching config objects
	 * @param vault      vault to use for loading configuration
	 */
	public static synchronized <T> void registerInterface(String prefix, Class<T> configType, Duration cacheTtl,
			IuVault... vault) {
		if (sealed)
			throw new IllegalStateException("sealed");

		final var propertyAdapter = IuJsonAdapter.from(configType, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES,
				IuConfig::adaptJson);
		final var adapter = IuJsonAdapter.from(v -> {
			if (v instanceof JsonString)
				return load(configType, ((JsonString) v).getString());
			else
				return IuObject.convert(v, propertyAdapter::fromJson);
		}, //
				propertyAdapter::toJson);
		registerAdapter(configType, adapter);

		IuObject.require(Objects.requireNonNull(prefix, "Missing prefix"), a -> a.matches("\\p{Lower}+"),
				"invalid prefix " + prefix);

		if (CONFIG.containsKey(configType))
			throw new IllegalArgumentException("already configured");

		CONFIG.put(configType, Objects.requireNonNull(new StorageConfig<>(prefix + '/', configType, cacheTtl, vault)));
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
	 * configurations can be registered. Configuration state is controlled by the
	 * auth module.
	 * </p>
	 */
	public static synchronized void seal() {
		sealed = true;
	}

	/**
	 * Provides additional JSON adapters for configuring the authorization module.
	 * 
	 * @param <T>  target type
	 * @param type type
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	public static <T> IuJsonAdapter<T> adaptJson(Class<T> type) {
		return (IuJsonAdapter<T>) adaptJson((Type) type);
	}

	/**
	 * Provides JSON adapters for components that used
	 * {@link #registerInterface(String, Class, Duration, IuVault...)} to register
	 * configuration interfaces for authentication and authorization. Registered
	 * types without a dedicated adapter, such as types registered with
	 * {@link #registerFactory(Class, Function)}, use the standard adapter for the
	 * requested type.
	 * 
	 * @param type type
	 * @return {@link IuJsonAdapter}
	 */
	public static IuJsonAdapter<?> adaptJson(Type type) {
		final var adapter = JSON.get(type);
		if (adapter != null)
			return adapter;

		if (type instanceof Class) {
			final var c = (Class<?>) type;
			if (!IuObject.isPlatformName(c.getName()))
				return IuJsonAdapter.from((Class<?>) type, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES,
						IuConfig::adaptJson);
		}

		return IuJsonAdapter.of(type, IuConfig::adaptJson);
	}

	private IuConfig() {
	}
}
