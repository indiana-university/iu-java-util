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

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Enum} types.
 * 
 * <p>
 * Converts to text naming the constant, or to a {@link JsonObject} describing
 * it when {@link IuJsonSerializationOptions#isEnumAsObject()}; see
 * {@link JsonSerializer#serializeEnum(Class, Enum, Supplier, Function)}.
 * </p>
 * 
 * <p>
 * Converts from either form whatever the options say, since options apply only
 * to the JSON conversion direction: a {@link JsonObject} converts by its
 * {@link JsonSerializer#NAME} property, ignoring every other property, and any
 * other value converts as text. This is what allows a value written as an
 * object to be read by a consumer that converts enum values as text.
 * </p>
 * 
 * @param <E> enum type
 */
public class EnumJsonAdapter<E extends Enum<E>> implements IuJsonAdapter<E> {

	private static final ClassValue<IuJsonAdapter<?>> STANDARD = new ClassValue<>() {
		@Override
		protected IuJsonAdapter<?> computeValue(Class<?> type) {
			return of(type, () -> IuJsonSerializationOptions.DEFAULT, IuJsonAdapter::of);
		}
	};

	/**
	 * Gets a singleton instance, by enum type, that converts to text.
	 * 
	 * @param type enum type
	 * @return {@link IuJsonAdapter}
	 */
	public static IuJsonAdapter<?> of(Class<?> type) {
		return STANDARD.get(type);
	}

	/**
	 * Gets an instance with dynamically supplied options.
	 * 
	 * @param type    enum type
	 * @param options supplies the options in effect for converting to JSON;
	 *                <em>should</em> return quickly, as it is called on every
	 *                conversion. A null value reads as
	 *                {@link IuJsonSerializationOptions#DEFAULT}
	 * @param adapt   adapter function for the constant's JavaBeans properties
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static IuJsonAdapter<?> of(Class<?> type, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {
		return new EnumJsonAdapter(type, options, adapt);
	}

	private final Class<E> type;
	private final Supplier<IuJsonSerializationOptions> options;
	private final Function<Type, IuJsonAdapter<?>> adapt;

	private EnumJsonAdapter(Class<E> type, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {
		this.type = type;
		this.options = options;
		this.adapt = adapt;
	}

	@Override
	public E fromJson(JsonValue value) {
		if (value == null || JsonValue.NULL.equals(value))
			return null;

		final String name;
		if (value instanceof JsonObject)
			// the name property is formatted like any other, and converting from JSON
			// has no options to read, so every format has to be checked
			name = TextJsonAdapter.INSTANCE
					.fromJson(JsonProxy.valueWithCaseConversion((JsonObject) value, JsonSerializer.NAME));
		else
			name = TextJsonAdapter.INSTANCE.fromJson(value);

		return Enum.valueOf(type, Objects.requireNonNull(name, JsonSerializer.NAME));
	}

	@Override
	public JsonValue toJson(E value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return JsonSerializer.serializeEnum(type, value, options, adapt);
	}

}
