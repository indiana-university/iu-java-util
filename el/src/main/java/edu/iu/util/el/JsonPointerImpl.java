package edu.iu.util.el;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonPointer;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

/**
 * JSON Pointer implementation. JSON Pointer specification:
 * https://datatracker.ietf.org/html/rfc6901
 */
public class JsonPointerImpl implements JsonPointer {

	private static final String SEPARATOR = "/";
	private static final Pattern INVALID_ESCAPE = Pattern.compile("~[^01]");

	private String pointer;

	private JsonPointerImpl() {
	}

	private JsonPointerImpl(String pointer) {
		this.pointer = pointer;
	}

	/**
	 * Create a JsonPointer from the given String. This pointer can then be given a
	 * JsonStructure to evaluate against to retrieve a value or manipulate the
	 * JsonStructure as specified by the JsonPointer interface.
	 * 
	 * @param pointer the JSON Pointer string
	 * @return a JsonPointer implementation
	 */
	public static JsonPointer create(String pointer) {
		if (pointer == null //
				|| (!pointer.isEmpty() //
						&& !pointer.startsWith(SEPARATOR))) {
			throw new IllegalArgumentException("Invalid JSON Pointer: " + pointer);
		}
		// basic validation
		// split on '/'
		// for each token
		// If it contains ~ followed by something other than 0 or 1 it is invalid
		// In order to have a literal ~ in the token, it must be escaped as ~0.
		// Literal / must be escaped as ~1.

		// TODO: should we allow creating a JsonPointer from a JsonString, and we need
		// to follow the below in that case?
		// Should that apply to regular Java Strings?
		// From the spec:
		// JSON Strings must have all instances of '"', '\', and control (0x00-0x1F)
		// characters escaped.
		// Before processing a JSON String as a JSON Pointer, backslash escape sequences
		// must be unescaped.

		// This does NOT check if the tokens are valid within a particular
		// JsonStructure.
		// That will happen during evaluation.
		final var tokens = pointer.split(SEPARATOR);
		for (final var token : tokens) {
			if (token.isEmpty())
				continue;
			Matcher invalidEscapeMatcher = INVALID_ESCAPE.matcher(token);
			if (invalidEscapeMatcher.find())
				throw new IllegalArgumentException("Invalid JSON Pointer: " + pointer);
		}
		// passed basic validation

		return new JsonPointerImpl(pointer);
	}

	@Override
	public <T extends JsonStructure> T add(T target, JsonValue value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T extends JsonStructure> T remove(T target) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T extends JsonStructure> T replace(T target, JsonValue value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean containsValue(JsonStructure target) {
		try {
			getValue(target);
			return true;
		} catch (JsonException | IllegalArgumentException e) {
			return false;
		}
	}

	@Override
	public JsonValue getValue(JsonStructure target) {
		JsonValue json = target;

		if (pointer.isEmpty())
			return json;

		int start = 1;
		do {
			int nextSeparator = pointer.indexOf(SEPARATOR, start);
			String fragment = nextSeparator == -1 ? pointer.substring(start) : pointer.substring(start, nextSeparator);
			// check if fragment has escape sequences
			fragment = fragment.replace("~1", "/").replace("~0", "~");
			// resolve the fragment
			JsonValue fragmentJson;
			if (json instanceof JsonObject) {
				fragmentJson = json.asJsonObject().get(fragment);
			} else if (json instanceof JsonArray) {
				// check if fragment fits the regex for an array index
				if (!fragment.matches("^(0|[1-9]\\d*)$"))
					throw new IllegalArgumentException("Invalid JSON Pointer: " + pointer);

				try {
					fragmentJson = json.asJsonArray().get(Integer.parseInt(fragment));
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Invalid JSON Pointer: " + pointer);
				}
			} else {
				throw new IllegalArgumentException("Invalid JSON Pointer: " + pointer);
			}
			json = fragmentJson;
			start = nextSeparator + 1;
		} while (start > 0);

		if (json == null)
			throw new JsonException("Value does not exist: " + pointer);

		return json;
	}

	@Override
	public String toString() {
		return pointer;
	}

	// TODO: Implement URL fragment resolution

}
