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
package iu.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.type.IuType;

/**
 * Handles remote bridge semantics for {@link AnnotationBridge}.
 */
class PotentiallyRemoteAnnotationHandler implements InvocationHandler {

	private static final Method ANNOTATION_TYPE = IuException
			.unchecked(() -> Annotation.class.getMethod("annotationType"));
	private static final Method EQUALS = IuException.unchecked(() -> Object.class.getMethod("equals", Object.class));

	private final Class<? extends Annotation> localAnnotationType;
	private final Annotation potentiallyRemoteAnnotation;

	/**
	 * Constructor for use by {@link AnnotationBridge}.
	 * 
	 * @param localAnnotationType         local annotation type
	 * @param potentiallyRemoteAnnotation potentially remote annotation
	 */
	PotentiallyRemoteAnnotationHandler(Class<? extends Annotation> localAnnotationType,
			Annotation potentiallyRemoteAnnotation) {
		this.localAnnotationType = localAnnotationType;
		this.potentiallyRemoteAnnotation = potentiallyRemoteAnnotation;
	}

	@SuppressWarnings("unchecked")
	private Object convert(Object o, Class<?> localClass) throws ClassNotFoundException {
		if (localClass.isInstance(o))
			return o;

		if (Annotation.class.isAssignableFrom(localClass)) {
			var potentiallyRemoteClass = BackwardsCompatibility.getPotentiallyRemoteClass(localClass);
			if (Annotation.class.isAssignableFrom(potentiallyRemoteClass) && potentiallyRemoteClass.isInstance(o))
				return Proxy.newProxyInstance(localClass.getClassLoader(), new Class<?>[] { localClass },
						new PotentiallyRemoteAnnotationHandler(localClass.asSubclass(Annotation.class),
								(Annotation) o));
		}

		if (localClass.isArray() && o.getClass().isArray()) {
			var length = Array.getLength(o);
			var componentType = localClass.getComponentType();
			var convertedArray = Array.newInstance(componentType, length);
			for (int i = 0; i < length; i++)
				Array.set(convertedArray, i, convert(Array.get(o, i), componentType));
			return convertedArray;
		}

		if (localClass.isEnum() && o.getClass().isEnum())
			return Enum.valueOf(localClass.asSubclass(Enum.class), ((Enum<?>) o).name());

		throw new IllegalStateException("cannot convert " + o + " (" + o.getClass().getName() + ") to " + localClass
				+ ", handling legacy annotation " + potentiallyRemoteAnnotation);
	}

	private boolean handleEquals(Object proxy, Object object) throws Throwable {
		if (potentiallyRemoteAnnotation.equals(object))
			return true;

		if (!localAnnotationType.isInstance(object))
			return false;

		for (var method : localAnnotationType.getDeclaredMethods())
			if (!Objects.equals(invoke(proxy, method, null), IuException.checkedInvocation(() -> method.invoke(object))))
				return false;
		return true;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return TypeUtils.callWithContext(potentiallyRemoteAnnotation.annotationType(), () -> {
			if (method.equals(ANNOTATION_TYPE))
				return localAnnotationType;

			if (method.equals(EQUALS))
				return handleEquals(proxy, args[0]);

			assert args == null : Arrays.toString(args);
			var potentiallyRemoteMethod = potentiallyRemoteAnnotation.annotationType().getMethod(method.getName());
			var potentiallyRemoteReturnValue = IuException
					.checkedInvocation(() -> potentiallyRemoteMethod.invoke(potentiallyRemoteAnnotation));
			var returnType = IuType.of(method.getGenericReturnType()).autoboxClass();

			return convert(potentiallyRemoteReturnValue, returnType);
		});
	}

}
