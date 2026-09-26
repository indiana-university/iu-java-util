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
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import edu.iu.IuObject;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import iu.client.GenericTypes;
import iu.client.JsonAdapters;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
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
 * {@link JsonbConfig#PROPERTY_NAMING_STRATEGY} ({@link PropertyNamingStrategy#IDENTITY}
 * and {@link PropertyNamingStrategy#LOWER_CASE_WITH_UNDERSCORES}),
 * {@link JsonbConfig#PROPERTY_ORDER_STRATEGY},
 * {@link JsonbConfig#PROPERTY_VISIBILITY_STRATEGY},
 * {@link JsonbConfig#SERIALIZERS}, {@link JsonbConfig#DESERIALIZERS}, and
 * {@link JsonbConfig#ADAPTERS}, as well as {@link #SERIALIZATION_OPTIONS}.
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
 * A component registered for {@link Object} doesn't apply to a
 * {@link #isScalar(Type) scalar} type: text, a number, or a boolean. It doesn't
 * see a null declared as one of those types, nor a value written whose runtime
 * type is one, nor a JSON string, number, or boolean read as any type. It does
 * see a null of a type that isn't scalar, or that isn't known, as when a null
 * is written without a type or passed to a context by a serializer that isn't
 * already converting it. Other values written as text, such as dates, still
 * reach {@link Object} components. Components registered for a supertype of a
 * scalar type, such as {@link CharSequence}, {@link Number}, or
 * {@link Comparable}, apply as usual.
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
	 * {@code Supplier<IuJsonSerializationOptions>}, read once per call; must
	 * agree with {@link JsonbConfig#PROPERTY_NAMING_STRATEGY} and
	 * {@link JsonbConfig#NULL_VALUES} when those are also set.
	 */
	public static final String SERIALIZATION_OPTIONS = "iu.jsonb.serializationOptions";

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

		private Registration(Type type, T component) {
			this.type = type;
			this.component = component;
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
	private final Boolean configuredNullValues;
	private final boolean checkConflicts;
	private final PropertyVisibilityStrategy propertyVisibilityStrategy;
	private final String propertyOrderStrategy;
	private final List<Registration<JsonbDeserializer>> deserializers;
	private final List<Registration<JsonbSerializer>> serializers;
	private final List<Registration<AdapterReference>> adapters;
	private final Map<Type, IuJsonbValueAdapter<?>> valueAdapters = new ConcurrentHashMap<>();
	private final Map<Class<?>, IuJsonbModel> models = new ConcurrentHashMap<>();

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

		final List<Registration<JsonbDeserializer>> deserializers = new ArrayList<>();
		for (final var deserializer : (JsonbDeserializer[]) config.getProperty(JsonbConfig.DESERIALIZERS)
				.orElse(new JsonbDeserializer[0]))
			register(deserializers, componentTypes(deserializer, JsonbDeserializer.class)[0], deserializer,
					"deserializer");
		this.deserializers = Collections.unmodifiableList(deserializers);

		final List<Registration<JsonbSerializer>> serializers = new ArrayList<>();
		for (final var serializer : (JsonbSerializer[]) config.getProperty(JsonbConfig.SERIALIZERS)
				.orElse(new JsonbSerializer[0]))
			register(serializers, componentTypes(serializer, JsonbSerializer.class)[0], serializer, "serializer");
		this.serializers = Collections.unmodifiableList(serializers);

		final List<Registration<AdapterReference>> adapters = new ArrayList<>();
		for (final var adapter : (JsonbAdapter[]) config.getProperty(JsonbConfig.ADAPTERS)
				.orElse(new JsonbAdapter[0])) {
			final var types = componentTypes(adapter, JsonbAdapter.class);
			register(adapters, types[0], new AdapterReference(types[0], types[1], adapter), "adapter");
		}
		this.adapters = Collections.unmodifiableList(adapters);

		configuredFormat = IuObject.convert(config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY).orElse(null),
				IuJsonb::propertyNameFormat);
		configuredNullValues = (Boolean) config.getProperty(JsonbConfig.NULL_VALUES).orElse(null);

		// IU options, when supplied, must agree with the equivalent JSON-B settings;
		// the supplier is dynamic, so each call's snapshot is checked
		final var serializationOptions = (Supplier<IuJsonSerializationOptions>) config
				.getProperty(SERIALIZATION_OPTIONS).orElse(null);
		if (serializationOptions != null) {
			options = serializationOptions;
			checkConflicts = configuredFormat != null || configuredNullValues != null;
		} else {
			final var configuredOptions = IuJsonSerializationOptions.of( //
					Objects.requireNonNullElse(configuredFormat, IuJsonPropertyNameFormat.IDENTITY), //
					Objects.requireNonNullElse(configuredNullValues, false));
			options = () -> configuredOptions;
			checkConflicts = false;
		}
	}

	private static IuJsonPropertyNameFormat propertyNameFormat(Object propertyNamingStrategy) {
		if (propertyNamingStrategy instanceof String)
			switch ((String) propertyNamingStrategy) {
			case PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES:
				return IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES;

			case PropertyNamingStrategy.IDENTITY:
				return IuJsonPropertyNameFormat.IDENTITY;

			default:
				throw new UnsupportedOperationException((String) propertyNamingStrategy);
			}
		else
			throw new UnsupportedOperationException(propertyNamingStrategy.toString());
	}

	/**
	 * Resolves the types a configured component converts, through
	 * sub-interfaces and generic superclasses.
	 *
	 * @param component          serializer, deserializer, or adapter
	 * @param componentInterface interface it implements
	 * @return one type per type parameter of {@code componentInterface}, which
	 *         may be or contain a type variable the component itself declares
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
						+ " on the class or one of its supertypes, since a lambda or raw implementation has none");
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
					+ "or a parameterized type such as List<" + type.getTypeName() + ">");
		registry.add(new Registration<>(type, component));
	}

	/**
	 * Orders the components that apply to a type.
	 *
	 * <p>
	 * A component applies to the type it is registered for and every subtype.
	 * The chain runs from the most specific registration to the least;
	 * registrations neither more nor less specific than each other, including
	 * several for the same type, run in the order they were configured.
	 * </p>
	 *
	 * @param <T>      component type
	 * @param registry components in the order they were configured
	 * @param type     type being converted; a primitive is boxed, and a type
	 *                 variable or wildcard reads as its upper bound
	 * @param scalar   true to leave out components registered for
	 *                 {@link Object}, for a scalar value
	 * @return components that apply, in order
	 */
	private static <T> List<T> chain(List<Registration<T>> registry, Type type, boolean scalar) {
		var lookup = GenericTypes.box(type);
		while (lookup instanceof TypeVariable || lookup instanceof WildcardType)
			lookup = lookup instanceof TypeVariable //
					? ((TypeVariable<?>) lookup).getBounds()[0]
					: ((WildcardType) lookup).getUpperBounds()[0];

		final List<Registration<T>> candidates = new ArrayList<>();
		for (final var registration : registry)
			if (GenericTypes.isAssignable(registration.type, lookup) //
					&& !(scalar && registration.type == Object.class))
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
	 * Determines if values of a type are scalar, so components registered for
	 * {@link Object} leave them alone.
	 *
	 * @param type type
	 * @return true for {@link CharSequence}, {@link Number}, {@link Boolean}, their
	 *         subtypes, and the primitive number types and {@code boolean}
	 */
	static boolean isScalar(Type type) {
		final var c = (Class<?>) GenericTypes.box(JsonAdapters.erase(type));
		return CharSequence.class.isAssignableFrom(c) //
				|| Number.class.isAssignableFrom(c) //
				|| c == Boolean.class;
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
	 * Determines if any adapter or serializer is configured, so writing a value
	 * might select a component by its runtime type.
	 *
	 * @return true if adapters or serializers are configured
	 */
	boolean hasWriteComponents() {
		return !adapters.isEmpty() || !serializers.isEmpty();
	}

	/**
	 * Gets the adapters that apply to a type, most specific first.
	 *
	 * @param type   type
	 * @param scalar true for a scalar value, to leave out adapters registered for
	 *               {@link Object}
	 * @return adapters
	 */
	List<AdapterReference> adapters(Type type, boolean scalar) {
		return chain(adapters, type, scalar);
	}

	/**
	 * Gets the serializers that apply to a type, most specific first.
	 *
	 * @param type   type
	 * @param scalar true for a scalar value, to leave out serializers registered
	 *               for {@link Object}
	 * @return serializers
	 */
	List<JsonbSerializer> serializers(Type type, boolean scalar) {
		return chain(serializers, type, scalar);
	}

	/**
	 * Gets the deserializers that apply to a type, most specific first.
	 *
	 * @param type   type
	 * @param scalar true for a scalar value, to leave out deserializers
	 *               registered for {@link Object}
	 * @return deserializers
	 */
	List<JsonbDeserializer> deserializers(Type type, boolean scalar) {
		return chain(deserializers, type, scalar);
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
		}
		return snapshot;
	}

	/**
	 * Gets the property model for a business object type.
	 *
	 * @param type business object type
	 * @return {@link IuJsonbModel}
	 */
	IuJsonbModel model(Class<?> type) {
		return models.computeIfAbsent(type, t -> new IuJsonbModel(t, this));
	}

	/**
	 * Gets the adapter for a Java type, as configured.
	 *
	 * <p>
	 * Adapters are created without resolving their dependencies, so this is safe
	 * to call while another adapter is being created.
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
	 * Reads a value as a new call, even when one is already in progress.
	 */
	private <T> T read(JsonParser parser, Type type) {
		try (parser) {
			return IuDeserializationContext.start(this, type, parser, c -> {
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
			return fromJson(new StringReader(str), runtimeType);
	}

	@Override
	public <T> T fromJson(Reader reader, Class<T> type) throws JsonbException {
		return fromJson(reader, (Type) type);
	}

	@Override
	public <T> T fromJson(Reader reader, Type runtimeType) throws JsonbException {
		return read(parserFactory.createParser(Objects.requireNonNull(reader, "reader")), runtimeType);
	}

	@Override
	public <T> T fromJson(InputStream stream, Class<T> type) throws JsonbException {
		return fromJson(stream, (Type) type);
	}

	@Override
	public <T> T fromJson(InputStream stream, Type runtimeType) throws JsonbException {
		return read(parserFactory.createParser(Objects.requireNonNull(stream, "stream")), runtimeType);
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
		write(generatorFactory.createGenerator(stream), object, runtimeType);
	}

	@Override
	public void close() throws Exception {
	}

}
