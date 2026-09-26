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
 * Converting from JSON reads only the {@link #getPropertyNameFormat() property
 * name format}: a property, including an enum object's name, is read from the
 * key formatted as it would be written, and a key in any other format is
 * ignored. The other options apply only to the JSON conversion direction.
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
	 * <li>An {@link java.util.Optional Optional} property is always present, since
	 * {@link java.util.Optional#empty() empty} is itself a non-null value that
	 * converts to {@link JsonValue#NULL}.</li>
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

}
