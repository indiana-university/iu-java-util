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
package edu.iu.type;

import java.lang.reflect.Executable;
import java.util.List;

/**
 * Facade interface for an {@link Executable} element: a method or constructor.
 * 
 * @param <D> declaring type
 * @param <R> result type
 * 
 */
public interface IuExecutable<D, R> extends IuDeclaredElement<D>, IuParameterizedElement {

	/**
	 * Gets the hash key for this executable.
	 * 
	 * @return hash key
	 */
	IuExecutableKey getKey();

	/**
	 * Gets the parameters.
	 * 
	 * @return parameters
	 */
	List<? extends IuParameter<?>> parameters();

	/**
	 * Gets a parameter type.
	 * 
	 * @param i index
	 * @return parameter type
	 */
	default IuParameter<?> parameter(int i) {
		return parameters().get(i);
	}

	/**
	 * Executes the element.
	 * 
	 * @param arguments argument values
	 * @return result
	 * @throws Exception If an exception occurs
	 */
	R exec(Object... arguments) throws Exception;

}
