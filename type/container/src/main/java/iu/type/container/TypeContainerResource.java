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
class TypeContainerResource implements Comparable<TypeContainerResource> {
	static {
		IuObject.assertNotOpen(TypeContainerResource.class);
	}

	private static final Logger LOG = Logger.getLogger(TypeContainerResource.class.getName());
	private static volatile int c;

	private final int id;
	private final int priority;
	private final Thread thread;
	private volatile boolean started;
	private volatile Throwable error;

	/**
	 * Constructor.
	 * 
	 * @param resource  {@link IuResource}
	 * @param component {@link IuComponent}
	 */
	TypeContainerResource(IuResource<?> resource, IuComponent component) {
		id = ++c;

		final var erased = resource.type().autoboxClass();
		final var nameBuilder = new StringBuilder();
		nameBuilder.append(erased.getModule().getName());
		nameBuilder.append('/');
		nameBuilder.append(erased.getName());
		IuObject.convert(resource.name(), a -> nameBuilder.append('/').append(a));
		nameBuilder.append('/');
		nameBuilder.append(id);

		priority = resource.priority();

		thread = new Thread(() -> {
			final var loader = component.classLoader();
			Thread.currentThread().setContextClassLoader(loader);

			LOG.fine("init resource " + resource);
			try {
				final var init = resource.get();

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
	}

	/**
	 * Asynchronously initializes the resource.
	 * 
	 * @see #join()
	 */
	synchronized void asyncInit() {
		if (!started) {
			thread.start();
			started = true;
		}
	}

	/**
	 * Returns the priority of the resource.
	 * 
	 * @return priority
	 */
	int priority() {
		return priority;
	}

	/**
	 * Waits for a previous call to asyncInit to complete.
	 * 
	 * @throws Throwable If an error occurs
	 */
	void join() throws Throwable {
		if (!started)
			throw new IllegalStateException("not started");

		thread.join();
		if (error != null)
			throw error;
	}

	@Override
	public int compareTo(TypeContainerResource o) {
		int p1 = priority;
		int p2 = o.priority;

		if (p1 == p2)
			return Integer.compare(Math.abs(id), Math.abs(o.id));

		if (p1 >= 0 && p2 < 0)
			return -1;
		if (p1 < 0 && p2 >= 0)
			return 1;

		return Integer.compare(Math.abs(p1), Math.abs(p2));
	}

	@Override
	public String toString() {
		return "TypeContainerResource [" + thread.getName() + ", priority=" + priority + ", started=" + started
				+ ", error=" + error + "]";
	}

}
