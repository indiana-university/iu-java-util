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
package edu.iu;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SPI listener interface for receiving {@link IuObservableEvent} notifications.
 */
public interface IuListener extends UnsafeConsumer<IuObservableEvent> {

	/**
	 * Passes an {@link IuObservableEvent} instance from an upstream resource to one
	 * or more container-specific event listeners.
	 * 
	 * @param event {@link IuObservableEvent}
	 */
	static void observe(IuObservableEvent event) {
		Objects.requireNonNull(event, "event");
		Throwable error = null;

		Iterator<IuListener> serviceIterator;
		try {
			serviceIterator = ServiceLoader.load(IuListener.class, IuListener.class.getClassLoader()).iterator();
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
