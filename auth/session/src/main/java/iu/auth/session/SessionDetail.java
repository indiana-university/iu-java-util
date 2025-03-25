/*
 * Copyright © 2025 Indiana University
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
package iu.auth.session;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;

import edu.iu.IuObject;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Holds Session attributes
 */
class SessionDetail implements InvocationHandler {
	static {
		IuObject.assertNotOpen(SessionDetail.class);
	}

	/** session attributes */
	private final Map<String, JsonValue> attributes;

	/** session */
	private final Session session;

	/** adapter factory */
	private final Function<Type, IuJsonAdapter<?>> adapterFactory;

	/**
	 * Constructor
	 * 
	 * @param attributes     attributes
	 * @param session        session
	 * @param adapterFactory adapter factory
	 */
	SessionDetail(Map<String, JsonValue> attributes, Session session,
			Function<Type, IuJsonAdapter<?>> adapterFactory) {
		this.attributes = attributes;
		this.session = session;
		this.adapterFactory = adapterFactory;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		final var methodName = method.getName();

		final String key;
		if (args == null) {
			if (methodName.equals("hashCode"))
				return System.identityHashCode(proxy);
			if (methodName.equals("toString"))
				return attributes.toString();

			if (methodName.startsWith("get"))
				key = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
			else if (methodName.startsWith("is"))
				key = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
			else
				throw new UnsupportedOperationException(method.toString());

			return adapterFactory.apply(method.getGenericReturnType()).fromJson(attributes.get(key));

		} else if (args.length == 1) {
			if (methodName.equals("equals"))
				return args[0] == proxy;

			if (methodName.startsWith("set")) {
				key = methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
				final var value = args[0];
				if (value == null) {
					if (attributes.remove(key) != null)
						session.setChanged(true);
				} else {
					IuJsonAdapter adapter = adapterFactory.apply(method.getGenericParameterTypes()[0]);
					final var jsonValue = adapter.toJson(value);
					if (!IuObject.equals(jsonValue, attributes.put(key, jsonValue)))
						session.setChanged(true);
				}
				return null;
			}
		}

		throw new UnsupportedOperationException(method.toString());
	}

}
