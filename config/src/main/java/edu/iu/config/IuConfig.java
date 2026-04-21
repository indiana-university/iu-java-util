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
import java.util.function.Consumer;

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

	private static class StorageConfig<T> {
		private final String prefix;
		private final IuJsonAdapter<T> adapter;
		private final IuVault[] vault;
		private final Map<String, T> cache;

		private StorageConfig(String prefix, IuJsonAdapter<T> adapter, Duration cacheTtl, IuVault... vault) {
			this.prefix = prefix;
			this.adapter = adapter;
			this.vault = vault;
			this.cache = new IuCacheMap<>(cacheTtl == null ? Duration.ofSeconds(15L) : cacheTtl);
		}
	}

	private static final Map<Class<?>, StorageConfig<?>> STORAGE = new HashMap<>();
	private static boolean sealed;

	static {
		registerDefaults();
	}

	private static void registerDefaults() {
		registerAdapter(JsonValue.class, IuJsonAdapter.from( //
				a -> a, //
				a -> a));
		registerAdapter(JsonObject.class, IuJsonAdapter.from( //
				a -> a == null //
						? null //
						: a.asJsonObject(), //
				a -> a));
		registerAdapter(WebKey.class, IuJsonAdapter.from( //
				v -> WebKey.parse(v.toString()), //
				v -> IuJson.parse(v.toString()) //
		));
		registerAdapter(X509Certificate.class, IuJsonAdapter.from( //
				v -> PemEncoded.asCertificate(IuText.base64(((JsonString) v).getString())), //
				v -> IuJson.string(IuText.base64(IuException.unchecked(v::getEncoded))) //
		));
		registerAdapter(X509CRL.class, IuJsonAdapter.from( //
				v -> PemEncoded.asCRL(IuText.base64(((JsonString) v).getString())), //
				v -> IuJson.string(IuText.base64(IuException.unchecked(v::getEncoded))) //
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

		if (STORAGE.containsKey(type))
			throw new IllegalArgumentException("already configured");

		STORAGE.put(type, new StorageConfig<>(null, adapter, null));
	}

	/**
	 * Registers an authorization configuration interface that doesn't tie to a
	 * vault configuration name.
	 * 
	 * @param <T>             configuration type
	 * @param configInterface configuration interface
	 */
	public static synchronized <T> void registerInterface(Class<T> configInterface) {
		registerAdapter(configInterface, IuJsonAdapter.from(configInterface,
				IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, IuConfig::adaptJson));
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
	 * @param <T>             configuration type
	 * @param prefix          prefix to append to vault key to classify the resource
	 *                        names used by {@link #load(Class, String)}
	 * @param configInterface configuration interface
	 * @param cacheTtl        time period for caching config objects
	 * @param vault           vault to use for loading configuration
	 */
	public static synchronized <T> void registerInterface(String prefix, Class<T> configInterface, Duration cacheTtl,
			IuVault... vault) {
		if (sealed)
			throw new IllegalStateException("sealed");

		IuObject.require(configInterface, Class::isInterface, configInterface + " is not an interface");
		IuObject.require(Objects.requireNonNull(prefix, "Missing prefix"), a -> a.matches("\\p{Lower}+"),
				"invalid prefix " + prefix);

		if (STORAGE.containsKey(configInterface))
			throw new IllegalArgumentException("already configured");

		final var propertyAdapter = IuJsonAdapter.from(configInterface,
				IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, IuConfig::adaptJson);

		final var adapter = IuJsonAdapter.from(v -> {
			if (v instanceof JsonString)
				return load(configInterface, ((JsonString) v).getString());
			else
				return IuObject.convert(v, propertyAdapter::fromJson);
		}, //
				propertyAdapter::toJson);

		STORAGE.put(configInterface,
				Objects.requireNonNull(new StorageConfig<>(prefix + '/', adapter, cacheTtl, vault)));
	}

	/**
	 * Loads a configuration object from vault.
	 * 
	 * @param <T>             configuration type
	 * @param configInterface configuration interface
	 * @param key             vault key
	 * @return loaded configuration
	 */
	public static <T> T load(Class<T> configInterface, String key) {
		@SuppressWarnings("unchecked") // enforced on put (above)
		final StorageConfig<T> vaultConfig = (StorageConfig<T>) Objects.requireNonNull(STORAGE.get(configInterface),
				"not configured");

		final var value = vaultConfig.cache.get(key);
		if (configInterface.isInstance(value))
			return configInterface.cast(value);

		return (new Object() {
			T value;
			Throwable error;

			void check(IuVault vault) {
				final var keyedValue = vault.get(vaultConfig.prefix + key).getValue();
				final var config = IuJson.parse(keyedValue).asJsonObject();

				final var value = IuJson.wrap(config, configInterface, IuConfig::adaptJson);
				vaultConfig.cache.put(key, value);
				this.value = value;
			}

			T load() {
				for (final var v : vaultConfig.vault) {
					error = IuException.suppress(error, () -> check(v));
					if (value != null)
						return value;
				}
				throw IuException.unchecked(error);
			}
		}).load();
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
	 * {@link #registerInterface(String, Class, Consumer, IuVault...)} to register
	 * configuration interfaces for authentication and authorization.
	 * 
	 * @param type type
	 * @return {@link IuJsonAdapter}
	 */
	public static IuJsonAdapter<?> adaptJson(Type type) {
		if (STORAGE.containsKey(type))
			return STORAGE.get(type).adapter;
		else
			return IuJsonAdapter.of(type, IuConfig::adaptJson);
	}

	private IuConfig() {
	}
}
