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

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonArray;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Converts {@code byte[]} by a binary data strategy, named as JSON-B names
 * them.
 *
 * <p>
 * {@code BYTE} converts as an array of numbers, each a signed byte.
 * {@code BASE_64} and {@code BASE_64_URL} convert as text, written padded
 * unless configured otherwise for base64url, and read padded or not.
 * </p>
 */
public final class BinaryJsonAdapter implements IuJsonAdapter<byte[]> {

	private static final IuJsonAdapter<Byte> BYTE = NumberAdapter.BYTE_PRIMITIVE;
	private static final Map<String, BinaryJsonAdapter> STRATEGIES = new ConcurrentHashMap<>();

	/**
	 * Adapts by the default strategy, {@code BYTE}.
	 */
	static final BinaryJsonAdapter INSTANCE = of(IuJsonSerializationOptions.BINARY_DATA_STRATEGY, false);

	/**
	 * Gets the conversion for a strategy.
	 *
	 * @param strategy          {@code BYTE}, {@code BASE_64}, or
	 *                          {@code BASE_64_URL}; null for {@code BYTE}
	 * @param base64UrlUnpadded true to write base64url without padding
	 * @return {@link BinaryJsonAdapter}
	 * @throws UnsupportedOperationException if the strategy is none of those
	 */
	public static BinaryJsonAdapter of(String strategy, boolean base64UrlUnpadded) {
		final var name = Objects.requireNonNullElse(strategy, IuJsonSerializationOptions.BINARY_DATA_STRATEGY);
		return STRATEGIES.computeIfAbsent(name + (base64UrlUnpadded ? "/unpadded" : ""),
				key -> new BinaryJsonAdapter(name, base64UrlUnpadded));
	}

	/**
	 * Gets a conversion that follows the strategy each options snapshot names.
	 *
	 * @param options supplies the options in effect for each conversion
	 * @return {@link IuJsonAdapter}
	 */
	public static IuJsonAdapter<byte[]> of(Supplier<IuJsonSerializationOptions> options) {
		return new IuJsonAdapter<>() {
			private BinaryJsonAdapter strategy() {
				return BinaryJsonAdapter.of(JsonSerializer.snapshot(options).getBinaryDataStrategy(), false);
			}

			@Override
			public byte[] fromJson(JsonValue value) {
				return strategy().fromJson(value);
			}

			@Override
			public JsonValue toJson(byte[] value) {
				return strategy().toJson(value);
			}

			@Override
			public byte[] read(JsonParser parser) {
				return strategy().read(parser);
			}

			@Override
			public void write(byte[] value, JsonGenerator generator) {
				strategy().write(value, generator);
			}
		};
	}

	private final boolean bytes;
	private final Base64.Encoder encoder;
	private final Base64.Decoder decoder;

	private BinaryJsonAdapter(String strategy, boolean base64UrlUnpadded) {
		switch (strategy) {
		case "BYTE":
			bytes = true;
			encoder = null;
			decoder = null;
			break;

		case "BASE_64":
			bytes = false;
			encoder = Base64.getEncoder();
			decoder = Base64.getDecoder();
			break;

		case "BASE_64_URL":
			bytes = false;
			encoder = base64UrlUnpadded ? Base64.getUrlEncoder().withoutPadding() : Base64.getUrlEncoder();
			decoder = Base64.getUrlDecoder();
			break;

		default:
			throw new UnsupportedOperationException(strategy);
		}
	}

	@Override
	public byte[] fromJson(JsonValue value) {
		if (value == null //
				|| JsonValue.NULL.equals(value))
			return null;

		if (bytes) {
			if (!(value instanceof JsonArray))
				throw JsonAdapters.expected("an array of bytes", value.getValueType());
			final var array = value.asJsonArray();
			final var data = new byte[array.size()];
			for (var i = 0; i < data.length; i++)
				data[i] = BYTE.fromJson(array.get(i));
			return data;
		}

		if (!(value instanceof JsonString))
			throw JsonAdapters.expected("base64 text", value.getValueType());
		return decoder.decode(((JsonString) value).getString());
	}

	@Override
	public JsonValue toJson(byte[] value) {
		if (value == null)
			return JsonValue.NULL;

		if (bytes) {
			final var array = IuJson.array();
			for (final var b : value)
				array.add(b);
			return array.build();
		} else
			return IuJson.string(encoder.encodeToString(value));
	}

	@Override
	public byte[] read(JsonParser parser) {
		final var event = parser.currentEvent();
		if (event == Event.VALUE_NULL)
			return null;

		if (bytes) {
			if (event != Event.START_ARRAY)
				throw JsonAdapters.expected("an array of bytes", event);
			final var data = new ByteArrayOutputStream();
			while (parser.next() != Event.END_ARRAY)
				data.write(BYTE.read(parser));
			return data.toByteArray();
		}

		if (event != Event.VALUE_STRING)
			throw JsonAdapters.expected("base64 text", event);
		return decoder.decode(parser.getString());
	}

	@Override
	public void write(byte[] value, JsonGenerator generator) {
		if (value == null)
			generator.writeNull();
		else if (bytes) {
			generator.writeStartArray();
			for (final var b : value)
				generator.write(b);
			generator.writeEnd();
		} else
			generator.write(encoder.encodeToString(value));
	}

}
