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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import edu.iu.IuException;
import edu.iu.type.IuMethod;
import edu.iu.type.IuReferenceKind;

/**
 * Facade implementation for {@link IuMethod}.
 * 
 * @param <D> declaring type
 * @param <R> method return type
 */
final class MethodFacade<D, R> extends ExecutableBase<D, R, Method> implements IuMethod<D, R> {

	private final TypeFacade<?, R> returnType;

	/**
	 * Facade constructor.
	 * 
	 * @param method                {@link Method}
	 * @param returnTypeTemplate    {@link TypeTemplate} for the return type
	 * @param declaringTypeTemplate {@link TypeTemplate} for the declaring type
	 */
	MethodFacade(Method method, TypeTemplate<?, R> returnTypeTemplate, TypeTemplate<?, D> declaringTypeTemplate) {
		super(method, returnTypeTemplate.deref(), declaringTypeTemplate);
		method.setAccessible(true);

		this.returnType = new TypeFacade<>(returnTypeTemplate, this, IuReferenceKind.RETURN_TYPE);
		declaringTypeTemplate.postInit(() -> returnTypeTemplate.postInit(this::seal));
	}

	@Override
	public String name() {
		return annotatedElement.getName();
	}

	@Override
	public boolean isStatic() {
		var mod = annotatedElement.getModifiers();
		return (mod | Modifier.STATIC) == mod;
	}

	@Override
	public TypeFacade<?, R> returnType() {
		checkSealed();
		return returnType;
	}

	@Override
	public R exec(Object... arguments) throws Exception {
		checkSealed();

		Object obj;
		Object[] args;

		if (isStatic()) {
			obj = null;
			args = arguments;
		} else {
			obj = arguments[0];
			args = Arrays.copyOfRange(arguments, 1, arguments.length);
		}

		return IuException.checkedInvocation(() -> returnType.autoboxClass().cast(annotatedElement.invoke(obj, args)));
	}

}
