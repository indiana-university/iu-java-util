package iu;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import edu.iu.IuClassLoaderContext;
import edu.iu.IuObject;

/**
 * Internal instance manager for {@link IuClassLoaderContext}
 */
public class ClassLoaderContext {

	private static final IuClassLoaderContext BOOT = () -> "boot";
	private static final IuClassLoaderContext PLATFORM = () -> "platform";
	private static final IuClassLoaderContext SYSTEM = () -> "system";
	private static final Map<ClassLoader, IuClassLoaderContext> CONTEXT;

	static {
		IuObject.assertNotOpen(ClassLoaderContext.class);
		final Map<ClassLoader, IuClassLoaderContext> context = new WeakHashMap<>();
		context.put(ClassLoader.getPlatformClassLoader(), PLATFORM);
		context.put(ClassLoader.getSystemClassLoader(), SYSTEM);
		CONTEXT = context;
	}

	/**
	 * Registers a context with a {@link ClassLoader}
	 * 
	 * @param context {@link IuClassLoaderContext}
	 * @param loader  {@link ClassLoader}
	 */
	public static void register(IuClassLoaderContext context, ClassLoader loader) {
		Objects.requireNonNull(loader, "loader");
		Objects.requireNonNull(context, "context");
		synchronized (CONTEXT) {
			if (CONTEXT.get(loader) != null)
				throw new IllegalStateException("already registered");
			else
				CONTEXT.put(loader, context);
		}
	}

	/**
	 * Gets the context registered for the current thread.
	 * 
	 * @return {@link IuClassLoaderContext}
	 */
	public static IuClassLoaderContext get() {
		var loader = Thread.currentThread().getContextClassLoader();

		while (loader != null) {
			final IuClassLoaderContext context;
			synchronized (CONTEXT) {
				context = CONTEXT.get(loader);
			}
			if (context != null)
				return context;
			loader = loader.getParent();
		}

		return BOOT;

	}

	private ClassLoaderContext() {
	}

}
