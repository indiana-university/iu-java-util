/*
 * Copyright © 2024 Indiana University
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
import java.util.Map;

/**
 * Holds Session attributes
 */
public class SessionDetail implements InvocationHandler {

	/** session attributes */
	final Map<String, Object> attributes;

	/** session */
	final Session session;

	/**
	 * Constructor
	 * 
	 * @param attributes attributes
	 * @param session    session
	 */
	public SessionDetail(Map<String, Object> attributes, Session session) {
		this.attributes = attributes;
		this.session = session;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		final var methodName = method.getName();

		if (methodName.equals("hashCode")) {
			return System.identityHashCode(proxy);
		}

		if (methodName.equals("equals")) {
			return args[0] == proxy;
		}

		if (methodName.equals("toString")) {
			return attributes.toString();
		}

		final String key;
		if (methodName.startsWith("get")) {
			key = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
			return attributes.get(key);
		} else if (methodName.startsWith("is")) {
			key = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
			return attributes.get(key);
		}

		if (methodName.startsWith("set") && args != null && args.length == 1) {
			key = methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
			if (attributes.containsKey(key)) {
				if (args[0] != attributes.get(key)) {
					attributes.put(key, args[0]);
					session.setChange(true);
				}
				return null;
			}
			attributes.put(key, args[0]);
			session.setChange(true);
			return null;
		}

		throw new UnsupportedOperationException("Method " + methodName + " is not supported.");
	}

}
