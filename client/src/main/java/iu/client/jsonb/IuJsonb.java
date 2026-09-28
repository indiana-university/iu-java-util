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
package iu.client.jsonb;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import iu.client.BinaryJsonAdapter;
import iu.client.BindingMetadata;
import iu.client.FormatAdapters;
import iu.client.GenericTypes;
import iu.client.ItemScope;
import iu.client.JsonAdapters;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.annotation.JsonbTypeSerializer;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

/**
 * Implements {@link Jsonb} over the {@link edu.iu.client.IuJsonAdapter}
 * conversions, streaming through
 * {@link edu.iu.client.IuJsonAdapter#read(JsonParser)} and
 * {@link edu.iu.client.IuJsonAdapter#write(Object, JsonGenerator)}.
 *
 * <p>
 * Supports {@link JsonbConfig#FORMATTING}, {@link JsonbConfig#NULL_VALUES},
 * {@link JsonbConfig#ENCODING}, {@link JsonbConfig#BINARY_DATA_STRATEGY}
 * ({@link BinaryDataStrategy#BYTE} by default),
 * {@link JsonbConfig#PROPERTY_NAMING_STRATEGY} (every standard strategy, and
 * {@link jakarta.json.bind.config.PropertyNamingStrategy} instances),
 * {@link JsonbConfig#PROPERTY_ORDER_STRATEGY},
 * {@link JsonbConfig#PROPERTY_VISIBILITY_STRATEGY},
 * {@link JsonbConfig#SERIALIZERS}, {@link JsonbConfig#DESERIALIZERS}, and
 * {@link JsonbConfig#ADAPTERS}, {@link JsonbConfig#DATE_FORMAT} and
 * {@link JsonbConfig#LOCALE}, and {@link JsonbConfig#STRICT_IJSON}, as well as
 * {@link #SERIALIZATION_OPTIONS} and {@link #BASE64_URL_UNPADDED}. An enum
 * converts as text by {@link Enum#name()}.
 * </p>
 *
 * <p>
 * A date format, from {@link JsonbConfig#DATE_FORMAT} or declared by
 * {@code @JsonbDateFormat}, is a {@link java.time.format.DateTimeFormatter}
 * pattern, or {@code TIME_IN_MILLIS}; see {@link FormatAdapters}. A declared
 * format, on a property's accessor or field, then the class declaring it, then
 * its package, wins over the configured one, even when it declares the default
 * format. {@code @JsonbNumberFormat} writes a number as text in its
 * {@link java.text.DecimalFormat} pattern, and reads that text or a number. A
 * format replaces the built-in conversion, so configured components still run
 * first.
 * </p>
 *
 * <p>
 * {@link JsonbConfig#STRICT_IJSON} writes only an object or array at the top
 * level, {@code byte[]} as base64url whatever else is configured, and dates
 * without a format of their own as I-JSON requires: {@link java.util.Date},
 * {@link java.util.Calendar}, {@link java.time.Instant},
 * {@link java.time.LocalDate}, and {@link java.time.LocalDateTime} as an ISO
 * date and time with an offset and seconds. Numbers are not restricted; the
 * JSON-B specification leaves them out of strict I-JSON.
 * </p>
 *
 * <p>
 * Serializers, deserializers, and adapters form chains: any number may be
 * configured for a type, each applies to its type and every subtype, and they
 * run from the most specific to the least, then in configured order. See
 * {@link IuJsonbValueAdapter} for how control passes along a chain.
 * </p>
 *
 * <p>
 * A component a type declares by {@code @JsonbTypeAdapter},
 * {@code @JsonbTypeSerializer}, or {@code @JsonbTypeDeserializer} joins the
 * chains as if registered for the declaring type, so also applies to its
 * subtypes, ahead of configured components that are no more specific. One a
 * property declares, on its accessor or field, heads that property's chain;
 * passed a value back, the context continues with the type's chain. Each
 * declared component class is instantiated once per provider by its no-arg
 * constructor.
 * </p>
 *
 * <p>
 * A component registered for a {@link #isBroad(Type) broad} type, such as
 * {@link Object}, {@link Comparable}, or {@link java.io.Serializable}, doesn't
 * apply to a {@link #isScalar(Type) scalar} type: text, a number, or a boolean.
 * It doesn't see a null declared as one of those types, nor a value written
 * whose runtime type is one, nor a JSON string, number, or boolean read as any
 * type. It does see a null of a type that isn't scalar, or that isn't known, as
 * when a null is written without a type or passed to a context by a serializer
 * that isn't already converting it. Other values written as text, such as
 * dates, still reach broad components. Components registered for a scalar type,
 * such as {@link CharSequence}, {@link Number}, or {@link Boolean}, apply to
 * scalars as usual.
 * </p>
 *
 * <p>
 * Thread-safe: every conversion runs as a call with its own
 * {@link IuSerializationContext} or {@link IuDeserializationContext}, held per
 * thread while it runs.
 * </p>
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class IuJsonb implements Jsonb {

	/**
	 * {@link JsonbConfig} property holding a
	 * {@code Supplier<IuJsonSerializationOptions>}, read once per call; must agree
	 * with {@link JsonbConfig#PROPERTY_NAMING_STRATEGY} and
	 * {@link JsonbConfig#NULL_VALUES} when those are also set.
	 */
	public static final String SERIALIZATION_OPTIONS = "iu.jsonb.serializationOptions";

	/**
	 * {@link JsonbConfig} property holding a {@link Boolean}: true to write
	 * {@link BinaryDataStrategy#BASE_64_URL} without padding, as JOSE requires.
	 * Reading accepts either form regardless.
	 */
	public static final String BASE64_URL_UNPADDED = "iu.jsonb.base64UrlUnpadded";

	/**
	 * A serializer registered for an explicit type.
	 */
	private static final class TypedSerializer<T> implements JsonbSerializer<T> {
		private final Type type;
		private final JsonbSerializer<T> serializer;

		private TypedSerializer(Type type, JsonbSerializer<T> serializer) {
			this.type = Objects.requireNonNull(type, "type");
			this.serializer = Objects.requireNonNull(serializer, "serializer");
		}

		@Override
		public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
			serializer.serialize(obj, generator, ctx);
		}
	}

	/**
	 * A deserializer registered for an explicit type.
	 */
	private static final class TypedDeserializer<T> implements JsonbDeserializer<T> {
		private final Type type;
		private final JsonbDeserializer<T> deserializer;

		private TypedDeserializer(Type type, JsonbDeserializer<T> deserializer) {
			this.type = Objects.requireNonNull(type, "type");
			this.deserializer = Objects.requireNonNull(deserializer, "deserializer");
		}

		@Override
		public T deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			return deserializer.deserialize(parser, ctx, rtType);
		}
	}

	/**
	 * An adapter registered for explicit types.
	 */
	private static final class TypedAdapter<O, A> implements JsonbAdapter<O, A> {
		private final Type original;
		private final Type adapted;
		private final JsonbAdapter<O, A> adapter;

		private TypedAdapter(Type original, Type adapted, JsonbAdapter<O, A> adapter) {
			this.original = Objects.requireNonNull(original, "original");
			this.adapted = Objects.requireNonNull(adapted, "adapted");
			this.adapter = Objects.requireNonNull(adapter, "adapter");
		}

		@Override
		public A adaptToJson(O obj) throws Exception {
			return adapter.adaptToJson(obj);
		}

		@Override
		public O adaptFromJson(A obj) throws Exception {
			return adapter.adaptFromJson(obj);
		}
	}

	/**
	 * Registers a serializer for an explicit type, such as a lambda, which has no
	 * type argument to name it, when passed to
	 * {@link JsonbConfig#withSerializers(JsonbSerializer...)}.
	 *
	 * @param <T>        serialized type
	 * @param type       type the serializer applies to, and to its subtypes
	 * @param serializer serializer
	 * @return serializer to configure
	 */
	public static <T> JsonbSerializer<T> typedSerializer(Type type, JsonbSerializer<T> serializer) {
		return new TypedSerializer<>(type, serializer);
	}

	/**
	 * Registers a deserializer for an explicit type, such as a lambda, which has
	 * no type argument to name it, when passed to
	 * {@link JsonbConfig#withDeserializers(JsonbDeserializer...)}.
	 *
	 * @param <T>          deserialized type
	 * @param type         type the deserializer applies to, and to its subtypes
	 * @param deserializer deserializer
	 * @return deserializer to configure
	 */
	public static <T> JsonbDeserializer<T> typedDeserializer(Type type, JsonbDeserializer<T> deserializer) {
		return new TypedDeserializer<>(type, deserializer);
	}

	/**
	 * Registers an adapter for explicit types, such as a lambda-backed adapter,
	 * which has no type arguments to name them, when passed to
	 * {@link JsonbConfig#withAdapters(JsonbAdapter...)}.
	 *
	 * @param <O>      original type
	 * @param <A>      adapted type
	 * @param original type the adapter converts from when writing, and applies to
	 *                 with its subtypes
	 * @param adapted  type the adapter converts to when writing
	 * @param adapter  adapter
	 * @return adapter to configure
	 */
	public static <O, A> JsonbAdapter<O, A> typedAdapter(Type original, Type adapted, JsonbAdapter<O, A> adapter) {
		return new TypedAdapter<>(original, adapted, adapter);
	}

	/**
	 * Gets the conversions of the provider running a deserialization, which stay
	 * valid after the call ends.
	 *
	 * @param context deserialization context
	 * @return function from a type to its configured conversion
	 * @throws IllegalArgumentException if the context isn't from this provider
	 */
	public static Function<Type, IuJsonAdapter<?>> adapters(DeserializationContext context) {
		if (context instanceof IuDeserializationContext)
			return ((IuDeserializationContext) context).jsonb.conversions();
		else
			throw new IllegalArgumentException("not a deserialization by " + IuJsonb.class.getName());
	}

	/**
	 * A configured {@link JsonbAdapter} and the types it converts between.
	 */
	static class AdapterReference {
		/**
		 * Type the adapter converts from when writing.
		 */
		final Type original;

		/**
		 * Type the adapter converts to when writing.
		 */
		final Type adapted;

		/**
		 * Adapter.
		 */
		final JsonbAdapter adapter;

		private AdapterReference(Type original, Type adapted, JsonbAdapter adapter) {
			this.original = original;
			this.adapted = adapted;
			this.adapter = adapter;
		}
	}

	/**
	 * A configured component and the type it is registered for.
	 */
	private static class Registration<T> {
		private final Type type;
		private final T component;
		private final boolean broad;

		private Registration(Type type, T component) {
			this.type = type;
			this.component = component;
			broad = isBroad(type);
		}
	}

	/**
	 * Reads the property name format from an options snapshot.
	 *
	 * @param options options snapshot
	 * @return property name format; null reads as
	 *         {@link IuJsonSerializationOptions#PROPERTY_NAME_FORMAT}
	 */
	static IuJsonPropertyNameFormat format(IuJsonSerializationOptions options) {
		return Objects.requireNonNullElse(options.getPropertyNameFormat(),
				IuJsonSerializationOptions.PROPERTY_NAME_FORMAT);
	}

	private final JsonProvider provider;
	private final JsonParserFactory parserFactory;
	private final JsonGeneratorFactory generatorFactory;
	private final Supplier<IuJsonSerializationOptions> options;
	private final IuJsonPropertyNameFormat configuredFormat;
	private final IuJsonbNaming naming;
	private final IuJsonbBinaryAdapter binary;
	private final Charset encoding;
	private final Boolean configuredNullValues;
	private final String configuredBinary;
	private final boolean strictIJson;
	private final String configuredDateFormat;
	private final Locale locale;
	private final boolean checkConflicts;
	private final PropertyVisibilityStrategy propertyVisibilityStrategy;
	private final String propertyOrderStrategy;
	private final List<Registration<JsonbDeserializer>> deserializers;
	private final List<Registration<JsonbSerializer>> serializers;
	private final List<Registration<AdapterReference>> adapters;
	private final Map<Type, IuJsonbValueAdapter<?>> valueAdapters = new ConcurrentHashMap<>();
	private final Map<Type, IuJsonbModel> models = new ConcurrentHashMap<>();
	private final Function<Type, IuJsonAdapter<?>> conversions = this::adapt;
	private final Map<Class<?>, DeclaredComponents> declaredComponents = new ConcurrentHashMap<>();
	private final Map<Class<?>, Object> components = new ConcurrentHashMap<>();
	private final Map<Class<?>, AdapterReference> declaredAdapters = new ConcurrentHashMap<>();

	/**
	 * Serialization call in progress on each thread.
	 */
	final ThreadLocal<IuSerializationContext> serialization = new ThreadLocal<>();

	/**
	 * Deserialization call in progress on each thread.
	 */
	final ThreadLocal<IuDeserializationContext> deserialization = new ThreadLocal<>();

	/**
	 * Constructor.
	 *
	 * @param config   configuration
	 * @param provider JSON-P provider for parsers, generators, and values
	 * @throws UnsupportedOperationException if an order or naming strategy is not
	 *                                       supported
	 * @throws JsonbException                if a serializer, deserializer, or
	 *                                       adapter can't be resolved, or converts
	 *                                       a type variable
	 */
	public IuJsonb(JsonbConfig config, JsonProvider provider) {
		this.provider = provider;
		parserFactory = provider.createParserFactory(Map.of());

		// some providers enable pretty printing when the key is present at all
		final var prettyPrinting = (boolean) config.getProperty(JsonbConfig.FORMATTING).orElse(false);
		generatorFactory = provider.createGeneratorFactory(prettyPrinting //
				? Map.of(JsonGenerator.PRETTY_PRINTING, true) //
				: Map.of());

		propertyVisibilityStrategy = (PropertyVisibilityStrategy) config
				.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY).orElse(null);

		propertyOrderStrategy = (String) config.getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY)
				.orElse(PropertyOrderStrategy.LEXICOGRAPHICAL);
		switch (propertyOrderStrategy) {
		case PropertyOrderStrategy.LEXICOGRAPHICAL:
		case PropertyOrderStrategy.REVERSE:
		case PropertyOrderStrategy.ANY:
			break;

		default:
			throw new UnsupportedOperationException(propertyOrderStrategy);
		}

		// a component registered through a typed factory declares its type
		// explicitly; any other declares it through its type arguments
		final List<Registration<JsonbDeserializer>> deserializers = new ArrayList<>();
		for (final var deserializer : (JsonbDeserializer[]) config.getProperty(JsonbConfig.DESERIALIZERS)
				.orElse(new JsonbDeserializer[0]))
			if (deserializer instanceof TypedDeserializer) {
				final var typed = (TypedDeserializer) deserializer;
				register(deserializers, typed.type, typed.deserializer, "deserializer");
			} else
				register(deserializers, componentTypes(deserializer, JsonbDeserializer.class)[0], deserializer,
						"deserializer");
		this.deserializers = Collections.unmodifiableList(deserializers);

		final List<Registration<JsonbSerializer>> serializers = new ArrayList<>();
		for (final var serializer : (JsonbSerializer[]) config.getProperty(JsonbConfig.SERIALIZERS)
				.orElse(new JsonbSerializer[0]))
			if (serializer instanceof TypedSerializer) {
				final var typed = (TypedSerializer) serializer;
				register(serializers, typed.type, typed.serializer, "serializer");
			} else
				register(serializers, componentTypes(serializer, JsonbSerializer.class)[0], serializer,
						"serializer");
		this.serializers = Collections.unmodifiableList(serializers);

		final List<Registration<AdapterReference>> adapters = new ArrayList<>();
		for (final var adapter : (JsonbAdapter[]) config.getProperty(JsonbConfig.ADAPTERS)
				.orElse(new JsonbAdapter[0])) {
			final Type[] types;
			final JsonbAdapter component;
			if (adapter instanceof TypedAdapter) {
				final var typed = (TypedAdapter) adapter;
				types = new Type[] { typed.original, typed.adapted };
				component = typed.adapter;
			} else {
				types = componentTypes(adapter, JsonbAdapter.class);
				component = adapter;
			}
			register(adapters, types[0], new AdapterReference(types[0], types[1], component), "adapter");
		}
		this.adapters = Collections.unmodifiableList(adapters);

		// IDENTITY and LOWER_CASE_WITH_UNDERSCORES are IU formats, which a
		// SERIALIZATION_OPTIONS supplier may vary; any other strategy is fixed
		final var configuredNaming = IuObject
				.convert(config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY).orElse(null), IuJsonbNaming::of);
		if (configuredNaming != null && configuredNaming.format() == null) {
			naming = configuredNaming;
			configuredFormat = null;
		} else {
			naming = null;
			configuredFormat = IuObject.convert(configuredNaming, IuJsonbNaming::format);
		}
		configuredNullValues = (Boolean) config.getProperty(JsonbConfig.NULL_VALUES).orElse(null);

		strictIJson = (Boolean) config.getProperty(JsonbConfig.STRICT_IJSON).orElse(false);
		configuredDateFormat = (String) config.getProperty(JsonbConfig.DATE_FORMAT).orElse(null);
		final var configuredLocale = config.getProperty(JsonbConfig.LOCALE).orElse(null);
		locale = configuredLocale instanceof String //
				? Locale.forLanguageTag((String) configuredLocale)
				: (Locale) configuredLocale;

		final var configuredStrategy = (String) config.getProperty(JsonbConfig.BINARY_DATA_STRATEGY).orElse(null);
		final var base64UrlUnpadded = (Boolean) config.getProperty(BASE64_URL_UNPADDED).orElse(false);
		// an unknown strategy fails now rather than on first use
		BinaryJsonAdapter.of(configuredStrategy, base64UrlUnpadded);
		// strict I-JSON writes base64url whatever else is configured
		configuredBinary = strictIJson ? null : configuredStrategy;
		binary = new IuJsonbBinaryAdapter(this, base64UrlUnpadded);
		encoding = IuObject.convert((String) config.getProperty(JsonbConfig.ENCODING).orElse(null), Charset::forName);

		// IU options, when supplied, must agree with the equivalent JSON-B settings;
		// the supplier is dynamic, so each call's snapshot is checked
		final var serializationOptions = (Supplier<IuJsonSerializationOptions>) config
				.getProperty(SERIALIZATION_OPTIONS).orElse(null);
		if (serializationOptions != null) {
			if (naming != null)
				throw new JsonbException(JsonbConfig.PROPERTY_NAMING_STRATEGY + " "
						+ config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY).get() + " conflicts with "
						+ SERIALIZATION_OPTIONS + ", which names properties by an IU property name format");
			options = serializationOptions;
			checkConflicts = configuredFormat != null || configuredNullValues != null || configuredBinary != null;
		} else {
			final var format = Objects.requireNonNullElse(configuredFormat, IuJsonPropertyNameFormat.IDENTITY);
			final var includeNulls = Objects.requireNonNullElse(configuredNullValues, false);
			final var binaryStrategy = Objects.requireNonNullElse(configuredBinary, BinaryDataStrategy.BYTE);
			final IuJsonSerializationOptions configuredOptions = new IuJsonSerializationOptions() {
				@Override
				public IuJsonPropertyNameFormat getPropertyNameFormat() {
					return format;
				}

				@Override
				public boolean isIncludeNullProperties() {
					return includeNulls;
				}

				@Override
				public String getBinaryDataStrategy() {
					return binaryStrategy;
				}
			};
			options = () -> configuredOptions;
			checkConflicts = false;
		}
	}

	/**
	 * Gets the fixed naming a JSON-B property naming strategy sets.
	 *
	 * @return {@link IuJsonbNaming}; null if properties are named by an IU
	 *         property name format, per call
	 */
	IuJsonbNaming naming() {
		return naming;
	}

	/**
	 * Gets the {@code byte[]} conversion the binary data strategy sets.
	 *
	 * @return {@link IuJsonbBinaryAdapter}
	 */
	IuJsonbBinaryAdapter binary() {
		return binary;
	}

	/**
	 * Gets the conversion for a map key: the key type's built-in text conversion,
	 * with an enum named by {@link Enum#name()}, or written as
	 * {@link Enum#toString()} when the call's options
	 * {@link IuJsonSerializationOptions#isEnumToString() say so}.
	 *
	 * @param type key type
	 * @return key adapter
	 */
	IuJsonAdapter<?> keyAdapter(Type type) {
		final var erased = JsonAdapters.erase(type);
		if (erased.isEnum())
			return IuJsonAdapter.from(v -> Enum.valueOf((Class) erased, ((JsonString) v).getString()), e -> {
				final var context = IuSerializationContext.current(this);
				final var options = context == null ? options() : context.options();
				return IuJson.string(options.isEnumToString() ? e.toString() : ((Enum<?>) e).name());
			});
		else
			return JsonAdapters.adapt(type, null);
	}

	/**
	 * Resolves the types a configured component converts, through sub-interfaces
	 * and generic superclasses.
	 *
	 * @param component          serializer, deserializer, or adapter
	 * @param componentInterface interface it implements
	 * @return one type per type parameter of {@code componentInterface}, which may
	 *         be or contain a type variable the component itself declares
	 * @throws JsonbException if an argument isn't declared anywhere in the
	 *                        component's hierarchy, as for a lambda or a raw
	 *                        implementation
	 */
	private static Type[] componentTypes(Object component, Class<?> componentInterface) {
		final var types = GenericTypes.typeArguments(component.getClass(), componentInterface);
		for (final var type : types)
			if (type instanceof TypeVariable //
					&& ((TypeVariable<?>) type).getGenericDeclaration() == componentInterface)
				throw new JsonbException("can't determine the type " + component.getClass().getName()
						+ " converts; declare the type argument of " + componentInterface.getSimpleName()
						+ " on the class or one of its supertypes, since a lambda or raw implementation has none, "
						+ "or register it with IuJsonb.typed" + componentInterface.getSimpleName().substring(5)
						+ "(Type, ...)");
		return types;
	}

	/**
	 * Registers a component for the type it converts.
	 *
	 * @param <T>       component type
	 * @param registry  components by the type they convert
	 * @param type      type the component converts
	 * @param component component
	 * @param kind      component kind, for messages
	 * @throws JsonbException if {@code type} is a type variable, which would apply
	 *                        the component to every type within its bounds
	 */
	private static <T> void register(List<Registration<T>> registry, Type type, T component, String kind) {
		if (type instanceof TypeVariable)
			throw new JsonbException(kind + " " + component.getClass().getName() + " converts the type variable "
					+ type.getTypeName() + ", so would apply to every type; declare a concrete type argument, "
					+ "or a parameterized type such as List<" + type.getTypeName() + ">, "
					+ "or register it for an explicit type with IuJsonb.typed" + Character.toUpperCase(kind.charAt(0))
					+ kind.substring(1) + "(Type, ...)");
		registry.add(new Registration<>(type, component));
	}

	/**
	 * Orders the components that apply to a type.
	 *
	 * <p>
	 * A component applies to the type it is registered for and every subtype. The
	 * chain runs from the most specific registration to the least; registrations
	 * neither more nor less specific than each other, including several for the
	 * same type, run in the order they were configured.
	 * </p>
	 *
	 * <p>
	 * Several registries merge into one chain by the same rule: between
	 * components of different registries neither more specific than the other,
	 * those of the registry listed first run first.
	 * </p>
	 *
	 * @param <T>        component type
	 * @param type       type being converted; a primitive is boxed, and a type
	 *                   variable or wildcard reads as its upper bound
	 * @param scalar     true to leave out components registered for a
	 *                   {@link #isBroad(Type) broad} type, for a scalar value
	 * @param registries components in the order they were configured, by
	 *                   precedence among equally specific components
	 * @return components that apply, in order
	 */
	@SafeVarargs
	private static <T> List<T> chain(Type type, boolean scalar, List<? extends Registration<? extends T>>... registries) {
		var lookup = GenericTypes.box(type);
		while (lookup instanceof TypeVariable || lookup instanceof WildcardType)
			lookup = lookup instanceof TypeVariable //
					? ((TypeVariable<?>) lookup).getBounds()[0]
					: ((WildcardType) lookup).getUpperBounds()[0];

		final List<Registration<? extends T>> candidates = new ArrayList<>();
		for (final var registry : registries)
			for (final var registration : registry)
				if (!(scalar && registration.broad) //
						&& GenericTypes.isAssignable(registration.type, lookup))
					candidates.add(registration);

		// specificity is a partial order, so rather than sorting, take the first
		// configured candidate that no remaining candidate is more specific than
		final List<T> chain = new ArrayList<>(candidates.size());
		while (!candidates.isEmpty()) {
			var next = 0;
			while (isLessSpecific(candidates.get(next), candidates))
				next++;
			chain.add(candidates.remove(next).component);
		}
		return chain;
	}

	/**
	 * Determines if values of a type are scalar, so components registered for a
	 * {@link #isBroad(Type) broad} type leave them alone.
	 *
	 * @param type type
	 * @return true for {@link CharSequence}, {@link Number}, {@link Boolean}, their
	 *         subtypes, and the primitive number types and {@code boolean}
	 */
	static boolean isScalar(Type type) {
		return JsonAdapters.isScalar(type);
	}

	/**
	 * Determines if components registered for a type are broad, so leave scalar
	 * values alone.
	 *
	 * @param type type a component is registered for
	 * @return true for {@link Object}, {@link java.io.Serializable}, and an
	 *         interface in {@code java.lang} or one of its subpackages, such as
	 *         {@link Comparable} or {@code java.lang.constant.Constable}, that
	 *         isn't {@link #isScalar(Type) scalar}
	 */
	static boolean isBroad(Type type) {
		return JsonAdapters.isBroad(type);
	}

	private static boolean isLessSpecific(Registration<?> candidate, List<? extends Registration<?>> candidates) {
		for (final var other : candidates)
			if (other != candidate //
					&& GenericTypes.isAssignable(candidate.type, other.type) //
					&& !GenericTypes.isAssignable(other.type, candidate.type))
				return true;
		return false;
	}

	/**
	 * Gets the adapters that apply to a type, most specific first; between
	 * adapters neither more specific than the other, one a type declares first.
	 *
	 * @param type   type
	 * @param scalar true for a scalar value, to leave out adapters registered for
	 *               {@link Object}
	 * @return adapters
	 */
	List<AdapterReference> adapters(Type type, boolean scalar) {
		return chain(type, scalar, declared(type).adapters, adapters);
	}

	/**
	 * Gets the serializers and adapters that apply to a type, most specific
	 * first; between components neither more specific than the other, those a
	 * type declares first, then a serializer before an adapter.
	 *
	 * @param type   type
	 * @param scalar true for a scalar value, to leave out components registered
	 *               for {@link Object}
	 * @return {@link JsonbSerializer} and {@link AdapterReference} components
	 */
	List<Object> writeChain(Type type, boolean scalar) {
		final var declared = declared(type);
		return chain(type, scalar, declared.serializers, declared.adapters, serializers, adapters);
	}

	/**
	 * Gets the deserializers and adapters that apply to a type, most specific
	 * first; between components neither more specific than the other, those a
	 * type declares first, then a deserializer before an adapter.
	 *
	 * @param type   type
	 * @param scalar true for a scalar value, to leave out components registered
	 *               for {@link Object}
	 * @return {@link JsonbDeserializer} and {@link AdapterReference} components
	 */
	List<Object> readChain(Type type, boolean scalar) {
		final var declared = declared(type);
		return chain(type, scalar, declared.deserializers, declared.adapters, deserializers, adapters);
	}

	/**
	 * Components a type and its supertypes declare by annotation, each registered
	 * for the type that declares it.
	 */
	private static final class DeclaredComponents {
		private final List<Registration<JsonbSerializer>> serializers = new ArrayList<>();
		private final List<Registration<JsonbDeserializer>> deserializers = new ArrayList<>();
		private final List<Registration<AdapterReference>> adapters = new ArrayList<>();
	}

	private DeclaredComponents declared(Type type) {
		return declaredComponents.computeIfAbsent(JsonAdapters.erase(type), this::declare);
	}

	private DeclaredComponents declare(Class<?> type) {
		final var declared = new DeclaredComponents();
		final Deque<Class<?>> todo = new ArrayDeque<>();
		final Set<Class<?>> done = new HashSet<>();
		todo.add(type);
		while (!todo.isEmpty()) {
			final var next = todo.poll();
			if (IuObject.isPlatformName(next.getName()) //
					|| !done.add(next))
				continue;

			final var serializer = next.getAnnotation(JsonbTypeSerializer.class);
			if (serializer != null)
				declared.serializers.add(new Registration<>(next, (JsonbSerializer) component(serializer.value())));
			final var deserializer = next.getAnnotation(JsonbTypeDeserializer.class);
			if (deserializer != null)
				declared.deserializers
						.add(new Registration<>(next, (JsonbDeserializer) component(deserializer.value())));
			final var adapter = next.getAnnotation(JsonbTypeAdapter.class);
			if (adapter != null)
				declared.adapters.add(new Registration<>(next, adapterReference(adapter.value())));

			final var parent = next.getSuperclass();
			if (parent != null)
				todo.add(parent);
			todo.addAll(Arrays.asList(next.getInterfaces()));
		}
		return declared;
	}

	/**
	 * Gets the one instance of a component class an annotation names, created by
	 * its no-arg constructor.
	 */
	private Object component(Class<?> componentClass) {
		return components.computeIfAbsent(componentClass, c -> {
			final var constructor = IuException.unchecked(() -> c.getDeclaredConstructor());
			constructor.trySetAccessible();
			return IuException.uncheckedInvocation(() -> constructor.newInstance());
		});
	}

	/**
	 * Gets the one reference to an adapter class an annotation names, so it
	 * applies at most once to a value, as a configured adapter does.
	 */
	private AdapterReference adapterReference(Class<?> adapterClass) {
		return declaredAdapters.computeIfAbsent(adapterClass, c -> {
			final var adapter = (JsonbAdapter) component(c);
			final var types = componentTypes(adapter, JsonbAdapter.class);
			return new AdapterReference(types[0], types[1], adapter);
		});
	}

	/**
	 * Gets a value adapter for a property: the type's, or, when the property
	 * declares components, a date or number format that applies to its type, or
	 * both, one with the components ahead of the type's and the format in place
	 * of the built-in conversion.
	 *
	 * @param type    property type
	 * @param date    date format declared; null if none
	 * @param number  number format declared; null if none
	 * @param members accessor, then field, that declare components for the
	 *                direction converted
	 * @return {@link IuJsonbValueAdapter}
	 */
	IuJsonbValueAdapter<Object> adapt(Type type, BindingMetadata.Format date, BindingMetadata.Format number,
			AnnotatedElement[] members) {
		final var declared = declared(type, date, number, members);
		return declared == null ? adapt(type) : declared;
	}

	/**
	 * Gets a value adapter for what a property declares.
	 *
	 * @param type    property type
	 * @param date    date format declared; null if none
	 * @param number  number format declared; null if none
	 * @param members accessor, then field, that declare components for the
	 *                direction converted
	 * @return {@link IuJsonbValueAdapter}; null if the property declares nothing
	 *         that applies
	 * @see #adapt(Type, BindingMetadata.Format, BindingMetadata.Format,
	 *      AnnotatedElement[])
	 */
	IuJsonbValueAdapter<Object> declared(Type type, BindingMetadata.Format date, BindingMetadata.Format number,
			AnnotatedElement[] members) {
		final List<Object> write = new ArrayList<>(2);
		final List<Object> read = new ArrayList<>(2);
		final var serializer = first(members, JsonbTypeSerializer.class);
		if (serializer != null)
			write.add(component(serializer.value()));
		final var deserializer = first(members, JsonbTypeDeserializer.class);
		if (deserializer != null)
			read.add(component(deserializer.value()));
		final var adapter = first(members, JsonbTypeAdapter.class);
		if (adapter != null) {
			final var reference = adapterReference(adapter.value());
			write.add(reference);
			read.add(reference);
		}

		final var formatted = FormatAdapters.declared(type, date, number, configuredDateFormat, locale, strictIJson);
		if (write.isEmpty() && read.isEmpty() && formatted == null)
			return null;
		else
			return new IuJsonbValueAdapter<>(type, this, formatted, write, read);
	}

	private static <A extends Annotation> A first(AnnotatedElement[] members, Class<A> annotationType) {
		for (final var member : members) {
			final var annotation = member.getAnnotation(annotationType);
			if (annotation != null)
				return annotation;
		}
		return null;
	}

	/**
	 * Gets the JSON-P provider.
	 *
	 * @return {@link JsonProvider}
	 */
	JsonProvider provider() {
		return provider;
	}

	/**
	 * Gets the configured property visibility strategy.
	 *
	 * @return {@link PropertyVisibilityStrategy}; null if not configured
	 */
	PropertyVisibilityStrategy propertyVisibilityStrategy() {
		return propertyVisibilityStrategy;
	}

	/**
	 * Gets the configured property order strategy.
	 *
	 * @return {@link PropertyOrderStrategy} constant
	 */
	String propertyOrderStrategy() {
		return propertyOrderStrategy;
	}

	/**
	 * Reads the options in effect for one conversion.
	 *
	 * @return options snapshot; a supplier that answers null reads as
	 *         {@link IuJsonSerializationOptions#DEFAULT}
	 */
	IuJsonSerializationOptions options() {
		final var snapshot = Objects.requireNonNullElse(options.get(), IuJsonSerializationOptions.DEFAULT);
		if (checkConflicts) {
			final var format = format(snapshot);
			if (configuredFormat != null && format != configuredFormat)
				throw new JsonbException(JsonbConfig.PROPERTY_NAMING_STRATEGY + " " + configuredFormat
						+ " conflicts with " + SERIALIZATION_OPTIONS + " property name format " + format);
			if (configuredNullValues != null && snapshot.isIncludeNullProperties() != configuredNullValues)
				throw new JsonbException(JsonbConfig.NULL_VALUES + " " + configuredNullValues + " conflicts with "
						+ SERIALIZATION_OPTIONS + " include null properties " + snapshot.isIncludeNullProperties());
			final var binaryStrategy = Objects.requireNonNullElse(snapshot.getBinaryDataStrategy(),
					IuJsonSerializationOptions.BINARY_DATA_STRATEGY);
			if (configuredBinary != null && !configuredBinary.equals(binaryStrategy))
				throw new JsonbException(JsonbConfig.BINARY_DATA_STRATEGY + " " + configuredBinary + " conflicts with "
						+ SERIALIZATION_OPTIONS + " binary data strategy " + binaryStrategy);
		}
		return snapshot;
	}

	/**
	 * Determines if JSON-B configuration calls for null values.
	 * 
	 * @return true when {@link JsonbConfig#withNullValues(Boolean)
	 *         withNullValues(true)} was invoked on the config
	 */
	boolean includeNullValues() {
		return Boolean.TRUE.equals(configuredNullValues);
	}

	/**
	 * Gets the property model for a business object type.
	 *
	 * @param type business object type, or a parameterized type of one, whose
	 *             arguments the model's property types resolve against
	 * @return {@link IuJsonbModel}
	 */
	IuJsonbModel model(Type type) {
		return models.computeIfAbsent(type, t -> new IuJsonbModel(t, this));
	}

	/**
	 * Gets the adapter for a Java type, as configured.
	 *
	 * <p>
	 * Adapters are created without resolving their dependencies, so this is safe to
	 * call while another adapter is being created.
	 * </p>
	 *
	 * @param type Java type
	 * @return {@link IuJsonbValueAdapter}
	 */
	IuJsonbValueAdapter<Object> adapt(Type type) {
		return (IuJsonbValueAdapter<Object>) valueAdapters.computeIfAbsent(type,
				t -> new IuJsonbValueAdapter<>(t, this));
	}

	/**
	 * Gets this provider's conversions, as a function.
	 *
	 * @return {@link #adapt(Type)}, the same function on every call
	 */
	Function<Type, IuJsonAdapter<?>> conversions() {
		return conversions;
	}

	/**
	 * Gets the configured conversion for a date type: by
	 * {@link JsonbConfig#DATE_FORMAT} and {@link JsonbConfig#LOCALE}, or as
	 * {@link JsonbConfig#STRICT_IJSON} writes dates.
	 *
	 * @param type date type
	 * @return conversion; null if neither is configured
	 */
	IuJsonAdapter<?> dateFormat(Class<?> type) {
		return FormatAdapters.date(type, null, configuredDateFormat, locale, strictIJson);
	}

	/**
	 * Determines if {@link JsonbConfig#STRICT_IJSON} is enabled.
	 *
	 * @return true for strict I-JSON
	 */
	boolean isStrictIJson() {
		return strictIJson;
	}

	/**
	 * Gets the options in effect: the call's in progress on this thread, or a
	 * new snapshot.
	 *
	 * @return {@link IuJsonSerializationOptions}
	 */
	IuJsonSerializationOptions callOptions() {
		final var serializing = IuSerializationContext.current(this);
		if (serializing != null)
			return serializing.options();
		final var deserializing = IuDeserializationContext.current(this);
		if (deserializing != null)
			return deserializing.options();
		return options();
	}

	/**
	 * Names each array, collection, and map item in the path of the call in
	 * progress, so a failure reads as {@code Root.items[2].name} or
	 * {@code Root.byName["k"]}.
	 *
	 * <p>
	 * An item converted with no call in progress, as a lazy {@link Iterable}
	 * converts its items when iterated, tracks nothing; its own conversion starts
	 * a call of its own.
	 * </p>
	 */
	final ItemScope itemScope = new ItemScope() {
		@Override
		public void enterIndex(boolean writing, int index) {
			final var context = context(writing);
			if (context != null)
				context.push("[" + index + "]");
		}

		@Override
		public void enterKey(boolean writing, String key) {
			// a map converts its entries eagerly, within its own call
			context(writing).push("[" + IuJson.string(key) + "]");
		}

		@Override
		public RuntimeException fail(boolean writing, RuntimeException failure) {
			final var context = context(writing);
			if (context != null)
				return context.fail(failure);
			else
				return failure;
		}

		@Override
		public void exit(boolean writing) {
			final var context = context(writing);
			if (context != null)
				context.pop();
		}

		private IuJsonbContext context(boolean writing) {
			if (writing)
				return IuSerializationContext.current(IuJsonb.this);
			else
				return IuDeserializationContext.current(IuJsonb.this);
		}
	};

	/**
	 * Gets the type a platform class converts as, for a class with no built-in
	 * conversion of its own, such as a JDK-internal collection or a subclass of a
	 * supported type.
	 *
	 * <p>
	 * The nearest superclass with a built-in conversion is used, short of
	 * {@link Object}; failing that, the first of {@link CharSequence}, {@link Map},
	 * {@link List}, {@link Set}, {@link Collection}, {@link Iterable},
	 * {@link Iterator}, {@link Enumeration}, and {@link Stream} the class
	 * implements.
	 * </p>
	 *
	 * @param type platform class
	 * @return {@code type} itself if it has a conversion of its own; the type it
	 *         converts as; {@link Object} if none applies
	 */
	Type conversionType(Class<?> type) {
		return JsonAdapters.conversionType(type);
	}

	/**
	 * Reads a value as a new call, even when one is already in progress.
	 */
	private <T> T read(JsonParser parser, Type type, String text) {
		try (parser) {
			return IuDeserializationContext.start(this, type, parser, text, c -> {
				parser.next();
				return (T) adapt(type).read(parser);
			});
		}
	}

	/**
	 * Writes a value as a new call, even when one is already in progress.
	 */
	private void write(JsonGenerator generator, Object object, Type type) {
		final var runtimeType = type == null ? IuSerializationContext.runtimeType(object) : type;
		try (generator) {
			IuSerializationContext.start(this, runtimeType, c -> {
				if (strictIJson) {
					// I-JSON's top-level value is an object or array
					final var value = adapt(runtimeType).toJson(object);
					if (!(value instanceof JsonStructure))
						throw new JsonbException(JsonbConfig.STRICT_IJSON
								+ " writes only an object or array at the top level, not " + value.getValueType());
					generator.write(value);
				} else
					adapt(runtimeType).write(object, generator);
				return null;
			});
		}
	}

	@Override
	public <T> T fromJson(String str, Class<T> type) throws JsonbException {
		return fromJson(str, (Type) type);
	}

	@Override
	public <T> T fromJson(String str, Type runtimeType) throws JsonbException {
		if (str == null)
			return null;
		else
			return read(parserFactory.createParser(new StringReader(str)), runtimeType, str);
	}

	@Override
	public <T> T fromJson(Reader reader, Class<T> type) throws JsonbException {
		return fromJson(reader, (Type) type);
	}

	@Override
	public <T> T fromJson(Reader reader, Type runtimeType) throws JsonbException {
		return read(parserFactory.createParser(Objects.requireNonNull(reader, "reader")), runtimeType, null);
	}

	@Override
	public <T> T fromJson(InputStream stream, Class<T> type) throws JsonbException {
		return fromJson(stream, (Type) type);
	}

	@Override
	public <T> T fromJson(InputStream stream, Type runtimeType) throws JsonbException {
		Objects.requireNonNull(stream, "stream");
		// detected from the stream unless configured
		final var parser = encoding == null //
				? parserFactory.createParser(stream)
				: parserFactory.createParser(stream, encoding);
		return read(parser, runtimeType, null);
	}

	@Override
	public String toJson(Object object) throws JsonbException {
		return toJson(object, (Type) null);
	}

	@Override
	public String toJson(Object object, Type runtimeType) throws JsonbException {
		final var writer = new StringWriter();
		toJson(object, runtimeType, writer);
		return writer.toString();
	}

	@Override
	public void toJson(Object object, Writer writer) throws JsonbException {
		toJson(object, null, writer);
	}

	@Override
	public void toJson(Object object, Type runtimeType, Writer writer) throws JsonbException {
		write(generatorFactory.createGenerator(writer), object, runtimeType);
	}

	@Override
	public void toJson(Object object, OutputStream stream) throws JsonbException {
		toJson(object, null, stream);
	}

	@Override
	public void toJson(Object object, Type runtimeType, OutputStream stream) throws JsonbException {
		write(generatorFactory.createGenerator(stream, Objects.requireNonNullElse(encoding, StandardCharsets.UTF_8)),
				object, runtimeType);
	}

	@Override
	public void close() throws Exception {
	}

}
