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
package edu.iu.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import iu.type.BackwardsCompatibilityHelper;
import iu.type.IuInvocationContext;

/**
 * Represents a method reflected from the base class of a generic type.
 * 
 * @param <R> method return type
 */
public class IuMethod<R> extends IuExecutable<R, Method> {

	static class Builder<R> {
		private final IuType<?> declaringType;
		private final IuType<R> type;
		private final Method method;
		private final List<IuParameter<?>> parameters;
		private Map<Class<?>, Method> inheritedMethods;

		public Builder(IuType<?> declaringType, IuType<R> type, Method method, List<IuParameter<?>> parameters) {
			super();
			this.declaringType = declaringType;
			this.type = type;
			this.method = method;
			this.parameters = parameters;
		}

		void inherit(Class<?> declaringType, Method method) {
			if (inheritedMethods == null)
				inheritedMethods = new LinkedHashMap<>();
			assert method.getName().equals(method.getName());
			assert IuObject.equals(method.getParameterTypes(), method.getParameterTypes());
			var replaced = inheritedMethods.put(declaringType, method);
			// method should not replace an inherited method
			assert replaced == null //
					|| !(Modifier.isPublic(replaced.getModifiers()) || Modifier.isProtected(replaced.getModifiers()))
					|| replaced.getDeclaringClass().isAssignableFrom(method.getDeclaringClass())
					: replaced + " " + method + " " + inheritedMethods + " " + declaringType + " " + this.declaringType;
		}

		IuMethod<R> build() {
			return new IuMethod<>(declaringType, type, method, parameters,
					inheritedMethods == null ? Collections.emptyMap() : Collections.unmodifiableMap(inheritedMethods));
		}
	}

	private final Map<Class<?>, Method> inheritedMethods;
	private Map<Class<? extends Annotation>, Annotation> inheritedAnnotations;

	private IuMethod(IuType<?> declaringType, IuType<R> type, Method method, List<IuParameter<?>> parameters,
			Map<Class<?>, Method> inheritedMethods) {
		super(declaringType, type, method, parameters);
		this.inheritedMethods = inheritedMethods;
	}

	/**
	 * Gets the method name
	 * 
	 * @return method name
	 */
	public String name() {
		return deref().getName();
	}

	/**
	 * Determines if a method is overridden by, or is, the reflected method.
	 * 
	 * @param method implemented or extended method
	 * @return true if overridden or equals, else false
	 */
	public boolean overrides(Method method) {
		return method.equals(deref()) || method.equals(inheritedMethods.get(method.getDeclaringClass()));
	}

	/**
	 * Determines if the method is static.
	 * 
	 * @return true if the method is static; else false
	 */
	public boolean isStatic() {
		return Modifier.isStatic(deref().getModifiers());
	}

	/**
	 * Checks implemented interfaces and extended classes for the presents of an
	 * annotation on an overridden method, in addition to this method.
	 * 
	 * @param annotationType annotation type
	 * @return true if this method or an overridden method has the annotation
	 *         present; else false
	 */
	public boolean hasInheritedAnnotation(Class<? extends Annotation> annotationType) {
		if (super.hasAnnotation(annotationType))
			return true;

		for (var inheritedMember : inheritedMethods.values())
			if (BackwardsCompatibilityHelper.isAnnotationPresent(annotationType, inheritedMember))
				return true;

		return false;
	}

	/**
	 * Gets an annotation of the given type from this method or a method overridden
	 * from an implemented interface or extended class, if present.
	 * 
	 * @param <A>            annotation type
	 * @param annotationType annotation type
	 * @return annotation if present on this method or an overridden method; else
	 *         false
	 */
	public <A extends Annotation> A inheritedAnnotation(Class<A> annotationType) {
		var annotation = super.annotation(annotationType);
		if (annotation != null)
			return annotation;

		for (var inheritedMember : inheritedMethods.values()) {
			annotation = BackwardsCompatibilityHelper.getAnnotation(annotationType, inheritedMember);
			if (annotation != null)
				return annotation;
		}

		return null;
	}

	/**
	 * Gets all annotations by type, including this defined on a method overridden
	 * from an implemented interface or extended class.
	 * 
	 * @return inherited annotations
	 */
	public Map<Class<? extends Annotation>, Annotation> inheritedAnnotations() {
		if (inheritedAnnotations == null) {
			var annotations = super.annotations();
			if (!inheritedMethods.isEmpty()) {
				annotations = new LinkedHashMap<>(annotations);
				for (var inheritedMethod : inheritedMethods.values()) {
					var inheritedAnnotations = BackwardsCompatibilityHelper.getAnnotations(inheritedMethod);
					for (var inheritedAnnotationEntry : inheritedAnnotations.entrySet()) {
						var inheritedAnnotationType = inheritedAnnotationEntry.getKey();
						if (!annotations.containsKey(inheritedAnnotationType))
							annotations.put(inheritedAnnotationEntry.getKey(), inheritedAnnotationEntry.getValue());
					}
				}
			}
			this.inheritedAnnotations = annotations;
		}
		return inheritedAnnotations;
	}

	/**
	 * Invokes the method.
	 * 
	 * @param args arguments if static; else instance followed by arguments
	 * @return return value
	 * @throws Exception if invocation results in an exception
	 */
	public R invoke(Object... args) throws Exception {
		Object target;
		if (isStatic())
			target = null;
		else if (args.length == 0)
			throw new IllegalArgumentException("missing instance");
		else {
			target = args[0];
			args = Arrays.copyOfRange(args, 1, args.length);
		}

		var interceptors = interceptors();
		if (interceptors.isEmpty())
			try {
				Method method = deref();
				method.setAccessible(true);
				return type().autoboxClass().cast(method.invoke(target, args));
			} catch (Throwable e) {
				throw IuException.handleChecked(IuException.handleInvocation(e));
			}
		else
			try {
				return type().baseClass().cast(new IuInvocationContext(deref(), target, args, interceptors).proceed());
			} catch (Throwable e) {
				throw IuException.handleChecked(e);
			}
	}

	@Override
	public String toString() {
		return "method " + super.toString();
	}

}
