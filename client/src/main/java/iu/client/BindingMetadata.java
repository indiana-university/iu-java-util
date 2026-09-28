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
import java.lang.reflect.Type;
import java.util.function.Supplier;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonSerializationOptions;

/**
 * Binding annotations on a business object type and its members, as JSON-B
 * defines them, read without referring to the JSON-B API.
 *
 * <p>
 * {@link #get()} reads JSON-B annotations when the JSON-B API is present at
 * runtime, and nothing otherwise; a type annotated with JSON-B annotations can
 * only load with the API present, so a type converted without it has none to
 * read.
 * </p>
 */
public interface BindingMetadata {

	/**
	 * A date or number format a member, type, or package declares.
	 */
	final class Format {
		private final String pattern;
		private final String locale;

		/**
		 * Constructor.
		 *
		 * @param pattern pattern, as declared
		 * @param locale  locale language tag, as declared
		 */
		public Format(String pattern, String locale) {
			this.pattern = pattern;
			this.locale = locale;
		}

		/**
		 * Gets the pattern.
		 *
		 * @return pattern, as declared: a {@link java.time.format.DateTimeFormatter}
		 *         or {@link java.text.DecimalFormat} pattern, or a marker such as
		 *         {@link FormatAdapters#DEFAULT} or
		 *         {@link FormatAdapters#TIME_IN_MILLIS}
		 */
		public String pattern() {
			return pattern;
		}

		/**
		 * Gets the locale.
		 *
		 * @return locale language tag, or {@link FormatAdapters#DEFAULT}
		 */
		public String locale() {
			return locale;
		}
	}

	/**
	 * Reads no annotations.
	 */
	BindingMetadata NONE = new BindingMetadata() {
	};

	/**
	 * Gets the metadata for the runtime: JSON-B annotations if the JSON-B API is
	 * present, otherwise {@link #NONE}.
	 *
	 * @return {@link BindingMetadata}
	 */
	static BindingMetadata get() {
		return JsonbPresence.METADATA;
	}

	/**
	 * Gets the properties a type lists first, in order.
	 *
	 * @param type type
	 * @return Java property names; null if the type lists none
	 */
	default String[] propertyOrder(Class<?> type) {
		return null;
	}

	/**
	 * Gets the visibility a type, or its package, declares for its own fields and
	 * methods.
	 *
	 * @param type declaring class
	 * @return visibility; null if neither declares one
	 */
	default BeanModel.Visibility visibility(Class<?> type) {
		return null;
	}

	/**
	 * Gets the JSON name a field or accessor declares for its property.
	 *
	 * @param member field or accessor method
	 * @return JSON name; null if it declares none
	 */
	default String name(AnnotatedElement member) {
		return null;
	}

	/**
	 * Determines if a field or accessor excludes its property.
	 *
	 * @param member field or accessor method
	 * @return true if transient
	 */
	default boolean isTransient(AnnotatedElement member) {
		return false;
	}

	/**
	 * Determines if a field or accessor carries binding annotations other than
	 * one excluding it, which conflict with its exclusion.
	 *
	 * @param member field or accessor method
	 * @return true if it has other binding annotations
	 */
	default boolean isCustomized(AnnotatedElement member) {
		return false;
	}

	/**
	 * Determines if a null value is written: for a field or accessor, by its own
	 * declaration; for a type, by its own declaration or its package's.
	 *
	 * @param element field, accessor method, or type
	 * @return true to write null, false to omit it; null if not declared
	 */
	default Boolean nillable(AnnotatedElement element) {
		return null;
	}

	/**
	 * Gets the date format declared: for a field or accessor, by its own
	 * declaration; for a type, by its own declaration or its package's.
	 *
	 * @param element field, accessor method, or type
	 * @return date format; null if not declared
	 */
	default Format dateFormat(AnnotatedElement element) {
		return null;
	}

	/**
	 * Gets the number format declared: for a field or accessor, by its own
	 * declaration; for a type, by its own declaration or its package's.
	 *
	 * @param element field, accessor method, or type
	 * @return number format; null if not declared
	 */
	default Format numberFormat(AnnotatedElement element) {
		return null;
	}

	/**
	 * Gets a conversion by the JSON-B components a type, or one of its
	 * supertypes, declares by annotation.
	 *
	 * @param type    type
	 * @param options supplies the options in effect for each conversion
	 * @return conversion; null if none are declared
	 */
	default IuJsonAdapter<?> components(Type type, Supplier<IuJsonSerializationOptions> options) {
		return null;
	}

	/**
	 * Gets a conversion by the JSON-B components a property declares by
	 * annotation, for one direction, with any format it declares.
	 *
	 * @param type    property type
	 * @param date    date format declared; null if none
	 * @param number  number format declared; null if none
	 * @param members accessor, then field, that declare how the property converts
	 *                in the direction converted
	 * @param options supplies the options in effect for each conversion
	 * @return conversion; null if the members declare no components
	 */
	default IuJsonAdapter<?> components(Type type, Format date, Format number, AnnotatedElement[] members,
			Supplier<IuJsonSerializationOptions> options) {
		return null;
	}

}
