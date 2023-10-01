package edu.iu;

import java.util.function.Function;

/**
 * Equivalent to {@link Function}, but may throw any exception.
 * 
 * @param <T> Argument type
 * @param <R> Return type
 */
@FunctionalInterface
public interface UnsafeFunction<T, R> {

	/**
	 * Applies an argument to a function that uses unsafe code.
	 * 
	 * @param argument The argument.
	 * @return The result.
	 * @throws Throwable If thrown by the unsafe code.
	 */
	R apply(T argument) throws Throwable;

}
