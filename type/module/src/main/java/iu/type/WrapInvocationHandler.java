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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import edu.iu.type.IuException;
import edu.iu.type.IuType;

public class WrapInvocationHandler implements InvocationHandler {

	private final ClassLoader loader;
	private final IuType<?> type;
	private final Object wrapped;

	public WrapInvocationHandler(ClassLoader loader, IuType<?> type, Object wrapped) {
		this.loader = loader;
		this.type = type;
		this.wrapped = wrapped;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		var resolvedImplMethod = IuType.resolve(wrapped.getClass()).method(method);

		Object result;
		try {
			result = new IuInvocationContext(method, wrapped, args, resolvedImplMethod.interceptors()).proceed();
		} catch (Throwable e) {
			throw IuException.handleInvocation(e);
		}

		if (result == null)
			return null;

		var resolvedMethod = type.method(method);
		var returnType = resolvedMethod.type();
		var returnBaseClass = returnType.baseClass();

		if (returnBaseClass.isInterface())
			result = Proxy.newProxyInstance(loader, new Class<?>[] { returnBaseClass },
					new WrapInvocationHandler(loader, returnType, result));

		else if (returnBaseClass.isArray()) {
			var resultComponentType = returnType.componentType();
			var resultComponentBaseClass = resultComponentType.baseClass();
			if (resultComponentBaseClass.isInterface()) {
				var length = Array.getLength(result);
				var replacedResult = resultComponentType.arrayGenerator().apply(length);
				for (int i = 0; i < length; i++)
					Array.set(replacedResult, i, Proxy.newProxyInstance(loader, new Class<?>[] { resultComponentBaseClass },
							new WrapInvocationHandler(loader, resultComponentType, Array.get(result, i))));
				result = replacedResult;
			}
		}

		return result;
	}

}
