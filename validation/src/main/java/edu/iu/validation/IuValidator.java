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
package edu.iu.validation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;

import iu.validation.ValidationWalk;

/**
 * Validates a business object against the
 * <a href="https://jakarta.ee/specifications/bean-validation/3.1/">Jakarta
 * Validation</a> constraints declared on its bean properties.
 * 
 * <p>
 * Every constraint on every reachable property is evaluated; nothing
 * short-circuits. {@link #validate(Class, Object)} returns the complete
 * {@link IuValidationResult} so a caller can log or transform it;
 * {@link #require(Class, Object)} throws instead, for a service entry point
 * that should reject bad input outright.
 * </p>
 * 
 * <pre>
 * IuValidator.require(EnrlHeader.class, header);
 * </pre>
 * 
 * <p>
 * The object graph is traversed breadth-first from the root, so an arbitrarily
 * deep graph costs heap rather than stack. A bean is validated at most once per
 * pass, identified by instance identity: this terminates cycles, and means a
 * single object reachable by two paths is reported under whichever path is
 * reached first.
 * </p>
 * 
 * @see IuValidationResult
 */
public final class IuValidator {

	/**
	 * Validates a business object against the constraints declared by a specific
	 * type.
	 * 
	 * <p>
	 * Constraints are collected from the object's runtime class and its entire
	 * supertype hierarchy, which includes {@code type}. Passing {@code type}
	 * therefore does not narrow what is validated; it names the type reported by
	 * {@link IuValidationResult#rootType()}.
	 * </p>
	 * 
	 * @param <T>   business type
	 * @param type  business type to report as the root of the pass
	 * @param value instance of {@code T}; a null value has no properties to
	 *              validate and is reported valid
	 * @return validation result; {@link IuValidationResult#isValid() valid} when no
	 *         constraint failed
	 * @throws NullPointerException if {@code type} is null
	 */
	public static <T> IuValidationResult validate(Class<T> type, T value) {
		return ValidationWalk.walk(type, value);
	}

	/**
	 * Validates a business object against the constraints declared by its runtime
	 * class.
	 * 
	 * @param value business object
	 * @return validation result; {@link IuValidationResult#isValid() valid} when no
	 *         constraint failed, and when {@code value} is null
	 */
	public static IuValidationResult validate(Object value) {
		return ValidationWalk.walk(value == null ? Object.class : value.getClass(), value);
	}

	/**
	 * Validates a business object and throws if any constraint failed.
	 * 
	 * @param <T>   business type
	 * @param type  business type to report as the root of the pass
	 * @param value instance of {@code T}; a null value has no properties to
	 *              validate and is reported valid
	 * @throws IuValidationException if any constraint failed
	 * @throws NullPointerException  if {@code type} is null
	 */
	public static <T> void require(Class<T> type, T value) throws IuValidationException {
		validate(type, value).checkValid();
	}

	/**
	 * Validates a business object against the constraints declared by its runtime
	 * class, and throws if any constraint failed.
	 * 
	 * @param value business object
	 * @throws IuValidationException if any constraint failed
	 */
	public static void require(Object value) throws IuValidationException {
		validate(value).checkValid();
	}

	/**
	 * Validates one value against the constraints declared on an annotation target.
	 *
	 * <p>
	 * For validating a value the caller already holds &mdash; a field, the return
	 * value of a getter, a method argument &mdash; where there is no bean to walk
	 * from. The target supplies the rules; the value is passed in.
	 * </p>
	 *
	 * <pre>
	 * IuValidator.require(parameter, argument);
	 * </pre>
	 *
	 * <p>
	 * A {@link Class} target is validated as a bean, exactly as
	 * {@link #validate(Class, Object)} does. Note that {@link Class} is itself an
	 * {@link AnnotatedElement}, so for a statically typed call such as
	 * {@code validate(Foo.class, foo)} the compiler selects
	 * {@link #validate(Class, Object)} as the more specific overload; where it
	 * cannot, it selects this method, which delegates. Both routes therefore
	 * behave identically.
	 * </p>
	 *
	 * <p>
	 * Violation paths are rooted at the target's name: a {@link Field} reports its
	 * own name, a {@link Method} reports the bean property it reads if it is a
	 * getter and its own name otherwise, and a {@link Parameter} reports
	 * {@link Parameter#getName()}. That last is {@code arg0} unless the declaring
	 * code was compiled with {@code -parameters}, so prefer
	 * {@link #validate(AnnotatedElement, String, Object)} wherever a better name is
	 * available &mdash; from a {@code jakarta.ws.rs.QueryParam}, for instance.
	 * </p>
	 *
	 * @param element annotation target: a {@link Class}, {@link Field},
	 *                {@link Method}, or {@link Parameter}
	 * @param value   value declared by {@code element}; may be null
	 * @return validation result; {@link IuValidationResult#isValid() valid} when no
	 *         constraint failed, and when {@code element} declares none
	 * @throws NullPointerException     if {@code element} is null
	 * @throws IllegalArgumentException if {@code element} is any other kind of
	 *                                  annotation target, such as a
	 *                                  {@link java.lang.reflect.Constructor},
	 *                                  {@link Package}, {@link Module}, or
	 *                                  {@link java.lang.reflect.AnnotatedType}
	 */
	public static IuValidationResult validate(AnnotatedElement element, Object value) {
		Objects.requireNonNull(element, "element");

		if (element instanceof Class<?> type)
			return ValidationWalk.walk(type, value);

		return ValidationWalk.walkElement(element, value);
	}

	/**
	 * Validates one value against the constraints declared on an annotation target,
	 * naming it explicitly.
	 *
	 * <p>
	 * Use this wherever the caller knows a better name than reflection does. A REST
	 * dispatcher reading {@code @QueryParam("message")} can report {@code message}
	 * rather than {@code arg0}, and a cascading target then reports
	 * {@code message.street}.
	 * </p>
	 *
	 * <p>
	 * For a {@link Class} target, {@code name} roots every path of the bean walk,
	 * so a request entity validated as {@code "body"} reports {@code body.emplid}.
	 * </p>
	 *
	 * @param element annotation target: a {@link Class}, {@link Field},
	 *                {@link Method}, or {@link Parameter}
	 * @param name    name to root the violation path at
	 * @param value   value declared by {@code element}; may be null
	 * @return validation result; {@link IuValidationResult#isValid() valid} when no
	 *         constraint failed, and when {@code element} declares none
	 * @throws NullPointerException     if {@code element} or {@code name} is null
	 * @throws IllegalArgumentException if {@code element} is not a supported
	 *                                  annotation target
	 */
	public static IuValidationResult validate(AnnotatedElement element, String name, Object value) {
		Objects.requireNonNull(element, "element");
		Objects.requireNonNull(name, "name");

		if (element instanceof Class<?> type)
			return ValidationWalk.walk(type, name, value);

		return ValidationWalk.walkElement(element, name, value);
	}

	/**
	 * Validates one value against the constraints declared on an annotation target,
	 * and throws if any constraint failed.
	 *
	 * @param element annotation target: a {@link Class}, {@link Field},
	 *                {@link Method}, or {@link Parameter}
	 * @param value   value declared by {@code element}; may be null
	 * @throws IuValidationException    if any constraint failed
	 * @throws NullPointerException     if {@code element} is null
	 * @throws IllegalArgumentException if {@code element} is not a supported
	 *                                  annotation target
	 */
	public static void require(AnnotatedElement element, Object value) throws IuValidationException {
		validate(element, value).checkValid();
	}

	/**
	 * Validates one value against the constraints declared on an annotation target,
	 * naming it explicitly, and throws if any constraint failed.
	 *
	 * @param element annotation target: a {@link Class}, {@link Field},
	 *                {@link Method}, or {@link Parameter}
	 * @param name    name to root the violation path at
	 * @param value   value declared by {@code element}; may be null
	 * @throws IuValidationException    if any constraint failed
	 * @throws NullPointerException     if {@code element} or {@code name} is null
	 * @throws IllegalArgumentException if {@code element} is not a supported
	 *                                  annotation target
	 */
	public static void require(AnnotatedElement element, String name, Object value) throws IuValidationException {
		validate(element, name, value).checkValid();
	}

	private IuValidator() {
	}

}
