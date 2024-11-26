package iu.type.container;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuObject;
import edu.iu.UnsafeRunnable;
import edu.iu.type.IuComponent;
import edu.iu.type.IuResource;

/**
 * Manages {@link IuResource} instances in behalf of bootstrapped
 * {@link IuComponent}s.
 */
class TypeContainerResource {
	static {
		IuObject.assertNotOpen(TypeContainerResource.class);
	}

	private static final Logger LOG = Logger.getLogger(TypeContainerResource.class.getName());
	private static volatile int c;

	private final Thread thread;
	private volatile Throwable error;

	/**
	 * Constructor.
	 * 
	 * @param resource  {@link IuResource}
	 * @param component {@link IuComponent}
	 */
	TypeContainerResource(IuResource<?> resource, IuComponent component) {
		final var erased = resource.type().autoboxClass();
		final var nameBuilder = new StringBuilder();
		nameBuilder.append(erased.getModule().getName());
		nameBuilder.append('/');
		nameBuilder.append(resource.name());
		nameBuilder.append('/');
		nameBuilder.append(++c);
		thread = new Thread(() -> {
			Thread.currentThread().setContextClassLoader(component.classLoader());
			LOG.fine("init resource " + resource);
			try {
				final var init = resource.get();

				// TODO: set @Resource fields
				// TODO: invoke @Resource property setters
				// TODO: invoke @PostConstruct methods

				if (init instanceof Runnable)
					((Runnable) init).run();
				else if (init instanceof UnsafeRunnable)
					((UnsafeRunnable) init).run();
				
			} catch (Throwable e) {
				LOG.log(Level.SEVERE, "fail resource " + resource, e);
				error = e;
			}
		}, nameBuilder.toString());
		thread.start();
	}

	/**
	 * Joins the current thread with the resource's control thread, returning once
	 * the resource is complete.
	 * 
	 * @throws Throwable If an error occurs
	 */
	void join() throws Throwable {
		thread.join();
		if (error != null)
			throw error;
	}
}
