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

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Map;

import edu.iu.type.IuParameter;
import edu.iu.type.IuReferenceKind;

/**
 * Facade implementation of {@link IuParameter}.
 * 
 * @param <T> parameter type
 */
final class ParameterFacade<T> extends AnnotatedElementBase<Parameter> implements IuParameter<T>, ParameterizedFacade {

	private final int index;
	private final ExecutableBase<?, ?, ?> executable;
	private final TypeFacade<?, T> type;

	/**
	 * Facade constructor, for exclusive use by {@link ExecutableBase}.
	 * 
	 * @param parameter    real parameter to provide a view of
	 * @param index        parameter index related to executable
	 *                     {@link Executable#getParameters()}
	 * @param typeTemplate {@link TypeTemplate} for the parameter type
	 * @param executable   declaring executable facade
	 */
	ParameterFacade(Parameter parameter, int index, ExecutableBase<?, ?, ?> executable,
			TypeTemplate<?, T> typeTemplate) {
		super(parameter);
		this.index = index;
		this.executable = executable;
		this.type = new TypeFacade<>(typeTemplate, this, IuReferenceKind.PARAMETER, index);
		executable.postInit(this::seal);
	}

	@Override
	public Map<String, TypeFacade<?, ?>> typeParameters() {
		return executable.typeParameters();
	}

	@Override
	public ExecutableBase<?, ?, ?> declaringExecutable() {
		return executable;
	}

	@Override
	public int index() {
		return index;
	}

	@Override
	public String name() {
		return annotatedElement.getName();
	}

	@Override
	public TypeFacade<?, T> type() {
		return type;
	}

	@Override
	public String toString() {
		return name() + ":" + TypeUtils.printType(type.deref());
	}

}
