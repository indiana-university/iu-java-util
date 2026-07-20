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
package edu.iu.util.el;

import java.util.Arrays;

import edu.iu.client.IuJson;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Internal parsing and JSON-selection operations shared by the expression
 * evaluator.
 */
final class ElUtils {

	/**
	 * Character to indicate any control character
	 */
	static char ANY = '\0';

	/**
	 * Escape character
	 */
	static char ESC_TOKEN = '\\';

	/**
	 * Control characters
	 */
	static char[] CONTROL_CHARS = new char[] { '\'', '@', '<', '=', '?', '!', '&', '#', '*', '[', '.' };

	/**
	 * Empty JsonString
	 */
	static JsonString EMPTY = Json.createValue("");

	static {
		Arrays.sort(CONTROL_CHARS);
	}

	private ElUtils() {
	}

	/**
	 * Finds the index of a character in a string, starting from a given position,
	 * while skipping over inline template blocks ({@code <`...`}}).
	 *
	 * <p>
	 * Inline template blocks delimited by {@code <`} and {@code `}} (or a terminal
	 * {@code `}) are skipped; nesting is tracked so that only characters at depth 0
	 * are considered when searching.
	 * </p>
	 *
	 * @param s    the string to search
	 * @param c    the character to search for, or {@link #ANY} ({@code '\0'}) to
	 *             match the first occurrence of any {@link #CONTROL_CHARS control
	 *             character}
	 * @param from the index to start searching from (inclusive)
	 * @return the index of the first matching character at depth 0, or {@code -1}
	 *         if not found
	 */
	static int getIndexFrom(String s, char c, int from) {
		boolean any = c == ANY;
		int l = s.length();
		int depth = 0;
		for (int i = from; i < l; i++) {
			char n = s.charAt(i);
			if (depth > 0) {
				if (n == '`') {
					char p = s.charAt(i - 1);
					if (p == '<')
						depth++;
					else
						depth--;
				}
			} else if (any) {
				if (Arrays.binarySearch(CONTROL_CHARS, n) >= 0)
					return i;
			} else if (n == c)
				return i;
			else if (n == '`' //
					&& i > 0 //
					&& s.charAt(i - 1) == '<')
				depth++;
		}
		return -1;
	}

	/**
	 * Finds the closing bracket for a bracketed expression while accounting for
	 * nested bracket pairs.
	 *
	 * @param s    expression to search
	 * @param from index at which to begin searching, inclusive
	 * @return index of the matching closing bracket, or {@code -1} when none is
	 *         found
	 */
	static int getCloseBracket(String s, int from) {
		int l = s.length();
		int depth = 0;
		for (int i = from; i < l; i++) {
			char n = s.charAt(i);
			if (n == ']') {
				if (depth == 0)
					return i;
				else
					depth--;
			} else if (n == '[')
				depth++;
		}
		return -1;
	}

	/**
	 * Selects a JSON value using a subexpression result as the path element.
	 *
	 * <p>
	 * A {@code null} path element leaves {@code selected} unchanged. JSON strings
	 * and numbers are converted to text and delegated to
	 * {@link #select(JsonValue, String)}.
	 * </p>
	 *
	 * @param selected    value from which to select
	 * @param pathElement string or numeric path element, or {@code null}
	 * @return selected JSON value
	 * @throws IllegalArgumentException if {@code pathElement} is not a string or
	 *                                  number
	 */
	static JsonValue select(JsonValue selected, JsonValue pathElement) {
		if (pathElement == null)
			return selected;
		else if (pathElement instanceof JsonString s)
			return select(selected, s.getString());
		else if (pathElement instanceof JsonNumber)
			return select(selected, pathElement.toString());
		else
			throw new IllegalArgumentException(
					"invalid subexpression result " + pathElement + ", expected a single value");
	}

	/**
	 * Selects a value from a JSON object or array.
	 *
	 * <p>
	 * A path beginning with {@code /} is interpreted as a JSON Pointer. Otherwise,
	 * the path is used as an object property name or parsed as an array index. An
	 * empty path returns {@code selected} unchanged.
	 * </p>
	 *
	 * @param selected    object or array from which to select
	 * @param pathElement JSON Pointer, property name, array index, or empty string
	 * @return selected JSON value
	 * @throws IllegalArgumentException if a non-empty path is applied to a scalar
	 *                                  value or an array index is not numeric
	 */
	static JsonValue select(JsonValue selected, String pathElement) {
		if (pathElement.startsWith("/")) {
			final var pointer = IuJson.PROVIDER.createPointer(pathElement);
			if (selected instanceof JsonObject)
				return pointer.getValue(selected.asJsonObject());
			else if (selected instanceof JsonArray)
				return pointer.getValue(selected.asJsonArray());
			else
				throw new IllegalArgumentException(
						"expected object or array for pointer " + pathElement + ", found " + selected);
		} else {
			if (pathElement.isEmpty())
				return selected;
			else if (selected instanceof JsonArray)
				return selected.asJsonArray().get(Integer.parseInt(pathElement));
			else if (selected instanceof JsonObject)
				return selected.asJsonObject().get(pathElement);
			else
				throw new IllegalArgumentException(
						"expected object or array for property '" + pathElement + "', found " + selected);
		}
	}

}
