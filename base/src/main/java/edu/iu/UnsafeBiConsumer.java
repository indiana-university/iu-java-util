package edu.iu;

import java.util.function.BiConsumer;

/**
 * Equivalent to {@link BiConsumer}, but may throw any exception.
 * 
 * @param <T> First argument type
 * @param <U> Second argument type
 */
@FunctionalInterface
public interface UnsafeBiConsumer<T, U> {

	/**
	 * Accepts two arguments using unsafe code.
	 * 
	 * @param firstArgument The argument.
	 * @param secondArgument The argument.
	 * @throws Throwable If thrown by the unsafe code.
	 */
	void accept(T firstArgument, U secondArgument) throws Throwable;

}
