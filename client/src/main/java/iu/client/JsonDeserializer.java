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
import java.util.function.Function;
import java.util.function.Supplier;

import edu.iu.IuObject;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonProperties;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonObject;

/**
 * Converts from JSON to a JavaBeans business object.
 */
public final class JsonDeserializer {
	static {
		IuObject.assertNotOpen(JsonDeserializer.class);
	}

	private JsonDeserializer() {
	}

	/**
	 * Deserializes a business object from JSON, reading property names in the
	 * format supplied by the options in effect.
	 *
	 * @param <T>     value type
	 * @param type    value type for introspection
	 * @param value   {@link JsonObject} to deserialize
	 * @param options supplies the options in effect; read once per invocation
	 * @param adapt   adapter function
	 * @return business object
	 * @see #deserialize(Type, JsonObject, Supplier, Function)
	 */
	public static <T> T deserialize(Class<T> type, JsonObject value, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {
		return type.cast(deserialize((Type) type, value, options, adapt));
	}

	/**
	 * Deserializes a business object from JSON, reading property names in the
	 * format supplied by the options in effect.
	 *
	 * <p>
	 * An interface is wrapped by a thin {@link JsonProxy} that reads property
	 * values directly from {@code value}. Any other type is instantiated using its
	 * no-arg constructor, then each JSON property that names a writable property,
	 * as {@link BeanModel} discovers them, is converted to the property's type,
	 * resolved against {@code type}, and applied. A JSON property that names no
	 * writable property is skipped, and a property without a JSON value keeps the
	 * value the constructor assigned.
	 * </p>
	 *
	 * @param type    value type for introspection, or a parameterized type of one
	 * @param value   {@link JsonObject} to deserialize
	 * @param options supplies the options in effect; read once per invocation
	 * @param adapt   adapter function
	 * @return business object
	 */
	public static Object deserialize(Type type, JsonObject value, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {
		final var snapshot = JsonSerializer.snapshot(options);
		final var format = JsonSerializer.propertyNameFormat(snapshot);

		final var erased = JsonAdapters.erase(type);
		if (erased.isInterface()) {
			final var metadata = snapshot.isLegacyProperties() ? BindingMetadata.NONE : BindingMetadata.get();
			return JsonProxy.wrap(IuJsonProperties.of(value, adapt), erased,
					name -> JsonSerializer.formatPropertyName(name, format), false, metadata,
					JsonProxy.declared(metadata, () -> snapshot));
		}

		final var model = JsonSerializer.model(type, snapshot);
		final var naming = PropertyNaming.of(format);
		final var bean = model.newInstance();
		for (final var entry : value.entrySet()) {
			final var property = model.writable(naming, entry.getKey());
			if (property != null)
				property.set(bean, JsonSerializer.writeAdapter(property, adapt, snapshot).fromJson(entry.getValue()));
		}
		return bean;
	}

}
