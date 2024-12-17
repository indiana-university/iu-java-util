package iu.web.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.LogManager;

/**
 * Configures logging.
 */
public final class IuHttpLogger {

	/**
	 * Logging configuration constructor.
	 * 
	 * @throws IOException if logging configuration couldn't be read.
	 */
	public IuHttpLogger() throws IOException {
		LogManager.getLogManager().readConfiguration(new ByteArrayInputStream("""
				.handlers=java.util.logging.ConsoleHandler
				java.util.logging.ConsoleHandler.level=FINEST
				.level=FINEST
				""".getBytes()));
	}
}
