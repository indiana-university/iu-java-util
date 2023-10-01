package edu.iu;

import java.util.function.BiFunction;

/**
 * Equivalent to {@link BiFunction}, but may throw any exception.
 * 
 * @param <T> First argument type
 * @param <U> Second argument type
 * @param <R> Return type
 */
@FunctionalInterface
public interface UnsafeBiFunction<T, U, R> {

	/**
	 * Applies two arguments to a function that uses unsafe code.
	 * 
	 * @param firstArgument The first argument.
	 * @param secondArgument The second argument.
	 * @return The result.
	 * @throws Throwable If thrown by the unsafe code.
	 */
	R apply(T firstArgument, U secondArgument) throws Throwable;

}
