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

import java.lang.reflect.Constructor;
import java.util.List;

import iu.type.IuInvocationContext;

/**
 * Represents a constructor reflected from the base class of a generic type.
 * 
 * @param <C> constructor type
 */
public class IuConstructor<C> extends IuExecutable<C, Constructor<C>> {

	IuConstructor(IuType<?> declaringType, IuType<C> type, Constructor<C> constructor,
			List<IuParameter<?>> parameters) {
		super(declaringType, type, constructor, parameters);
	}

	/**
	 * Gets a new instance, interpolated by
	 * {@link jakarta.interceptor.AroundConstruct} where defined by a bound
	 * interceptor.
	 * 
	 * @param args constructor args
	 * @return new instance
	 * @throws Exception if invocation results in an exception
	 */
	public C newInstance(Object... args) throws Exception {
		var interceptors = interceptors();
		if (interceptors.isEmpty())
			try {
				var constructor = deref();
				constructor.setAccessible(true);
				return constructor.newInstance(args);
			} catch (Throwable e) {
				throw IuException.handleChecked(IuException.handleInvocation(e));
			}
		else
			try {
				return type().baseClass().cast(new IuInvocationContext(deref(), args, interceptors).proceed());
			} catch (Throwable e) {
				throw IuException.handleChecked(e);
			}
	}

	@Override
	public String toString() {
		return "constructor" + super.toString();
	}

}
