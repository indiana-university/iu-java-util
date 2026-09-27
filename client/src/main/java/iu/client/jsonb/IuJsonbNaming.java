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

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import edu.iu.client.IuJsonPropertyNameFormat;
import iu.client.JsonSerializer;
import iu.client.PropertyNaming;
import jakarta.json.bind.config.PropertyNamingStrategy;

/**
 * How a call names properties in JSON: by an IU property name format, which a
 * {@link IuJsonb#SERIALIZATION_OPTIONS} supplier may change from call to call,
 * or by a JSON-B {@link PropertyNamingStrategy} fixed for the provider.
 */
final class IuJsonbNaming implements PropertyNaming {

	private static final Map<IuJsonPropertyNameFormat, IuJsonbNaming> FORMATS = new EnumMap<>(
			IuJsonPropertyNameFormat.class);

	static {
		for (final var format : IuJsonPropertyNameFormat.values())
			FORMATS.put(format, new IuJsonbNaming(format, null, false));
	}

	/**
	 * Gets the naming for an IU property name format.
	 *
	 * @param format property name format
	 * @return {@link IuJsonbNaming}
	 */
	static IuJsonbNaming of(IuJsonPropertyNameFormat format) {
		return FORMATS.get(format);
	}

	/**
	 * Gets the naming for a JSON-B property naming strategy.
	 *
	 * @param strategy {@link PropertyNamingStrategy}: one of its standard names,
	 *                 or an instance
	 * @return {@link IuJsonbNaming}
	 * @throws UnsupportedOperationException if not a standard name or an instance
	 */
	static IuJsonbNaming of(Object strategy) {
		if (strategy instanceof PropertyNamingStrategy)
			return new IuJsonbNaming(null, (PropertyNamingStrategy) strategy, false);

		if (strategy instanceof String)
			switch ((String) strategy) {
			case PropertyNamingStrategy.IDENTITY:
				return of(IuJsonPropertyNameFormat.IDENTITY);

			case PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES:
				return of(IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES);

			case PropertyNamingStrategy.LOWER_CASE_WITH_DASHES:
				return new IuJsonbNaming(null, name -> separated(name, '-', false), false);

			case PropertyNamingStrategy.UPPER_CAMEL_CASE:
				return new IuJsonbNaming(null, IuJsonbNaming::capitalized, false);

			case PropertyNamingStrategy.UPPER_CAMEL_CASE_WITH_SPACES:
				return new IuJsonbNaming(null, name -> separated(name, ' ', true), false);

			case PropertyNamingStrategy.CASE_INSENSITIVE:
				return new IuJsonbNaming(null, name -> name, true);

			default:
				break;
			}

		throw new UnsupportedOperationException(String.valueOf(strategy));
	}

	/**
	 * Capitalizes the first character.
	 *
	 * @param name property name
	 * @return name with its first character upper case
	 */
	static String capitalized(String name) {
		if (name.isEmpty())
			return name;
		else
			return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	/**
	 * Separates words at each upper case character after the first.
	 *
	 * @param name        property name, in camel case
	 * @param separator   separator
	 * @param capitalized true to capitalize each word, as in {@code Foo Bar};
	 *                    false to lower case them, as in {@code foo-bar}
	 * @return separated name
	 */
	static String separated(String name, char separator, boolean capitalized) {
		final var sb = new StringBuilder(name.length() + 4);
		for (var i = 0; i < name.length(); i++) {
			final var c = name.charAt(i);
			if (Character.isUpperCase(c)) {
				if (i > 0)
					sb.append(separator);
				sb.append(capitalized ? c : Character.toLowerCase(c));
			} else if (i == 0 && capitalized)
				sb.append(Character.toUpperCase(c));
			else
				sb.append(c);
		}
		return sb.toString();
	}

	private final IuJsonPropertyNameFormat format;
	private final PropertyNamingStrategy strategy;
	private final boolean caseInsensitive;

	private IuJsonbNaming(IuJsonPropertyNameFormat format, PropertyNamingStrategy strategy,
			boolean caseInsensitive) {
		this.format = format;
		this.strategy = strategy;
		this.caseInsensitive = caseInsensitive;
	}

	/**
	 * Gets the IU property name format this naming is, if it is one.
	 *
	 * @return property name format; null for a JSON-B strategy
	 */
	IuJsonPropertyNameFormat format() {
		return format;
	}

	/**
	 * Names a property in JSON.
	 *
	 * @param javaName Java property name
	 * @return JSON property name
	 */
	@Override
	public String name(String javaName) {
		if (format != null)
			return JsonSerializer.formatPropertyName(javaName, format);
		else
			return Objects.requireNonNull(strategy.translateName(javaName), "translated name");
	}

	/**
	 * Determines if property names are matched ignoring case, as for
	 * {@link PropertyNamingStrategy#CASE_INSENSITIVE}.
	 *
	 * @return true if case is ignored
	 */
	boolean ignoresCase() {
		return caseInsensitive;
	}

	/**
	 * Gets the key a JSON property name is matched by when reading.
	 *
	 * @param jsonName JSON property name
	 * @return the name itself; lower cased if matching ignores case
	 */
	@Override
	public String key(String jsonName) {
		if (caseInsensitive)
			return jsonName.toLowerCase(Locale.ROOT);
		else
			return jsonName;
	}

}
