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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to mark interceptors to bind by default to a target set of resources.
 * 
 * @see <a href=
 *      "https://jakarta.ee/specifications/interceptors/2.1/jakarta-interceptors-spec-2.1#default_interceptors">
 *      Jakarta Interceptors 2.1</a>
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface DefaultInterceptor {

	/**
	 * Defines the scope of the target interceptor set.
	 */
	enum Scope {
		/**
		 * Applies to all applicable types in the same {@link Module}.
		 * 
		 * <p>
		 * This is the default scope.
		 * </p>
		 */
		MODULE,

		/**
		 * Applies to all applicable types loaded by the same {@link ClassLoader}.
		 */
		CLASS_LOADER,

		/**
		 * Applies to all applicable types deployed to the same container:
		 * {@link ClassLoader} and all child {@link ClassLoader}s.
		 */
		CONTAINER;
	}

	/**
	 * Defines the scope of coverage for the default class loader.
	 * 
	 * @return scope
	 */
	Scope scope() default Scope.MODULE;

}
