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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.type.IuParameterizedElement;
import edu.iu.type.IuReferenceKind;

/**
 * Implements the facade view of an {@link GenericDeclaration}.
 * 
 * @param <E> generic declaration type
 */
sealed class ParameterizedElementBase<E extends GenericDeclaration> extends AnnotatedElementBase<E>
		implements IuParameterizedElement permits TypeTemplate {

	private Map<String, TypeFacade<?>> typeParameters;

	/**
	 * Facade constructor.
	 * 
	 * @param annotatedElement parameterized element to provide a view of
	 */
	protected ParameterizedElementBase(E annotatedElement) {
		super(annotatedElement);
	}

	@Override
	public Map<String, TypeFacade<?>> typeParameters() {
		if (typeParameters == null) {
			Map<String, TypeFacade<?>> typeParameterMap = new LinkedHashMap<>();
			var typeParameters = annotatedElement.getTypeParameters();
			if (typeParameters != null)
				for (var typeParameter : typeParameters) {
					var name = typeParameter.getName();

					IuReferenceKind kind;
					if (annotatedElement instanceof Class)
						kind = IuReferenceKind.TYPE_PARAM;
					else if (annotatedElement instanceof Method)
						kind = IuReferenceKind.METHOD_PARAM;
					else
						kind = IuReferenceKind.CONSTRUCTOR_PARAM;

					typeParameterMap.put(name,
							new TypeFacade<>(TypeFactory.resolveType(typeParameter), this, kind, name));
				}

			this.typeParameters = Collections.unmodifiableMap(typeParameterMap);
		}
		return typeParameters;
	}

}
