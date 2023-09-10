/*
 * Copyright © 2023 Indiana University
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
package edu.iu.runtime;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Provides uniform access to runtime configuration.
 * 
 * <h2>Expected Types</h2>
 * <p>
 * The underlying configuration value must be appropriate for the expected type,
 * otherwise {@link IllegalArgumentException} is thrown.
 * </p>
 * 
 * <h2>Atomic Types</h2>
 * <p>
 * The following atomic types <em>must</em> be supported:
 * </p>
 * 
 * <ul>
 * <li>{@link BigDecimal}</li>
 * <li>{@link BigInteger}</li>
 * <li>{@link Boolean}</li>
 * <li>{@link Date}</li>
 * <li>{@link Instant}</li>
 * <li>{@link Integer}</li>
 * <li>{@link LocalDate}</li>
 * <li>{@link LocalDateTime}</li>
 * <li>{@link LocalTime}</li>
 * <li>{@link Long}</li>
 * <li>{@link Number}</li>
 * <li>{@link Pattern}</li>
 * <li>{@link String}</li>
 * <li>{@link Year}</li>
 * <li>{@link YearMonth}</li>
 * <li>{@link ZonedDateTime}</li>
 * </ul>
 * 
 * <h2>Collection Types</h2>
 * <p>
 * The following collection types <em>must</em> be supported:
 * </p>
 * 
 * <ul>
 * <li>Array</li>
 * <li>{@link Collection}</li>
 * <li>{@link DoubleStream}</li>
 * <li>{@link IntStream}</li>
 * <li>{@link Iterable}</li>
 * <li>{@link List}</li>
 * <li>{@link LongStream}</li>
 * <li>{@link Set}</li>
 * <li>{@link SortedSet}</li>
 * <li>{@link Stream}</li>
 * </ul>
 * 
 * <p>
 * When handing collection types, an implementation:
 * </p>
 * 
 * <ul>
 * <li><em>Must</em> convert atomic values from the underlying configuration
 * source to a singleton when a collection type is expected.</li>
 * 
 * <li><em>Must</em> use {@link ParameterizedType},
 * {@link GenericArrayType#getGenericComponentType()}, or
 * {@link Class#getComponentType()} to determine the expected type of items in
 * the collection.</li>
 * 
 * <li><em>Must not</em> return an empty collection instead of throwing
 * {@link IllegalArgumentException} when the reference cannot be resolved.</li>
 * </ul>
 * 
 * <h2>Named Value Types</h2>
 * <p>
 * The following named value collection types <em>must</em> be supported:
 * </p>
 *
 * <ul>
 * <li>{@link IuRuntimeConfiguration}</li>
 * <li>{@link Map}</li>
 * </ul>
 * 
 * <p>
 * When handing named value collection types, an implementation:
 * </p>
 * 
 * <ul>
 * <li><em>Must</em> convert atomic types from the underlying configuration
 * source to a singleton with an empty (non-null) name when a named value type
 * is expected and only one value is provided.</li>
 * 
 * <li><em>Must</em> convert collection types from the underlying configuration
 * source to a named value collection using the non-negative integer index
 * values implied by the underlying collection's iterator as names.</li>
 * 
 * <li><em>Must</em> return all configured values relative to the reference when
 * {@link IuRuntimeConfiguration} is used. The resulting configuration must only
 * understand references relative to the resulting configuration.</li>
 * 
 * <li><em>Must</em> use {@link ParameterizedType} to determine the expected
 * type of named values when a generic {@link Map} is used.</li>
 * 
 * <li><em>Should</em> support the use of qualified Java module, package, and
 * type names to resolve specific configuration subsets when using
 * {@link IuRuntimeConfiguration} as the expected type.</li>
 * </ul>
 *
 * <h2>Jakarta JSON-P Types</h2>
 * <p>
 * The following types from the
 * <a href="https://jakarta.ee/specifications/jsonp/">Jakarta JSON-P API</a>
 * <em>should</em> be supported.
 * </p>
 * 
 * <ul>
 * <li>JsonArray (collection)</li>
 * <li>JsonNumber</li>
 * <li>JsonString</li>
 * <li>JsonStructure</li>
 * <li>JsonObject (named value)</li>
 * <li>JsonPointer</li>
 * <li>JsonValue</li>
 * </ul>
 * 
 * <p>
 * <a href="https://datatracker.ietf.org/doc/html/rfc6901">JSON Pointer</a>
 * <em>should</em> be a supported configuration reference format.
 * </p>
 * 
 * <h2>Serialized Form</h2>
 * <p>
 * All atomic and collection configuration values <em>must</em> be retrievable
 * using the {@link String} type.
 * </p>
 * 
 * <ul>
 * <li>Values with an underlying collection type <em>should</em> use
 * <a href="https://www.ietf.org/rfc/rfc4180.txt">CSV</a> format when converting
 * to {@link String}.</li>
 * <li>A null value implies that an explicit null was defined by the underlying
 * configuration source.</li>
 * <li>An empty string value implies an empty collection.</li>
 * <li>An empty string literal (two double quote characters "") implies a
 * singleton collection with an empty string value.</li>
 * </ul>
 * 
 * <p>
 * Attempted retrieval of a value with an underlying named value collection type
 * using {@link String} <em>should</em> result in
 * {@link IllegalArgumentException} being thrown.
 * </p>
 * 
 * <h2>Null Values</h2>
 * <p>
 * Implementations <em>should</em> support null values when a reference resolved
 * to an explicit null in the configuration source. A null value does not imply
 * that configuration is missing.
 * </p>
 * 
 * <p>
 * For example, an application can use the {@link Void} type to check for the
 * presence of a null configuration value.
 * </p>
 * 
 * <pre>
 * try {
 *     config.getValue(reference, Void.class);
 *     // The value is defined
 * } catch (IllegalArgumentException e) {
 *     // The value is not defined
 * }</pre>
 *
 * <h2>Security Considerations</h2>
 * <p>
 * When throwing {@link IllegalArgumentException}, an implementation:
 * </p>
 * <ul>
 * <li><em>Should not</em> include the unresolved reference in the message.</li>
 * <li><em>Must not</em> include any configuration values in the a message.</li>
 * </ul>
 */
public interface IuRuntimeConfiguration {

	/**
	 * Gets a serialized configuration value.
	 * 
	 * @param reference configuration value reference
	 * 
	 * @return serialized configuration value
	 * @throws IllegalArgumentException If the name cannot be resolved to a value,
	 *                                  or if the resolved value is not
	 *                                  serializable.
	 */
	default String getValue(String reference) throws IllegalArgumentException {
		return getValue(reference, String.class);
	}

	/**
	 * Gets a serialized configuration value.
	 * 
	 * @param reference    configuration value reference
	 * @param defaultValue value to return instead of throwing
	 *                     {@link IllegalArgumentException} if the reference cannot
	 *                     be resolved
	 * 
	 * @return property value
	 */
	default String getValue(String reference, String defaultValue) {
		return getValue(reference, String.class, defaultValue);
	}

	/**
	 * Gets an environment property.
	 * 
	 * @param reference configuration value reference
	 * @param type      expected type
	 * 
	 * @return property value
	 * @throws IllegalArgumentException If the reference cannot be resolved to a
	 *                                  value, or if the resolved value is
	 *                                  inappropriate for the expected type.
	 */
	Object getValue(String reference, Type type) throws IllegalArgumentException;

	/**
	 * Gets an environment property.
	 * 
	 * @param reference    configuration value reference
	 * @param type         expected type
	 * @param defaultValue default value
	 * 
	 * @return property value
	 */
	default Object getValue(String reference, Type type, Object defaultValue) {
		try {
			return getValue(reference, type);
		} catch (IllegalArgumentException e) {
			Logger.getLogger(IuRuntimeConfiguration.class.getName()).log(Level.FINEST, e,
					() -> "Invalid configuration value for " + reference + " using default");
			return defaultValue;
		}
	}

	/**
	 * Gets an environment property.
	 * 
	 * @param <V>       expected type
	 * 
	 * @param reference configuration value reference
	 * @param type      expected type
	 * 
	 * @return property value
	 * @throws IllegalArgumentException If the reference cannot be resolved to a
	 *                                  value, or if the resolved value is
	 *                                  inappropriate for the expected type.
	 */
	default <V> V getValue(String reference, Class<V> type) throws IllegalArgumentException {
		return type.cast(getValue(reference, (Type) type));
	}

	/**
	 * Gets an environment property.
	 * 
	 * @param <V>          expected type
	 * 
	 * @param reference    configuration value reference
	 * @param type         expected type
	 * @param defaultValue default value
	 * 
	 * @return property value
	 */
	default <V> V getValue(String reference, Class<V> type, V defaultValue) {
		return type.cast(getValue(reference, (Type) type, defaultValue));
	}

}
