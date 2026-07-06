package edu.iu;

import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SPI listener interface for receiving {@link IuObservableEvent} notifications.
 */
public interface IuListener extends UnsafeConsumer<IuObservableEvent> {

	/**
	 * Passes an {@link IuObservableEvent} instance from an an upstream resource to
	 * one or more container-specific event listeners.
	 * 
	 * @param event {@link IuObservableEvent}
	 */
	static void observe(IuObservableEvent event) {
		Throwable error = null;

		for (final var listener : ServiceLoader.load(IuListener.class))
			error = IuException.suppress(error, () -> listener.accept(event));

		if (error != null) {
			final var logger = Logger.getLogger(IuListener.class.getName());
			if (logger.isLoggable(Level.CONFIG))
				logger.log(Level.CONFIG, "event listener failure; " + event, error);
		}
	}

}
