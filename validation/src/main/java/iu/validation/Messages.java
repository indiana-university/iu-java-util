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
package iu.validation;

import java.util.Arrays;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Interpolates a constraint's message template.
 * 
 * <p>
 * Implements the substitution the Jakarta Validation specification requires of
 * every implementation: a <code>{token}</code> naming one of the constraint's
 * own attributes is replaced by that attribute's value, and a
 * <code>{token}</code> naming a key in the message catalog is replaced by that
 * template, which is then substituted in turn.
 * </p>
 * 
 * <p>
 * The specification's optional <code>${expression}</code> form is not
 * supported. That form is why a specification-complete implementation needs a
 * Jakarta Expression Language provider, and no default template used here
 * requires it.
 * </p>
 */
final class Messages {

	/** Suffix of every default template key in the catalog. */
	private static final String MESSAGE = ".message";

	/** Default constraint message templates. */
	private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("iu.validation.ValidationMessages");

	/**
	 * Interpolates a constraint's message template against its own attributes.
	 * 
	 * @param attributes constraint attributes, including {@code message}, which
	 *                   holds the template
	 * @return interpolated message; the template verbatim if it holds no resolvable
	 *         token
	 */
	static String interpolate(Map<String, Object> attributes) {
		return expand(String.valueOf(attributes.get("message")), attributes, true);
	}

	/**
	 * Substitutes every resolvable token in a template.
	 * 
	 * @param template     message template
	 * @param attributes   constraint attributes
	 * @param resolveKeys  true to resolve a token naming a catalog key; false to
	 *                     resolve attribute names only. Cleared for the expansion
	 *                     of a catalog template, so that a catalog entry cannot
	 *                     reference another and recurse.
	 * @return interpolated message
	 */
	private static String expand(String template, Map<String, Object> attributes, boolean resolveKeys) {
		final var expanded = new StringBuilder();

		var position = 0;
		while (position < template.length()) {
			final var open = template.indexOf('{', position);
			final var close = open < 0 ? -1 : template.indexOf('}', open);
			if (close < 0) {
				expanded.append(template, position, template.length());
				break;
			}

			expanded.append(template, position, open);

			final var token = template.substring(open + 1, close);
			final var replacement = resolve(token, attributes, resolveKeys);
			if (replacement == null)
				expanded.append(template, open, close + 1);
			else
				expanded.append(replacement);

			position = close + 1;
		}

		return expanded.toString();
	}

	/**
	 * Resolves one token.
	 * 
	 * @param token       token name, without braces
	 * @param attributes  constraint attributes
	 * @param resolveKeys true to consult the message catalog
	 * @return replacement text; null if {@code token} resolves to nothing, in which
	 *         case it is left in place
	 */
	private static String resolve(String token, Map<String, Object> attributes, boolean resolveKeys) {
		if (attributes.containsKey(token))
			return display(attributes.get(token));

		if (resolveKeys) {
			final var catalogued = lookup(token, attributes);
			if (catalogued != null)
				return expand(catalogued, attributes, false);
		}

		return null;
	}

	/**
	 * Reads a template from the message catalog.
	 * 
	 * <p>
	 * A constraint that declares {@code inclusive() == false} resolves to the
	 * {@code .exclusive.message} variant of its key when the catalog has one. This
	 * is how {@link jakarta.validation.constraints.DecimalMin} and
	 * {@link jakarta.validation.constraints.DecimalMax} say "greater than" rather
	 * than "greater than or equal to" without an EL expression.
	 * </p>
	 * 
	 * @param key        catalog key
	 * @param attributes constraint attributes
	 * @return template; null if the catalog has no entry for {@code key}
	 */
	private static String lookup(String key, Map<String, Object> attributes) {
		if (key.endsWith(MESSAGE) //
				&& Boolean.FALSE.equals(attributes.get("inclusive"))) {
			final var exclusive = key.substring(0, key.length() - MESSAGE.length()) + ".exclusive" + MESSAGE;
			if (BUNDLE.containsKey(exclusive))
				return BUNDLE.getString(exclusive);
		}

		return BUNDLE.containsKey(key) ? BUNDLE.getString(key) : null;
	}

	/**
	 * Renders an attribute value for inclusion in a message.
	 * 
	 * @param value attribute value
	 * @return display text
	 */
	private static String display(Object value) {
		if (value instanceof Object[] array)
			return Arrays.toString(array);
		return String.valueOf(value);
	}

	private Messages() {
	}

}
