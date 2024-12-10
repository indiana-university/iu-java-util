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
import java.util.function.Consumer;
import java.util.function.Predicate;

import edu.iu.type.IuAnnotatedElement;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

/**
 * Implements the facade view of an {@link AnnotatedElement}.
 * 
 * @param <E> annotated element type
 */
sealed class AnnotatedElementBase<E extends AnnotatedElement> extends ElementBase implements IuAnnotatedElement
		permits DeclaredElementBase, ParameterFacade {

	/**
	 * Real annotated element viewed via this facade.
	 */
	final E annotatedElement;

	/**
	 * Default constructor, for use by all subclasses extend {@link TypeTemplate}.
	 * 
	 * @param annotatedElement real annotated element to provide a view of
	 */
	AnnotatedElementBase(E annotatedElement) {
		this.annotatedElement = annotatedElement;
	}

	/**
	 * Constructor for use by {@link TypeTemplate}.
	 * 
	 * @param annotatedElement real annotated element to provide a view of
	 * @param preInitHook      used by {@link TypeFactory}
	 */
	AnnotatedElementBase(E annotatedElement, Consumer<TypeTemplate<?, ?>> preInitHook) {
		super(preInitHook);
		this.annotatedElement = annotatedElement;
	}

	@Override
	public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
		checkSealed();
		return AnnotationBridge.isAnnotationPresent(annotationType, annotatedElement);
	}

	@Override
	public <A extends Annotation> A annotation(Class<A> annotationType) {
		checkSealed();
		return AnnotationBridge.getAnnotation(annotationType, annotatedElement);
	}

	@Override
	public Iterable<? extends Annotation> annotations() {
		checkSealed();
		return AnnotationBridge.getAnnotations(annotatedElement);
	}

	@Override
	public boolean permitted(Predicate<String> isUserInRole) {
		checkSealed();

		if (hasAnnotation(DenyAll.class))
			return false;

		if (hasAnnotation(PermitAll.class))
			return true;

		var rolesAllowed = annotation(RolesAllowed.class);
		if (rolesAllowed != null)
			for (var role : rolesAllowed.value())
				if (isUserInRole.test(role))
					return true;

		return false;
	}

}
