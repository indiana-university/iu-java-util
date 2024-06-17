/*
 * Copyright © 2024 Indiana University
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
package edu.iu;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;

/**
 * Exception handling utilities.
 */
public final class IuException {

	/**
	 * Expects a {@link Throwable} to be an (unchecked) {@link RuntimeException}.
	 * 
	 * <p>
	 * This method <em>may</em> be used as an exception handler in situations where
	 * {@link Throwable} or one or more {@link Exception checked exceptions} are
	 * thrown from a downstream invocation, but a {@link Exception checked
	 * exception} <em>must not</em> be thrown from the interface being implemented
	 * and no special handling is specified.
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	// something unsafe
	 * } catch (Throwable e) {
	 * 	throw IuException.unchecked(e);
	 * }
	 * </pre>
	 * 
	 * @param throwable Any {@link Throwable}
	 * @return {@code throwable} if {@link RuntimeException}
	 * @throws IllegalStateException if {@code throwable} is a {@link Exception
	 *                               checked exception} or {@link Throwable custom
	 *                               throwable}
	 * @throws Error                 if {@code throwable} is an {@link Error}
	 */
	public static RuntimeException unchecked(Throwable throwable) throws IllegalStateException, Error {
		return unchecked(throwable, (String) null);
	}

	/**
	 * Expects a {@link Throwable} to be an (unchecked) {@link RuntimeException}.
	 * 
	 * <p>
	 * This method <em>may</em> be used as an exception handler in situations where
	 * {@link Throwable} or one or more {@link Exception checked exceptions} are
	 * thrown from a downstream invocation, but a {@link Exception checked
	 * exception} <em>must not</em> be thrown from the interface being implemented
	 * and no special handling is specified.
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	// something unsafe
	 * } catch (Throwable e) {
	 * 	throw IuException.unchecked(e);
	 * }
	 * </pre>
	 * 
	 * @param throwable Any {@link Throwable}
	 * @param message   Message to use with {@link IllegalStateException} if
	 *                  throwable is not a {@link RuntimeException} or {@link Error}
	 * @return {@code throwable} if {@link RuntimeException}
	 * @throws IllegalStateException if {@code throwable} is a {@link Exception
	 *                               checked exception} or {@link Throwable custom
	 *                               throwable}
	 * @throws Error                 if {@code throwable} is an {@link Error}
	 */
	public static RuntimeException unchecked(Throwable throwable, String message) throws IllegalStateException, Error {
		if (throwable instanceof RuntimeException)
			return (RuntimeException) throwable;

		if (throwable instanceof Error)
			throw (Error) throwable;

		throw new IllegalStateException(message, throwable);
	}

	/**
	 * Expects a {@link Throwable} to be a {@link Exception checked exception}.
	 * 
	 * <p>
	 * This method <em>may</em> be used as an exception handler in situations where
	 * {@link Throwable} is thrown from a downstream invocation, but only
	 * {@link Exception exception} <em>may</em> be thrown from the interface being
	 * implemented and special handling is not specified for {@link Throwable custom
	 * throwables}.
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	// something unsafe
	 * } catch (Throwable e) {
	 * 	throw IuException.checked(e);
	 * }
	 * </pre>
	 * 
	 * @param throwable Any {@link Throwable}
	 * @return {@code throwable} if {@link Exception}
	 * @throws IllegalStateException if {@code throwable} is a {@link Throwable
	 *                               custom throwable}
	 * @throws Error                 if {@code throwable} is an {@link Error}
	 */
	public static Exception checked(Throwable throwable) throws Error, IllegalStateException {
		if (throwable instanceof Exception)
			return (Exception) throwable;

		else if (throwable instanceof Error)
			throw (Error) throwable;

		throw new IllegalStateException(throwable);
	}

	/**
	 * Expects a {@link Throwable} to be a specific {@link Exception checked
	 * exception}.
	 * 
	 * <p>
	 * This method <em>may</em> be used as an exception handler in situations where
	 * {@link Throwable}, {@link Exception}, or specific {@link Exception checked
	 * exceptions} are thrown from a downstream invocation, but at least one of
	 * these <em>must not</em> be thrown from the interface being implemented and
	 * special handling is not specified for the unexpected {@link Throwable
	 * throwable types}.
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	// something unsafe
	 * } catch (Throwable e) {
	 * 	throw IuException.checked(e, ACheckedException.class);
	 * }
	 * </pre>
	 * 
	 * @param <T>                    Expected exception type
	 * 
	 * @param throwable              Any {@link Throwable}
	 * @param expectedExceptionClass Expected exception class
	 * @return {@code throwable} if {@code instanceof T}
	 * 
	 * @throws RuntimeException      if {@code throwable} is a
	 *                               {@link RuntimeException}
	 * @throws Error                 if {@code throwable} is an {@link Error}
	 * @throws IllegalStateException if {@code throwable} is custom or a
	 *                               {@link Exception checked exception} other than
	 *                               {@code T}
	 */
	public static <T extends Exception> T checked(Throwable throwable, Class<T> expectedExceptionClass)
			throws RuntimeException, Error, IllegalStateException {
		if (expectedExceptionClass.isInstance(throwable))
			return expectedExceptionClass.cast(throwable);

		else if (throwable instanceof Error)
			throw (Error) throwable;

		else if (throwable instanceof RuntimeException)
			throw (RuntimeException) throwable;

		throw new IllegalStateException(throwable);
	}

	/**
	 * Expects a {@link Throwable} to be a specific {@link Exception checked
	 * exception}.
	 * 
	 * <p>
	 * This method <em>may</em> be used as an exception handler in situations where
	 * {@link Throwable}, {@link Exception}, or specific {@link Exception checked
	 * exceptions} are thrown from a downstream invocation, but at least one of
	 * these <em>must not</em> be thrown from the interface being implemented and
	 * special handling is not specified for the unexpected {@link Throwable
	 * throwable types}.
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	// something unsafe
	 * } catch (Throwable e) {
	 * 	throw IuException.checked(e, ACheckedException.class, AnotherCheckedException.class);
	 * }
	 * </pre>
	 * 
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Additional exception type that <em>may</em> be
	 *                                thrown
	 * 
	 * @param throwable               Any {@link Throwable}
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Additional exception class that <em>may</em>
	 *                                be thrown
	 * @return {@code throwable} if {@code instanceof T1}
	 * @throws T2                    if {@code throwable} is {@code T2}
	 * @throws RuntimeException      if {@code throwable} is a
	 *                               {@link RuntimeException}
	 * @throws Error                 if {@code throwable} is an {@link Error}
	 * @throws IllegalStateException if {@code throwable} is custom or a
	 *                               {@link Exception checked exception} other than
	 *                               {@code T} be thrown
	 */
	public static <T1 extends Exception, T2 extends Exception> T1 checked(Throwable throwable,
			Class<T1> expectedExceptionClass1, Class<T2> expectedExceptionClass2)
			throws T2, RuntimeException, Error, IllegalStateException {
		if (expectedExceptionClass1.isInstance(throwable))
			return expectedExceptionClass1.cast(throwable);

		else if (expectedExceptionClass2.isInstance(throwable))
			throw expectedExceptionClass2.cast(throwable);

		else if (throwable instanceof RuntimeException)
			throw (RuntimeException) throwable;

		else if (throwable instanceof Error)
			throw (Error) throwable;

		throw new IllegalStateException(throwable);
	}

	/**
	 * Expects a {@link Throwable} to be a specific {@link Exception checked
	 * exception}.
	 * 
	 * <p>
	 * This method <em>may</em> be used as an exception handler in situations where
	 * {@link Throwable}, {@link Exception}, or specific {@link Exception checked
	 * exceptions} are thrown from a downstream invocation, but at least one of
	 * these <em>must not</em> be thrown from the interface being implemented and
	 * special handling is not specified for the unexpected {@link Throwable
	 * throwable types}.
	 * </p>
	 * 
	 * <pre>
	 * try {
	 * 	// something unsafe
	 * } catch (Throwable e) {
	 * 	throw IuException.checked(e, ACheckedException.class, AnotherCheckedException.class,
	 * 			YetAnotherCheckedException.class);
	 * }
	 * </pre>
	 * 
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Additional exception type that <em>may</em> be
	 *                                thrown
	 * @param <T3>                    Additional exception type that <em>may</em> be
	 *                                thrown
	 * 
	 * @param throwable               Any {@link Throwable}
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Additional exception class that <em>may</em>
	 *                                be thrown
	 * @param expectedExceptionClass3 Additional exception class that <em>may</em>
	 *                                be thrown
	 * @return {@code throwable} if {@code instanceof T1}
	 * @throws T2                    if {@code throwable} is {@code T2}
	 * @throws T3                    if {@code throwable} is {@code T3}
	 * @throws RuntimeException      if {@code throwable} is a
	 *                               {@link RuntimeException}
	 * @throws Error                 if {@code throwable} is an {@link Error}
	 * @throws IllegalStateException if {@code throwable} is custom or a
	 *                               {@link Exception checked exception} other than
	 *                               {@code T} be thrown
	 */
	public static <T1 extends Exception, T2 extends Exception, T3 extends Exception> T1 checked(Throwable throwable,
			Class<T1> expectedExceptionClass1, Class<T2> expectedExceptionClass2, Class<T3> expectedExceptionClass3)
			throws T2, T3, RuntimeException, Error, IllegalStateException {
		if (expectedExceptionClass1.isInstance(throwable))
			return expectedExceptionClass1.cast(throwable);

		else if (expectedExceptionClass2.isInstance(throwable))
			throw expectedExceptionClass2.cast(throwable);

		else if (expectedExceptionClass3.isInstance(throwable))
			throw expectedExceptionClass3.cast(throwable);

		else if (throwable instanceof RuntimeException)
			throw (RuntimeException) throwable;

		else if (throwable instanceof Error)
			throw (Error) throwable;

		throw new IllegalStateException(throwable);
	}

	/**
	 * Gracefully invokes a supplier assumed to wrap {@link Executable invocation}.
	 * 
	 * <p>
	 * Handles {@link InvocationTargetException} by unwrapping the cause and
	 * re-throwing.
	 * </p>
	 * <p>
	 * Handles other exceptions via {@link #unchecked(Throwable)}.
	 * </p>
	 * 
	 * @param <R>      result type
	 * @param supplier {@link UnsafeSupplier} assumed to wrap {@link Executable
	 *                 invocation}.
	 * @return result
	 */
	public static <R> R uncheckedInvocation(UnsafeSupplier<R> supplier) {
		try {
			try {
				return supplier.get();
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		} catch (Throwable e) {
			throw unchecked(e);
		}
	}

	/**
	 * Gracefully invokes a {@link Executable method or constructor}.
	 * 
	 * <p>
	 * Handles {@link InvocationTargetException} by unwrapping the cause and
	 * re-throwing.
	 * </p>
	 * <p>
	 * Handles other exceptions via {@link #checked(Throwable)}.
	 * </p>
	 * 
	 * @param <R>      result type
	 * @param supplier {@link UnsafeSupplier} assumed to wrap {@link Executable
	 *                 invocation}.
	 * @return result
	 * @throws Exception If invocation fails
	 */
	public static <R> R checkedInvocation(UnsafeSupplier<R> supplier) throws Exception {
		try {
			try {
				return supplier.get();
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		} catch (Throwable e) {
			throw checked(e);
		}
	}

	/**
	 * Gracefully invokes a {@link Executable method or constructor}.
	 * 
	 * <p>
	 * Handles {@link InvocationTargetException} by unwrapping the cause and
	 * re-throwing.
	 * </p>
	 * <p>
	 * Handles other exceptions via {@link #checked(Throwable, Class)}.
	 * </p>
	 * 
	 * @param <R>                    result type
	 * @param <T>                    Expected exception type
	 * 
	 * @param expectedExceptionClass Expected exception class
	 * @param supplier               {@link UnsafeSupplier} assumed to wrap
	 *                               {@link Executable invocation}.
	 * @return result
	 * 
	 * @throws T If invocation fails
	 */
	public static <R, T extends Exception> R checkedInvocation(Class<T> expectedExceptionClass,
			UnsafeSupplier<R> supplier) throws T {
		try {
			try {
				return supplier.get();
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass);
		}
	}

	/**
	 * Gracefully invokes a {@link Executable method or constructor}.
	 * 
	 * <p>
	 * Handles {@link InvocationTargetException} by unwrapping the cause and
	 * re-throwing.
	 * </p>
	 * <p>
	 * Handles other exceptions via {@link #checked(Throwable, Class, Class)}.
	 * </p>
	 * 
	 * @param <R>                     result type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param supplier                {@link UnsafeSupplier} assumed to wrap
	 *                                {@link Executable invocation}.
	 * @return result
	 * @throws T1 If invocation fails
	 * @throws T2 If invocation fails
	 */
	public static <R, T1 extends Exception, T2 extends Exception> R checkedInvocation(Class<T1> expectedExceptionClass1,
			Class<T2> expectedExceptionClass2, UnsafeSupplier<R> supplier) throws T1, T2 {
		try {
			try {
				return supplier.get();
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2);
		}
	}

	/**
	 * Gracefully invokes a {@link Executable method or constructor}.
	 * 
	 * <p>
	 * Handles {@link InvocationTargetException} by unwrapping the cause and
	 * re-throwing.
	 * </p>
	 * <p>
	 * Handles other exceptions via
	 * {@link #checked(Throwable, Class, Class, Class)}.
	 * </p>
	 * 
	 * @param <R>                     result type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * @param <T3>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param expectedExceptionClass3 Expected exception class
	 * @param supplier                {@link UnsafeSupplier} assumed to wrap
	 *                                {@link Executable invocation}.
	 * @return result
	 * @throws T1 If invocation fails
	 * @throws T2 If invocation fails
	 * @throws T3 If invocation fails
	 */
	public static <R, T1 extends Exception, T2 extends Exception, T3 extends Exception> R checkedInvocation(
			Class<T1> expectedExceptionClass1, Class<T2> expectedExceptionClass2, Class<T3> expectedExceptionClass3,
			UnsafeSupplier<R> supplier) throws T1, T2, T3 {
		try {
			try {
				return supplier.get();
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2, expectedExceptionClass3);
		}
	}

	/**
	 * Gracefully runs an {@link UnsafeRunnable}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #unchecked(Throwable)}.
	 * </p>
	 * 
	 * @param runnable Any {@link UnsafeRunnable}
	 */
	public static void unchecked(UnsafeRunnable runnable) {
		unchecked(runnable, (String) null);
	}

	/**
	 * Gracefully runs an {@link UnsafeRunnable}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #unchecked(Throwable)}.
	 * </p>
	 * 
	 * @param runnable Any {@link UnsafeRunnable}
	 * @param message message for use with {@link #unchecked(Throwable, String)}
	 */
	public static void unchecked(UnsafeRunnable runnable, String message) {
		try {
			runnable.run();
		} catch (Throwable e) {
			throw unchecked(e, message);
		}
	}

	/**
	 * Gracefully runs an {@link UnsafeRunnable}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable)}.
	 * </p>
	 * 
	 * @param runnable Any {@link UnsafeRunnable}
	 * @throws Exception If thrown by {@link UnsafeRunnable#run()}
	 */
	public static void checked(UnsafeRunnable runnable) throws Exception {
		try {
			runnable.run();
		} catch (Throwable e) {
			throw checked(e);
		}
	}

	/**
	 * Gracefully runs an {@link UnsafeRunnable}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class)}.
	 * </p>
	 * 
	 * @param <T>                    Expected exception type
	 * 
	 * @param expectedExceptionClass Expected exception class
	 * 
	 * @param runnable               Any {@link UnsafeRunnable}
	 * @throws T If thrown by {@link UnsafeRunnable#run()}
	 */
	public static <T extends Exception> void checked(Class<T> expectedExceptionClass, UnsafeRunnable runnable)
			throws T {
		try {
			runnable.run();
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass);
		}
	}

	/**
	 * Gracefully runs an {@link UnsafeRunnable}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class)}.
	 * </p>
	 * 
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * 
	 * @param runnable                Any {@link UnsafeRunnable}
	 * @throws T1 If thrown by {@link UnsafeRunnable#run()}
	 * @throws T2 If thrown by {@link UnsafeRunnable#run()}
	 */
	public static <T1 extends Exception, T2 extends Exception> void checked(Class<T1> expectedExceptionClass1,
			Class<T2> expectedExceptionClass2, UnsafeRunnable runnable) throws T1, T2 {
		try {
			runnable.run();
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2);
		}
	}

	/**
	 * Gracefully runs an {@link UnsafeRunnable}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class, Class)}.
	 * </p>
	 * 
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * @param <T3>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param expectedExceptionClass3 Expected exception class
	 * 
	 * @param runnable                Any {@link UnsafeRunnable}
	 * @throws T1 If thrown by {@link UnsafeRunnable#run()}
	 * @throws T2 If thrown by {@link UnsafeRunnable#run()}
	 * @throws T3 If thrown by {@link UnsafeRunnable#run()}
	 */
	public static <T1 extends Exception, T2 extends Exception, T3 extends Exception> void checked(
			Class<T1> expectedExceptionClass1, Class<T2> expectedExceptionClass2, Class<T3> expectedExceptionClass3,
			UnsafeRunnable runnable) throws T1, T2, T3 {
		try {
			runnable.run();
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2, expectedExceptionClass3);
		}
	}

	/**
	 * Gracefully gets from an {@link UnsafeSupplier}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #unchecked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>      result type
	 * 
	 * @param supplier Any {@link UnsafeSupplier}
	 * @return result
	 */
	public static <T> T unchecked(UnsafeSupplier<T> supplier) {
		return unchecked(supplier, (String) null);
	}

	/**
	 * Gracefully gets from an {@link UnsafeSupplier}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #unchecked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>      result type
	 * 
	 * @param supplier Any {@link UnsafeSupplier}
	 * @param message Message to use with {@link #unchecked(Throwable, String)}
	 * @return result
	 */
	public static <T> T unchecked(UnsafeSupplier<T> supplier, String message) {
		try {
			return supplier.get();
		} catch (Throwable e) {
			throw unchecked(e);
		}
	}

	/**
	 * Gracefully gets from an {@link UnsafeSupplier}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>      result type
	 * 
	 * @param supplier Any {@link UnsafeSupplier}
	 * @return result
	 * @throws Exception If thrown by {@link UnsafeSupplier#get()}
	 */
	public static <T> T checked(UnsafeSupplier<T> supplier) throws Exception {
		try {
			return supplier.get();
		} catch (Throwable e) {
			throw checked(e);
		}
	}

	/**
	 * Gracefully gets from an {@link UnsafeSupplier}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class)}.
	 * </p>
	 * 
	 * @param <T>                    result type
	 * @param <T1>                   Expected exception type
	 * 
	 * @param expectedExceptionClass Expected exception class
	 * @param supplier               Any {@link UnsafeSupplier}
	 * @return result
	 * @throws T1 If thrown by {@link UnsafeSupplier#get()}
	 */
	public static <T, T1 extends Exception> T checked(Class<T1> expectedExceptionClass, UnsafeSupplier<T> supplier)
			throws T1 {
		try {
			return supplier.get();
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass);
		}
	}

	/**
	 * Gracefully gets from an {@link UnsafeSupplier}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     result type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param supplier                Any {@link UnsafeSupplier}
	 * @return result
	 * @throws T1 If thrown by {@link UnsafeSupplier#get()}
	 * @throws T2 If thrown by {@link UnsafeSupplier#get()}
	 */
	public static <T, T1 extends Exception, T2 extends Exception> T checked(Class<T1> expectedExceptionClass1,
			Class<T2> expectedExceptionClass2, UnsafeSupplier<T> supplier) throws T1, T2 {
		try {
			return supplier.get();
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2);
		}
	}

	/**
	 * Gracefully gets from an {@link UnsafeSupplier}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     result type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * @param <T3>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param expectedExceptionClass3 Expected exception class
	 * @param supplier                Any {@link UnsafeSupplier}
	 * @return result
	 * @throws T1 If thrown by {@link UnsafeSupplier#get()}
	 * @throws T2 If thrown by {@link UnsafeSupplier#get()}
	 * @throws T3 If thrown by {@link UnsafeSupplier#get()}
	 */
	public static <T, T1 extends Exception, T2 extends Exception, T3 extends Exception> T checked(
			Class<T1> expectedExceptionClass1, Class<T2> expectedExceptionClass2, Class<T3> expectedExceptionClass3,
			UnsafeSupplier<T> supplier) throws T1, T2, T3 {
		try {
			return supplier.get();
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2, expectedExceptionClass3);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #unchecked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>      argument type
	 * 
	 * @param argument Argument for {@link UnsafeConsumer#accept(Object)}
	 * @param consumer Any {@link UnsafeConsumer}
	 */
	public static <T> void unchecked(T argument, UnsafeConsumer<T> consumer) {
		try {
			consumer.accept(argument);
		} catch (Throwable e) {
			throw unchecked(e);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>      argument type
	 * 
	 * @param argument Argument for {@link UnsafeConsumer#accept(Object)}
	 * @param consumer Any {@link UnsafeConsumer}
	 * @throws Exception If thrown by {@link UnsafeConsumer#accept(Object)}
	 */
	public static <T> void checked(T argument, UnsafeConsumer<T> consumer) throws Exception {
		try {
			consumer.accept(argument);
		} catch (Throwable e) {
			throw checked(e);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class)}.
	 * </p>
	 * 
	 * @param <T>                    argument type
	 * @param <T1>                   Expected exception type
	 * 
	 * @param expectedExceptionClass Expected exception class
	 * @param argument               Argument for
	 *                               {@link UnsafeConsumer#accept(Object)}
	 * @param consumer               Any {@link UnsafeConsumer}
	 * 
	 * @throws T1 If thrown by {@link UnsafeConsumer#accept(Object)}
	 */
	public static <T, T1 extends Exception> void checked(Class<T1> expectedExceptionClass, T argument,
			UnsafeConsumer<T> consumer) throws T1 {
		try {
			consumer.accept(argument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     argument type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param argument                Argument for
	 *                                {@link UnsafeConsumer#accept(Object)}
	 * @param consumer                Any {@link UnsafeConsumer}
	 * 
	 * @throws T1 If thrown by {@link UnsafeConsumer#accept(Object)}
	 * @throws T2 If thrown by {@link UnsafeConsumer#accept(Object)}
	 */
	public static <T, T1 extends Exception, T2 extends Exception> void checked(Class<T1> expectedExceptionClass1,
			Class<T2> expectedExceptionClass2, T argument, UnsafeConsumer<T> consumer) throws T1, T2 {
		try {
			consumer.accept(argument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     argument type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * @param <T3>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param expectedExceptionClass3 Expected exception class
	 * @param argument                Argument for
	 *                                {@link UnsafeConsumer#accept(Object)}
	 * @param consumer                Any {@link UnsafeConsumer}
	 * 
	 * @throws T1 If thrown by {@link UnsafeConsumer#accept(Object)}
	 * @throws T2 If thrown by {@link UnsafeConsumer#accept(Object)}
	 * @throws T3 If thrown by {@link UnsafeConsumer#accept(Object)}
	 */
	public static <T, T1 extends Exception, T2 extends Exception, T3 extends Exception> void checked(
			Class<T1> expectedExceptionClass1, Class<T2> expectedExceptionClass2, Class<T3> expectedExceptionClass3,
			T argument, UnsafeConsumer<T> consumer) throws T1, T2, T3 {
		try {
			consumer.accept(argument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2, expectedExceptionClass3);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #unchecked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>      argument type
	 * @param <R>      result type
	 * 
	 * @param argument Argument to {@link UnsafeFunction#apply(Object)}
	 * @param function Any {@link UnsafeFunction}
	 * @return result
	 */
	public static <T, R> R unchecked(T argument, UnsafeFunction<T, R> function) {
		try {
			return function.apply(argument);
		} catch (Throwable e) {
			throw unchecked(e);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>      argument type
	 * @param <R>      result type
	 * 
	 * @param argument Argument to {@link UnsafeFunction#apply(Object)}
	 * @param function Any {@link UnsafeFunction}
	 * @return result
	 * @throws Exception If thrown by {@link UnsafeFunction#apply(Object)}
	 */
	public static <T, R> R checked(T argument, UnsafeFunction<T, R> function) throws Exception {
		try {
			return function.apply(argument);
		} catch (Throwable e) {
			throw checked(e);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class)}.
	 * </p>
	 * 
	 * @param <T>                    argument type
	 * @param <R>                    result type
	 * @param <T1>                   Expected exception type
	 * 
	 * @param expectedExceptionClass Expected exception class
	 * @param argument               Argument to
	 *                               {@link UnsafeFunction#apply(Object)}
	 * @param function               Any {@link UnsafeFunction}
	 * @return result
	 * @throws T1 If thrown by {@link UnsafeFunction#apply(Object)}
	 */
	public static <T, R, T1 extends Exception> R checked(Class<T1> expectedExceptionClass, T argument,
			UnsafeFunction<T, R> function) throws T1 {
		try {
			return function.apply(argument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     argument type
	 * @param <R>                     result type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param argument                Argument to
	 *                                {@link UnsafeFunction#apply(Object)}
	 * @param function                Any {@link UnsafeFunction}
	 * @return result
	 * @throws T1 If thrown by {@link UnsafeFunction#apply(Object)}
	 * @throws T2 If thrown by {@link UnsafeFunction#apply(Object)}
	 */
	public static <T, R, T1 extends Exception, T2 extends Exception> R checked(Class<T1> expectedExceptionClass1,
			Class<T2> expectedExceptionClass2, T argument, UnsafeFunction<T, R> function) throws T1, T2 {
		try {
			return function.apply(argument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     argument type
	 * @param <R>                     result type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * @param <T3>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param expectedExceptionClass3 Expected exception class
	 * @param argument                Argument to
	 *                                {@link UnsafeFunction#apply(Object)}
	 * @param function                Any {@link UnsafeFunction}
	 * @return result
	 * @throws T1 If thrown by {@link UnsafeFunction#apply(Object)}
	 * @throws T2 If thrown by {@link UnsafeFunction#apply(Object)}
	 * @throws T3 If thrown by {@link UnsafeFunction#apply(Object)}
	 */
	public static <T, R, T1 extends Exception, T2 extends Exception, T3 extends Exception> R checked(
			Class<T1> expectedExceptionClass1, Class<T2> expectedExceptionClass2, Class<T3> expectedExceptionClass3,
			T argument, UnsafeFunction<T, R> function) throws T1, T2, T3 {
		try {
			return function.apply(argument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2, expectedExceptionClass3);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeBiConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #unchecked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>            first argument type
	 * @param <U>            second argument type
	 * 
	 * @param firstArgument  First argument for
	 *                       {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param secondArgument Second argument for
	 *                       {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param consumer       Any {@link UnsafeBiConsumer}
	 */
	public static <T, U> void unchecked(T firstArgument, U secondArgument, UnsafeBiConsumer<T, U> consumer) {
		try {
			consumer.accept(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw unchecked(e);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeBiConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>            first argument type
	 * @param <U>            second argument type
	 * 
	 * @param firstArgument  Argument for
	 *                       {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param secondArgument Second argument for
	 *                       {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param consumer       Any {@link UnsafeBiConsumer}
	 * @throws Exception If thrown by {@link UnsafeBiConsumer#accept(Object,Object)}
	 */
	public static <T, U> void checked(T firstArgument, U secondArgument, UnsafeBiConsumer<T, U> consumer)
			throws Exception {
		try {
			consumer.accept(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw checked(e);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeBiConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class)}.
	 * </p>
	 * 
	 * @param <T>                    first argument type
	 * @param <U>                    second argument type
	 * @param <T1>                   Expected exception type
	 * 
	 * @param expectedExceptionClass Expected exception class
	 * @param firstArgument          Argument for
	 *                               {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param secondArgument         Second argument for
	 *                               {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param consumer               Any {@link UnsafeBiConsumer}
	 * 
	 * @throws T1 If thrown by {@link UnsafeBiConsumer#accept(Object,Object)}
	 */
	public static <T, U, T1 extends Exception> void checked(Class<T1> expectedExceptionClass, T firstArgument,
			U secondArgument, UnsafeBiConsumer<T, U> consumer) throws T1 {
		try {
			consumer.accept(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeBiConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     first argument type
	 * @param <U>                     second argument type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param firstArgument           Argument for
	 *                                {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param secondArgument          Second argument for
	 *                                {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param consumer                Any {@link UnsafeBiConsumer}
	 * 
	 * @throws T1 If thrown by {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @throws T2 If thrown by {@link UnsafeBiConsumer#accept(Object,Object)}
	 */
	public static <T, U, T1 extends Exception, T2 extends Exception> void checked(Class<T1> expectedExceptionClass1,
			Class<T2> expectedExceptionClass2, T firstArgument, U secondArgument, UnsafeBiConsumer<T, U> consumer)
			throws T1, T2 {
		try {
			consumer.accept(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2);
		}
	}

	/**
	 * Gracefully supplies a value to an {@link UnsafeBiConsumer}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     first argument type
	 * @param <U>                     second argument type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * @param <T3>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param expectedExceptionClass3 Expected exception class
	 * @param firstArgument           Argument for
	 *                                {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param secondArgument          Second argument for
	 *                                {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @param consumer                Any {@link UnsafeBiConsumer}
	 * 
	 * @throws T1 If thrown by {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @throws T2 If thrown by {@link UnsafeBiConsumer#accept(Object,Object)}
	 * @throws T3 If thrown by {@link UnsafeBiConsumer#accept(Object,Object)}
	 */
	public static <T, U, T1 extends Exception, T2 extends Exception, T3 extends Exception> void checked(
			Class<T1> expectedExceptionClass1, Class<T2> expectedExceptionClass2, Class<T3> expectedExceptionClass3,
			T firstArgument, U secondArgument, UnsafeBiConsumer<T, U> consumer) throws T1, T2, T3 {
		try {
			consumer.accept(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2, expectedExceptionClass3);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeBiFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #unchecked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>            first argument type
	 * @param <U>            second argument type
	 * @param <R>            result type
	 * 
	 * @param firstArgument  First argument to
	 *                       {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param secondArgument Second argument to
	 *                       {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param function       Any {@link UnsafeFunction}
	 * @return result
	 */
	public static <T, U, R> R unchecked(T firstArgument, U secondArgument, UnsafeBiFunction<T, U, R> function) {
		try {
			return function.apply(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw unchecked(e);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeBiFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable)}.
	 * </p>
	 * 
	 * @param <T>            first argument type
	 * @param <U>            second argument type
	 * @param <R>            result type
	 * 
	 * @param firstArgument  First argument to
	 *                       {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param secondArgument Second argument to
	 *                       {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param function       Any {@link UnsafeBiFunction}
	 * @return result
	 * @throws Exception If thrown by {@link UnsafeBiFunction#apply(Object, Object)}
	 */
	public static <T, U, R> R checked(T firstArgument, U secondArgument, UnsafeBiFunction<T, U, R> function)
			throws Exception {
		try {
			return function.apply(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw checked(e);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeBiFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class)}.
	 * </p>
	 * 
	 * @param <T>                    first argument type
	 * @param <U>                    second argument type
	 * @param <R>                    result type
	 * @param <T1>                   Expected exception type
	 * 
	 * @param expectedExceptionClass Expected exception class
	 * @param firstArgument          First argument to
	 *                               {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param secondArgument         Second argument to
	 *                               {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param function               Any {@link UnsafeBiFunction}
	 * @return result
	 * @throws T1 If thrown by {@link UnsafeBiFunction#apply(Object, Object)}
	 */
	public static <T, U, R, T1 extends Exception> R checked(Class<T1> expectedExceptionClass, T firstArgument,
			U secondArgument, UnsafeBiFunction<T, U, R> function) throws T1 {
		try {
			return function.apply(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeBiFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     first argument type
	 * @param <U>                     second argument type
	 * @param <R>                     result type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param firstArgument           First argument to
	 *                                {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param secondArgument          Second argument to
	 *                                {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param function                Any {@link UnsafeBiFunction}
	 * @return result
	 * @throws T1 If thrown by {@link UnsafeBiFunction#apply(Object, Object)}
	 * @throws T2 If thrown by {@link UnsafeBiFunction#apply(Object, Object)}
	 */
	public static <T, U, R, T1 extends Exception, T2 extends Exception> R checked(Class<T1> expectedExceptionClass1,
			Class<T2> expectedExceptionClass2, T firstArgument, U secondArgument, UnsafeBiFunction<T, U, R> function)
			throws T1, T2 {
		try {
			return function.apply(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2);
		}
	}

	/**
	 * Gracefully applies an {@link UnsafeBiFunction}.
	 * 
	 * <p>
	 * Handles exceptions via {@link #checked(Throwable, Class, Class, Class)}.
	 * </p>
	 * 
	 * @param <T>                     first argument type
	 * @param <U>                     second argument type
	 * @param <R>                     result type
	 * @param <T1>                    Expected exception type
	 * @param <T2>                    Expected exception type
	 * @param <T3>                    Expected exception type
	 * 
	 * @param expectedExceptionClass1 Expected exception class
	 * @param expectedExceptionClass2 Expected exception class
	 * @param expectedExceptionClass3 Expected exception class
	 * @param firstArgument           First argument to
	 *                                {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param secondArgument          Second argument to
	 *                                {@link UnsafeBiFunction#apply(Object, Object)}
	 * @param function                Any {@link UnsafeBiFunction}
	 * @return result
	 * @throws T1 If thrown by {@link UnsafeBiFunction#apply(Object, Object)}
	 * @throws T2 If thrown by {@link UnsafeBiFunction#apply(Object, Object)}
	 * @throws T3 If thrown by {@link UnsafeBiFunction#apply(Object, Object)}
	 */
	public static <T, U, R, T1 extends Exception, T2 extends Exception, T3 extends Exception> R checked(
			Class<T1> expectedExceptionClass1, Class<T2> expectedExceptionClass2, Class<T3> expectedExceptionClass3,
			T firstArgument, U secondArgument, UnsafeBiFunction<T, U, R> function) throws T1, T2, T3 {
		try {
			return function.apply(firstArgument, secondArgument);
		} catch (Throwable e) {
			throw checked(e, expectedExceptionClass1, expectedExceptionClass2, expectedExceptionClass3);
		}
	}

	/**
	 * Gracefully initializes a component that depends on an {@link AutoCloseable}
	 * resource, {@link AutoCloseable#close() closing} the resource and suppressing
	 * any close errors before rethrowing a checked exception if the component fails
	 * to initialize.
	 * 
	 * <p>
	 * If no exception or error is thrown by {@code initializer}, then the resource
	 * remains open, and becomes the responsibility of the initialized component to
	 * close when finished with it.
	 * </p>
	 * 
	 * @param <T>         {@link AutoCloseable} resource type
	 * @param <R>         {@link UnsafeFunction} dependent component type
	 * 
	 * @param resource    Open resource
	 * @param initializer {@link UnsafeFunction} for initializing the resource
	 * @return {@code resource}, after successfully invoking
	 *         {@code initializationConsumer}
	 * @throws Exception If thrown from by {@code initializationConsumer}
	 */
	public static <T extends AutoCloseable, R> R initialize(T resource, UnsafeFunction<T, R> initializer)
			throws Exception {
		try {
			return initializer.apply(resource);
		} catch (Throwable e) {
			suppress(e, resource::close);
			throw checked(e);
		}
	}

	/**
	 * Runs an {@link UnsafeRunnable} and adds any exception thrown as suppressed by
	 * an another exception.
	 * 
	 * <p>
	 * This is useful for adding clean-up tasks to error handling routines related
	 * to failed initialization of closeable resources.
	 * </p>
	 * 
	 * @param throwable Throwable that will
	 *                  {@link Throwable#addSuppressed(Throwable) suppress}
	 *                  exceptions thrown from {@code runnable}.
	 * @param runnable  {@link UnsafeRunnable}; will be run, and any exceptions
	 *                  thrown will be suppressed by {@code throwable}
	 * @return throwable if non-null; the exception thrown from runnable or null if
	 *         no exception was thrown
	 */
	public static Throwable suppress(Throwable throwable, UnsafeRunnable runnable) {
		try {
			runnable.run();
		} catch (Throwable e) {
			if (throwable == null)
				throwable = e;
			else
				throwable.addSuppressed(e);
		}
		return throwable;
	}

	/**
	 * Runs a sequence of tasks with error suppression.
	 * 
	 * <p>
	 * All tasks are guaranteed to run, but not guaranteed to finish. After all
	 * tasks have run, the first error encountered will be thrown; all additional
	 * errors will be suppressed.
	 * </p>
	 * 
	 * @param tasks tasks to run
	 * @throws Throwable from the first task the fails in error
	 */
	public static void suppress(UnsafeRunnable... tasks) throws Throwable {
		suppress(IuIterable.iter(tasks));
	}

	/**
	 * Runs a sequence of tasks with error suppression.
	 * 
	 * <p>
	 * All tasks are guaranteed to run, but not guaranteed to finish. After all
	 * tasks have run, the first error encountered will be thrown; all additional
	 * errors will be suppressed.
	 * </p>
	 * 
	 * @param tasks tasks to run
	 * @throws Throwable from the first task the fails in error
	 */
	public static void suppress(Iterable<UnsafeRunnable> tasks) throws Throwable {
		Throwable e = null;
		for (final var task : tasks)
			e = suppress(e, task);
		if (e != null)
			throw e;
	}

	private IuException() {
	}
}
