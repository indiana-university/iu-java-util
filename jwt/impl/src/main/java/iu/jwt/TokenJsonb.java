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
package iu.jwt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import edu.iu.crypt.Init;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;

/**
 * Holds the {@link Jsonb} instance that converts token claims, and the
 * components registered for it.
 */
final class TokenJsonb {

	/**
	 * Converts every {@link Instant} as a NumericDate, seconds since the epoch.
	 *
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-2">RFC
	 *      7519 JWT Section 2</a>
	 */
	static final class NumericDate implements JsonbAdapter<Instant, BigDecimal> {
		/**
		 * Default constructor.
		 */
		NumericDate() {
		}

		@Override
		public BigDecimal adaptToJson(Instant obj) {
			return obj == null ? null : BigDecimal.valueOf(obj.getEpochSecond());
		}

		@Override
		public Instant adaptFromJson(BigDecimal obj) {
			// a NumericDate may have a fraction; seconds are kept
			return obj == null ? null : Instant.ofEpochSecond(obj.setScale(0, RoundingMode.FLOOR).longValueExact());
		}
	}

	private static final List<JsonbAdapter<?, ?>> ADAPTERS = new ArrayList<>();
	private static final List<JsonbSerializer<?>> SERIALIZERS = new ArrayList<>();
	private static final List<JsonbDeserializer<?>> DESERIALIZERS = new ArrayList<>();
	private static Jsonb jsonb;

	private TokenJsonb() {
	}

	/**
	 * Registers a JSON-B adapter for claim values.
	 *
	 * @param adapter {@link JsonbAdapter}
	 */
	static synchronized void registerAdapter(JsonbAdapter<?, ?> adapter) {
		requireNotCreated();
		ADAPTERS.add(Objects.requireNonNull(adapter, "Missing adapter"));
	}

	/**
	 * Registers a JSON-B serializer for claim values.
	 *
	 * @param serializer {@link JsonbSerializer}
	 */
	static synchronized void registerSerializer(JsonbSerializer<?> serializer) {
		requireNotCreated();
		SERIALIZERS.add(Objects.requireNonNull(serializer, "Missing serializer"));
	}

	/**
	 * Registers a JSON-B deserializer for claim values.
	 *
	 * @param deserializer {@link JsonbDeserializer}
	 */
	static synchronized void registerDeserializer(JsonbDeserializer<?> deserializer) {
		requireNotCreated();
		DESERIALIZERS.add(Objects.requireNonNull(deserializer, "Missing deserializer"));
	}

	/**
	 * Gets the {@link Jsonb} instance that converts token claims, creating it on
	 * first use.
	 *
	 * @return {@link Jsonb}
	 */
	static synchronized Jsonb get() {
		if (jsonb == null)
			jsonb = JsonbBuilder.newBuilder("iu.client.jsonb.IuJsonbProvider")
					.withConfig(Init.<JsonbConfig>jsonbConfig() //
							.withAdapters(new NumericDate()) //
							.withAdapters(ADAPTERS.toArray(JsonbAdapter[]::new)) //
							.withSerializers(SERIALIZERS.toArray(JsonbSerializer[]::new)) //
							.withDeserializers(DESERIALIZERS.toArray(JsonbDeserializer[]::new)))
					.build();
		return jsonb;
	}

	private static void requireNotCreated() {
		if (jsonb != null)
			throw new IllegalStateException("sealed");
	}

}
