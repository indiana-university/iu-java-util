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
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Describes a reference to a generic type.
 * 
 * <p>
 * Each {@link IuType} instance either wraps a plain {@link Class} instance
 * ("base" type) or contains a reference back to the {@link Class},
 * {@link TypeVariable Type Parameter}, {@link Parameter}, {@link Constructor},
 * or {@link Method} that defined a generic and/or component {@link Type}.
 * </p>
 * 
 * @param <T> referent type
 */
public interface IuTypeReference<T> {

	/**
	 * Gets the reference kind.
	 * 
	 * @return reference kind
	 */
	IuReferenceKind kind();

	/**
	 * Gets the introspection wrapper through which the reference was obtained.
	 * 
	 * @return introspection wrapper
	 */
	IuAnnotatedElement referrer();

	/**
	 * Gets the name of the referent type as known by the referrer.
	 * 
	 * @return reference name; <em>must</em> be non-null when
	 *         {@link #kind()}{@link IuReferenceKind#named() .isNamed()} is true,
	 *         if false <em>must</em> be null.
	 */
	String name();

	/**
	 * Gets the ordinal index associated with a parameter reference.
	 * 
	 * @return index; <em>must</em> be &gt;= 0 when
	 *         {@link #kind()}{@link IuReferenceKind#indexed() .isIndexed()} is
	 *         true, if false <em>must</em> be -1.
	 */
	int index();

	/**
	 * Gets the referent type.
	 * 
	 * @return referent type
	 */
	IuType<T> referent();

}
