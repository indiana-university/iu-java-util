package edu.iu;

import java.util.function.Consumer;

/**
 * Equivalent to {@link Consumer}, but may throw any exception.
 * 
 * @param <T> Argument type
 */
@FunctionalInterface
public interface UnsafeConsumer<T> {

	/**
	 * Accepts an argument using unsafe code.
	 * 
	 * @param argument The argument.
	 * @throws Throwable If thrown by the unsafe code.
	 */
	void accept(T argument) throws Throwable;

}
