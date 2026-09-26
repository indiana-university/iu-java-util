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

import java.beans.Introspector;
import java.lang.reflect.Type;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
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
	 * <p>
	 * An interface is wrapped by a thin {@link JsonProxy} that reads property
	 * values directly from {@code value}. Any other type is instantiated using its
	 * no-arg constructor, then each JavaBeans property with a setter that maps to a
	 * defined JSON value is converted and applied. Properties without a setter, and
	 * setters without a corresponding JSON value, are skipped.
	 * </p>
	 *
	 * @param <T>     value type
	 * @param type    value type for introspection
	 * @param value   {@link JsonObject} to deserialize
	 * @param options supplies the options in effect; read once per invocation
	 * @param adapt   adapter function
	 * @return business object
	 */
	public static <T> T deserialize(Class<T> type, JsonObject value, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {
		return deserialize(type, value, JsonSerializer.propertyNameFormat(JsonSerializer.snapshot(options)), adapt);
	}

	private static <T> T deserialize(Class<T> type, JsonObject value, IuJsonPropertyNameFormat propertyNameFormat,
			Function<Type, IuJsonAdapter<?>> adapt) {
		if (type.isInterface())
			return JsonProxy.wrap(value, type, propertyNameFormat, adapt);

		final var bean = IuException.uncheckedInvocation(() -> type.getDeclaredConstructor().newInstance());

		for (final var propertyDescriptor : IuException.unchecked(() -> Introspector.getBeanInfo(type))
				.getPropertyDescriptors()) {
			final var writeMethod = propertyDescriptor.getWriteMethod();
			if (writeMethod == null)
				continue;

			final var jsonValue = value
					.get(JsonSerializer.formatPropertyName(propertyDescriptor.getName(), propertyNameFormat));
			if (jsonValue == null)
				continue;

			final var propertyValue = adapt.apply(writeMethod.getGenericParameterTypes()[0]).fromJson(jsonValue);
			IuException.uncheckedInvocation(() -> writeMethod.invoke(bean, propertyValue));
		}

		return bean;
	}

}
