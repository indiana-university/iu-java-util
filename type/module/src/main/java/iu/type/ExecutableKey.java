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

import java.util.Arrays;

import edu.iu.type.IuObject;

/**
 * Represents the call signature for a method or constructor.
 */
public class ExecutableKey {

	private final String methodName;
	private final Class<?>[] parameterTypes;

	/**
	 * Creates a key representing a constructor call signature.
	 * 
	 * @param parameterTypes parameter types.
	 */
	public ExecutableKey(Class<?>... parameterTypes) {
		this(null, parameterTypes);
	}

	/**
	 * Creates a key representing a method call signature.
	 * 
	 * @param methodName     method name
	 * @param parameterTypes parameter types.
	 */
	public ExecutableKey(String methodName, Class<?>... parameterTypes) {
		this.methodName = methodName;
		this.parameterTypes = parameterTypes;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(methodName, parameterTypes);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		ExecutableKey other = (ExecutableKey) obj;
		return IuObject.equals(methodName, other.methodName) && IuObject.equals(parameterTypes, other.parameterTypes);
	}

	@Override
	public String toString() {
		var sb = new StringBuilder("(");
		if (methodName == null)
			sb.append("constructor");
		else
			sb.append(methodName);
		sb.append(Arrays.toString(parameterTypes));
		sb.append(')');
		return sb.toString();
	}

}
