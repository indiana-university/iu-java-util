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

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Queue;

import edu.iu.IuObject;

/**
 * Hash key for mapping {@link IuExecutable} instances.
 */
public final class IuExecutableKey {

	/**
	 * Reduced from {@link IuObject}.
	 * 
	 * @param name           method name; null for constructor
	 * @param parameterTypes generic parameter types
	 * 
	 * @return result of {@link #of(String, Type...)}.hashCode() without object
	 *         creation.
	 */
	static int hashCode(String name, Type... parameterTypes) {
		final int prime = 31;
		int result = prime;
		if (name != null)
			result += name.hashCode();

		{
			int length = parameterTypes.length;
			int hash = Class.class.hashCode();
			for (var i = 0; i < length; i++)
				hash = prime * hash + IuType.of(parameterTypes[i]).erasedClass().hashCode();
			result = prime * result + hash;
		}

		return result;
	}

	/**
	 * Reduced from {@link IuObject}.
	 * 
	 * @param name           method name; null for constructor
	 * @param parameterTypes parameter type facades
	 * 
	 * @return result of {@link #of(String, Type...)}.hashCode() without object
	 *         creation.
	 */
	static int hashCode(String name, Iterable<? extends IuType<?, ?>> parameterTypes) {
		final int prime = 31;
		int result = prime;
		if (name != null)
			result += name.hashCode();

		{
			int hash = Class.class.hashCode();
			for (var parameterType : parameterTypes)
				hash = prime * hash + parameterType.erasedClass().hashCode();
			result = prime * result + hash;
		}

		return result;
	}

	/**
	 * Gets an executable hash key from type parameters.
	 * 
	 * @param name           method name; null for constructor
	 * @param parameterTypes generic parameter types
	 * 
	 * @return executable key of erased classes
	 */
	public static IuExecutableKey of(String name, Type... parameterTypes) {
		final var length = parameterTypes.length;
		var classes = new Class<?>[length];
		for (var i = 0; i < length; i++)
			classes[i] = IuType.of(parameterTypes[i]).erasedClass();
		return new IuExecutableKey(name, classes);
	}

	/**
	 * Gets a executable hash key from type parameters.
	 * 
	 * @param name           method name; null for constructor
	 * @param parameterTypes parameter type facades
	 * 
	 * @return executable key of erased classes
	 */
	public static IuExecutableKey of(String name, Iterable<? extends IuType<?, ?>> parameterTypes) {
		Queue<Class<?>> erasedParameterClasses = new ArrayDeque<>();
		for (var type : parameterTypes)
			erasedParameterClasses.offer(type.erasedClass());
		return new IuExecutableKey(name, erasedParameterClasses.toArray(new Class<?>[erasedParameterClasses.size()]));
	}

	private final String name;
	private final Class<?>[] params;

	private IuExecutableKey(String name, Class<?>[] params) {
		this.name = name;
		this.params = params;
	}

	/**
	 * Reduced from {@link IuObject}.
	 * 
	 * @param name           method name; null for constructor
	 * @param parameterTypes generic parameter types
	 * 
	 * @return result of {@link #of(String, Type...)}.equals(key) without object
	 *         creation.
	 */
	boolean equals(String name, Type... parameterTypes) {
		if (!IuObject.equals(name, this.name))
			return false;

		final var params = this.params;
		final var length = params.length;
		if (length != parameterTypes.length)
			return false;

		for (var i = 0; i < length; i++)
			if (params[i] != IuType.of(parameterTypes[i]).erasedClass())
				return false;

		return true;
	}

	/**
	 * Reduced from {@link IuObject}.
	 * 
	 * @param name           method name; null for constructor
	 * @param parameterTypes generic parameter types
	 * 
	 * @return result of {@link #of(String, Type...)}.equals(key) without object
	 *         creation.
	 */
	boolean equals(String name, Iterable<IuType<?, ?>> parameterTypes) {
		if (!IuObject.equals(name, this.name))
			return false;

		final var params = this.params;
		final var length = params.length;

		var parameterTypeCount = 0;
		for (var parameterType : parameterTypes) {
			if (parameterTypeCount >= length)
				return false;

			var parameterClass = params[parameterTypeCount];
			if (parameterClass != parameterType.erasedClass())
				return false;

			parameterTypeCount++;
		}

		return parameterTypeCount == length;
	}

	@Override
	public int hashCode() {
		return hashCode(name, params);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;

		var other = (IuExecutableKey) obj;
		return equals(other.name, other.params);
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		if (name != null)
			sb.append(name).append('(');
		else
			sb.append("<init>(");
		var l = sb.length();
		for (var param : params) {
			if (sb.length() > l)
				sb.append(',');
			sb.append(param.getSimpleName());
		}
		sb.append(")");
		return sb.toString();
	}

}
