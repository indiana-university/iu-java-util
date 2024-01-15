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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;

import edu.iu.IuException;

/**
 * Bridge utility for adapting annotations defined in one module to a different
 * module with an isolated and potentially different version of the same
 * annotation library.
 */
final class AnnotationBridge {

	/**
	 * Gets an equivalent, potentially remote, class.
	 * 
	 * @param annotatedElement {@link AnnotatedElement} context target
	 * @param localClass       local class
	 * @return remote class; <em>may</em> be same as local class
	 * @throws ClassNotFoundException if an equivalent remote type cannot be found
	 */
	static Class<?> getPotentiallyRemoteClass(AnnotatedElement annotatedElement, Class<?> localClass)
			throws ClassNotFoundException {
		try {
			return TypeUtils.callWithContext(annotatedElement,
					() -> BackwardsCompatibility.getCompatibleClass(localClass));
		} catch (Throwable e) {
			throw IuException.checked(e, ClassNotFoundException.class);
		}
	}

	/**
	 * Determines if an equivalent annotation is present on a potentially remote
	 * target element.
	 * 
	 * @param annotationType   local annotation type
	 * @param annotatedElement {@link AnnotatedElement} context target
	 * @return true if an equivalent annotation is present on the potentially remote
	 *         target element; else false
	 */
	static boolean isAnnotationPresent(Class<? extends Annotation> annotationType, AnnotatedElement annotatedElement) {
		if (annotatedElement.isAnnotationPresent(annotationType))
			return true;

		Class<?> legacyAnnotationType;
		try {
			legacyAnnotationType = getPotentiallyRemoteClass(annotatedElement, annotationType);
		} catch (ClassNotFoundException e) {
			return false;
		}

		if (!Annotation.class.isAssignableFrom(legacyAnnotationType))
			return false;

		return annotatedElement.isAnnotationPresent(legacyAnnotationType.asSubclass(Annotation.class));
	}

	/**
	 * Gets an equivalent annotation if present on a potentially remote target.
	 * 
	 * @param <A>              local annotation type
	 * @param annotationType   local annotation type
	 * @param annotatedElement {@link AnnotatedElement} context target
	 * @return equivalent annotation; else false
	 */
	static <A extends Annotation> A getAnnotation(Class<A> annotationType, AnnotatedElement annotatedElement) {
		var annotation = annotatedElement.getAnnotation(annotationType);
		if (annotation != null)
			return annotation;

		Class<?> legacyAnnotationType;
		try {
			legacyAnnotationType = getPotentiallyRemoteClass(annotatedElement, annotationType);
		} catch (ClassNotFoundException e) {
			return null;
		}

		if (!Annotation.class.isAssignableFrom(legacyAnnotationType))
			return null;

		var legacyAnnotation = annotatedElement.getAnnotation(legacyAnnotationType.asSubclass(Annotation.class));
		if (legacyAnnotation == null)
			return null;

		return annotationType.cast(Proxy.newProxyInstance(annotationType.getClassLoader(),
				new Class<?>[] { annotationType }, new PotentiallyRemoteAnnotationHandler(annotationType, legacyAnnotation)));
	}

	/**
	 * Gets all annotations for an potentially remote element.
	 * 
	 * @param annotatedElement {@link AnnotatedElement} context target
	 * @return local equivalents to all adaptable remote annotations
	 */
	static Iterable<? extends Annotation> getAnnotations(AnnotatedElement annotatedElement) {
		var annotations = annotatedElement.getAnnotations();
		if (annotations.length == 0)
			return Collections.emptySet();

		Queue<Annotation> localAnnotations = new ArrayDeque<>();
		for (final var annotation : annotations) {
			final var annotationType = annotation.annotationType();
			Class<?> localClass;
			try {
				localClass = BackwardsCompatibility.getCompatibleClass(annotationType);
			} catch (ClassNotFoundException e) {
				continue;
			}

			if (localClass.isInstance(annotation))
				localAnnotations.offer(annotation);
			else if (Annotation.class.isAssignableFrom(localClass)) {
				var localAnnotationType = localClass.asSubclass(Annotation.class);
				localAnnotations.offer((Annotation) Proxy.newProxyInstance(localClass.getClassLoader(),
						new Class<?>[] { localClass }, new PotentiallyRemoteAnnotationHandler(localAnnotationType, annotation)));
			}
		}

		return localAnnotations;
	}

	private AnnotationBridge() {
	}

}
