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
package iu.type.jsonb;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;

import edu.iu.IuIterable;
import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuAttribute;
import edu.iu.type.IuType;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.spi.JsonProvider;

/**
 * Stack processing node for internal use by {@link IuJsonb}.
 * 
 * <p>
 * Encapsulates adding a single value via {@link JsonStructureBuilder} once
 * ready, or returning an in-order sequence of pending values to push, starting
 * with {@code this}, which <em>must</em> be resolved first.
 * </p>
 * 
 * @param <T> value type
 */
class PendingToJsonValue<T> implements Function<IuJsonbConfig, Iterable<PendingToJsonValue<?>>> {

	private final JsonProvider jsonpProvider;
	private final JsonStructureBuilder builder;
	private final IuAnnotatedElement annotatedElement;
	private final IuType<?, T> type;
	private final T sourceValue;

	/**
	 * Constructor.
	 * 
	 * @param jsonpProvider    {@link JsonProvider}
	 * @param builder          {@link JsonStructureBuilder}
	 * @param annotatedElement {@link IuAnnotatedElement}
	 * @param sourceValue      source value
	 */
	@SuppressWarnings("unchecked")
	PendingToJsonValue(JsonProvider jsonpProvider, JsonStructureBuilder builder, IuAnnotatedElement annotatedElement,
			T sourceValue) {
		this.jsonpProvider = jsonpProvider;
		this.builder = builder;
		this.annotatedElement = annotatedElement;

		if (annotatedElement instanceof IuType)
			this.type = (IuType<?, T>) annotatedElement;
		else
			this.type = ((IuAttribute<?, T>) annotatedElement).type();

		this.sourceValue = type.autoboxClass().cast(sourceValue);
	}

	/**
	 * Serializes a {@link Date} value.
	 * 
	 * @param value {@link Date}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-util-date-calendar-gregoriancalendar">JSON-B
	 *      3.0 Section 3.5.1</a>
	 */
	JsonValue serialize(Date value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes a {@link Calendar}.
	 * 
	 * @param value {@link Calendar}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-util-date-calendar-gregoriancalendar">JSON-B
	 *      3.0 Section 3.5.1</a>
	 */
	JsonValue serialize(Calendar value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes a {@link TimeZone}.
	 * 
	 * @param value {@link TimeZone}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-util-timezone-simpletimezone">JSON-B
	 *      3.0 Section 3.5.2</a>
	 */
	JsonValue serialize(TimeZone value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes an {@link Instant}.
	 * 
	 * @param value {@link Instant}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-time">JSON-B
	 *      3.0 Section 3.5.3</a>
	 */
	JsonValue serialize(Instant value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes an {@link LocalDate}.
	 * 
	 * @param value {@link LocalDate}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-time">JSON-B
	 *      3.0 Section 3.5.3</a>
	 */
	JsonValue serialize(LocalDate value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes an {@link LocalTime}.
	 * 
	 * @param value {@link LocalTime}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-time">JSON-B
	 *      3.0 Section 3.5.3</a>
	 */
	JsonValue serialize(LocalTime value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes an {@link LocalDateTime}.
	 * 
	 * @param value {@link LocalDateTime}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-time">JSON-B
	 *      3.0 Section 3.5.3</a>
	 */
	JsonValue serialize(LocalDateTime value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes an {@link ZonedDateTime}.
	 * 
	 * @param value {@link ZonedDateTime}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-time">JSON-B
	 *      3.0 Section 3.5.3</a>
	 */
	JsonValue serialize(ZonedDateTime value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes an {@link OffsetDateTime}.
	 * 
	 * @param value {@link OffsetDateTime}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-time">JSON-B
	 *      3.0 Section 3.5.3</a>
	 */
	JsonValue serialize(OffsetDateTime value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes an {@link OffsetTime}.
	 * 
	 * @param value {@link OffsetTime}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-time">JSON-B
	 *      3.0 Section 3.5.3</a>
	 */
	JsonValue serialize(OffsetTime value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes a {@link URI}
	 * 
	 * @param value {@link URI}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-net-url-uri">JSON-B
	 *      3.0 Section 3.4.2</a>
	 */
	JsonValue serialize(URI value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Serializes a {@link URL}
	 * 
	 * @param value {@link URL}
	 * @return {@link JsonValue}
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-net-url-uri">JSON-B
	 *      3.0 Section 3.4.2</a>
	 */
	JsonValue serialize(URL value) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Adds binary data
	 * 
	 * @param binary binary data
	 * @return {@link JsonValue}
	 * @see BinaryDataStrategy
	 */
	JsonValue serialize(byte[] binary) {
		// TODO: implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<PendingToJsonValue<?>> apply(IuJsonbConfig config) {
		var empty = true;

		if (sourceValue == null) {
			if (!config.isSkipNull(annotatedElement))
				builder.addNull();
		}

		else if (sourceValue instanceof JsonValue jsonValue) {
			if (!config.isSkipNull(annotatedElement) || !(sourceValue instanceof JsonArray array) //
					|| !array.isEmpty())
				builder.add(jsonValue);
		}

		else if (sourceValue instanceof JsonArrayBuilder arrayBuilder) {
			if (!config.isSkipNull(annotatedElement)) {
				JsonArray array = arrayBuilder.build();
				if (!array.isEmpty())
					builder.add(array);
			} else
				builder.add(arrayBuilder);
		}

		else if (sourceValue instanceof JsonObjectBuilder o)
			builder.add(o);

		else if (sourceValue instanceof Boolean b)
			builder.add(b);
		else if (sourceValue instanceof Long l)
			builder.add(l.longValue());
		else if (sourceValue instanceof Integer i)
			builder.add(i.intValue());
		else if (sourceValue instanceof Short s)
			builder.add(s.shortValue());
		else if (sourceValue instanceof Byte b)
			builder.add(b.byteValue());
		else if (sourceValue instanceof Float f)
			builder.add(f.floatValue());
		else if (sourceValue instanceof Character c)
			builder.add(c.toString());

		else if (sourceValue instanceof String s)
			builder.add(s);

		else if (sourceValue instanceof BigInteger i)
			builder.add(i);
		else if (sourceValue instanceof BigDecimal d)
			builder.add(d);

		// Covers double by default
		// https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#java-lang-number
		else if (sourceValue instanceof Number n)
			builder.add(n.doubleValue());

		else if (sourceValue instanceof Date d)
			builder.add(serialize(d));
		else if (sourceValue instanceof Calendar c)
			builder.add(serialize(c));
		else if (sourceValue instanceof TimeZone z)
			builder.add(serialize(z));
		else if (sourceValue instanceof Instant i)
			builder.add(serialize(i));
		else if (sourceValue instanceof LocalDate d)
			builder.add(serialize(d));
		else if (sourceValue instanceof LocalTime t)
			builder.add(serialize(t));
		else if (sourceValue instanceof LocalDateTime l)
			builder.add(serialize(l));
		else if (sourceValue instanceof ZonedDateTime z)
			builder.add(serialize(z));
		else if (sourceValue instanceof OffsetDateTime o)
			builder.add(serialize(o));
		else if (sourceValue instanceof OffsetTime o)
			builder.add(serialize(o));
		else if (sourceValue instanceof URI u)
			builder.add(serialize(u));
		else if (sourceValue instanceof URL u)
			builder.add(serialize(u));

		else if (sourceValue instanceof byte[])
			builder.add(Base64.getEncoder().encodeToString((byte[]) sourceValue));

		else if (sourceValue instanceof Enum<?> e)
			builder.add(e.name());

		else if (config.isClassSerializationAllowed() && (sourceValue instanceof Class<?> c))
			builder.add(c.getName());

		else
			empty = false;

		if (empty)
			return IuIterable.empty();

		if (sourceValue instanceof Iterable<?> iterable) {
			// TODO implementation stub
			throw new UnsupportedOperationException("TODO");
		}

		else if (type.erasedClass().isArray()) {
			// TODO implementation stub
			throw new UnsupportedOperationException("TODO");
		}

		else if (sourceValue instanceof Map<?, ?> map) {
			// TODO implementation stub
			throw new UnsupportedOperationException("TODO");
		}

		else {
			final var builder = jsonpProvider.createObjectBuilder();

			Map<String, IuAttribute<? super T, ?>> attributes = new LinkedHashMap<>();
			for (final var property : type.properties())
				attributes.put(property.name(), property);

			field: for (final var field : type.fields()) {
				if (attributes.containsKey(field.name()))
					continue;
				
				if (field.serializable()) {
					// apply logic defined by
					// https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#scope-and-field-access-strategy
					final var fieldName = field.name();
					final var propertySuffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
					final var getterName = type.erasedClass() == boolean.class ? "is" + propertySuffix
							: "get" + propertySuffix;
					for (final var method : type.methods())
						if (method.name().equals(getterName) && method.parameters().isEmpty())
							continue field;

					attributes.put(field.name(), field);
				}
			}
			

			//
////		if (Proxy.isProxyClass(value.getClass())) {
////			InvocationHandler invocationHandler = Proxy.getInvocationHandler(value);
////			if (invocationHandler instanceof JsonObjectProxy) {
////				currentBuilder.value = ((JsonObjectProxy) value).object;
////				buildStack.push(currentBuilder);
////				continue;
////			}
////		}
//
//		assert itemStack.isEmpty() : itemStack;
//
//		JsonObjectBuilder jso = Json.createObjectBuilder();
//
//		PropertyDescriptor[] props;
//		if (value == v && properties != null && properties.length > 0) {
//			props = new PropertyDescriptor[properties.length];
//			for (int i = 0; i < properties.length; i++)
//				props[i] = getPropertyDescriptor(type, properties[i]);
//		} else
//			props = getPrintSafeProperties(type);
//
//		for (PropertyDescriptor propertyDescriptor : props)
//			try {
//				try {
//					itemStack.push(new PendingToJsonValue(jso, propertyDescriptor.getName(),
//							propertyDescriptor.getReadMethod().invoke(value), currentBuilder.type == null ? null
//									: propertyDescriptor.getReadMethod().getGenericReturnType()));
//				} catch (InvocationTargetException e) {
//					throw e.getCause();
//				}
//			} catch (RuntimeException e) {
//				throw e;
//			} catch (Throwable e) {
//				throw new IllegalStateException(propertyDescriptor.getReadMethod().toString(), e);
//			}
//
//		currentBuilder.value = jso;
//		buildStack.push(currentBuilder);
//		while (!itemStack.isEmpty())
//			buildStack.push(itemStack.pop());
			throw new UnsupportedOperationException("TODO");
		}

		// TODO: REVIEW LINE
//		config.fail(() -> "Unsupported value for serialization: " + value.getClass());
//		// Array values
//		else if (value instanceof Iterable<?> iterable) {
//			assert itemStack.isEmpty() : itemStack;
//			JsonArrayBuilder jsa = Json.createArrayBuilder();
//			for (Object item : iterable)
//				itemStack.push(new PendingToJsonValue<>(jsa, 
//						jsa, item,
//						currentBuilder.type == null ? null : ReflectionUtil.getComponentType(type)));
//
//			currentBuilder.value = jsa;
//			buildStack.push(currentBuilder);
//			while (!itemStack.isEmpty())
//				buildStack.push(itemStack.pop());
//
//		} else if ((type instanceof GenericArrayType) || ReflectionUtil.getBaseClass(type).isArray()) {
//			assert itemStack.isEmpty() : itemStack;
//			JsonArrayBuilder jsa = Json.createArrayBuilder();
//			Type componentType;
//			if (type instanceof GenericArrayType)
//				componentType = ((GenericArrayType) type).getGenericComponentType();
//			else
//				componentType = ReflectionUtil.getBaseClass(type).getComponentType();
//			for (int i = 0; i < Array.getLength(value); i++)
//				itemStack.push(new PendingToJsonValue(jsa, Array.get(value, i), componentType));
//
//			currentBuilder.value = jsa;
//			buildStack.push(currentBuilder);
//			while (!itemStack.isEmpty())
//				buildStack.push(itemStack.pop());
//		}
//
//		// Object values
//		else if (value instanceof Map) {
//			assert itemStack.isEmpty() : itemStack;
//			JsonObjectBuilder jso = Json.createObjectBuilder();
//			for (Entry<?, ?> e : ((Map<?, ?>) value).entrySet())
//				itemStack.push(new PendingToJsonValue(jso, (String) e.getKey(), e.getValue(),
//						currentBuilder.type == null ? null : ReflectionUtil.getComponentType(type)));
//
//			currentBuilder.value = jso;
//			buildStack.push(currentBuilder);
//			while (!itemStack.isEmpty())
//				buildStack.push(itemStack.pop());
//		} else {
//		}

	}

}