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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import edu.iu.GenericTypes;
import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonArrayAdapter;
import edu.iu.client.IuVault;
import edu.iu.crypt.Init;
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
 * <li>Optionally, {@link #configureJsonb(Consumer) customize} how
 * configuration binds.</li>
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
 * configuration. {@link Thread#start()} makes everything written before
 * sealing visible to threads started afterward; setting up from more than one
 * thread, or after such threads exist, isn't supported.
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
 * <ul>
 * <li>Wherever a type registered by
 * {@link #registerInterface(String, Class, Duration, IuVault...)
 * registerInterface} or {@link #registerFactory(Class, Function, Duration)
 * registerFactory} is bound, a JSON string refers to a value by key, loaded by
 * {@link #load(Class, String)}. A factory type binds only from a key, or null,
 * and doesn't convert to JSON.</li>
 * <li>Wherever an {@link Iterable}, {@link java.util.Collection Collection},
 * {@link java.util.List List}, or {@link java.util.Set Set} is bound, a value
 * other than an array binds as a one-item container of that type.</li>
 * </ul>
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
					final var value = jsonb.fromJson(vault.get(prefix + key).getValue(), configType);
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
	 * null. Any other JSON value, and writing the type to JSON, fails with
	 * {@link JsonbException}. As with
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
		registerFactory(configType, load, null);
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
	 * null. Any other JSON value, and writing the type to JSON, fails with
	 * {@link JsonbException}. As with
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
	 * @param cacheTtl   time period for caching config objects; null for 15 seconds
	 * @throws IllegalStateException    if sealed
	 * @throws IllegalArgumentException if {@code configType} is already registered
	 * @throws NullPointerException     if {@code load} is null
	 */
	public static <T> void registerFactory(Class<T> configType, Function<String, T> load,
			Duration cacheTtl) {
		requireNotSealed();

		if (CONFIG.containsKey(configType))
			throw new IllegalArgumentException("already configured");

		final var factory = Objects.requireNonNull(load, "Missing factory");

		// a key string is the only JSON a factory type converts from, and it has no
		// JSON form to write
		jsonbConfig.withDeserializers(IuJsonAdapter.<T>typedDeserializer(configType, (parser, context, type) -> {
			final var event = parser.currentEvent();
			if (Event.VALUE_NULL.equals(event))
				return null;
			else if (Event.VALUE_STRING.equals(event))
				return load(configType, parser.getString());
			else
				throw new JsonbException(
						configType.getName() + " is created by its factory; expected a key string, found " + event);
		}));
		jsonbConfig.withSerializers(IuJsonAdapter.<T>typedSerializer(configType, (value, generator, context) -> {
			throw new JsonbException(configType.getName() + " is created by its factory and doesn't convert to JSON");
		}));

		CONFIG.put(configType, new FactoryConfig<>(factory, cacheTtl));
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
	 *                                  or {@code prefix} isn't lowercase letters
	 * @throws NullPointerException     if {@code prefix} is null
	 * @see #registerInterface(String, Class, Duration, IuVault...)
	 */
	public static <T> void registerInterface(String prefix, Class<T> configType, IuVault... vault) {
		registerInterface(prefix, configType, null, vault);
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
	 * each vault in turn: the first that binds is cached and returned, and if none
	 * does, the first failure is thrown with the others suppressed.
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
	 * @param cacheTtl   time period for caching config objects; null for 15 seconds
	 * @param vault      vaults to load configuration from, in order
	 * @throws IllegalStateException    if sealed
	 * @throws IllegalArgumentException if {@code configType} is already registered,
	 *                                  or {@code prefix} isn't lowercase letters
	 * @throws NullPointerException     if {@code prefix} is null
	 */
	public static <T> void registerInterface(String prefix, Class<T> configType, Duration cacheTtl,
			IuVault... vault) {
		requireNotSealed();

		IuObject.require(Objects.requireNonNull(prefix, "Missing prefix"), a -> a.matches("\\p{Lower}+"),
				"invalid prefix " + prefix);

		if (CONFIG.containsKey(configType))
			throw new IllegalArgumentException("already configured");

		jsonbConfig.withDeserializers(IuJsonAdapter.<T>typedDeserializer(configType, (parser, context, type) -> {
			if (Event.VALUE_STRING.equals(parser.currentEvent()))
				return load(configType, parser.getString());
			else
				return context.deserialize(configType, parser);
		}));

		CONFIG.put(configType, new StorageConfig<>(prefix + '/', configType, cacheTtl, vault));
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
	 * registration and {@link #configureJsonb(Consumer)}. Until sealed, nothing
	 * can be loaded; once sealed, no type can be registered. The application
	 * calls this before creating thread pools or starting any thread that loads
	 * configuration; see the class documentation.
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
	 * Loads a configuration object, from its registered vaults or factory.
	 *
	 * <p>
	 * A loaded object is cached by key for the time period registered with its
	 * type.
	 * </p>
	 *
	 * @param <T>        configuration type
	 * @param configType configuration type
	 * @param key        key
	 * @return loaded configuration
	 * @throws IllegalStateException if not sealed
	 * @throws NullPointerException  if {@code configType} isn't registered
	 * @throws RuntimeException      if no vault loads the key, as described for
	 *                               {@link #registerInterface(String, Class, Duration, IuVault...)},
	 *                               or if the factory fails
	 */
	public static <T> T load(Class<T> configType, String key) {
		requireSealed();
		return configType.cast(Objects.requireNonNull(CONFIG.get(configType), "not configured").load(key));
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
