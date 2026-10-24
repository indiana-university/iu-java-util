/*
 * Copyright © 2026 Indiana University
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

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.iu.IuException;
import jakarta.interceptor.InvocationContext;

/**
 * {@link InvocationContext} implementation for one invocation of a method or
 * constructor through an {@link InterceptorChain}.
 */
final class InvocationContextImpl implements InvocationContext {

	/**
	 * Performs the intercepted invocation, once all interceptors have proceeded.
	 */
	@FunctionalInterface
	interface Terminal {
		/**
		 * Invokes the target method or constructor.
		 *
		 * @param context invocation context
		 * @return result of the invocation
		 * @throws Exception from the invocation
		 */
		Object call(InvocationContextImpl context) throws Exception;
	}

	private final Executable executable;
	private final List<InterceptorChain.Step> steps;
	private final Map<Class<?>, Object> instances;
	private final Terminal terminal;
	private final Map<String, Object> contextData = new HashMap<>();
	private Object target;
	private Object[] parameters;
	private int next;

	/**
	 * Constructor.
	 *
	 * @param executable method or constructor
	 * @param target     target instance, null for a static method or constructor
	 * @param parameters invocation parameters
	 * @param steps      interceptor methods, in invocation order
	 * @param instances  interceptor instances
	 * @param terminal   performs the invocation after the last interceptor
	 */
	InvocationContextImpl(Executable executable, Object target, Object[] parameters,
			List<InterceptorChain.Step> steps, Map<Class<?>, Object> instances, Terminal terminal) {
		this.executable = executable;
		this.target = target;
		this.parameters = parameters;
		this.steps = steps;
		this.instances = instances;
		this.terminal = terminal;
	}

	/**
	 * Sets the target, once a constructor has been invoked.
	 *
	 * @param target constructed instance
	 */
	void target(Object target) {
		this.target = target;
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
	public Method getMethod() {
		return executable instanceof Method method ? method : null;
	}

	@Override
	public Constructor<?> getConstructor() {
		return executable instanceof Constructor<?> constructor ? constructor : null;
	}

	@Override
	public Object[] getParameters() {
		return parameters;
	}

	@Override
	public void setParameters(Object[] params) {
		if (params == null)
			throw new IllegalArgumentException("parameters must not be null");

		final var types = executable.getParameterTypes();
		if (params.length != types.length)
			throw new IllegalArgumentException(
					"Expected " + types.length + " parameters for " + executable + ", but found " + params.length);

		for (var i = 0; i < types.length; i++)
			if (params[i] == null ? types[i].isPrimitive()
					: !MethodType.methodType(types[i]).wrap().returnType().isInstance(params[i]))
				throw new IllegalArgumentException("Invalid value for parameter " + i + " of " + executable);

		this.parameters = params;
	}

	@Override
	public Map<String, Object> getContextData() {
		return contextData;
	}

	@Override
	public Object proceed() throws Exception {
		final var index = next;
		if (index >= steps.size())
			return terminal.call(this);

		final var step = steps.get(index);
		final var instance = step.owner() == null ? target : instances.get(step.owner());
		final var context = contextFor(step.method().getParameterTypes()[0]);
		next = index + 1;
		try {
			return IuException.checkedInvocation(() -> step.method().invoke(instance, context));
		} finally {
			next = index;
		}
	}

	/**
	 * Adapts this context to the {@link InvocationContext} type an interceptor
	 * method was compiled against.
	 *
	 * <p>
	 * An interceptor loaded in an isolated component references its own copy of
	 * the Jakarta Interceptors API, which is not the type this module implements.
	 * </p>
	 *
	 * @param contextType parameter type of the interceptor method
	 * @return this, if assignable to the type; otherwise a proxy that delegates to
	 *         this by method name
	 */
	private Object contextFor(Class<?> contextType) {
		if (contextType.isInstance(this))
			return this;

		return Proxy.newProxyInstance(contextType.getClassLoader(), new Class<?>[] { contextType },
				(proxy, method, args) -> IuException.checkedInvocation(
						() -> InvocationContextImpl.class.getMethod(method.getName(), method.getParameterTypes())
								.invoke(this, args)));
	}

}
