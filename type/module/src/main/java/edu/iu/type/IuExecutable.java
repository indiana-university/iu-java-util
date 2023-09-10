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
package edu.iu.type;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import iu.type.InterceptorHelper;

/**
 * Represents an executable member reflected from the base class of a generic
 * type.
 * 
 * @param <T> constructor or method return type
 * @param <E> executable type
 */
public abstract class IuExecutable<T, E extends Executable> extends IuMember<T, E> {

	private static final Logger LOG = Logger.getLogger(IuExecutable.class.getName());

	private static boolean interceptorSupportDisabled;

	private final List<IuParameter<?>> parameters;
	private Collection<IuType<?>> interceptorTypes;

	IuExecutable(IuType<?> declaringType, IuType<T> type, E executable, List<IuParameter<?>> parameters) {
		super(declaringType, type, executable);
		this.parameters = Collections.unmodifiableList(parameters);
	}

	/**
	 * Gets the parameters.
	 * 
	 * @return parameters
	 */
	public List<IuParameter<?>> parameters() {
		return parameters;
	}

	/**
	 * Gets a parameter type.
	 * 
	 * @param i index
	 * @return parameter type
	 */
	public IuParameter<?> parameter(int i) {
		return parameters.get(i);
	}

	/**
	 * Locates interceptor classes defined on this executable.
	 * 
	 * @return interceptor classes
	 */
	public Collection<?> interceptors() {
		if (interceptorTypes == null)
			try {
				this.interceptorTypes = InterceptorHelper.findInterceptors(this);
			} catch (NoClassDefFoundError e) {
				// cleanroom
				if (interceptorSupportDisabled)
					LOG.log(Level.FINEST, "interceptor support disabled", e);
				else {
					LOG.log(Level.CONFIG,
							"interceptor support disabled, further occurrences only logged at FINEST level", e);
					interceptorSupportDisabled = true;
				}
				this.interceptorTypes = Collections.emptySet();
			}

		if (interceptorTypes.isEmpty())
			return Collections.emptySet();

		Queue<Object> interceptors = new ArrayDeque<>();
		for (var interceptorType : interceptorTypes)
			try {
				interceptors.add(interceptorType.constructor().deref().newInstance());
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
				throw IuException.handleUnchecked(IuException.handleInvocation(e));
			}
		return interceptors;
	}

	@Override
	public String toString() {
		return " [" + deref() + "; " + type() + "; params = " + parameters + "]";
	}

	@Override
	public int hashCode() {
		return IuObject.hashCodeSuper(super.hashCode(), parameters);
	}

	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj))
			return false;
		IuExecutable<?, ?> other = (IuExecutable<?, ?>) obj;
		return IuObject.equals(parameters, other.parameters);
	}

}
