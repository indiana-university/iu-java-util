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
package iu.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.Map;

import edu.iu.IuException;

/**
 * Builds a bean interface instance from a map of getter name to value.
 *
 * <p>
 * A dynamic proxy rather than a hand-written class, for two reasons: the
 * business objects this module validates in production are proxies of getter
 * interfaces, and a proxy method carries no annotations of its own, so every
 * fixture built here also exercises the reason {@link BeanModel} unions
 * constraints across the type hierarchy instead of taking the nearest
 * declaration.
 * </p>
 *
 * <p>
 * A getter with no entry in the map returns null, so a fixture need only name
 * the properties a test cares about.
 * </p>
 */
@SuppressWarnings("javadoc")
public final class Beans {

	public static <T> T of(Class<T> type) {
		return of(type, Map.of());
	}

	@SuppressWarnings("unchecked")
	public static <T> T of(Class<T> type, Map<String, ?> byGetter) {
		return (T) Proxy.newProxyInstance(Beans.class.getClassLoader(), new Class<?>[] { type },
				(proxy, method, arguments) -> {
					switch (method.getName()) {
					case "toString":
						return type.getSimpleName() + byGetter;
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == arguments[0];
					default:
						return byGetter.get(method.getName());
					}
				});
	}

	public static <A extends Annotation> A annotation(Class<?> type, String getter, Class<A> annotationType) {
		return IuException.unchecked(() -> type.getMethod(getter).getAnnotation(annotationType));
	}

	public static Annotation[] annotations(Class<?> type, String getter) {
		return IuException.unchecked(() -> type.getMethod(getter).getAnnotations());
	}

	private Beans() {
	}

}
