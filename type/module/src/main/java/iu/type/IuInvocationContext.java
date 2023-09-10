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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;

import edu.iu.type.IuException;
import edu.iu.type.IuType;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;

public class IuInvocationContext implements InvocationContext {

	private final Constructor<?> constructor;
	private final Method method;
	private final Map<String, Object> contextData = new HashMap<>();
	private final Queue<Callable<?>> interceptorCalls = new ArrayDeque<>();
	private Object target;
	private Object[] parameters;

	public IuInvocationContext(Constructor<?> constructor, Object[] parameters, Iterable<?> interceptors) {
		this.constructor = constructor;
		this.method = null;
		this.target = null;
		this.parameters = parameters;
		for (Object interceptor : interceptors)
			queueInterceptorCalls(interceptor, AroundConstruct.class);
	}

	public IuInvocationContext(Method method, Object target, Object[] parameters, Iterable<?> interceptors) {
		this.constructor = null;
		this.method = method;
		this.target = target;
		this.parameters = parameters;
		for (Object interceptor : interceptors)
			queueInterceptorCalls(interceptor, AroundInvoke.class);
		queueInterceptorCalls(target, AroundInvoke.class);
	}

	private void queueInterceptorCalls(Object interceptor, Class<? extends Annotation> annotationType) {
		Deque<IuType<?>> interceptorStack = new ArrayDeque<>();
		for (var type : IuType.resolve(interceptor.getClass()).hierarchy())
			interceptorStack.push(type);

		while (!interceptorStack.isEmpty()) {
			var interceptorType = interceptorStack.pop();
			for (var interceptMethod : interceptorType.annotatedMethods(annotationType)) {
				interceptorCalls.offer(() -> {
					var interceptorMethod = interceptMethod.deref();
					if (!interceptorMethod.canAccess(interceptor))
						interceptorMethod.setAccessible(true);
					Object result;
					try {
						result = interceptorMethod.invoke(interceptor, this);
					} catch (Throwable e) {
						throw IuException.handleInvocation(e);
					}
					if (constructor != null)
						return target;
					else
						return result;
				});
			}
		}
	}

	@Override
	public Constructor<?> getConstructor() {
		return constructor;
	}

	@Override
	public Map<String, Object> getContextData() {
		return contextData;
	}

	@Override
	public Method getMethod() {
		return method;
	}

	@Override
	public Object[] getParameters() {
		return parameters;
	}

	@Override
	public Object getTarget() {
		return target;
	}

	@Override
	public Object getTimer() {
		return null;
	}

	@Override
	public void setParameters(Object[] params) {
		parameters = params;
	}

	@Override
	public Object proceed() throws Exception {
		if (!interceptorCalls.isEmpty())
			return interceptorCalls.poll().call();

		try {
			if (constructor != null) {
				if (!constructor.canAccess(null))
					constructor.setAccessible(true);
				return target = constructor.newInstance(parameters);
			} else {
				if (!method.canAccess(target))
					method.setAccessible(true);
				return method.invoke(target, parameters);
			}
		} catch (Throwable e) {
			throw IuException.handleInvocation(e);
		}
	}

}
