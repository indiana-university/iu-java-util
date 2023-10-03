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

import static iu.type.BackwardsCompatibility.getNonLegacyClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Queue;

import edu.iu.IuException;

final class AnnotationBridge {

	private static final Annotation[] A0 = new Annotation[0];

	static Class<?> getLegacyClass(AnnotatedElement annotatedElement, Class<?> nonLegacyClass)
			throws ClassNotFoundException {
		try {
			return TypeUtils.callWithContext(annotatedElement,
					() -> BackwardsCompatibility.getLegacyClass(nonLegacyClass));
		} catch (Throwable e) {
			throw IuException.checked(e, ClassNotFoundException.class);
		}
	}

	static boolean isAnnotationPresent(Class<? extends Annotation> annotationType, AnnotatedElement annotatedElement) {
		if (annotatedElement.isAnnotationPresent(annotationType))
			return true;

		Class<?> legacyAnnotationType;
		try {
			legacyAnnotationType = getLegacyClass(annotatedElement, annotationType);
		} catch (ClassNotFoundException e) {
			return false;
		}

		if (!Annotation.class.isAssignableFrom(legacyAnnotationType))
			return false;

		return annotatedElement.isAnnotationPresent(legacyAnnotationType.asSubclass(Annotation.class));
	}

	static <A extends Annotation> A getAnnotation(Class<A> annotationType, AnnotatedElement annotatedElement) {
		var annotation = annotatedElement.getAnnotation(annotationType);
		if (annotation != null)
			return annotation;

		Class<?> legacyAnnotationType;
		try {
			legacyAnnotationType = getLegacyClass(annotatedElement, annotationType);
		} catch (ClassNotFoundException e) {
			return null;
		}

		if (!Annotation.class.isAssignableFrom(legacyAnnotationType))
			return null;

		var legacyAnnotation = annotatedElement.getAnnotation(legacyAnnotationType.asSubclass(Annotation.class));
		if (legacyAnnotation == null)
			return null;

		return annotationType.cast(Proxy.newProxyInstance(annotationType.getClassLoader(),
				new Class<?>[] { annotationType }, new LegacyAnnotationHandler(annotationType, legacyAnnotation)));
	}

	static Annotation[] getAnnotations(AnnotatedElement annotatedElement) {
		var annotations = annotatedElement.getAnnotations();
		if (annotations.length == 0)
			return A0;

		Queue<Annotation> upgradedAnnotations = new ArrayDeque<>();
		for (var annotation : annotations) {
			var annotationType = annotation.annotationType();
			Class<?> nonLegacyType;
			try {
				nonLegacyType = getNonLegacyClass(annotationType);
			} catch (ClassNotFoundException e) {
				continue;
			}

			if (Annotation.class.isAssignableFrom(nonLegacyType)) {
				var nonLegacyAnnotationType = nonLegacyType.asSubclass(Annotation.class);
				upgradedAnnotations.offer((Annotation) Proxy.newProxyInstance(nonLegacyType.getClassLoader(),
						new Class<?>[] { nonLegacyType },
						new LegacyAnnotationHandler(nonLegacyAnnotationType, annotation)));
			}
		}

		return upgradedAnnotations.toArray(A0);
	}

	private AnnotationBridge() {
	}

}
