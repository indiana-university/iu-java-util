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

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.Map;

import edu.iu.IuCacheMap;

/**
 * The constraints declared on one standalone annotation target, for validating a
 * value the caller already holds.
 *
 * <p>
 * Complements {@link BeanModel}, which models a whole type. Here there is no
 * bean to read from: the value is supplied, and the target only supplies the
 * rules and a name for the violation path.
 * </p>
 *
 * @param name          name to root the violation path at &mdash; the field name,
 *                      the bean property name for a getter, or the parameter name
 * @param declaringType type that declares the target, reported as the validation
 *                      result's root type
 * @param constraints   the target's declared constraints; every list is empty
 *                      when it declares none, in which case a validation pass
 *                      simply finds nothing
 */
record ElementModel(String name, Class<?> declaringType, BeanProperty constraints) {

	/**
	 * Derived models, keyed by target.
	 *
	 * <p>
	 * {@link Field}, {@link Method}, and {@link Parameter} all define
	 * {@code equals} and {@code hashCode} structurally, so they are sound map keys.
	 * Time-limited and softly reachable for the same reason as
	 * {@code BeanModel}'s cache: a model holds {@link Annotation} instances that
	 * reference their declaring class.
	 * </p>
	 */
	private static final Map<AnnotatedElement, ElementModel> CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));

	/**
	 * Gets the model for an annotation target, deriving it if it is not cached.
	 *
	 * @param element annotation target: a {@link Field}, a {@link Method}, or a
	 *                {@link Parameter}
	 * @return element model
	 * @throws IllegalArgumentException if {@code element} is any other kind of
	 *                                  annotation target
	 */
	static ElementModel of(AnnotatedElement element) {
		return CACHE.computeIfAbsent(element, ElementModel::describe);
	}

	/**
	 * Derives the model for an annotation target.
	 *
	 * <p>
	 * A {@link java.lang.reflect.Constructor} is refused along with
	 * {@link Package}, {@link Module}, and {@link AnnotatedType}: it has no value
	 * to validate, and validating an invocation is a separate Jakarta Validation
	 * feature that this module does not implement. A {@link Class} is refused here
	 * because {@code IuValidator} routes it to the bean walk before reaching this
	 * point.
	 * </p>
	 *
	 * @param element annotation target
	 * @return element model
	 * @throws IllegalArgumentException if {@code element} is not a supported target
	 */
	private static ElementModel describe(AnnotatedElement element) {
		if (element instanceof Field field)
			return describe(field.getName(), field.getDeclaringClass(), field, field.getAnnotatedType());

		if (element instanceof Method method)
			return describe(propertyName(method), method.getDeclaringClass(), method,
					method.getAnnotatedReturnType());

		if (element instanceof Parameter parameter)
			return describe(parameter.getName(), parameter.getDeclaringExecutable().getDeclaringClass(), parameter,
					parameter.getAnnotatedType());

		throw new IllegalArgumentException(element
				+ " is not a supported constraint target; expected a Field, Method, or Parameter, or a Class to validate as a bean");
	}

	/**
	 * Collects the constraints declared on a resolved target.
	 *
	 * @param name          violation path name
	 * @param declaringType declaring type
	 * @param element       annotation target
	 * @param annotated     annotated type of the value
	 * @return element model
	 */
	private static ElementModel describe(String name, Class<?> declaringType, AnnotatedElement element,
			AnnotatedType annotated) {
		final var collector = new ConstraintCollector(name);
		collector.element(declaringType, element, annotated);
		return new ElementModel(name, declaringType, collector.describe());
	}

	/**
	 * Gets the name a method contributes to a violation path.
	 *
	 * <p>
	 * A getter reports the bean property it reads rather than its own name, so that
	 * a violation on the same underlying property reads identically whether it was
	 * found by a bean walk or by validating the accessor directly. Anything that is
	 * not a getter reports its own name.
	 * </p>
	 *
	 * @param method annotation target
	 * @return bean property name, or the method name
	 */
	private static String propertyName(Method method) {
		final var name = method.getName();

		if (method.getParameterCount() == 0) {
			if (name.length() > 3 && name.startsWith("get"))
				return Introspector.decapitalize(name.substring(3));

			if (name.length() > 2 && name.startsWith("is") //
					&& (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class))
				return Introspector.decapitalize(name.substring(2));
		}

		return name;
	}

}
