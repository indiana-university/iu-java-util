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

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;

/**
 * Accumulates the constraint declarations that apply to one value.
 *
 * <p>
 * Used two ways. {@link BeanModel} creates one per bean property and
 * {@link #merge merges} every declaration of that property found in the type
 * hierarchy, because Jakarta Validation constraints are additive. {@link
 * ElementModel} creates one per standalone annotation target and calls
 * {@link #element} once.
 * </p>
 */
final class ConstraintCollector {

	private final String name;
	private final Set<Method> accessors = new HashSet<>();
	private final List<ConstraintDeclaration> constraints = new ArrayList<>();
	private final List<ConstraintDeclaration> elementConstraints = new ArrayList<>();
	private final List<ConstraintDeclaration> keyConstraints = new ArrayList<>();

	private Method readMethod;
	private boolean cascade;
	private boolean cascadeElements;
	private boolean cascadeKeys;

	/**
	 * Constructor.
	 *
	 * @param name property or element name, used in the violation path and in the
	 *             message of a rejected custom constraint
	 */
	ConstraintCollector(String name) {
		this.name = name;
	}

	/**
	 * Merges one declaration of a bean property.
	 *
	 * @param declaringType type currently being introspected
	 * @param descriptor    property descriptor from {@code declaringType}
	 */
	void merge(Class<?> declaringType, PropertyDescriptor descriptor) {
		final var read = descriptor.getReadMethod();
		if (readMethod == null)
			readMethod = read;

		if (accessors.add(read)) {
			cascade |= collect(read.getDeclaringClass(), read.getAnnotations(), constraints);
			containerElements(read.getDeclaringClass(), read.getAnnotatedReturnType());
		}

		final var write = descriptor.getWriteMethod();
		if (write != null && accessors.add(write))
			cascade |= collect(write.getDeclaringClass(), write.getAnnotations(), constraints);

		field(declaringType);
	}

	/**
	 * Collects the constraints declared on a standalone annotation target.
	 *
	 * @param declaringType type that declares {@code element}
	 * @param element       annotation target
	 * @param annotated     annotated type of the value, for container element
	 *                      constraints
	 */
	void element(Class<?> declaringType, AnnotatedElement element, AnnotatedType annotated) {
		cascade |= collect(declaringType, element.getAnnotations(), constraints);
		containerElements(declaringType, annotated);
	}

	/**
	 * Merges the backing field of a bean property, if {@code declaringType} declares
	 * one.
	 *
	 * <p>
	 * Needs no guard against running twice: the hierarchy walk visits each type
	 * once, and one type reports a given property name once, so this runs at most
	 * once per declaring type.
	 * </p>
	 *
	 * @param declaringType type currently being introspected
	 */
	private void field(Class<?> declaringType) {
		final Field field;
		try {
			field = declaringType.getDeclaredField(name);
		} catch (NoSuchFieldException e) {
			return;
		}

		cascade |= collect(declaringType, field.getAnnotations(), constraints);
		containerElements(declaringType, field.getAnnotatedType());
	}

	/**
	 * Merges the constraints declared on the type arguments of a container type.
	 *
	 * <p>
	 * One type argument is an element, as for {@link Iterable} and
	 * {@link java.util.Optional}; two are a key and a value, as for {@link Map}.
	 * Any other arity is not a container this walk descends, so its arguments are
	 * ignored.
	 * </p>
	 *
	 * <p>
	 * Arrays are deliberately excluded. A constraint annotation that targets both
	 * {@code METHOD} and {@code TYPE_USE} written as {@code @NotEmpty String[]} is,
	 * per the Java Language Specification, simultaneously a declaration annotation
	 * on the accessor and a type annotation on the component. Reading the component
	 * would therefore apply every such constraint twice, once to the array and once
	 * to each element, and there is no syntax that distinguishes the two intents.
	 * Element constraints on a sequence belong on a type argument, as in
	 * {@code List<@NotBlank String>}. {@link jakarta.validation.Valid} is unaffected:
	 * it cascades to the elements of an array either way.
	 * </p>
	 *
	 * @param declaringType type that declared the container
	 * @param annotated     annotated type of the container
	 */
	private void containerElements(Class<?> declaringType, AnnotatedType annotated) {
		if (annotated instanceof AnnotatedParameterizedType parameterized) {
			final var arguments = parameterized.getAnnotatedActualTypeArguments();
			if (arguments.length == 1)
				cascadeElements |= collect(declaringType, arguments[0].getAnnotations(), elementConstraints);
			else if (arguments.length == 2) {
				cascadeKeys |= collect(declaringType, arguments[0].getAnnotations(), keyConstraints);
				cascadeElements |= collect(declaringType, arguments[1].getAnnotations(), elementConstraints);
			}
		}
	}

	/**
	 * Classifies a set of annotations, collecting those that are constraints
	 * applying to the default group.
	 *
	 * @param declaringType type that declared the annotations
	 * @param annotations   declared annotations
	 * @param into          list to collect constraint declarations into
	 * @return true if {@link Valid} was declared; else false
	 */
	private boolean collect(Class<?> declaringType, Annotation[] annotations, List<ConstraintDeclaration> into) {
		var valid = false;

		for (final var annotation : annotations) {
			final var repeated = Constraints.unwrapRepeatable(annotation);
			if (repeated == null)
				valid |= collectOne(declaringType, annotation, into);
			else
				for (final var element : repeated)
					valid |= collectOne(declaringType, element, into);
		}

		return valid;
	}

	/**
	 * Classifies one annotation.
	 *
	 * @param declaringType type that declared the annotation
	 * @param annotation    declared annotation
	 * @param into          list to collect a constraint declaration into
	 * @return true if {@code annotation} is {@link Valid}; else false
	 * @throws UnsupportedOperationException if {@code annotation} is a custom
	 *                                       constraint, which would otherwise
	 *                                       silently do nothing
	 */
	private boolean collectOne(Class<?> declaringType, Annotation annotation, List<ConstraintDeclaration> into) {
		final var annotationType = annotation.annotationType();

		if (annotationType == Valid.class)
			return true;

		if (Constraints.isUnsupportedConstraint(annotationType))
			throw new UnsupportedOperationException("Custom constraint " + annotationType.getName() + " on "
					+ declaringType.getName() + '.' + name
					+ " is not supported; only the built-in Jakarta Validation constraints are implemented");

		if (Constraints.isConstraint(annotationType) //
				&& Constraints.appliesToDefaultGroup(annotation))
			into.add(new ConstraintDeclaration(annotation, declaringType));

		return false;
	}

	/**
	 * Builds the accumulated declarations, whether or not anything was collected.
	 *
	 * @return constraint descriptor
	 */
	BeanProperty describe() {
		return new BeanProperty(name, readMethod, //
				List.copyOf(constraints), List.copyOf(elementConstraints), List.copyOf(keyConstraints), //
				cascade, cascadeElements, cascadeKeys);
	}

	/**
	 * Builds the accumulated declarations of a bean property.
	 *
	 * @return bean property; null if nothing about this property needs validating,
	 *         in which case the walk should not read it at all
	 */
	BeanProperty build() {
		if (constraints.isEmpty() && elementConstraints.isEmpty() && keyConstraints.isEmpty() //
				&& !cascade && !cascadeElements && !cascadeKeys)
			return null;

		return describe();
	}

}
