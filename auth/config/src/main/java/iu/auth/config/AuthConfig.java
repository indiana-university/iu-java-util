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
package iu.auth.config;

import java.lang.reflect.Type;
import java.net.URI;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.auth.config.AuthMethod;
import edu.iu.auth.config.GrantType;
import edu.iu.auth.config.IuAuthenticationRealm;
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

//	private static class StorageConfig<T> {
//		private final String prefix;
//		private final Consumer<? super T> verifier;
//		private final Function<URI, T> uriResolver;
//		private final IuJsonAdapter<T> adapter;
//		private final IuVault[] vault;
//
//		private StorageConfig(String prefix, Consumer<? super T> verifier, Function<URI, T> uriResolver,
//				IuJsonAdapter<T> adapter, IuVault... vault) {
//			this.prefix = prefix;
//			this.verifier = verifier;
//			this.uriResolver = uriResolver;
//			this.adapter = adapter;
//			this.vault = vault;
//		}
//	}

	private static final Queue<IuAuthConfig> CONFIG = new ArrayDeque<>();
	private static final Map<Class<?>, AuthConfigRegistration<?>> STORAGE = new HashMap<>();

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
		registerAdapter(IuAuthenticationRealm.Type.class, IuAuthenticationRealm.Type.JSON);
	}

	/**
	 * Registers a configuration descriptor
	 * 
	 * @param config configuration descriptor
	 * @see #get(Class)
	 */
	public static synchronized void register(IuAuthConfig config) {
		if (sealed)
			throw new IllegalStateException("sealed");

		IuObject.assertNotOpen(config.getClass());
		IuObject.requireFinalImpl(config.getClass());

		synchronized (CONFIG) {
			CONFIG.offer(config);
		}
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

		STORAGE.put(type, new StorageConfig<>(null, null, null, adapter));
	}

	/**
	 * Registers an authorization configuration interface that doesn't tie to a
	 * vault configuration name.
	 * 
	 * @param <T>             configuration type
	 * @param configInterface configuration interface
	 */
	public static synchronized <T> void registerInterface(Class<T> configInterface) {
		registerInterface(configInterface, null);
	}

	/**
	 * Registers an authorization configuration interface that doesn't tie to a
	 * vault configuration name.
	 * 
	 * @param <T>             configuration type
	 * @param configInterface configuration interface
	 * @param uriResolver     resolves a {@link JsonString} reference to a
	 *                        configuration object of the registered type
	 */
	public static synchronized <T> void registerInterface(Class<T> configInterface, Function<URI, T> uriResolver) {
		if (sealed)
			throw new IllegalStateException("sealed");

		IuObject.require(configInterface, Class::isInterface, configInterface + " is not an interface");

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
				Objects.requireNonNull(new StorageConfig<>(prefix + '/', verifier, uriResolver, adapter, vault)));

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
	@SuppressWarnings("unchecked")
	public static synchronized <T> void registerInterface(String prefix, Class<T> configInterface, IuVault... vault) {
		final Consumer<? super T> verifier;
		if (IuAuthenticationRealm.class.isAssignableFrom(configInterface))
			verifier = ((Consumer<? super T>) (Consumer<IuAuthenticationRealm>) IuAuthenticationRealm::verify);
		else
			verifier = null;

		registerInterface(prefix, configInterface, verifier, vault);
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
		registerInterface(prefix, configInterface, verifier, null, vault);
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
	 * @param uriResolver     resolves a {@link JsonString} reference to a
	 *                        configuration object of the registered type
	 * @param vault           vault to use for loading configuration
	 */
	public static synchronized <T> void registerInterface(String prefix, Class<T> configInterface,
			Consumer<? super T> verifier, Function<URI, T> uriResolver, IuVault... vault) {
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
				Objects.requireNonNull(new StorageConfig<>(prefix + '/', verifier, uriResolver, adapter, vault)));
	}

	/**
	 * Loads a configuration object from vault.
	 * 
	 * @param <T>             configuration type
	 * @param configInterface configuration interface
	 * @param key             vault key
	 * @return loaded configuration
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T load(Class<T> configInterface, String key) {
		final var vaultConfig = Objects.requireNonNull(STORAGE.get(configInterface), "not configured");

		final T config;
		if (key.indexOf(':') != -1)
			config = configInterface
					.cast(Objects.requireNonNull(
							Objects.requireNonNull(vaultConfig.uriResolver,
									"no URI resolver configured for " + configInterface).apply(URI.create(key)),
							"no configuration found for " + key));
		else
			config = (new Object() {
				T value;
				Throwable error;

				void check(IuVault vault) {
					this.value = IuJson.wrap(
							IuJson.parse(vault.get(vaultConfig.prefix + key).getValue()).asJsonObject(),
							configInterface, AuthConfig::adaptJson);
				}

				T load() {
					for (final var v : IuObject.require(vaultConfig.vault, a -> a.length > 0,
							"No vaults configured for " + configInterface)) {
						error = IuException.suppress(error, () -> check(v));
						if (value != null)
							return value;
					}
					throw IuException.unchecked(error);
				}
			}).load();

		if (vaultConfig.verifier != null)
			((Consumer) vaultConfig.verifier).accept(config);

		return config;
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
			return IuIterable.map(IuIterable.filter(CONFIG, type::isInstance), type::cast);
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
