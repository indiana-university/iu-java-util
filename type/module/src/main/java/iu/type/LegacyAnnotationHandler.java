/*
 * Copyright © 2023 Indiana University
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
package iu.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.type.IuType;

class LegacyAnnotationHandler implements InvocationHandler {

	private static final Object[] O0 = new Object[0];

	private static final Method ANNOTATION_TYPE = IuException
			.unchecked(() -> Annotation.class.getMethod("annotationType"));
	private static final Method EQUALS = IuException.unchecked(() -> Object.class.getMethod("equals", Object.class));

	private final Class<? extends Annotation> nonLegacyAnnotationType;
	private final Annotation legacyAnnotation;

	LegacyAnnotationHandler(Class<? extends Annotation> nonLegacyAnnotationType, Annotation legacyAnnotation) {
		this.nonLegacyAnnotationType = nonLegacyAnnotationType;
		this.legacyAnnotation = legacyAnnotation;
	}

	@SuppressWarnings("unchecked")
	private Object convert(Object o, Class<?> type) throws ClassNotFoundException {
		if (type.isInstance(o))
			return o;

		if (Annotation.class.isAssignableFrom(type)) {
			var legacyAnnotationType = BackwardsCompatibility.getLegacyClass(type);
			if (Annotation.class.isAssignableFrom(legacyAnnotationType) && legacyAnnotationType.isInstance(o))
				return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
						new LegacyAnnotationHandler(type.asSubclass(Annotation.class), (Annotation) o));
		}

		if (type.isArray() && o.getClass().isArray()) {
			var length = Array.getLength(o);
			var componentType = type.getComponentType();
			var convertedArray = Array.newInstance(componentType, length);
			for (int i = 0; i < length; i++)
				Array.set(convertedArray, i, convert(Array.get(o, i), componentType));
			return convertedArray;
		}

		if (type.isEnum() && o.getClass().isEnum())
			return Enum.valueOf(type.asSubclass(Enum.class), ((Enum<?>) o).name());

		throw new IllegalStateException("cannot convert " + o + " (" + o.getClass().getName() + ") to " + type
				+ ", handling legacy annotation " + legacyAnnotation);
	}

	private boolean handleEquals(Object proxy, Object object) throws Throwable {
		if (legacyAnnotation.equals(object))
			return true;

		if (!nonLegacyAnnotationType.isInstance(object))
			return false;

		for (var method : nonLegacyAnnotationType.getDeclaredMethods())
			if (!Objects.equals(invoke(proxy, method, O0), IuException.checked(method, object)))
				return false;
		return true;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return TypeUtils.callWithContext(legacyAnnotation.annotationType(), () -> {
			if (method.equals(ANNOTATION_TYPE))
				return nonLegacyAnnotationType;

			if (method.equals(EQUALS))
				return handleEquals(proxy, args[0]);

			var legacyMethod = legacyAnnotation.annotationType().getMethod(method.getName(),
					method.getParameterTypes());

			var legacyInvokeArgs = new Object[args == null ? 1 : args.length + 1];
			legacyInvokeArgs[0] = legacyAnnotation;
			if (args != null)
				System.arraycopy(args, 0, legacyInvokeArgs, 1, args.length);

			var legacyReturnValue = IuException.checked(legacyMethod, legacyInvokeArgs);
			var returnType = IuType.of(method.getGenericReturnType()).autoboxClass();

			return convert(legacyReturnValue, returnType);
		});
	}

}
