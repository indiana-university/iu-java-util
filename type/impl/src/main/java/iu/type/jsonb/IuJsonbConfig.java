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

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuAttribute;
import edu.iu.type.IuDeclaredElement;
import edu.iu.type.IuType;
import iu.type.TypeProperties;
import jakarta.json.JsonConfig;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;

/**
 * Internal JSON-B configuration state.
 */
class IuJsonbConfig {

	private static final Logger LOG = Logger.getLogger(IuJsonbConfig.class.getName());

	private Boolean formatted;
	private String encoding = "UTF-8";
	private Boolean failsafe;
	private Boolean classSerializationAllowed;
	private Boolean skipNull;

	/**
	 * Default constructor.
	 */
	IuJsonbConfig() {
	}

	/**
	 * Applies mapped configuration values.
	 * 
	 * @param config externally provided configuration values
	 */
	void apply(Map<String, ?> config) {
		for (final var configEntry : config.entrySet()) {
			final var key = configEntry.getKey();
			final var value = configEntry.getValue();

			switch (key) {
			case JsonbConfig.FORMATTING:
				formatted = (boolean) value;
			case JsonbConfig.ENCODING:
				encoding = (String) value;

			case JsonbConfig.NULL_VALUES:
				skipNull = !((boolean) value);

			case TypeProperties.JSONB_FAILSAFE:
				failsafe = (boolean) value;
			case TypeProperties.JSONB_CLASS_SERIALIZATION_ALLOWED:
				classSerializationAllowed = (boolean) value;

			default:
				fail(() -> "Not supported in this version " + key);
			}
		}
	}

	/**
	 * Gets the character encoding.
	 * 
	 * @return character encoding
	 * @see JsonbConfig#ENCODING
	 */
	String encoding() {
		return encoding;
	}

	/**
	 * Gets properties for use with {@link JsonProvider#createWriterFactory(Map)}
	 * 
	 * @return writer properties
	 */
	Map<String, ?> getWriterProperties() {
		if (formatted)
			return Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true);
		else
			return Collections.emptyMap();
	}

	/**
	 * Reports a failure during JSON-B processing.
	 * 
	 * <p>
	 * Failures are logged at {@link Level#CONFIG} when the
	 * {@link TypeProperties#is(String) jsonb.failsafe type property} is set.
	 * Otherwise, {@link UnsupportedOperationException} is thrown.
	 * </p>
	 * 
	 * @param messageSupplier failure message supplier
	 * @throws UnsupportedOperationException unless {@link TypeProperties#is(String)
	 *                                       jsonb.failsafe type property} is set
	 */
	void fail(Supplier<String> messageSupplier) throws UnsupportedOperationException {
		if (TypeProperties.is(TypeProperties.JSONB_FAILSAFE))
			LOG.finest(messageSupplier);
		else
			throw new UnsupportedOperationException(messageSupplier.get());
	}

	/**
	 * Determines whether or not null values should be skipped.
	 * 
	 * <p>
	 * Skipping nulls is the default behavior specified by <a href=
	 * "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#null-value-handling">JSON-B
	 * 3.0 Section 3.14</a>.
	 * </p>
	 * 
	 * @param annotatedElement typically {@link IuAttribute} or {@link IuType}; will
	 *                         recursively check
	 *                         {@link IuDeclaredElement#declaringType()} and
	 *                         {@link IuAttribute#type()} for <a href=
	 *                         "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#customizing-null-handling">customized
	 *                         null handling</a> before returning {@code false}.
	 * @return true if nulls should be skipped (default); else false
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#customizing-null-handling">JSON-B
	 *      3.0 Section 4.3</a>
	 */
	@SuppressWarnings("deprecation")
	boolean isSkipNull(IuAnnotatedElement annotatedElement) {
		if (!skipNull)
			return false;

		if (annotatedElement.hasAnnotation(JsonbNillable.class))
			return false;

		final var property = annotatedElement.annotation(JsonbProperty.class);
		if (property != null && property.nillable())
			return false;

		return !(annotatedElement instanceof IuDeclaredElement<?> declaredElement) //
				|| !isSkipNull(declaredElement.declaringType()) //
				|| !(annotatedElement instanceof IuAttribute<?, ?> attribute) //
				|| !isSkipNull(attribute.type());
	}

	/**
	 * Determines if {@link Class} should be allowed as a value type for
	 * serialization/deserialization.
	 * 
	 * @return true if class serialization is allowed; else false
	 * @see TypeProperties#JSONB_CLASS_SERIALIZATION_ALLOWED
	 */
	boolean isClassSerializationAllowed() {
		return TypeProperties.is(TypeProperties.JSONB_CLASS_SERIALIZATION_ALLOWED);
	}

}
