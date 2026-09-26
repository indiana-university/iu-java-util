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

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;

import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import iu.client.JsonAdapters;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonLocation;

/**
 * State shared by one serialization or deserialization call: a single options
 * snapshot, and the property path to the value being converted.
 */
abstract class IuJsonbContext {

	/**
	 * Provider the call runs on.
	 */
	final IuJsonb jsonb;

	private final IuJsonSerializationOptions options;
	private final IuJsonPropertyNameFormat format;
	private final String root;
	private final Deque<String> path = new ArrayDeque<>();
	private Throwable failure;
	private String failurePath;

	/**
	 * Constructor; reads the options snapshot for the call.
	 *
	 * @param jsonb provider
	 * @param root  type converted by the call, which names the start of a path
	 * @throws JsonbException if the snapshot conflicts with the provider's JSON-B
	 *                        settings
	 */
	IuJsonbContext(IuJsonb jsonb, Type root) {
		this.jsonb = jsonb;
		options = jsonb.options();
		format = IuJsonb.format(options);

		final var rootClass = JsonAdapters.erase(root);
		final var simpleName = rootClass.getSimpleName();
		this.root = simpleName.isEmpty() ? rootClass.getName() : simpleName;
	}

	/**
	 * Gets the property name format for the call.
	 *
	 * @return {@link IuJsonPropertyNameFormat}
	 */
	IuJsonPropertyNameFormat format() {
		return format;
	}

	/**
	 * Determines if a property with a null value is written.
	 *
	 * @return true to write null properties; false to omit them
	 */
	boolean isIncludeNullProperties() {
		return options.isIncludeNullProperties();
	}

	/**
	 * Determines if an enum value is written as an object.
	 *
	 * @return true to write an enum as an object; false to write it as text
	 */
	boolean isEnumAsObject() {
		return options.isEnumAsObject();
	}

	/**
	 * Enters a property.
	 *
	 * @param name JSON property name
	 */
	void push(String name) {
		path.push(name);
	}

	/**
	 * Leaves the property most recently entered.
	 */
	void pop() {
		path.pop();
	}

	/**
	 * Records the current path as the location of a failure, unless a deeper
	 * property already recorded it or its cause.
	 *
	 * @param e failure
	 * @return {@code e}, to rethrow
	 */
	RuntimeException fail(RuntimeException e) {
		if (!isRecorded(e)) {
			failure = e;
			failurePath = path();
		}
		return e;
	}

	/**
	 * Describes a failure that ended the call.
	 *
	 * @param verb     "read" or "write"
	 * @param e        failure
	 * @param location parser location at the time of failure; null if not
	 *                 reading from a parser
	 * @return {@link JsonbException} naming the root type, the path to the failed
	 *         property, and the location; {@code e} itself if it is already a
	 *         {@link JsonbException} and there is nothing to add
	 */
	JsonbException failed(String verb, RuntimeException e, JsonLocation location) {
		final var where = isRecorded(e) ? failurePath : root;
		final var hasLocation = location != null && location.getLineNumber() >= 0;
		if (e instanceof JsonbException //
				&& where.equals(root) //
				&& !hasLocation)
			return (JsonbException) e;

		final var message = new StringBuilder("failed to ").append(verb).append(' ').append(where);
		if (hasLocation)
			message.append(" (line ").append(location.getLineNumber()) //
					.append(", column ").append(location.getColumnNumber()).append(')');
		if (e.getMessage() != null)
			message.append(": ").append(e.getMessage());
		return new JsonbException(message.toString(), e);
	}

	private boolean isRecorded(Throwable e) {
		for (var t = e; t != null; t = t.getCause())
			if (t == failure)
				return true;
		return false;
	}

	private String path() {
		final var sb = new StringBuilder(root);
		final var i = path.descendingIterator();
		while (i.hasNext())
			sb.append('.').append(i.next());
		return sb.toString();
	}

}
