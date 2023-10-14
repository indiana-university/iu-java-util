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
import java.util.ArrayDeque;
import java.util.Queue;
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
sealed class AnnotatedElementBase<E extends AnnotatedElement> implements IuAnnotatedElement
		permits ParameterizedElementBase, FieldFacade, ParameterFacade {

	/**
	 * Real annotated element viewed via this facade.
	 */
	protected final E annotatedElement;

	private Queue<Runnable> postInit;

	/**
	 * Facade constructor.
	 * 
	 * @param annotatedElement real annotated element to provide a view of
	 * @param preInitHook      receives a handle to {@code this} after binding the
	 *                         annotated element but before initializing and
	 *                         members. {@code preInitHook} is used by TypeFactory
	 *                         to bind template instances to its weak singleton
	 *                         cache prior to initializing related members, so those
	 *                         members responsible for building references can
	 *                         safely use TypeFactory.resolveType() within their own
	 *                         constructors. When non-null,
	 *                         {@link #finishPostInit()} should be invoked at the
	 *                         end of the last constructor in the chain.
	 * @see #finishPostInit()
	 */
	AnnotatedElementBase(E annotatedElement, Consumer<AnnotatedElementBase<E>> preInitHook) {
		this.annotatedElement = annotatedElement;

		if (preInitHook != null) {
			postInit = new ArrayDeque<>();
			preInitHook.accept(this);
		}
	}

	/**
	 * <em>May</em> be invoked within a base constructor to defer part of
	 * initialization until after the facade instance is fully formed.
	 * 
	 * @param postInit initialization segment to run after all facade elements are
	 *                 populated
	 */
	void postInit(Runnable postInit) {
		if (this.postInit == null)
			postInit.run();
		else
			this.postInit.offer(postInit);
	}

	/**
	 * <em>Should</em> be called at the end of any constructor that passes a
	 * non-null {@code preInitHook} to
	 * {@link #AnnotatedElementBase(AnnotatedElement, Consumer)}.
	 */
	void finishPostInit() {
		var postInit = this.postInit;
		this.postInit = null;
		if (postInit != null)
			while (!postInit.isEmpty())
				postInit.poll().run();
	}

	@Override
	public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
		return AnnotationBridge.isAnnotationPresent(annotationType, annotatedElement);
	}

	@Override
	public <A extends Annotation> A annotation(Class<A> annotationType) {
		return AnnotationBridge.getAnnotation(annotationType, annotatedElement);
	}

	@Override
	public Iterable<? extends Annotation> annotations() {
		return AnnotationBridge.getAnnotations(annotatedElement);
	}

	@Override
	public boolean permitted(Predicate<String> isUserInRole) {
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
