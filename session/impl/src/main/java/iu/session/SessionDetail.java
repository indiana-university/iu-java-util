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
package iu.session;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonProperties;
import edu.iu.jwt.WebToken;
import jakarta.json.JsonValue;

/**
 * Holds Session attributes
 *
 * <p>
 * Attribute names are the property names in snake_case, and values convert by
 * {@link WebToken#jsonb()}, as the token the session is stored in does.
 * </p>
 */
class SessionDetail implements InvocationHandler {
	static {
		IuObject.assertNotOpen(SessionDetail.class);
	}

	/**
	 * Gets the attribute name for an accessor.
	 *
	 * @param methodName accessor method name
	 * @param prefix     length of the accessor prefix: get, is, or set
	 * @return property name in snake_case
	 */
	static String attributeName(String methodName, int prefix) {
		final var name = new StringBuilder();
		for (var i = prefix; i < methodName.length(); i++) {
			final var c = methodName.charAt(i);
			if (Character.isUpperCase(c)) {
				if (i > prefix)
					name.append('_');
				name.append(Character.toLowerCase(c));
			} else
				name.append(c);
		}
		return name.toString();
	}

	/** session attributes */
	private final Map<String, JsonValue> attributes;

	/** session */
	private final Session session;

	/**
	 * Constructor
	 * 
	 * @param attributes attributes
	 * @param session    session
	 */
	SessionDetail(Map<String, JsonValue> attributes, Session session) {
		this.attributes = attributes;
		this.session = session;
	}

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
				key = attributeName(methodName, 3);
			else if (methodName.startsWith("is"))
				key = attributeName(methodName, 2);
			else
				throw new UnsupportedOperationException(method.toString());

			// an attribute not set reads as a primitive's default, an empty optional,
			// or null
			final var object = IuJson.object();
			final var value = attributes.get(key);
			if (value != null)
				object.add(key, value);
			return IuJsonProperties.of(object.build(), WebToken.jsonb()).get(key, method.getGenericReturnType());

		} else if (args.length == 1) {
			if (methodName.equals("equals"))
				return args[0] == proxy;

			if (methodName.startsWith("set")) {
				key = attributeName(methodName, 3);
				final var value = args[0];
				if (value == null) {
					if (attributes.remove(key) != null)
						session.setChanged(true);
				} else {
					final var jsonValue = IuJsonProperties.builder(WebToken.jsonb()) //
							.put(key, value, method.getGenericParameterTypes()[0]).build().toJsonObject().get(key);
					if (!IuObject.equals(jsonValue, attributes.put(key, jsonValue)))
						session.setChanged(true);
				}
				return null;
			}
		}

		throw new UnsupportedOperationException(method.toString());
	}

}
