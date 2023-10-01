package edu.iu;

/**
 * Equivalent to {@link Runnable}, but may throw any exception.
 */
@FunctionalInterface
public interface UnsafeRunnable {

	/**
	 * Runs unsafe code.
	 * 
	 * @throws Throwable If thrown by the unsafe code.
	 */
	void run() throws Throwable;

}
