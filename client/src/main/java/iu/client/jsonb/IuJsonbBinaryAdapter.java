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

import edu.iu.client.IuJsonAdapter;
import iu.client.BinaryJsonAdapter;
import jakarta.json.JsonValue;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Converts {@code byte[]} by the binary data strategy of the call in progress:
 * {@link jakarta.json.bind.JsonbConfig#BINARY_DATA_STRATEGY}, or the
 * {@link edu.iu.client.IuJsonSerializationOptions#getBinaryDataStrategy()}
 * of a {@link edu.iu.client.IuJsonAdapter#SERIALIZATION_OPTIONS} snapshot; always
 * {@link BinaryDataStrategy#BASE_64_URL} with
 * {@link jakarta.json.bind.JsonbConfig#STRICT_IJSON}.
 */
final class IuJsonbBinaryAdapter implements IuJsonAdapter<byte[]> {

	private final IuJsonb jsonb;
	private final boolean base64UrlUnpadded;

	/**
	 * Constructor.
	 *
	 * @param jsonb             provider
	 * @param base64UrlUnpadded true to write base64url without padding
	 */
	IuJsonbBinaryAdapter(IuJsonb jsonb, boolean base64UrlUnpadded) {
		this.jsonb = jsonb;
		this.base64UrlUnpadded = base64UrlUnpadded;
	}

	private BinaryJsonAdapter strategy(IuJsonbContext context) {
		// strict I-JSON writes base64url whatever else is configured
		if (jsonb.isStrictIJson())
			return BinaryJsonAdapter.of(BinaryDataStrategy.BASE_64_URL, base64UrlUnpadded);

		final var options = context == null ? jsonb.options() : context.options();
		return BinaryJsonAdapter.of(options.getBinaryDataStrategy(), base64UrlUnpadded);
	}

	@Override
	public byte[] fromJson(JsonValue value) {
		return strategy(IuDeserializationContext.current(jsonb)).fromJson(value);
	}

	@Override
	public JsonValue toJson(byte[] value) {
		return strategy(IuSerializationContext.current(jsonb)).toJson(value);
	}

	@Override
	public byte[] read(JsonParser parser) {
		return strategy(IuDeserializationContext.current(jsonb)).read(parser);
	}

	@Override
	public void write(byte[] value, JsonGenerator generator) {
		strategy(IuSerializationContext.current(jsonb)).write(value, generator);
	}

}
