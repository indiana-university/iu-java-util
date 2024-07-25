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
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.auth.config.IuAuthenticationRealm;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuVault;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import jakarta.json.JsonString;

/**
 * Authentication and authorization root configuration utility.
 */
public class AuthConfig {
	static {
		IuObject.assertNotOpen(AuthConfig.class);
	}

	private static class VaultConfig {
		private final String prefix;
		private final Consumer<?> verifier;
		private final IuVault[] vault;

		private VaultConfig(String prefix, Consumer<?> verifier, IuVault... vault) {
			this.prefix = prefix;
			this.verifier = verifier;
			this.vault = vault;
		}
	}

	private static final Map<String, IuAuthConfig> CONFIG = new HashMap<>();
	private static final Map<Class<?>, VaultConfig> VAULT = new HashMap<>();
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
	 * @param <T>             configuration type
	 * @param prefix          prefix to append to vault key to classify the resource
	 *                        names used by {@link #load(Class, String)}
	 * @param configInterface configuration interface
	 * @param vault           vault to use for loading configuration
	 */
	@SuppressWarnings("unchecked")
	public static synchronized <T> void addVault(String prefix, Class<T> configInterface, IuVault... vault) {
		final Consumer<? super T> verifier;
		if (IuAuthenticationRealm.class.isAssignableFrom(configInterface))
			verifier = ((Consumer<? super T>) (Consumer<IuAuthenticationRealm>) IuAuthenticationRealm::verify);
		else
			verifier = null;

		addVault(prefix, configInterface, verifier, vault);
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
	public static synchronized <T> void addVault(String prefix, Class<T> configInterface, Consumer<? super T> verifier,
			IuVault... vault) {
		if (sealed)
			throw new IllegalStateException("sealed");

		IuObject.require(configInterface, Class::isInterface, configInterface + " is not an interface");
		IuObject.require(Objects.requireNonNull(prefix, "Missing prefix"), a -> a.matches("\\p{Lower}+"),
				"invalid prefix " + prefix);

		if (VAULT.containsKey(configInterface))
			throw new IllegalArgumentException("already configured");

		VAULT.put(configInterface, Objects.requireNonNull(new VaultConfig(prefix + '/', verifier, vault)));
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
		final var vaultConfig = Objects.requireNonNull(VAULT.get(configInterface), "not configured");
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
		return adaptJson((Type) type);
	}

	/**
	 * Provides JSON adapters for components that used
	 * {@link #addVault(String, Class, Consumer, IuVault...)} to register
	 * configuration interfaces for authentication and authorization.
	 * 
	 * @param type type
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static IuJsonAdapter adaptJson(Type type) {
		if (VAULT.containsKey(type)) {
			final var propertyAdapter = IuJsonAdapter.from((Class) type,
					IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, AuthConfig::adaptJson);

			return IuJsonAdapter.from(v -> (v instanceof JsonString) //
					? load((Class) type, ((JsonString) v).getString()) //
					: IuObject.convert(v, propertyAdapter::fromJson), //
					propertyAdapter::toJson);
		}

		// Web Crypto and OAuth/OIDC enums
		else if (AuthMethod.class == type)
			return AuthMethod.JSON;
		else if (GrantType.class == type)
			return GrantType.JSON;
		else if (Algorithm.class == type)
			return Algorithm.JSON;
		else if (Encryption.class == type)
			return Encryption.JSON;
		else if (IuAuthenticationRealm.Type.class == type)
			return IuAuthenticationRealm.Type.JSON;

		// Web Key and PKI support
		else if (WebKey.class == type)
			return WebKey.JSON;
		else if (X509Certificate.class == type)
			return PemEncoded.CERT_JSON;
		else if (X509CRL.class == type)
			return PemEncoded.CRL_JSON;

		else
			return IuJsonAdapter.of(type, AuthConfig::adaptJson);
	}

	private AuthConfig() {
	}
}
