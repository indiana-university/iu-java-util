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

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.type.IuParameterizedElement;
import edu.iu.type.IuReferenceKind;

/**
 * Implements the facade view of an {@link IuParameterizedElement} as a
 * delegating mix-in.
 */
final class ParameterizedElement implements ParameterizedFacade {

	private Map<String, TypeFacade<?, ?>> typeArguments = new LinkedHashMap<>();
	private Map<String, TypeFacade<?, ?>> typeParameters;

	/**
	 * Facade constructor.
	 */
	ParameterizedElement() {
	}

	/**
	 * Applies type arguments.
	 * 
	 * <p>
	 * This is typically done after sealing original type parameters to apply the
	 * resulting parameters as arguments to the next reference.
	 * </p>
	 * 
	 * @param typeArguments Type arguments to apply. Expects arguments to be applied
	 *                      in highest-order last; type arguments matching the name
	 *                      of a previously applied argument will be overridden.
	 */
	void apply(Map<String, TypeFacade<?, ?>> typeArguments) {
		if (this.typeArguments == null)
			throw new IllegalStateException("sealed");

		this.typeArguments.putAll(typeArguments);
	}

	/**
	 * Applies a type argument from a parameterized type.
	 * 
	 * @param referrer           element referring to the parameterized type,
	 *                           typically its {@link TypeTemplate}.
	 * @param typeVariable       type variable declared by
	 *                           {@link ParameterizedType#getRawType() raw type
	 *                           erasure}.
	 * @param actualTypeArgument from
	 *                           {@link ParameterizedType#getActualTypeArguments()}
	 *                           relative to position of {@code typeVariable}.
	 */
	void apply(AnnotatedElementBase<?> referrer, TypeVariable<?> typeVariable, Type actualTypeArgument) {
		if (this.typeArguments == null)
			throw new IllegalStateException("sealed");

		final var name = typeVariable.getName();
		final var typeArgumentTemplate = TypeFactory.resolveType(actualTypeArgument);
		this.typeArguments.put(name,
				new TypeFacade<>(typeArgumentTemplate, referrer, IuReferenceKind.TYPE_PARAM, name));
	}

	/**
	 * Seals type parameters from incoming arguments based on a generic declaration.
	 * 
	 * @param genericDeclaration source element capable of declaring type parameters
	 * @param referrer           referring element to use with any type parameters
	 *                           generated from {@code genericDeclaration}
	 */
	void seal(GenericDeclaration genericDeclaration, AnnotatedElementBase<?> referrer) {
		if (typeParameters != null)
			throw new IllegalStateException("already sealed");

		final var typeArguments = this.typeArguments;
		this.typeArguments = null;

		final var typeVariables = genericDeclaration.getTypeParameters();
		if (typeVariables.length == 0) {
			this.typeParameters = Collections.emptyMap();
			return;
		}

		final IuReferenceKind kind;
		if (genericDeclaration instanceof Class)
			kind = IuReferenceKind.TYPE_PARAM;
		else if (genericDeclaration instanceof Method)
			kind = IuReferenceKind.METHOD_PARAM;
		else
			kind = IuReferenceKind.CONSTRUCTOR_PARAM;

		// Step through all incoming type parameters
		final Map<String, TypeFacade<?, ?>> typeParameters = new LinkedHashMap<>();
		for (var typeVariable : typeVariables) {
			final var typeVariableName = typeVariable.getName();

			var typeArgument = typeArguments.get(typeVariableName);
			if (typeArgument != null) {
				while (typeArgument.deref() instanceof TypeVariable<?> argVariable) {
					final var derefArgVar = typeArguments.get(argVariable.getName());
					if (derefArgVar == null // if unresolved or
							|| derefArgVar == typeArgument) // self-reference
						break; // then keep variable and defer to bounds

					else // push dereferenced argument and check again
						typeArgument = derefArgVar;
				}
				typeParameters.put(typeVariableName, typeArgument);
			} else
				typeParameters.put(typeVariableName,
						new TypeFacade<>(TypeFactory.resolveType(typeVariable), referrer, kind, typeVariableName));
		}
		this.typeParameters = typeParameters;
	}

	@Override
	public Map<String, TypeFacade<?, ?>> typeParameters() {
		if (typeParameters == null)
			throw new IllegalStateException("not sealed");
		return typeParameters;
	}

	@Override
	public TypeFacade<?, ?> typeParameter(String name) {
		return typeParameters().get(name);
	}

}
