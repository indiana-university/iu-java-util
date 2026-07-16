/*
 * Copyright © 2026 Indiana University
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
package iu;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuListener;
import edu.iu.IuObservableEvent;

/**
 * Internal dispatcher for {@link IuListener} service providers.
 * <p>
 * Listener providers are discovered lazily with {@link ServiceLoader} and are
 * invoked synchronously in service-loader order. Failures obtaining the service
 * iterator or invoking a listener do not interrupt event publication; they are
 * collected and logged at {@link Level#WARNING} after all available listeners
 * have been invoked.
 */
public final class ListenerSpi {

	private static volatile ServiceLoader<IuListener> serviceLoader;

	private ListenerSpi() {
	}

	private static synchronized ServiceLoader<IuListener> serviceLoader() {
		if (serviceLoader == null)
			serviceLoader = ServiceLoader.load(IuListener.class, IuListener.class.getClassLoader());
		return serviceLoader;
	}

	/**
	 * Publishes an event to each available {@link IuListener} service provider.
	 * Listener failures are isolated so that the remaining providers can receive the
	 * event.
	 * 
	 * @param event event to publish
	 * @throws NullPointerException if {@code event} is {@code null}
	 */
	public static void observe(IuObservableEvent event) {
		Objects.requireNonNull(event, "event");
		Throwable error = null;

		Iterator<IuListener> serviceIterator;
		try {
			serviceIterator = serviceLoader().iterator();
		} catch (Throwable e) {
			error = IuException.suppress(error, e);
			serviceIterator = Collections.emptyIterator();
		}

		while (serviceIterator.hasNext())
			try {
				serviceIterator.next().accept(event);
			} catch (Throwable e) {
				error = IuException.suppress(error, e);
			}

		if (error != null) {
			final var logger = Logger.getLogger(IuListener.class.getName());
			if (logger.isLoggable(Level.WARNING))
				logger.log(Level.WARNING, "event listener failure; " + event, error);
		}
	}

}
