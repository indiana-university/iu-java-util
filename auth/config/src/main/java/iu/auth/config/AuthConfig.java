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
package iu.auth.config;

import java.lang.reflect.Type;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuVault;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignedPayload;
import iu.crypt.CryptJsonAdapters;
import iu.crypt.Jwe;
import iu.crypt.JwsBuilder;
import jakarta.json.JsonString;

/**
 * Authentication and authorization root configuration utility.
 */
public class AuthConfig {
	static {
		IuObject.assertNotOpen(AuthConfig.class);
	}

	private static class StorageConfig<T> {
		private final String prefix;
		private final Consumer<? super T> verifier;
		private final IuJsonAdapter<T> adapter;
		private final IuVault[] vault;

		private StorageConfig(String prefix, Consumer<? super T> verifier, IuJsonAdapter<T> adapter, IuVault... vault) {
			this.prefix = prefix;
			this.verifier = verifier;
			this.adapter = adapter;
			this.vault = vault;
		}
	}

	private static final Map<String, IuAuthConfig> CONFIG = new HashMap<>();
	private static final Map<Class<?>, StorageConfig<?>> STORAGE = new HashMap<>();
	private static boolean sealed;

	static {
		registerDefaults();
	}

	private static void registerDefaults() {
		registerAdapter(AuthMethod.class, AuthMethod.JSON);
		registerAdapter(GrantType.class, GrantType.JSON);
		registerAdapter(Algorithm.class, CryptJsonAdapters.ALG);
		registerAdapter(Encryption.class, CryptJsonAdapters.ENC);
		registerAdapter(WebKey.class, CryptJsonAdapters.WEBKEY);
		registerAdapter(WebCryptoHeader.class, CryptJsonAdapters.JOSE);
		registerAdapter(WebEncryption.class, Jwe.JSON);
		registerAdapter(WebSignedPayload.class, JwsBuilder.JSON);
		registerAdapter(X509Certificate.class, CryptJsonAdapters.CERT);
		registerAdapter(X509CRL.class, CryptJsonAdapters.CRL);
	}

	/**
	 * Registers a configuration descriptor for an authentication realm.
	 * 
	 * <p>
	 * Only one verifier may be registered per realm
	 * </p>
	 * 
	 * @param config principal identity verifier
	 */
	public static synchronized void register(IuAuthConfig config) {
		if (sealed)
			throw new IllegalStateException("sealed");

		IuObject.assertNotOpen(config.getClass());
		IuObject.requireFinalImpl(config.getClass());

		final var realm = config.getRealm();
		if (CONFIG.containsKey(realm))
			throw new IllegalArgumentException("already configured");

		CONFIG.put(realm, config);
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

		STORAGE.put(type, new StorageConfig<>(null, null, adapter));
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
				IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, AuthConfig::adaptJson));
	}

	/**
	 * Registers a vault for loading authorization configuration.
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
	 * @param verifier        provides additional verification logic to be apply
	 *                        before returning each loaded instance
	 * @param vault           vault to use for loading configuration
	 */
	public static synchronized <T> void registerInterface(String prefix, Class<T> configInterface,
			Consumer<? super T> verifier, IuVault... vault) {
		if (sealed)
			throw new IllegalStateException("sealed");

		IuObject.require(configInterface, Class::isInterface, configInterface + " is not an interface");
		IuObject.require(Objects.requireNonNull(prefix, "Missing prefix"), a -> a.matches("\\p{Lower}+"),
				"invalid prefix " + prefix);

		if (STORAGE.containsKey(configInterface))
			throw new IllegalArgumentException("already configured");

		final var propertyAdapter = IuJsonAdapter.from(configInterface,
				IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, AuthConfig::adaptJson);

		final var adapter = IuJsonAdapter.from(v -> {
			if (v instanceof JsonString)
				return load(configInterface, ((JsonString) v).getString());
			else
				return IuObject.convert(v, propertyAdapter::fromJson);
		}, //
				propertyAdapter::toJson);

		STORAGE.put(configInterface,
				Objects.requireNonNull(new StorageConfig<>(prefix + '/', verifier, adapter, vault)));
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
		final var vaultConfig = Objects.requireNonNull(STORAGE.get(configInterface), "not configured");
		return (new Object() {
			T value;
			Throwable error;

			@SuppressWarnings({ "unchecked", "rawtypes" })
			void check(IuVault vault) {
				final var keyedValue = vault.get(vaultConfig.prefix + key).getValue();
				final var config = IuJson.parse(keyedValue).asJsonObject();

				final var value = IuJson.wrap(config, configInterface, AuthConfig::adaptJson);
				if (vaultConfig.verifier != null)
					((Consumer) vaultConfig.verifier).accept(value);

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
	 * Gets the configuration registered for a realm.
	 * 
	 * @param <T>   configuration type
	 * @param realm authentication realm
	 * @return {@link IuAuthConfig} by realm
	 */
	@SuppressWarnings("unchecked")
	public static <T extends IuAuthConfig> T get(String realm) {
		if (sealed)
			return (T) Objects.requireNonNull(CONFIG.get(realm), "invalid realm");
		else
			throw new IllegalStateException("not sealed");
	}

	/**
	 * Finds configuration registered by interface.
	 * 
	 * @param <T>  configuration type
	 * @param type type
	 * @return {@link IuAuthConfig} by type
	 */
	public static <T extends IuAuthConfig> Iterable<T> get(Class<T> type) {
		if (sealed)
			return () -> CONFIG.values().stream().filter(a -> type.isInstance(a)).map(a -> type.cast(a)).iterator();
		else
			throw new IllegalStateException("not sealed");
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
			return IuJsonAdapter.of(type, AuthConfig::adaptJson);
	}

	private AuthConfig() {
	}
}
