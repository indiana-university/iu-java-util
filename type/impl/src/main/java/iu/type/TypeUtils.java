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
package iu.type;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import edu.iu.UnsafeRunnable;
import edu.iu.UnsafeSupplier;
import edu.iu.type.IuType;

/**
 * Miscellaneous type introspection utilities.
 */
final class TypeUtils {

	/**
	 * Gets the context class loader appropriate for a given annotated element.
	 * 
	 * @param element annotated element
	 * @return class loader that loaded the element
	 */
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

	/**
	 * Invokes an {@link UnsafeSupplier} using a specific context class loader.
	 * 
	 * @param <T>           return type
	 * @param contextLoader {@link ClassLoader}
	 * @param supplier      {@link UnsafeSupplier}
	 * @return result of {@link UnsafeSupplier#get()}
	 * 
	 * @throws Throwable from {@link UnsafeSupplier#get()}
	 */
	static <T> T callWithContext(ClassLoader contextLoader, UnsafeSupplier<T> supplier) throws Throwable {
		if (contextLoader == null)
			contextLoader = ClassLoader.getPlatformClassLoader();
		
		var current = Thread.currentThread();
		var loader = current.getContextClassLoader();
		try {
			current.setContextClassLoader(contextLoader);
			return supplier.get();
		} finally {
			current.setContextClassLoader(loader);
		}
	}

	/**
	 * Invokes an {@link UnsafeRunnable} using a specific context class loader.
	 * 
	 * @param contextLoader {@link ClassLoader}
	 * @param runnable      {@link UnsafeRunnable}
	 * 
	 * @throws Throwable from {@link UnsafeRunnable#run()}
	 */
	static void callWithContext(ClassLoader contextLoader, UnsafeRunnable runnable) throws Throwable {
		if (contextLoader == null)
			contextLoader = ClassLoader.getPlatformClassLoader();
		
		var current = Thread.currentThread();
		var loader = current.getContextClassLoader();
		try {
			current.setContextClassLoader(contextLoader);
			runnable.run();
		} finally {
			current.setContextClassLoader(loader);
		}
	}

	/**
	 * Invokes an {@link UnsafeSupplier} using a context appropriate for an
	 * annotated element.
	 * 
	 * @param <T>      return type
	 * @param element  {@link AnnotatedElement}
	 * @param supplier {@link UnsafeSupplier}
	 * @return result of {@link UnsafeSupplier#get()}
	 * 
	 * @throws Throwable from {@link UnsafeSupplier#get()}
	 */
	static <T> T callWithContext(AnnotatedElement element, UnsafeSupplier<T> supplier) throws Throwable {
		return callWithContext(getContext(element), supplier);
	}

	/**
	 * Prints a generic type.
	 * 
	 * @param type generic type
	 * @return human-readable form
	 */
	static String printType(Type type) {
		if (type instanceof Class) {
			var c = (Class<?>) type;
			if (c.isArray())
				return printType(c.componentType()) + "[]";
			else
				return c.getSimpleName();
		} else if (type instanceof GenericArrayType) {
			var genericArrayType = (GenericArrayType) type;
			var genericComponentType = genericArrayType.getGenericComponentType();
			return printType(genericComponentType) + "[]";
		} else if (type instanceof ParameterizedType) {
			var parameterizedType = (ParameterizedType) type;
			StringBuilder sb = new StringBuilder(printType(parameterizedType.getRawType()));
			sb.append('<');
			var l = sb.length();
			for (var actualTypeArgument : parameterizedType.getActualTypeArguments()) {
				if (sb.length() > l)
					sb.append(',');
				sb.append(printType(actualTypeArgument));
			}
			sb.append('>');
			return sb.toString();
		} else
			return type.toString();
	}

	/**
	 * Refers to a type in a hierarchy, for internal use by {@link TypeTemplate} and
	 * {@link TypeFacade}.
	 * 
	 * @param <T>          referrer type
	 * @param referrerType referrer type facade
	 * @param hierarchy    referrer's type hierarchy
	 * @param referentType generic referent type to match
	 * 
	 * @return inherited type facade with same erasure as the {@code referentType}
	 */
	static <T> IuType<?, ? super T> referTo(IuType<?, T> referrerType, Iterable<TypeFacade<?, ? super T>> hierarchy,
			Type referentType) {
		var erasedClass = TypeFactory.getErasedClass(referentType);
		if (erasedClass == referrerType.erasedClass())
			return referrerType;

		for (var superType : hierarchy)
			if (superType.erasedClass() == erasedClass)
				return superType;

		throw new IllegalArgumentException(
				printType(referentType) + " not present in type hierarchy for " + referrerType + "; " + hierarchy);
	}

	private TypeUtils() {
	}

}
