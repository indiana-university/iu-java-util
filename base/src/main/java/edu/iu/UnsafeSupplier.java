package edu.iu;

import java.util.function.Supplier;

/**
 * Equivalent to {@link Supplier}, but may throw any exception.
 * 
 * @param <T> Result type
 */
@FunctionalInterface
public interface UnsafeSupplier<T> {

	/**
	 * Gets a result using unsafe code.
	 * 
	 * @return The result.
	 * @throws Throwable If thrown by the unsafe code.
	 */
	T get() throws Throwable;

}
