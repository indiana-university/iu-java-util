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
package iu.logging.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxy from an external interface to an implementation class that follows the
 * same contract.
 */
public class IuLoggingProxy implements InvocationHandler {

	private final Object impl;

	private IuLoggingProxy(Object impl) {
		this.impl = impl;
	}

	/**
	 * Adapts an implementation to an external interface it follows the contract
	 * for.
	 * 
	 * @param <T>               external type
	 * @param externalInterface external interface
	 * @param impl              implementation
	 * @return target if instance of interface, else a proxy wrapper that translates
	 *         from the target to the interface
	 */
	public static <T> T adapt(Class<T> externalInterface, Object impl) {
		if (impl == null)
			return null;

		if (externalInterface.isInstance(impl))
			return externalInterface.cast(impl);

		if (Proxy.isProxyClass(impl.getClass())) {
			final var h = Proxy.getInvocationHandler(impl);
			if (h instanceof IuLoggingProxy)
				impl = ((IuLoggingProxy) h).impl;
		}

		return externalInterface.cast(Proxy.newProxyInstance(externalInterface.getClassLoader(),
				new Class<?>[] { externalInterface }, new IuLoggingProxy(impl)));
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return impl.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(impl, args);
	}

}
