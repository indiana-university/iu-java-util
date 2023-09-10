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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import iu.type.BaseAnnotatedElement;

/**
 * Represents a class member reflected from the base class of a generic type.
 * 
 * @param <T> type associated with the member
 * @param <M> member type
 */
public abstract class IuMember<T, M extends Member & AnnotatedElement> extends BaseAnnotatedElement<M> {

	private final IuType<?> declaringType;
	private final IuType<T> type;

	IuMember(IuType<?> declaringType, IuType<T> type, M member) {
		super(member);
		this.declaringType = declaringType;
		this.type = type;
	}

	/**
	 * Gets the declaring type.
	 * 
	 * @return declaring type
	 */
	public IuType<?> declaringType() {
		return declaringType;
	}

	/**
	 * Gets the member type.
	 * 
	 * <p>
	 * Relates to:
	 * </p>
	 * <ul>
	 * <li>{@link Field#getGenericType()}</li>
	 * <li>{@link Method#getGenericReturnType()}</li>
	 * <li>{@link Constructor} generic type reference</li>
	 * </ul>
	 * 
	 * @return member type
	 */
	public IuType<T> type() {
		return type;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCodeSuper(super.hashCode(), type);
	}

	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj))
			return false;
		IuMember<?, ?> other = (IuMember<?, ?>) obj;
		return IuObject.equals(type, other.type);
	}

}
