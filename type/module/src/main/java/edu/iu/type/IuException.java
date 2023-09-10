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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * Exception handling utilities.
 */
public final class IuException {

	/**
	 * Casts an unchecked exception to {@link RuntimeException}, or wraps a checked
	 * exception in {@link UndeclaredThrowableException}.
	 * 
	 * <p>
	 * This method is useful for situations where {@link Throwable} or
	 * {@link Exception} is declared, but no special handling exists for dealing
	 * with a checked exception.
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	// something unsafe
	 * } catch (Throwable e) {
	 * 	throw IuType.handleUnchecked(e);
	 * }
	 * </pre>
	 * 
	 * @param e exception
	 * @return unchecked exception
	 * @throws Error directly if e is an {@link Error}
	 */
	public static RuntimeException handleUnchecked(Throwable e) throws Error {
		if (e instanceof RuntimeException)
			return (RuntimeException) e;
		if (e instanceof Error)
			throw (Error) e;
		if (e instanceof Exception)
			return new IllegalStateException(e);
		else
			throw (Error) new UnknownError(e.toString()).initCause(e);
	}

	/**
	 * Casts a checked exception to {@link Exception}, or wraps a non-error,
	 * non-exception throwable in {@link UndeclaredThrowableException}.
	 * 
	 * <p>
	 * This method is useful for situations where {@link Throwable} or is declared,
	 * but no special handling exists for dealing with non-exception conditions.
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	// something unsafe
	 * } catch (Throwable e) {
	 * 	throw IuType.handleChecked(e);
	 * }
	 * </pre>
	 * 
	 * @param e exception
	 * @return checked exception
	 * @throws Error directly if e is an {@link Error}
	 */
	public static Exception handleChecked(Throwable e) throws Error {
		if (e instanceof Exception)
			return (Exception) e;
		else if (e instanceof Error)
			throw (Error) e;
		throw (Error) new UnknownError(e.toString()).initCause(e);
	}

	/**
	 * Gracefully unwraps the root cause of {@link InvocationTargetException}.
	 * 
	 * <p>
	 * Method example:
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	method.invoke(o);
	 * } catch (Throwable e) {
	 * 	throw IuType.handleInvocation(e);
	 * }
	 * </pre>
	 * 
	 * <p>
	 * Constructor example:
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	return constructor.newInstance();
	 * } catch (Throwable e) {
	 * 	throw IuType.handleInvocation(e);
	 * }
	 * </pre>
	 * 
	 * @param e exception
	 * @return root cause
	 */
	public static Exception handleInvocation(Throwable e) {
		if (e instanceof InvocationTargetException)
			return handleChecked(e.getCause());
		else
			return handleUnchecked(e);
	}

	private IuException() {
	}
}
