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
package edu.iu.client;

import java.lang.reflect.Type;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Provides runtime tuning parameters for converting a JavaBeans business
 * object, or an enum value, to JSON.
 * 
 * <p>
 * All methods supply a default, so an implementation only overrides the values
 * it needs to change. Options are received as a {@link Supplier}, and one
 * snapshot is read for each conversion, so an adapter that captured the
 * supplier observes a configuration change without being recreated. See
 * {@link IuJsonAdapter#from(Class, Supplier, Function)} and
 * {@link IuJsonAdapter#adapt(Type, Supplier)}.
 * </p>
 * 
 * <p>
 * Values are validated by the conversion that reads them, so an invalid value
 * fails that conversion and leaves the adapter intact. A supplier that answers
 * null reads as {@link #DEFAULT}.
 * </p>
 * 
 * <p>
 * Converting from JSON reads the {@link #getPropertyNameFormat() property name
 * format}, so a property, including an enum object's name, is read from the key
 * formatted as it would be written, and a key in any other format is ignored.
 * It also reads the {@link #getBinaryDataStrategy() binary data strategy},
 * {@link #isLegacyProperties() property discovery}, and
 * {@link #isLegacyDates() date formats}. The other options apply only to the
 * JSON conversion direction.
 * </p>
 */
public interface IuJsonSerializationOptions {

	/**
	 * Default {@link #getPropertyNameFormat() property name format}:
	 * {@link IuJsonPropertyNameFormat#IDENTITY}.
	 */
	IuJsonPropertyNameFormat PROPERTY_NAME_FORMAT = IuJsonPropertyNameFormat.IDENTITY;

	/**
	 * Options with all default values: Java property names as-is, with null
	 * properties omitted.
	 */
	IuJsonSerializationOptions DEFAULT = new IuJsonSerializationOptions() {
	};

	/**
	 * Options with the default property name format, with every readable property
	 * present.
	 * 
	 * <p>
	 * See {@link #isIncludeNullProperties()} for what is and is not included.
	 * </p>
	 */
	IuJsonSerializationOptions INCLUDE_NULLS = of(PROPERTY_NAME_FORMAT, true);

	/**
	 * Options with all other values default, with an enum value converted to a
	 * {@link JsonObject} describing the constant.
	 * 
	 * <p>
	 * See {@link #isEnumAsObject()} for what the object holds.
	 * </p>
	 */
	IuJsonSerializationOptions ENUM_AS_OBJECT = of(PROPERTY_NAME_FORMAT, false, true);

	/**
	 * Default {@link #getBinaryDataStrategy() binary data strategy}:
	 * {@code BYTE}, as in JSON-B.
	 */
	String BINARY_DATA_STRATEGY = "BYTE";

	/**
	 * Options with all other values default, restoring every conversion that
	 * changed in 7.1 to its earlier behavior: {@code byte[]} as base64 text, enum
	 * text by {@link Enum#toString()}, an empty {@link java.util.Optional} written
	 * as null, {@link #isLegacyProperties() legacy property discovery}, and
	 * {@link #isLegacyDates() legacy date formats}.
	 *
	 * <p>
	 * To restore only some of these, override the corresponding methods instead.
	 * </p>
	 */
	IuJsonSerializationOptions LEGACY = new IuJsonSerializationOptions() {
		@Override
		public boolean isLegacyDates() {
			return true;
		}

		@Override
		public String getBinaryDataStrategy() {
			return "BASE_64";
		}

		@Override
		public boolean isEnumToString() {
			return true;
		}

		@Override
		public boolean isEmptyOptionalPresent() {
			return true;
		}

		@Override
		public boolean isLegacyProperties() {
			return true;
		}
	};

	/**
	 * Gets options that override only the property name format.
	 * 
	 * @param propertyNameFormat property name format
	 * @return {@link IuJsonSerializationOptions} with null properties omitted
	 */
	static IuJsonSerializationOptions of(IuJsonPropertyNameFormat propertyNameFormat) {
		return of(propertyNameFormat, false);
	}

	/**
	 * Gets options that override the property name format and null property
	 * handling.
	 * 
	 * @param propertyNameFormat    property name format
	 * @param includeNullProperties true to include a property with a null value;
	 *                              false to omit it
	 * @return {@link IuJsonSerializationOptions} with enum values converted as
	 *         text
	 */
	static IuJsonSerializationOptions of(IuJsonPropertyNameFormat propertyNameFormat,
			boolean includeNullProperties) {
		return of(propertyNameFormat, includeNullProperties, false);
	}

	/**
	 * Gets options that override every value.
	 * 
	 * @param propertyNameFormat    property name format
	 * @param includeNullProperties true to include a property with a null value;
	 *                              false to omit it
	 * @param enumAsObject          true to convert an enum value to a
	 *                              {@link JsonObject}; false to convert it to a
	 *                              {@link jakarta.json.JsonString JsonString}
	 * @return {@link IuJsonSerializationOptions}
	 */
	static IuJsonSerializationOptions of(IuJsonPropertyNameFormat propertyNameFormat,
			boolean includeNullProperties, boolean enumAsObject) {
		return new IuJsonSerializationOptions() {
			@Override
			public IuJsonPropertyNameFormat getPropertyNameFormat() {
				return propertyNameFormat;
			}

			@Override
			public boolean isIncludeNullProperties() {
				return includeNullProperties;
			}

			@Override
			public boolean isEnumAsObject() {
				return enumAsObject;
			}
		};
	}

	/**
	 * Gets the format to use when converting a Java property name to a JSON
	 * property name.
	 * 
	 * @return property name format; a null value reads as
	 *         {@link #PROPERTY_NAME_FORMAT}
	 */
	default IuJsonPropertyNameFormat getPropertyNameFormat() {
		return PROPERTY_NAME_FORMAT;
	}

	/**
	 * Determines whether a readable JavaBeans property with a null value is
	 * included in the {@link JsonObject}, as {@link JsonValue#NULL}, rather than
	 * omitted.
	 * 
	 * <p>
	 * Java makes no distinction between a null property and an undefined one, so
	 * null properties are omitted by default. Enable this when the consumer does
	 * make that distinction — in particular JavaScript, which separates
	 * {@code null} from {@code undefined} — so that UI code can tell a value that
	 * is absent from one the application declared to have no value.
	 * </p>
	 * 
	 * <p>
	 * What is included:
	 * </p>
	 * <ul>
	 * <li>A null-valued {@link java.util.List List},
	 * {@link java.util.Map Map}, or array property is included as
	 * {@link JsonValue#NULL}, <em>not</em> as an empty array or object.</li>
	 * <li>A null business object property is included as {@link JsonValue#NULL},
	 * <em>not</em> as an object of all-null properties.</li>
	 * </ul>
	 * 
	 * <p>
	 * What is not affected:
	 * </p>
	 * <ul>
	 * <li>Only <em>readable</em> properties are converted, so a write-only
	 * property, and a property with only an indexed read method, remains absent
	 * either way.</li>
	 * <li>A property whose read method returns a primitive can never have a null
	 * value, so it is always present.</li>
	 * <li>An {@link java.util.Optional Optional} property that is
	 * {@link java.util.Optional#empty() empty} is treated as null, so it is
	 * included as {@link JsonValue#NULL} only when this option is enabled, unless
	 * {@link #isEmptyOptionalPresent()} restores its pre-7.1 behavior.</li>
	 * <li>A value wrapped by {@link IuJson#wrap(JsonObject, Class)} converts to its
	 * source {@link JsonObject} as-is, without introspection, so neither this
	 * option nor {@link #getPropertyNameFormat()} applies to it. That is what
	 * allows a value handled through a stub interface to carry properties the stub
	 * does not declare. To include nulls in such a value, enable this option where
	 * its source {@link JsonObject} is created.</li>
	 * </ul>
	 * 
	 * <p>
	 * This is an egress format, not a round-trip format. An explicitly null
	 * property is a defined JSON value, so converting back from JSON applies it to
	 * a business object rather than skipping it, overwriting a value assigned by
	 * the no-arg constructor; and, for an interface, it suppresses the fallback to
	 * a {@code default} method that an undefined value would have invoked.
	 * </p>
	 * 
	 * @return true to include a property with a null value; false to omit it
	 */
	default boolean isIncludeNullProperties() {
		return false;
	}

	/**
	 * Determines whether an enum value is converted to a {@link JsonObject}
	 * describing the constant, rather than to a {@link jakarta.json.JsonString
	 * JsonString} naming it.
	 * 
	 * <p>
	 * Enable this for a consumer that has no decoded metadata for the enum type
	 * &mdash; a REST client or a UI, which receives {@code "ACTIVE"} and has
	 * nowhere to get a display label, a sort order, or any other attribute the
	 * constant carries.
	 * </p>
	 * 
	 * <p>
	 * The object holds a {@code name} property, formatted by
	 * {@link #getPropertyNameFormat()} and carrying {@link Enum#name()}, followed
	 * by the constant's readable JavaBeans properties, converted as they are for a
	 * business object &mdash; so
	 * {@link #isIncludeNullProperties() null properties} and the property name
	 * format apply to them as well. Properties declared by {@link Object} and
	 * {@link Enum}, in particular {@link Enum#getDeclaringClass() declaringClass},
	 * are skipped. An enum that declares its own {@code name} property replaces
	 * the constant name with it, which is only sound when that property answers
	 * the constant name.
	 * </p>
	 * 
	 * <p>
	 * Introspection uses the enum type rather than the value's class, so a
	 * constant declared with a class body converts to the same shape as every
	 * other constant, while a property it overrides answers the override.
	 * </p>
	 * 
	 * <p>
	 * Only the conversion to JSON is affected. Converting from JSON accepts
	 * either form whatever this option says &mdash; a defined {@code name}
	 * property if the value is an object, ignoring every other property, and
	 * otherwise the value as text &mdash; so a value written with this enabled is
	 * read by a consumer that leaves it disabled. Note that the text form is
	 * {@link Enum#toString()} rather than {@link Enum#name()}, so only the object
	 * form is guaranteed to convert back.
	 * </p>
	 * 
	 * <p>
	 * An enum reached through a property declared {@link Object}, and so
	 * converted by {@link IuJsonAdapter#basic()}, is unaffected: that adapter
	 * cannot convert an enum in either direction.
	 * </p>
	 * 
	 * @return true to convert an enum value to a {@link JsonObject}; false to
	 *         convert it to a {@link jakarta.json.JsonString JsonString}
	 */
	default boolean isEnumAsObject() {
		return false;
	}

	/**
	 * Gets how {@code byte[]} converts, in both directions, named as JSON-B's
	 * {@code jakarta.json.bind.config.BinaryDataStrategy} names it.
	 *
	 * <ul>
	 * <li>{@code BYTE}, the default: an array of numbers, each a signed
	 * byte.</li>
	 * <li>{@code BASE_64}: base64 text, as before 7.1.</li>
	 * <li>{@code BASE_64_URL}: base64url text.</li>
	 * </ul>
	 *
	 * <p>
	 * Base64 text is written padded, and read padded or not.
	 * </p>
	 *
	 * @return binary data strategy; a null value reads as
	 *         {@link #BINARY_DATA_STRATEGY}
	 */
	default String getBinaryDataStrategy() {
		return BINARY_DATA_STRATEGY;
	}

	/**
	 * Determines whether an enum value's text is its {@link Enum#toString()},
	 * as before 7.1, rather than its {@link Enum#name()}.
	 *
	 * <p>
	 * Text reads by {@link Enum#name()} either way, as it always has, so only
	 * the name form is guaranteed to convert back. Map keys follow the same
	 * rule.
	 * </p>
	 *
	 * @return true to write {@link Enum#toString()}; false to write
	 *         {@link Enum#name()}
	 */
	default boolean isEnumToString() {
		return false;
	}

	/**
	 * Determines whether a property holding an empty {@link java.util.Optional},
	 * {@link java.util.OptionalInt}, {@link java.util.OptionalLong}, or
	 * {@link java.util.OptionalDouble} is written as {@link JsonValue#NULL} even
	 * when {@link #isIncludeNullProperties() null properties} are omitted, as
	 * before 7.1.
	 *
	 * @return true to write an empty optional as null; false to treat it as an
	 *         absent value
	 */
	default boolean isEmptyOptionalPresent() {
		return false;
	}

	/**
	 * Determines whether a business object's properties are discovered as before
	 * 7.1, in both directions: only its public accessor methods, with public
	 * fields ignored, and JSON-B annotations not consulted.
	 *
	 * <p>
	 * By default, properties are discovered as JSON-B discovers them: public
	 * fields as well as accessors, in lexicographic order unless
	 * {@code @JsonbPropertyOrder} says otherwise, with a non-public accessor
	 * closing its field, and honoring JSON-B annotations such as
	 * {@code @JsonbProperty}, {@code @JsonbTransient}, and
	 * {@code @JsonbVisibility} when the JSON-B API is present.
	 * </p>
	 *
	 * @return true for pre-7.1 property discovery
	 */
	default boolean isLegacyProperties() {
		return false;
	}

	/**
	 * Determines whether dates convert as before 7.1, in both directions.
	 *
	 * <p>
	 * By default, dates convert as JSON-B converts them:
	 * </p>
	 * <ul>
	 * <li>{@link java.time.LocalTime}, {@link java.time.LocalDateTime},
	 * {@link java.time.OffsetTime}, {@link java.time.OffsetDateTime}, and
	 * {@link java.time.ZonedDateTime} write by the ISO formatter for the type, so
	 * seconds are written even when zero. Before 7.1, they wrote as
	 * {@code toString()} does, leaving zero seconds out.</li>
	 * <li>{@link java.util.Calendar} writes in its own time zone, as an ISO date
	 * at midnight, otherwise an ISO date and time, and reads in the zone written.
	 * Before 7.1, it wrote as {@link java.util.Date} in UTC, and read in the
	 * default time zone.</li>
	 * </ul>
	 *
	 * <p>
	 * Any of these reads the other form either way. {@link java.util.Date} and
	 * {@link java.time.Instant} are unaffected.
	 * </p>
	 *
	 * @return true for pre-7.1 date formats
	 */
	default boolean isLegacyDates() {
		return false;
	}

}
