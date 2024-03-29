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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.util.function.Consumer;

import edu.iu.type.IuDeclaredElement;
import edu.iu.type.IuReferenceKind;

/**
 * Implements the facade view of an {@link GenericDeclaration}.
 * 
 * @param <D> declaring type
 * @param <E> generic declaration type
 */
abstract sealed class DeclaredElementBase<D, E extends AnnotatedElement> extends AnnotatedElementBase<E>
		implements IuDeclaredElement<D> permits ExecutableBase, FieldFacade, TypeTemplate {

	/**
	 * Holds the generic type associated with the declared element.
	 * 
	 * <p>
	 * Initialized first (after {@code preInitHook}) accessible early.
	 * </p>
	 */
	final Type type;

	/**
	 * Hold an unguarded reference to the declaring {@link TypeFacade}, to
	 * facilitate subclasses registering {@link #postInit(Runnable)} hooks.
	 */
	final TypeFacade<?, D> declaringType;

	/**
	 * Default constructor, for use by all subclasses extended by {@link TypeTemplate}.
	 * 
	 * @param annotatedElement      parameterized element to provide a view of
	 * @param type                  generic type associated with the element
	 * @param declaringTypeTemplate type template for the type that declares this
	 *                              element
	 */
	DeclaredElementBase(E annotatedElement, Type type, TypeTemplate<?, D> declaringTypeTemplate) {
		super(annotatedElement);
		this.type = type;
		this.declaringType = new TypeFacade<>(declaringTypeTemplate, this, IuReferenceKind.DECLARING_TYPE);
	}

	/**
	 * Constructor for use by {@link TypeTemplate}.
	 * 
	 * @param annotatedElement      parameterized element to provide a view of
	 * @param preInitHook           used by {@link TypeFactory}
	 * @param type                  generic type associated with the element
	 * @param declaringTypeTemplate type template for the type that declares this
	 *                              element
	 */
	DeclaredElementBase(E annotatedElement, Consumer<TypeTemplate<?, ?>> preInitHook, Type type,
			TypeTemplate<?, D> declaringTypeTemplate) {
		super(annotatedElement, preInitHook);
		this.type = type;

		if (declaringTypeTemplate == null)
			this.declaringType = null;
		else
			this.declaringType = new TypeFacade<>(declaringTypeTemplate, this, IuReferenceKind.DECLARING_TYPE);
	}

	@Override
	public TypeFacade<?, D> declaringType() {
		if (declaringType != null)
			declaringType.checkSealed();
		return declaringType;
	}

}
