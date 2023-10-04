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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;

import edu.iu.UnsafeSupplier;

final class TypeUtils {

	/**
	 * Determines if a type name is exempt from the {@link ClassLoader} delegation
	 * suppression required for web applications.
	 * 
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0#web-application-class-loader">
	 *      Servlet 6.0, section 10.7.2</a>
	 */
	static boolean isPlatformType(String name) {
		return name.startsWith("jakarta.") //
				|| name.startsWith("java.") //
				|| name.startsWith("javax.") //
				|| name.startsWith("jdk.");
	}

	static ClassLoader getContext(AnnotatedElement element) {
		if (element instanceof Class)
			return ((Class<?>) element).getClassLoader();
		else if (element instanceof Executable)
			return ((Executable) element).getDeclaringClass().getClassLoader();
		else if (element instanceof Field)
			return ((Field) element).getDeclaringClass().getClassLoader();
		else if (element instanceof Parameter)
			return ((Parameter) element).getDeclaringExecutable().getDeclaringClass().getClassLoader();
		else
			throw new UnsupportedOperationException("Cannot determine context for " + element);
	}

	static <T> T callWithContext(ClassLoader contextLoader, UnsafeSupplier<T> supplier) throws Throwable {
		var current = Thread.currentThread();
		var loader = current.getContextClassLoader();
		try {
			current.setContextClassLoader(contextLoader);
			return supplier.get();
		} finally {
			current.setContextClassLoader(loader);
		}
	}

	static <T> T callWithContext(AnnotatedElement element, UnsafeSupplier<T> supplier) throws Throwable {
		return callWithContext(getContext(element), supplier);
	}

	private TypeUtils() {
	}

}
