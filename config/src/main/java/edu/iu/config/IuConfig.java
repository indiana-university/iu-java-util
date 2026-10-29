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
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.GenericTypes;
import edu.iu.IuCacheMap;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuRefreshableCache;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonArrayAdapter;
import edu.iu.client.IuVault;
import edu.iu.crypt.Init;
import edu.iu.validation.IuValidator;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Secure configuration utility.
 *
 * <p>
 * Configuration has a one-way lifecycle, and like a
 * {@link ModuleLayer.Controller}, everything before sealing is the
 * application's responsibility. IuConfig is embedded in the application and
 * loaded by the application's own class loader; it doesn't synchronize setup.
 * </p>
 * <ol>
 * <li>Register each configuration type, with
 * {@link #registerInterface(String, Class, IuVault...)} for a type stored in
 * vault or {@link #registerFactory(Class, Function)} for one created by the
 * application.</li>
 * <li>Optionally, {@link #configureJsonb(Consumer) customize} how configuration
 * binds.</li>
 * <li>Call {@link #seal()} once. Sealing is required and can't be undone: no
 * type can be registered afterward, and no configuration can be loaded
 * before.</li>
 * <li>{@link #load(Class, String) Load} configuration by type and key.</li>
 * </ol>
 *
 * <p>
 * The first three steps belong in one block, on one thread, early in
 * application initialization, ending with {@link #seal()} <em>before</em> the
 * application creates thread pools or starts any thread that loads
 * configuration. {@link Thread#start()} makes everything written before sealing
 * visible to threads started afterward; setting up from more than one thread,
 * or after such threads exist, isn't supported.
 * </p>
 *
 * <p>
 * Stored configuration binds from JSON by the web crypto configuration,
 * {@link Init#jsonbConfig()}: property names in snake_case, binary data in
 * base64url, algorithms and encryptions by JOSE name, and crypt's conversions
 * for keys and certificates; JSON-B's defaults apply otherwise, so dates are
 * ISO-8601 and enums are by {@link Enum#name() name}. Beyond that
 * configuration:
 * </p>
 * 
 * <ul>
 * <li>Wherever a type registered by
 * {@link #registerInterface(String, Class, Duration, IuVault...)
 * registerInterface} or {@link #registerFactory(Class, Function, Duration)
 * registerFactory} is bound, a JSON string refers to a value by key, loaded by
 * {@link #load(Class, String)}. A factory type binds only from a key, or
 * null.</li>
 * <li>Wherever an {@link Iterable}, {@link java.util.Collection Collection},
 * {@link java.util.List List}, or {@link java.util.Set Set} is bound, a value
 * other than an array binds as a one-item container of that type.</li>
 * </ul>
 *
 * <p>
 * IuConfig only reads configuration: {@link #load(Class, String)} is the one
 * operation it provides to consumers. {@link #jsonb()} lets application code
 * convert in the same JSON dialect. A tool that writes configuration may load
 * through IuConfig, but saves values by its own logic, which is outside this
 * module's scope.
 * </p>
 * 
 * <p>
 * Configuration load operations are cached. This is a pull-only defensive cache
 * used to optimize upstream conversion overhead, i.e., parsing JSON from Vault.
 * Upstream errors are also cached, whether the error is caused by a connection
 * failure or invalid backing configuration value; the upstream source SHOULD
 * use {@link IuRefreshableCache}, or a similar defensive cache mechanism, with
 * refresh set &gt; config's cache TTL to ensure fault-tolerance for configured
 * values.
 * </p>
 * 
 * <p>
 * With {@link #registerFactory(Class, Function)} and
 * {@link #registerInterface(String, Class, IuVault...)}, a default cache TTL of
 * PT15S is used. Registration methods that accept an alternate cacheTtl require
 * a non-null, positive {@link Duration}, as is required by
 * {@link IuCacheMap#IuCacheMap(Duration)}.
 * </p>
 *
 * <p>
 * {@link #configureJsonb(Consumer)} is how the application changes how
 * configuration binds; the configuration is fixed when sealed, and
 * {@link #jsonb()} then returns the {@link Jsonb} it binds with.
 * </p>
 */
public class IuConfig {
	static {
		IuObject.assertNotOpen(IuConfig.class);
	}

	private static final Logger LOG = Logger.getLogger(IuConfig.class.getName());

	private record CachedResult<T>(T value, RuntimeException error) {
	}

	private abstract static class BaseConfig<T> {
		final Class<T> type;
		final Map<String, CachedResult<T>> cache;
		final Map<String, Optional<T>> lastKnownGood;

		private BaseConfig(Class<T> type, Duration cacheTtl) {
			this.type = type;
			this.cache = new IuCacheMap<>(cacheTtl);
			this.lastKnownGood = new ConcurrentHashMap<>();
		}

		final T load(String key) {
			final var knownGood = lastKnownGood.get(key);

			CachedResult<T> result;
			synchronized (cache) {
				result = cache.get(key);
				if (result == null)
					try {
						final var resolvedValue = resolve(key);

						final var validationResult = IuValidator.validate(resolvedValue);
						if (!validationResult.isValid())
							throw new IllegalStateException(
									"Validation failed for " + key + "; " + validationResult.report());

						lastKnownGood.put(key, Optional.ofNullable(resolvedValue));
						cache.put(key, result = new CachedResult<>(resolvedValue, null));
					} catch (RuntimeException e) {
						if (knownGood != null)
							LOG.log(Level.WARNING, e, () -> "Config refresh failure for " + key + "!" + type.getName()
									+ ", will issue last known value until next refresh");
						cache.put(key, result = new CachedResult<>(null, e));
					}
			}

			if (result.error != null)
				if (knownGood != null)
					return knownGood.orElse(null);
				else
					throw result.error;
			else
				return result.value;
		}

		abstract T resolve(String key);
	}

	private static class StorageConfig<T> extends BaseConfig<T> {
		private final String prefix;
		private final IuVault[] vault;

		private StorageConfig(Class<T> type, String prefix, Duration cacheTtl, IuVault... vault) {
			super(type, cacheTtl);
			this.prefix = prefix;
			this.vault = vault;
		}

		T resolve(String key) {
			for (final var v : vault) {
				final var value = v.get(prefix + key);
				if (value != null)
					return jsonb.fromJson(value.getValue(), type);
			}
			return null;
		}

		@Override
		public String toString() {
			return "StorageConfig [type=" + type + ", prefix=" + prefix + ", vault=" + Arrays.toString(vault) + "]";
		}
	}

	private static class FactoryConfig<T> extends BaseConfig<T> {
		private final Function<String, T> load;

		private FactoryConfig(Class<T> type, Function<String, T> load, Duration cacheTtl) {
			super(type, cacheTtl);
			this.load = load;
		}

		T resolve(String key) {
			return load.apply(key);
		}

		@Override
		public String toString() {
			return "FactoryConfig [type=" + type + ", load=" + load + "]";
		}
	}

	private static final Map<Class<?>, BaseConfig<?>> CONFIG = new HashMap<>();

	private static final JsonbConfig jsonbConfig = Init.<JsonbConfig>jsonbConfig()
			.withDeserializers(new JsonbDeserializer<Iterable<?>>() {
				@Override
				public Iterable<?> deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
					final var event = parser.currentEvent();
					if (Event.VALUE_NULL.equals(event))
						return null;
					else if (!Event.START_ARRAY.equals(event))
						return (Iterable<?>) IuJsonArrayAdapter.of(rtType)
								.collect(IuIterable.iter((Object) ctx.deserialize(GenericTypes.item(rtType), parser)));
					else
						return ctx.deserialize(rtType, parser);
				}
			});

	private static boolean sealed;
	private static Jsonb jsonb;

	// Registration methods; only supported until seal() is invoked

	private static void requireNotSealed() {
		if (sealed)
			throw new IllegalStateException("already sealed");
	}

	/**
	 * Registers a factory method for a configuration type, caching what it creates
	 * for 15 seconds.
	 *
	 * <p>
	 * Part of the application's setup; see the class documentation.
	 * </p>
	 *
	 * <p>
	 * Wherever the configuration type is bound, a JSON string is a key, loaded by
	 * {@link #load(Class, String)} through the factory, and JSON null binds as
	 * null. Any other JSON value fails with {@link JsonbException}. A subtype
	 * registered in its own right, such as a configuration interface that extends
	 * the type, converts by its own registration instead. Writing the type is
	 * outside IuConfig's scope; an application that writes configuration may
	 * register its own serializer with {@link #configureJsonb(Consumer)}, for
	 * example writing the key. As with
	 * {@link #registerInterface(String, Class, Duration, IuVault...)
	 * registerInterface}, the order of this call relative to
	 * {@link #configureJsonb(Consumer)} matters for components of the same type.
	 * </p>
	 *
	 * @param <T>        configuration type
	 * @param configType configuration type
	 * @param load       factory method handle; creates the configuration for a key
	 * @throws IllegalStateException    if sealed
	 * @throws IllegalArgumentException if {@code configType} is already registered
	 * @throws NullPointerException     if {@code load} is null
	 * @see #registerFactory(Class, Function, Duration)
	 */
	public static <T> void registerFactory(Class<T> configType, Function<String, T> load) {
		registerFactory(configType, load, Duration.ofSeconds(15L));
	}

	/**
	 * Registers a factory method for a configuration type.
	 *
	 * <p>
	 * Part of the application's setup; see the class documentation.
	 * </p>
	 *
	 * <p>
	 * Wherever the configuration type is bound, a JSON string is a key, loaded by
	 * {@link #load(Class, String)} through the factory, and JSON null binds as
	 * null. Any other JSON value fails with {@link JsonbException}. A subtype
	 * registered in its own right, such as a configuration interface that extends
	 * the type, converts by its own registration instead. Writing the type is
	 * outside IuConfig's scope; an application that writes configuration may
	 * register its own serializer with {@link #configureJsonb(Consumer)}, for
	 * example writing the key. As with
	 * {@link #registerInterface(String, Class, Duration, IuVault...)
	 * registerInterface}, the order of this call relative to
	 * {@link #configureJsonb(Consumer)} matters for components of the same type.
	 * </p>
	 *
	 * <p>
	 * When concurrent callers request the same uncached key, the factory is invoked
	 * only once; callers that arrive while it is loading use the cached object once
	 * the load completes.
	 * </p>
	 *
	 * @param <T>        configuration type
	 * @param configType configuration type
	 * @param load       factory method handle; creates the configuration for a key
	 * @param cacheTtl   cache TTL
	 * @throws IllegalStateException    if sealed
	 * @throws IllegalArgumentException if {@code configType} is already registered
	 * @throws NullPointerException     if {@code load} is null
	 */
	public static <T> void registerFactory(Class<T> configType, Function<String, T> load, Duration cacheTtl) {
		requireNotSealed();

		if (CONFIG.containsKey(configType))
			throw new IllegalArgumentException("already configured");

		final var factory = Objects.requireNonNull(load, "Missing factory");

		// a key string is the only JSON a factory type converts from; a subtype
		// registered in its own right converts by its own registration
		jsonbConfig.withDeserializers(IuJsonAdapter.<T>typedDeserializer(configType, (parser, context, type) -> {
			final var erased = GenericTypes.erase(type);
			if (erased != configType //
					&& CONFIG.containsKey(erased))
				return context.deserialize(type, parser);

			final var event = parser.currentEvent();
			if (Event.VALUE_NULL.equals(event))
				return null;
			else if (Event.VALUE_STRING.equals(event))
				return load(configType, parser.getString());
			else
				throw new JsonbException(
						configType.getName() + " is created by its factory; expected a key string, found " + event);
		}));

		CONFIG.put(configType, new FactoryConfig<>(configType, factory, cacheTtl));
	}

	/**
	 * Registers a configuration type loaded from vault, caching what it loads for
	 * 15 seconds.
	 *
	 * <p>
	 * Part of the application's setup; see the class documentation.
	 * </p>
	 *
	 * @param <T>        configuration type
	 * @param prefix     prefix to append to vault key to classify the resource
	 *                   names used by {@link #load(Class, String)}; lowercase
	 *                   letters only
	 * @param configType configuration type
	 * @param vault      vaults to load configuration from, in order
	 * @throws IllegalStateException    if sealed
	 * @throws IllegalArgumentException if {@code configType} is already registered,
	 *                                  {@code prefix} isn't lowercase letters, or
	 *                                  {@code vault} is empty
	 * @throws NullPointerException     if {@code prefix} or {@code vault} is null
	 * @see #registerInterface(String, Class, Duration, IuVault...)
	 */
	public static <T> void registerInterface(String prefix, Class<T> configType, IuVault... vault) {
		registerInterface(prefix, configType, Duration.ofSeconds(15L), vault);
	}

	/**
	 * Registers a configuration type loaded from vault.
	 *
	 * <p>
	 * Part of the application's setup; see the class documentation.
	 * </p>
	 *
	 * <p>
	 * A key loads from the secret named by the prefix, a slash, and the key, from
	 * each vault in turn: the first non-null value is returned; null is returned if
	 * no non-null values are found.
	 * </p>
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
	 *                   names used by {@link #load(Class, String)}; lowercase
	 *                   letters only
	 * @param configType configuration type
	 * @param cacheTtl   cache TTL
	 * @param vault      vaults to load configuration from, in order
	 * @throws IllegalStateException    if sealed
	 * @throws IllegalArgumentException if {@code configType} is already registered,
	 *                                  {@code prefix} isn't lowercase letters, or
	 *                                  {@code vault} is empty
	 * @throws NullPointerException     if {@code prefix} or {@code vault} is null
	 */
	public static <T> void registerInterface(String prefix, Class<T> configType, Duration cacheTtl, IuVault... vault) {
		requireNotSealed();

		IuObject.require(Objects.requireNonNull(prefix, "Missing prefix"), a -> a.matches("\\p{Lower}+"),
				"invalid prefix " + prefix);
		IuObject.require(Objects.requireNonNull(vault, "Missing vault"), a -> a.length > 0, "Missing vault");

		if (CONFIG.containsKey(configType))
			throw new IllegalArgumentException("already configured");

		jsonbConfig.withDeserializers(IuJsonAdapter.<T>typedDeserializer(configType, (parser, context, type) -> {
			if (Event.VALUE_STRING.equals(parser.currentEvent()))
				return load(configType, parser.getString());
			else
				return context.deserialize(configType, parser);
		}));

		CONFIG.put(configType, new StorageConfig<>(configType, prefix + '/', cacheTtl, vault));
	}

	/**
	 * Customizes how configuration binds, before sealing.
	 *
	 * <p>
	 * Part of the application's setup; see the class documentation.
	 * {@code configConsumer} receives the live {@link JsonbConfig} and may add
	 * components or replace anything in it, including the defaults the class
	 * documentation describes. Components for the same type run in the order they
	 * were configured, so the order of this call relative to
	 * {@link #registerInterface(String, Class, Duration, IuVault...)
	 * registerInterface} matters. Changes made after {@link #seal()} have no
	 * effect, so don't keep the reference.
	 * </p>
	 *
	 * @param configConsumer accepts the in-progress {@link JsonbConfig}
	 * @throws IllegalStateException if sealed
	 * @throws NullPointerException  if {@code configConsumer} is null
	 */
	public static void configureJsonb(Consumer<JsonbConfig> configConsumer) {
		requireNotSealed();
		configConsumer.accept(jsonbConfig);
	}

	/**
	 * Seals configuration; required once, at the end of the application's setup.
	 *
	 * <p>
	 * Fixes how configuration binds, from the JSON-B configuration accumulated by
	 * registration and {@link #configureJsonb(Consumer)}. Until sealed, nothing can
	 * be loaded; once sealed, no type can be registered. The application calls this
	 * before creating thread pools or starting any thread that loads configuration;
	 * see the class documentation.
	 * </p>
	 *
	 * @throws IllegalStateException if already sealed
	 */
	public static void seal() {
		requireNotSealed();
		jsonb = JsonbBuilder.newBuilder("iu.client.jsonb.IuJsonbProvider").withConfig(jsonbConfig).build();
		sealed = true;
	}

	private static void requireSealed() {
		if (!sealed)
			throw new IllegalStateException("not sealed");
	}

	/**
	 * Loads an optional configuration object, from its registered vaults or
	 * factory.
	 *
	 * <p>
	 * A loaded object is cached by key for the time period registered with its
	 * type.
	 * </p>
	 * 
	 * <p>
	 * This method treats the configured value as OPTIONAL: null key returns null,
	 * and null value is returned without error. For a REQUIRED configured value,
	 * use {@link #require(Class, String)}.
	 * </p>
	 *
	 * @param <T>        configuration type
	 * @param configType configuration type
	 * @param key        key; may be null to return null after verifying sealed
	 * @return loaded configuration
	 * @throws IllegalStateException if not sealed
	 * @throws NullPointerException  if {@code configType} isn't registered
	 * @throws RuntimeException      if a configured value could not be loaded due
	 *                               to an error
	 */
	public static <T> T optional(Class<T> configType, String key) {
		requireSealed();
		final var config = Objects.requireNonNull(CONFIG.get(configType), configType + " not configured");
		return IuObject.convert(key, a -> configType.cast(config.load(a)));
	}

	/**
	 * Loads a configuration object, from its registered vaults or factory.
	 *
	 * <p>
	 * A loaded object is cached by key for the time period registered with its
	 * type.
	 * </p>
	 * 
	 * <p>
	 * This method treats the configured value as REQUIRED if key is non-null: null
	 * value throws {@link IllegalArgumentException}. To also require a non-null
	 * key, use {@link #require(Class, String)}; to also allow a null value, use
	 * {@link #optional(Class, String)}
	 * </p>
	 *
	 * @param <T>        configuration type
	 * @param configType configuration type
	 * @param key        key; may be null to return null after verifying sealed
	 * @return loaded configuration
	 * @throws IllegalStateException if not sealed
	 * @throws IllegalArgumentException if the configured value was resolved as null
	 * @throws NullPointerException  if {@code configType} isn't registered
	 * @throws RuntimeException      if a configured value could not be loaded due
	 *                               to an error
	 */
	public static <T> T load(Class<T> configType, String key) {
		requireSealed();
		final var config = Objects.requireNonNull(CONFIG.get(configType), configType + " not configured");

		if (key == null)
			return null;

		final var value = configType.cast(config.load(key));
		if (value == null)
			throw new IllegalArgumentException("missing value for " + key + " using " + config);

		return value;
	}

	/**
	 * Loads a required configuration object, from its registered vaults or factory.
	 *
	 * <p>
	 * A loaded object is cached by key for the time period registered with its
	 * type.
	 * </p>
	 * 
	 * <p>
	 * This method treats the configured value as REQUIRED: null key throws
	 * {@link NullPointerException}, and a null value throws
	 * {@link IllegalArgumentException}. For an OPTIONAL configured value, use
	 * {@link #optional(Class, String)}; to allow a null key to return null, but
	 * still require a non-null value for a non-null key use
	 * {@link #load(Class, String)}
	 * </p>
	 * 
	 * @param <T>        configuration type
	 * @param configType configuration type
	 * @param key        key
	 * @return loaded configuration
	 * @throws IllegalStateException    if not sealed
	 * @throws IllegalArgumentException if the configured value was resolved as null
	 * @throws NullPointerException     if {@code configType} isn't registered or
	 *                                  {@code key} is null
	 * @throws RuntimeException         if a configured value could not be loaded
	 *                                  due to an error
	 */
	public static <T> T require(Class<T> configType, String key) {
		requireSealed();

		final var config = Objects.requireNonNull(CONFIG.get(configType), configType + " not configured");
		Objects.requireNonNull(key, "missing key for " + config);

		final var value = configType.cast(config.load(key));
		if (value == null)
			throw new IllegalArgumentException("missing value for " + key + " using " + config);

		return value;
	}

	/**
	 * Gets the {@link Jsonb} instance used to deserialize configuration documents
	 * loaded from Vault.
	 *
	 * <p>
	 * The same instance {@link #load(Class, String)} binds with, for conversions
	 * that need identical semantics. Where a type registered by
	 * {@link #registerInterface(String, Class, Duration, IuVault...)
	 * registerInterface} is bound, a JSON string resolves through
	 * {@link #load(Class, String)}, which may read from Vault.
	 * </p>
	 *
	 * @return {@link Jsonb}
	 * @throws IllegalStateException if not sealed
	 */
	public static Jsonb jsonb() {
		requireSealed();
		return jsonb;
	}

	private IuConfig() {
	}
}
