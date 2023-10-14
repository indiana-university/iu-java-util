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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.iu.type.IuExecutable;
import edu.iu.type.IuExecutableKey;
import edu.iu.type.IuReferenceKind;

/**
 * Facade implementation for an {@link Executable}.
 * 
 * @param <D> declaring type
 * @param <R> result type: constructor declaring type or method return type
 * @param <E> executable type: {@link Method} or {@link Constructor}
 */
abstract sealed class ExecutableBase<D, R, E extends Executable> extends ParameterizedElementBase<E>
		implements IuExecutable<R> permits ConstructorFacade, MethodFacade {

	private final IuExecutableKey key;
	private final TypeFacade<D> declaringType;
	private final List<ParameterFacade<?>> parameters;

	/**
	 * Facade constructor.
	 * 
	 * @param executable            method or constructor
	 * @param declaringTypeTemplate fully realized {@link TypeTemplate} for a
	 *                              generic type whose erasure declared the
	 *                              executable
	 */
	ExecutableBase(E executable, TypeTemplate<D> declaringTypeTemplate) {
		super(executable, null);

		final String name;
		if (executable instanceof Method)
			name = ((Method) executable).getName();
		else
			name = null;
		this.key = IuExecutableKey.of(name, executable.getParameterTypes());

		this.declaringType = new TypeFacade<>(declaringTypeTemplate, this, IuReferenceKind.DECLARING_TYPE);

		List<ParameterFacade<?>> parameters = new ArrayList<>();
		this.parameters = Collections.unmodifiableList(parameters);

		for (var parameter : executable.getParameters())
			parameters.add(new ParameterFacade<>(parameter, parameters.size(), this,
					TypeFactory.resolveType(parameter.getParameterizedType())));

		declaringTypeTemplate.postInit(() -> {
			typeParameters = TypeUtils.sealTypeParameters(typeParameters, declaringTypeTemplate.typeParameters);
			for (var parameter : parameters)
				parameter.sealTypeParameters(typeParameters);
		});
	}

	@Override
	public IuExecutableKey getKey() {
		return key;
	}

	@Override
	public TypeFacade<D> declaringType() {
		return declaringType;
	}

	@Override
	public List<ParameterFacade<?>> parameters() {
		return parameters;
	}

}
