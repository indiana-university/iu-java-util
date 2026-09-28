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
package iu.client;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * Converts from a JavaBeans business object to JSON.
 */
public final class JsonSerializer {
	static {
		IuObject.assertNotOpen(JsonSerializer.class);
	}

	private JsonSerializer() {
	}

	/**
	 * Name of the property that holds {@link Enum#name()} when an enum value
	 * converts to a {@link JsonObject}.
	 */
	public static final String NAME = "name";

	/**
	 * Formats a property name for JSON serialization.
	 * 
	 * @param propertyName       property name
	 * @param propertyNameFormat format
	 * @return formatted property name
	 */
	public static String formatPropertyName(String propertyName, IuJsonPropertyNameFormat propertyNameFormat) {
		switch (propertyNameFormat) {
		case LOWER_CASE_WITH_UNDERSCORES:
			return JsonProxy.convertToSnakeCase(propertyName);

		case UPPER_CASE_WITH_UNDERSCORES:
			return JsonProxy.convertToSnakeCase(propertyName).toUpperCase();

		default:
		case IDENTITY:
			return propertyName;
		}
	}

	/**
	 * Serializes a business object as JSON, with default options apart from the
	 * property name format.
	 *
	 * @param <T>                value type
	 * @param type               value type for introspection
	 * @param value              business object to serialize
	 * @param propertyNameFormat property name format
	 * @param adapt              adapter function
	 * @return {@link JsonObject}
	 * @see #serialize(Class, Object, Supplier, Function)
	 */
	public static <T> JsonObject serialize(Class<T> type, T value, IuJsonPropertyNameFormat propertyNameFormat,
			Function<Type, IuJsonAdapter<?>> adapt) {
		final var options = IuJsonSerializationOptions.of(propertyNameFormat);
		return serialize(type, value, () -> options, adapt);
	}

	/**
	 * Serializes a business object as JSON.
	 *
	 * @param <T>     value type
	 * @param type    value type for introspection
	 * @param value   business object to serialize
	 * @param options supplies the options in effect; <em>should</em> return
	 *                quickly, as it is called on every invocation
	 * @param adapt   adapter function
	 * @return {@link JsonObject}
	 * @see #serialize(Type, Object, Supplier, Function)
	 */
	public static <T> JsonObject serialize(Class<T> type, T value, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {
		return serialize((Type) type, value, options, adapt);
	}

	/**
	 * Serializes a business object as JSON.
	 *
	 * <p>
	 * Includes an entry for each readable property of {@code type}, as
	 * {@link BeanModel} discovers them: public fields and accessors, including
	 * those inherited from non-platform superclasses and interfaces, in
	 * lexicographic order, honoring JSON-B annotations when the JSON-B API is
	 * present. {@link IuJsonSerializationOptions#isLegacyProperties()} restores
	 * discovery by public accessors only. Property types resolve against
	 * {@code type}, so a type variable of a generic superclass converts as the
	 * argument supplied for it.
	 * </p>
	 *
	 * <p>
	 * A property with a null value, or an empty {@link java.util.Optional} unless
	 * {@link IuJsonSerializationOptions#isEmptyOptionalPresent()}, is omitted
	 * unless declared nillable, or
	 * {@link IuJsonSerializationOptions#isIncludeNullProperties()}.
	 * </p>
	 *
	 * <p>
	 * A value wrapped by {@link IuJson#wrap(JsonObject, Class)} is returned as its
	 * source {@link JsonObject}, without introspection, so no option applies to it.
	 * This preserves properties the wrapped interface doesn't declare, as when a
	 * value is handled through a stub.
	 * </p>
	 *
	 * <p>
	 * One options snapshot is read for each invocation, so an adapter that captured
	 * {@code options} observes a configuration change without being recreated. A
	 * supplier that answers null, or an option that answers null, reads as the
	 * default for this invocation.
	 * </p>
	 *
	 * @param type    value type for introspection, or a parameterized type of one
	 * @param value   business object to serialize
	 * @param options supplies the options in effect; <em>should</em> return
	 *                quickly, as it is called on every invocation
	 * @param adapt   adapter function
	 * @return {@link JsonObject}
	 */
	public static JsonObject serialize(Type type, Object value, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {

		final var valueClass = value.getClass();
		if (Proxy.isProxyClass(valueClass)) {
			final var invocationHandler = Proxy.getInvocationHandler(value);
			if (invocationHandler instanceof JsonProxy)
				return JsonProxy.unwrap(value);
		}

		// one snapshot per invocation, so an options change takes effect without
		// recreating the adapters that captured the supplier
		final var snapshot = snapshot(options);

		final var builder = IuJson.object();
		addProperties(type, value, snapshot, adapt, builder);
		return builder.build();
	}

	/**
	 * Gets the property model for a type as the options discover properties.
	 *
	 * @param type     business object type
	 * @param snapshot options
	 * @return {@link BeanModel#legacy(Type)} if
	 *         {@link IuJsonSerializationOptions#isLegacyProperties()}; else
	 *         {@link BeanModel#of(Type)}
	 */
	static BeanModel model(Type type, IuJsonSerializationOptions snapshot) {
		return snapshot.isLegacyProperties() ? BeanModel.legacy(type) : BeanModel.of(type);
	}

	/**
	 * Gets the conversion for a map key under options: the key type's built-in
	 * text conversion, with an enum as text by the options' enum text, never as
	 * an object.
	 *
	 * @param type    key type
	 * @param options supplies the options in effect for each conversion
	 * @return key adapter
	 */
	public static IuJsonAdapter<?> keyAdapter(Type type, Supplier<IuJsonSerializationOptions> options) {
		final var erased = JsonAdapters.erase(type);
		if (!erased.isEnum())
			return JsonAdapters.adapt(type, null);

		return EnumJsonAdapter.of(erased, () -> {
			final var snapshot = snapshot(options);
			return new IuJsonSerializationOptions() {
				@Override
				public boolean isEnumToString() {
					return snapshot.isEnumToString();
				}
			};
		}, null);
	}

	/**
	 * Gets an enum value's text.
	 *
	 * @param value    enum value
	 * @param snapshot options
	 * @return {@link Enum#name()}, or {@link Enum#toString()} if the options ask for it
	 */
	static String enumText(Enum<?> value, IuJsonSerializationOptions snapshot) {
		return snapshot.isEnumToString() ? value.toString() : value.name();
	}

	/**
	 * Serializes an enum value as JSON.
	 *
	 * <p>
	 * Returns a {@link jakarta.json.JsonString JsonString} holding
	 * {@link Enum#name()}, or {@link Enum#toString()} when
	 * {@link IuJsonSerializationOptions#isEnumToString()}, unless
	 * {@link IuJsonSerializationOptions#isEnumAsObject()}, in which case the value
	 * converts to a {@link JsonObject} holding the {@link #NAME} property, with
	 * {@link Enum#name()}, followed by the readable properties of {@code type}.
	 * The {@link #NAME} property comes first either way; an enum that declares its
	 * own answers the value, which is only sound when that property answers the
	 * constant name.
	 * </p>
	 *
	 * <p>
	 * Introspection uses {@code type} rather than the value's class, so a constant
	 * declared with a class body converts to the same shape as every other
	 * constant of the enum, while a property it overrides answers the override.
	 * </p>
	 *
	 * @param type    enum type for introspection
	 * @param value   enum value to serialize
	 * @param options supplies the options in effect; <em>should</em> return
	 *                quickly, as it is called on every invocation
	 * @param adapt   adapter function
	 * @return {@link JsonValue}
	 */
	static JsonValue serializeEnum(Class<?> type, Enum<?> value, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {

		// one snapshot per invocation, as in serialize(Class, Object, Supplier,
		// Function)
		final var snapshot = snapshot(options);
		if (!snapshot.isEnumAsObject())
			return IuJson.string(enumText(value, snapshot));

		final var propertyNameFormat = propertyNameFormat(snapshot);
		final var nameProperty = formatPropertyName(NAME, propertyNameFormat);

		final var properties = IuJson.object();
		addProperties(type, value, snapshot, adapt, properties);
		final var declared = properties.build();

		// the name property comes first whether or not the enum declares one of its
		// own; a builder rejects a duplicate key, so the value is chosen up front
		final var builder = IuJson.object();
		if (declared.containsKey(nameProperty))
			builder.add(nameProperty, declared.get(nameProperty));
		else
			builder.add(nameProperty, IuJson.string(value.name()));

		for (final var declaredProperty : declared.entrySet())
			if (!nameProperty.equals(declaredProperty.getKey()))
				builder.add(declaredProperty.getKey(), declaredProperty.getValue());

		return builder.build();
	}

	/**
	 * Reads the options in effect for one conversion.
	 *
	 * @param options options supplier
	 * @return {@link IuJsonSerializationOptions}; a supplier that answers null
	 *         reads as {@link IuJsonSerializationOptions#DEFAULT}
	 */
	static IuJsonSerializationOptions snapshot(Supplier<IuJsonSerializationOptions> options) {
		return Objects.requireNonNullElse(options.get(), IuJsonSerializationOptions.DEFAULT);
	}

	/**
	 * Reads the property name format from an options snapshot.
	 *
	 * @param snapshot options snapshot
	 * @return {@link IuJsonPropertyNameFormat}; a snapshot that answers null reads
	 *         as {@link IuJsonSerializationOptions#PROPERTY_NAME_FORMAT}
	 */
	static IuJsonPropertyNameFormat propertyNameFormat(IuJsonSerializationOptions snapshot) {
		return Objects.requireNonNullElse(snapshot.getPropertyNameFormat(),
				IuJsonSerializationOptions.PROPERTY_NAME_FORMAT);
	}

	/**
	 * Gets the conversion for writing a property to JSON: by the JSON-B
	 * components it declares, else by the date or number format it declares, if
	 * one applies to its type, else by its type.
	 *
	 * @param property property
	 * @param adapt    adapter function
	 * @param snapshot options
	 * @return {@link IuJsonAdapter}
	 */
	static IuJsonAdapter<?> readAdapter(BeanModel.Property property, Function<Type, IuJsonAdapter<?>> adapt,
			IuJsonSerializationOptions snapshot) {
		return declared(property.readType(), property.readDateFormat(), property.readNumberFormat(),
				property.readMembers(), adapt, snapshot);
	}

	/**
	 * Gets the conversion for reading a property from JSON: by the JSON-B
	 * components it declares, else by the date or number format it declares, if
	 * one applies to its type, else by its type.
	 *
	 * @param property property
	 * @param adapt    adapter function
	 * @param snapshot options
	 * @return {@link IuJsonAdapter}
	 */
	static IuJsonAdapter<?> writeAdapter(BeanModel.Property property, Function<Type, IuJsonAdapter<?>> adapt,
			IuJsonSerializationOptions snapshot) {
		return declared(property.writeType(), property.writeDateFormat(), property.writeNumberFormat(),
				property.writeMembers(), adapt, snapshot);
	}

	private static IuJsonAdapter<?> declared(Type type, BindingMetadata.Format date, BindingMetadata.Format number,
			AnnotatedElement[] members, Function<Type, IuJsonAdapter<?>> adapt, IuJsonSerializationOptions snapshot) {
		final var metadata = snapshot.isLegacyProperties() ? BindingMetadata.NONE : BindingMetadata.get();
		final var components = metadata.components(type, date, number, members, () -> snapshot);
		if (components != null)
			return components;

		final var formatted = FormatAdapters.declared(type, date, number, null, null, false);
		return formatted == null ? adapt.apply(type) : formatted;
	}

	/**
	 * Adds an entry for each readable property of a type.
	 *
	 * @param type     value type for introspection
	 * @param value    value to read properties from
	 * @param snapshot options
	 * @param adapt    adapter function
	 * @param builder  receives one entry per property
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void addProperties(Type type, Object value, IuJsonSerializationOptions snapshot,
			Function<Type, IuJsonAdapter<?>> adapt, JsonObjectBuilder builder) {
		final var naming = PropertyNaming.of(propertyNameFormat(snapshot));
		for (final var property : model(type, snapshot).readable(naming)) {
			final var name = property.readName(naming);
			final var propertyValue = property.get(value);
			if (propertyValue != null //
					&& (snapshot.isEmptyOptionalPresent() || !BeanModel.isAbsent(propertyValue)))
				builder.add(name, ((IuJsonAdapter) readAdapter(property, adapt, snapshot)).toJson(propertyValue));
			else if (Objects.requireNonNullElse(property.nillable(), snapshot.isIncludeNullProperties()))
				builder.addNull(name);
		}
	}

}
