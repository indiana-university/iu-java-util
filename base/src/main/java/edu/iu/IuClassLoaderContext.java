package edu.iu;

import iu.ClassLoaderContext;

/**
 * Provides descriptive metadata per application context {@link ClassLoader}.
 */
public interface IuClassLoaderContext {

	/**
	 * Registers a {@link IuClassLoaderContext} instance.
	 * 
	 * <p>
	 * MAY be invoked exactly once per {@link ClassLoader}, at initialization time.
	 * </p>
	 * 
	 * @param context {@link IuClassLoaderContext}
	 * @param loader  {@link ClassLoader}
	 */
	static void register(IuClassLoaderContext context, ClassLoader loader) {
		ClassLoaderContext.register(context, loader);
	}

	/**
	 * Gets the context registered for the current thread.
	 * 
	 * @return {@link IuClassLoaderContext}
	 */
	static IuClassLoaderContext getContext() {
		return ClassLoaderContext.get();
	}

	/**
	 * Gets the context name.
	 * 
	 * @return context name
	 */
	String getName();

}
