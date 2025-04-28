/*
 * Copyright © 2025 Indiana University
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
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

/**
 * Describes the kind of reference used to refer to a generic type.
 */
public enum IuReferenceKind {

	/**
	 * The referent type is the {@link Class} type erasure of a generic type.
	 */
	ERASURE(IuType.class, false, false),

	/**
	 * The referent type is a generic abstract class or interface in the referent
	 * type's hierarchy.
	 */
	SUPER(IuType.class, false, false),

	/**
	 * The referent describes the {@link Member#getDeclaringClass() declaring type}
	 * of a (referring) {@link Member member}.
	 */
	DECLARING_TYPE(IuAnnotatedElement.class, false, false),

	/**
	 * The referent describes the {@link Class#getEnclosingClass() enclosing type}
	 * of a (referring) {@link Class}.
	 */
	ENCLOSING_TYPE(IuAnnotatedElement.class, false, false),

	/**
	 * The type was referred to by {@link GenericDeclaration#getTypeParameters()}
	 * from a {@link ParameterizedType}.
	 */
	TYPE_PARAM(IuType.class, true, false),

	/**
	 * The type was referred to by {@link GenericDeclaration#getTypeParameters()}
	 * from a {@link Method}.
	 */
	METHOD_PARAM(IuMethod.class, true, false),

	/**
	 * The type was referred to by {@link GenericDeclaration#getTypeParameters()}
	 * from a {@link Constructor}.
	 */
	CONSTRUCTOR_PARAM(IuConstructor.class, true, false),

	/**
	 * The type was referred to by {@link Class#getComponentType()} or
	 * {@link GenericArrayType#getGenericComponentType()}.
	 */
	COMPONENT_TYPE(IuType.class, false, false),

	/**
	 * The type was referred to by {@link Field#getGenericType()}.
	 */
	FIELD(IuField.class, true, false),

	/**
	 * The type was referred to by {@link Method#getGenericReturnType()} on a
	 * property read method.
	 */
	PROPERTY(IuProperty.class, true, false),

	/**
	 * The type was referred to by {@link Executable#getGenericParameterTypes()}.
	 */
	PARAMETER(IuParameter.class, false, true),

	/**
	 * The type was referred to by {@link Method#getGenericReturnType()}.
	 */
	RETURN_TYPE(IuMethod.class, false, false);

	private final Class<? extends IuAnnotatedElement> referrerType;
	private final boolean named;
	private final boolean indexed;

	private IuReferenceKind(Class<? extends IuAnnotatedElement> referrerType, boolean named, boolean indexed) {
		this.referrerType = referrerType;
		this.named = named;
		this.indexed = indexed;
	}

	/**
	 * Gets the type of the referrer.
	 * 
	 * @return referrer type
	 */
	public Class<? extends IuAnnotatedElement> referrerType() {
		return referrerType;
	}

	/**
	 * Determines if the reference was obtained by name.
	 * 
	 * <p>
	 * Only one of {@link #named()} and {@link #indexed()} may return true, both may
	 * return false.
	 * </p>
	 * 
	 * @return true if the reference is a name; else false.
	 */
	public boolean named() {
		return named;
	}

	/**
	 * Determines if the reference was obtained by index.
	 * 
	 * <p>
	 * Only only of {@link #named()} and {@link #indexed()} may return true, both
	 * may return false.
	 * </p>
	 * 
	 * @return true if the reference is an index; else false.
	 */
	public boolean indexed() {
		return indexed;
	}

}
