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

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import edu.iu.type.IuParameterizedElement;
import edu.iu.type.IuReferenceKind;

/**
 * Implements the facade view of an {@link GenericDeclaration}.
 * 
 * @param <E> generic declaration type
 */
sealed class ParameterizedElementBase<E extends GenericDeclaration> extends AnnotatedElementBase<E>
		implements IuParameterizedElement permits TypeTemplate, ExecutableBase {

	/**
	 * Type parameter mapping, may be resolved during construction, then converted
	 * to unmodifiable at postInit phase.
	 */
	protected Map<String, TypeFacade<?>> typeParameters;

	/**
	 * Facade constructor.
	 * 
	 * @param annotatedElement parameterized element to provide a view of
	 * @param preInitHook      receives a handle to {@code this} after binding the
	 *                         annotated element but before initializing and members
	 */
	protected ParameterizedElementBase(E annotatedElement, Consumer<ParameterizedElementBase<E>> preInitHook) {
		super(annotatedElement, preInitHook == null ? null : s -> preInitHook.accept((ParameterizedElementBase<E>) s));

		this.typeParameters = new LinkedHashMap<>();
		var typeParameters = annotatedElement.getTypeParameters();
		if (typeParameters != null)
			postInit(() -> {
				for (var typeParameter : typeParameters) {
					var name = typeParameter.getName();

					IuReferenceKind kind;
					if (annotatedElement instanceof Class)
						kind = IuReferenceKind.TYPE_PARAM;
					else if (annotatedElement instanceof Method)
						kind = IuReferenceKind.METHOD_PARAM;
					else
						kind = IuReferenceKind.CONSTRUCTOR_PARAM;

					this.typeParameters.put(name,
							new TypeFacade<>(TypeFactory.resolveType(typeParameter), this, kind, name));
				}
			});
	}

	@Override
	public Map<String, TypeFacade<?>> typeParameters() {
		return typeParameters;
	}

}
