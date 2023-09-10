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
import java.lang.reflect.AnnotatedElement;
import java.util.Map;

import iu.type.BackwardsCompatibilityHelper;

/**
 * Encompasses generic type resolution behavior equivalent to
 * {@link AnnotatedElement}.
 * 
 * <p>
 * This interface supports backwards compatibility for annotations on elements
 * defined using legacy annotation types.
 * </p>
 * 
 * @see BackwardsCompatibilityHelper
 */
public interface IuAnnotatedElement {

	/**
	 * Determines if an annotation is present.
	 * 
	 * @param annocationType annotation type
	 * @return true if the annotation is present, else null
	 * @see BackwardsCompatibilityHelper#isAnnotationPresent(Class,
	 *      AnnotatedElement)
	 */
	boolean hasAnnotation(Class<? extends Annotation> annocationType);

	/**
	 * Gets a type annotation.
	 * 
	 * @param <A>            annotation type
	 * @param annocationType annotation type
	 * @return annotation if present, else null
	 * @see BackwardsCompatibilityHelper#getAnnotation(Class, AnnotatedElement)
	 */
	<A extends Annotation> A annotation(Class<A> annocationType);

	/**
	 * Gets the annotations defined on the type.
	 * 
	 * @return annotations
	 * @see BackwardsCompatibilityHelper#getAnnotations(AnnotatedElement)
	 */
	Map<Class<? extends Annotation>, ? extends Annotation> annotations();

}
