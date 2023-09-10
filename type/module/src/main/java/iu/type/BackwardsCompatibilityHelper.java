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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.type.DefaultInterceptor;

/**
 * Provides runtime support for utilities and dependencies that have moved to a
 * different package since a prior version, but without substantial change in
 * functionality.
 * 
 * <p>
 * This utility addresses types that were present in IUJEE 6, but are not part
 * of IUJU 7.
 * </p>
 * <ul>
 * <li>{@code jakarta.*}, moved from {@code javax.*}</li>
 * <li>{@link DefaultInterceptor}, moved from {@code edu.iu.spi}</li>
 * </ul>
 * 
 */
public final class BackwardsCompatibilityHelper {

	private BackwardsCompatibilityHelper() {
	}

	/**
	 * Returns a legacy class that may have been superseded by a functionally
	 * equivalent class with a different name.
	 * 
	 * @param maySupersedeLegacyType non-legacy type
	 * @return legacy type if visible to the same class loader as this utility, else
	 *         null
	 */
	public static Class<?> getLegacyType(Class<?> maySupersedeLegacyType) {
		var typeName = maySupersedeLegacyType.getName();

		String legacyTypeName;
		if (typeName.startsWith("jakarta."))
			legacyTypeName = "javax" + typeName.substring(7);
		else if (maySupersedeLegacyType.equals(DefaultInterceptor.class))
			legacyTypeName = "edu.iu.spi.DefaultInterceptor";
		else
			return null;

		try {
			return Class.forName(legacyTypeName);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	/**
	 * Returns the type that superseded a legacy type, if applicable.
	 * 
	 * @param maybeLegacyType type to check
	 * @return superseding type if applicable, else null
	 */
	public static Class<?> getNonLegacyType(Class<?> maybeLegacyType) {
		var typeName = maybeLegacyType.getName();

		String nonLegacyTypeName;
		if (typeName.startsWith("javax."))
			nonLegacyTypeName = "jakarta" + typeName.substring(5);
		else if (typeName.equals("edu.iu.spi.DefaultInterceptor"))
			return DefaultInterceptor.class;
		else
			return null;

		try {
			return Class.forName(nonLegacyTypeName);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	/**
	 * Determines if an annotation is present.
	 * 
	 * @param annotationType   annotation type
	 * @param annotatedElement annotated element
	 * @return true if the annotation is present, or if an equivalent prior version
	 *         of the annotation is present; else false
	 */
	public static boolean isAnnotationPresent(Class<? extends Annotation> annotationType,
			AnnotatedElement annotatedElement) {
		if (annotatedElement.isAnnotationPresent(annotationType))
			return true;

		var legacyAnnotationType = getLegacyType(annotationType);
		if (legacyAnnotationType == null || !Annotation.class.isAssignableFrom(legacyAnnotationType))
			return false;

		return annotatedElement.isAnnotationPresent(legacyAnnotationType.asSubclass(Annotation.class));
	}

	/**
	 * Gets an annotation defined on an element, or a functionally equivalent
	 * instance of a legacy annotation that was superseded by the annotation type.
	 * 
	 * @param <A>              annotation type
	 * @param annotationType   annotation type
	 * @param annotatedElement annotated element
	 * @return annotation, or functional equivalent; null if neither the annotation
	 *         type nor its legacy equivalent is defined on the element.
	 */
	public static <A extends Annotation> A getAnnotation(Class<A> annotationType, AnnotatedElement annotatedElement) {
		var annotation = annotatedElement.getAnnotation(annotationType);
		if (annotation != null)
			return annotation;

		var legacyAnnotationType = getLegacyType(annotationType);
		if (legacyAnnotationType == null || !Annotation.class.isAssignableFrom(legacyAnnotationType))
			return null;

		var legacyAnnotation = annotatedElement.getAnnotation(legacyAnnotationType.asSubclass(Annotation.class));
		if (legacyAnnotation == null)
			return null;

		return annotationType.cast(Proxy.newProxyInstance(annotationType.getClassLoader(),
				new Class<?>[] { annotationType }, new LegacyAnnotationHandler(annotationType, legacyAnnotation)));
	}

	/**
	 * Gets the annotations defined on the element, converting legacy annotations to
	 * non-legacy.
	 * 
	 * @param annotatedElement annotated element
	 * @return annotations
	 */
	public static Map<Class<? extends Annotation>, Annotation> getAnnotations(AnnotatedElement annotatedElement) {
		var annotations = annotatedElement.getAnnotations();
		if (annotations.length == 0)
			return Collections.emptyMap();

		Map<Class<? extends Annotation>, Annotation> upgradedAnnotations = new LinkedHashMap<>();
		for (var annotation : annotations) {
			var annotationType = annotation.annotationType();
			var nonLegacyType = getNonLegacyType(annotationType);
			if (nonLegacyType != null && Annotation.class.isAssignableFrom(nonLegacyType)) {
				var nonLegacyAnnotationType = nonLegacyType.asSubclass(Annotation.class);
				upgradedAnnotations.put(nonLegacyAnnotationType,
						(Annotation) Proxy.newProxyInstance(nonLegacyType.getClassLoader(),
								new Class<?>[] { nonLegacyType },
								new LegacyAnnotationHandler(nonLegacyAnnotationType, annotation)));
			} else
				upgradedAnnotations.put(annotationType, annotation);
		}

		return Collections.unmodifiableMap(upgradedAnnotations);
	}

}
