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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.iu.type.IuExecutable;
import edu.iu.type.IuExecutableKey;

/**
 * Facade implementation for an {@link Executable}.
 * 
 * @param <D> declaring type
 * @param <R> result type: constructor declaring type or method return type
 * @param <E> executable type: {@link Method} or {@link Constructor}
 */
abstract sealed class ExecutableBase<D, R, E extends Executable> extends DeclaredElementBase<D, E>
		implements IuExecutable<D, R>, ParameterizedFacade permits ConstructorFacade, MethodFacade {

	private final ParameterizedElement parameterizedElement = new ParameterizedElement();
	private final IuExecutableKey key;
	private final List<ParameterFacade<?>> parameters;

	/**
	 * Facade constructor.
	 * 
	 * @param executable            method or constructor
	 * @param type                  generic type associated with the element
	 * @param declaringTypeTemplate fully realized {@link TypeTemplate} for a
	 *                              generic type whose erasure declared the
	 *                              executable
	 */
	ExecutableBase(E executable, Type type, TypeTemplate<?, D> declaringTypeTemplate) {
		super(executable, type, declaringTypeTemplate);

		final String name;
		if (executable instanceof Method)
			name = ((Method) executable).getName();
		else
			name = null;
		this.key = IuExecutableKey.of(name, executable.getParameterTypes());

		List<ParameterFacade<?>> parameters = new ArrayList<>();
		this.parameters = Collections.unmodifiableList(parameters);

		final var allParameters = executable.getParameters();
		final var parameterCount = allParameters.length;

		final var genericParameterTypes = executable.getGenericParameterTypes();
		final var genericParameterCount = genericParameterTypes.length;
		final var nonGenericParameterCount = parameterCount - genericParameterCount;

		for (var parameterIndex = 0; parameterIndex < parameterCount; parameterIndex++) {
			final var parameter = allParameters[parameterIndex];
			final var genericParameterIndex = parameterIndex - nonGenericParameterCount;

			TypeTemplate<?, ?> paramTypeTemplate;
			if (genericParameterIndex >= 0)
				paramTypeTemplate = TypeFactory.resolveType(genericParameterTypes[genericParameterIndex]);
			else
				paramTypeTemplate = TypeFactory.resolveType(parameter.getParameterizedType());

			parameters.add(new ParameterFacade<>(parameter, parameters.size(), this, paramTypeTemplate));
		}

		declaringTypeTemplate.postInit(() -> parameterizedElement.apply(declaringTypeTemplate.typeParameters()));
	}

	@Override
	void seal() {
		parameterizedElement.seal(annotatedElement, this);
		super.seal();
	}

	@Override
	public IuExecutableKey getKey() {
		return key;
	}

	@Override
	public Map<String, TypeFacade<?, ?>> typeParameters() {
		checkSealed();
		return parameterizedElement.typeParameters();
	}

	@Override
	public TypeFacade<?, ?> typeParameter(String name) {
		checkSealed();
		return parameterizedElement.typeParameter(name);
	}

	@Override
	public List<ParameterFacade<?>> parameters() {
		return parameters;
	}

	@Override
	public String toString() {
		if (declaringType == null)
			return "<uninitialized>";

		var sb = new StringBuilder();
		sb.append(TypeUtils.printType(declaringType.deref()));
		sb.append('(');
		var l = sb.length();
		for (var p : parameters) {
			if (l < sb.length())
				sb.append(',');
			sb.append(TypeUtils.printType(p.type().deref()));
		}
		sb.append(')');
		return sb.toString();
	}

}
