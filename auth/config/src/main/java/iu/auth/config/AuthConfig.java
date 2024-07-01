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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import edu.iu.IuObject;
import edu.iu.auth.config.IuAuthenticationRealm;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.Credentials;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.auth.config.IuAuthorizedAudience;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVault;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import jakarta.json.JsonObject;

/**
 * Authentication and authorization root configuration utility.
 */
public class AuthConfig {
	static {
		IuObject.assertNotOpen(AuthConfig.class);
	}

	private static final Map<String, IuAuthConfig> CONFIG = new HashMap<>();
	private static final Map<Class<?>, IuVault> VAULT = new HashMap<>();
	private static boolean sealed;

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
	 * Registers a vault for loading authorization configuration.
	 * 
	 * @param configInterface configuration interface
	 * @param vault           vault to use for loading configuration
	 */
	public static synchronized void addVault(Class<?> configInterface, IuVault vault) {
		if (sealed)
			throw new IllegalStateException("sealed");

		IuObject.require(configInterface, Class::isInterface);

		if (VAULT.containsKey(configInterface))
			throw new IllegalArgumentException("already configured");

		VAULT.put(configInterface, Objects.requireNonNull(vault));
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
		return load(configInterface, key, a -> configInterface);
	}

	/**
	 * Loads a configuration object from vault.
	 * 
	 * @param <T>             configuration type
	 * @param configInterface configuration interface
	 * @param key             vault key
	 * @param specifier       function that determines the correct configuration
	 *                        subinterface based on raw data
	 * @return loaded configuration
	 */
	public static <T> T load(Class<T> configInterface, String key, Function<JsonObject, Class<? extends T>> specifier) {
		final var vault = Objects.requireNonNull(VAULT.get(configInterface), "not configured");
		final var config = IuJson.parse(vault.get(key).getValue()).asJsonObject();
		return IuJson.wrap(config, specifier.apply(config), AuthConfig::adaptJson);
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
	public static <T> IuJsonAdapter<T> adaptJson(Class<T> type) {
		return adaptJson((Type) type);
	}

	/**
	 * Provides additional JSON adapters for configuring the authorization module.
	 * 
	 * @param <T>  target type
	 * @param type type
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	public static <T> IuJsonAdapter<T> adaptJson(Type type) {
		if (type == Credentials.class)
			return (IuJsonAdapter<T>) Credentials.JSON;
		else if ((type instanceof Class) //
				&& IuAuthenticationRealm.class.isAssignableFrom((Class<?>) type))
			return (IuJsonAdapter<T>) IuAuthenticationRealm.JSON;
		else if (IuAuthorizedAudience.class == type)
			return (IuJsonAdapter<T>) IuAuthorizedAudience.JSON;
		else if (AuthMethod.class == type)
			return (IuJsonAdapter<T>) AuthMethod.JSON;
		else if (GrantType.class == type)
			return (IuJsonAdapter<T>) GrantType.JSON;
		else if (WebKey.class == type)
			return (IuJsonAdapter<T>) WebKey.JSON;
		else if (Algorithm.class == type)
			return (IuJsonAdapter<T>) Algorithm.JSON;
		else if (Encryption.class == type)
			return (IuJsonAdapter<T>) Encryption.JSON;
		else
			return IuJsonAdapter.of(type, AuthConfig::adaptJson);
	}

	private AuthConfig() {
	}
}
